# SMS Forwarder — Setup Guide

End-to-end setup for the three pieces:

| Component | Repo | Role |
| --- | --- | --- |
| **Android app** | `msgs` (this repo) | Default SMS app; forwards received SMS to the server |
| **API server** | `sms_web_api` | Stores + serves messages, broadcasts new ones over socket.io |
| **Web app** | `sms_web_app` | Browser UI to read messages / OTPs |

Auth is Keycloak (OIDC) across all three. Do the setup in this order:
**Keycloak → API server → Web app → Android app.**

---

## 1. Keycloak

You need a realm, a public client (used by both the web and Android apps), a role, and a
user that holds that role.

### 1.1 Realm
Create a realm, e.g. **`sms`**. (You can reuse an existing realm; just substitute its name
everywhere below.)

### 1.2 Realm role
Realm roles → Create role → name it **`READ_SMS`**. This is the role that grants access to
read messages. Assign it to your user:
Users → *your user* → Role mapping → Assign role → `READ_SMS`.

### 1.3 Client (public, PKCE)
Clients → Create client:

- **Client type:** OpenID Connect
- **Client ID:** `sms-app` (used as `VITE_KEYCLOAK_CLIENTID` and the Android "Client id")
- **Client authentication:** **Off** (public client)
- **Standard flow:** **On** (Authorization Code)
- **Direct access grants:** off (not needed)

Then on the client's **Settings**:

- **Valid redirect URIs** (add both):
  - `the.waste.fellow.sms:/oauth2redirect`  ← Android app (custom scheme)
  - `http://localhost:5173/*`  ← web app dev (add your production URL too, e.g. `https://sms.example.com/*`)
- **Valid post logout redirect URIs:** `http://localhost:5173/*` (+ prod)
- **Web origins:** `http://localhost:5173` (+ prod) — needed for browser CORS
- **Advanced → Proof Key for Code Exchange (PKCE):** `S256`

### 1.4 Refresh tokens (Android offline login)
The Android app requests the `offline_access` scope so it gets a long-lived refresh token
and doesn't need re-login. `offline_access` is a default client scope in Keycloak — confirm
it's listed under the client's **Client scopes** (Default). Nothing else needed.

### 1.5 What the token must contain
The server and apps expect these claims (Keycloak provides them by default):
- `preferred_username` — becomes the account name messages are stored under
- `realm_access.roles` — must include `READ_SMS`
- `iss` — `https://<keycloak-host>/realms/sms`

---

## 2. API server (`sms_web_api`)

### 2.1 Requirements
- **Node 20 LTS recommended.** `better-sqlite3` builds native code and does **not** compile
  on very new Node (e.g. 26). Use `nvm use 20`.
- Build tools for native modules (`build-essential` / Xcode CLT / windows-build-tools).

### 2.2 Environment (`.env`)
```dotenv
SERVER_PORT=3000
SERVER_HOST=0.0.0.0            # 0.0.0.0 so your phone/LAN can reach it
DATABASE_PATH=./messages.db
CRON_SCHEDULE=0 * * * *        # prune job cadence
RETENTION_PERIOD_HOURS=48     # delete messages older than this

# CORS + realtime origin: the web app's URL
CORS_ORIGIN=http://localhost:5173

# Keycloak token verification (either set URL+REALM, or KEYCLOAK_JWKS_URI directly)
KEYCLOAK_URL=https://auth.example.com
KEYCLOAK_REALM=sms
# KEYCLOAK_JWKS_URI=https://auth.example.com/realms/sms/protocol/openid-connect/certs
# KEYCLOAK_ISSUER=https://auth.example.com/realms/sms   # defaults to URL/realm

# Optional: require this realm role (leave unset to accept any valid token)
REALM_ACCESS=READ_SMS
```

Notes:
- The server verifies every request's Bearer token against Keycloak's JWKS and (if
  `REALM_ACCESS` is set) checks the role. Without `KEYCLOAK_URL`+`KEYCLOAK_REALM` (or
  `KEYCLOAK_JWKS_URI`) it refuses to start.
- The socket.io channel is authenticated the same way and its CORS is locked to
  `CORS_ORIGIN`.

### 2.3 Run
```bash
nvm use 20
npm install
node messages.js       # or: npx nodemon messages.js
```
Serves on `http://<host>:3000`. It auto-creates `messages.db`.

---

## 3. Web app (`sms_web_app`)

### 3.1 Environment (`.env`)
```dotenv
VITE_KEYCLOAK_URL=https://auth.example.com
VITE_KEYCLOAK_REALM=sms
VITE_KEYCLOAK_CLIENTID=sms-app
VITE_REALM_ACCESS=READ_SMS
VITE_BACKEND_API=http://localhost:3000
```
(`VITE_*` values are compiled into the browser bundle — they're public config, not secrets.)

### 3.2 Run
```bash
npm install
npm run dev        # http://localhost:5173
# or
npm run build && npm run preview
```
On load it redirects to Keycloak; after login you must hold `READ_SMS` or you'll see the
access-denied screen. **Logging into the web app once also registers your user on the
server** (it calls `POST /user`) — see the gotcha in §5.

---

## 4. Android app (`msgs`)

Nothing here is hard-coded — configure it in-app. Build/install the app, set it as the
default SMS app, then:

**Settings → Server sync**
- **Forward messages to server:** on
- **Server URL:** `http://<server-host>:3000` (or `https://…`)
- **SIM label:** optional (shown per message)

**Settings → Sign in (Keycloak)**
- **Keycloak issuer URL:** `https://auth.example.com/realms/sms`
- **Client id:** `sms-app`
- **Sign in:** launches the browser login; after success it refreshes tokens automatically
- **Account username:** leave blank (defaults to your `preferred_username`)
- **Manual bearer token:** leave blank (only a fallback if you don't sign in)

The app's OAuth redirect is `the.waste.fellow.sms:/oauth2redirect` — this must be in the
Keycloak client's Valid redirect URIs (§1.3).

> **Cleartext HTTP:** the app allows plain `http://` so a self-hosted LAN server works. For
> anything beyond your LAN, use `https://` — the Bearer token and SMS text would otherwise
> travel in the clear.

---

## 5. Gotchas / order of operations

1. **Register the user before Android sync works.** The server's `/send-message` requires
   the target user to already exist in its `users` table. The **web app creates it on first
   login**. So: log into the web app once with your account *before* (or shortly after)
   turning on Android sync. Until then, forwarded messages stay queued on the phone and
   retry (they aren't lost).
2. **Keycloak client must be public + PKCE S256**, with both redirect URIs registered, or
   login fails on one of the apps.
3. **Node version for the server** — use 20 LTS; newer Node breaks the `better-sqlite3`
   native build.
4. **Same realm everywhere** — `KEYCLOAK_REALM`, `VITE_KEYCLOAK_REALM`, and the Android
   issuer must all point at the same realm, or the server rejects tokens on the issuer check.
5. **Messages auto-expire** after `RETENTION_PERIOD_HOURS` (default 48h) — this is an
   ephemeral OTP viewer, not an archive.

---

## 6. Quick smoke test

1. Start Keycloak, the API server, and the web app.
2. Open the web app, log in (registers your user).
3. In the Android app: enable sync, sign in, send yourself an SMS (or `adb emu sms send`).
4. The message should appear in the web app's list in real time, with a sound/notification
   if the sender isn't muted.

---

## 7. Production: run as systemd services

This assumes a Linux host with:

- Code deployed to **`/opt/sms/sms_web_api`** and **`/opt/sms/sms_web_app`**
- A dedicated unprivileged user **`sms`**
- **Node 20 LTS installed system-wide** at `/usr/bin/node` (e.g. via
  [NodeSource](https://github.com/nodesource/distributions)) — nvm paths don't work well
  under systemd. Verify with `which node` and adjust `ExecStart` paths if different.

### 7.0 One-time host prep
```bash
# dedicated service account with its OWN writable home at /opt/sms (needed so npm's cache
# has somewhere to live — don't use bare /opt as the home, it's root-owned).
sudo useradd --system --create-home --home-dir /opt/sms --shell /usr/sbin/nologin sms

# deploy code (git clone / rsync) into /opt/sms/sms_web_api and /opt/sms/sms_web_app

# IMPORTANT: install deps AS the sms user, never as root. If a root-owned node_modules
# already exists, the sms user can't replace it and the install breaks (see Troubleshooting).
sudo chown -R sms:sms /opt/sms

# better-sqlite3 ships a prebuilt binary for Node 20 (no compiler needed). Only if your
# platform has no prebuilt (ARM, Alpine/musl) install build tools first:
#   sudo apt-get install -y build-essential python3

# API deps
cd /opt/sms/sms_web_api && sudo -u sms npm ci

# Web: install deps, build the static bundle, and a static server to serve it
cd /opt/sms/sms_web_app && sudo -u sms npm ci && sudo -u sms npm run build
sudo npm install -g serve         # provides /usr/bin/serve (check with `which serve`)
```
Put each app's `.env` (from §2.2 and §3.1) in its directory: `/opt/sms/sms_web_api/.env` and
`/opt/sms/sms_web_app/.env` (the web `.env` is only needed at build time).

> **Troubleshooting — `better-sqlite3` install fails with `Cannot find module …/minimist`
> (or `gauge`) / `prebuild-install || node-gyp rebuild` errors:** the `node_modules` tree is
> partial or was created by a different user (often root). Clean it and reinstall as `sms`:
> ```bash
> cd /opt/sms/sms_web_api
> sudo rm -rf node_modules            # keep package-lock.json
> sudo chown -R sms:sms /opt/sms/sms_web_api
> sudo -u sms npm ci
> ```
> A clean `npm ci` on Node 20 fetches the prebuilt binary and does not compile. If it still
> drops to `node-gyp`, you're missing a prebuilt for your arch — install
> `build-essential python3` and retry.

> **Troubleshooting — `npm error code EACCES … mkdir … /opt/.npm` / "cache folder contains
> root-owned files":** npm's cache lives in the `sms` user's home, but that home isn't writable
> by `sms` (e.g. it was set to the root-owned `/opt`). Point the account at a home it owns
> (`/opt/sms`) and reinstall:
> ```bash
> sudo usermod -d /opt/sms sms                    # set home to /opt/sms (no -m — don't move /opt!)
> sudo mkdir -p /opt/sms && sudo chown -R sms:sms /opt/sms
> cd /opt/sms/sms_web_api && sudo rm -rf node_modules && sudo -u sms npm ci
> ```

### 7.1 API — `/etc/systemd/system/sms-web-api.service`
```ini
[Unit]
Description=SMS Web API
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=sms
Group=sms
WorkingDirectory=/opt/sms/sms_web_api
# The app loads /opt/sms/sms_web_api/.env itself (via dotenv).
ExecStart=/usr/bin/node messages.js
Environment=NODE_ENV=production
Restart=on-failure
RestartSec=5

# Hardening (sane defaults)
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/sms/sms_web_api        # needed to write messages.db
ProtectKernelTunables=true
ProtectControlGroups=true
RestrictSUIDSGID=true

[Install]
WantedBy=multi-user.target
```

### 7.2 Web — `/etc/systemd/system/sms-web.service`
Serves the built `dist/` as a static SPA. `serve -s` does the history-API fallback that
React Router needs for deep links.
```ini
[Unit]
Description=SMS Web App (static SPA)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=sms
Group=sms
WorkingDirectory=/opt/sms/sms_web_app
ExecStart=/usr/bin/serve -s dist -l 5173
Restart=on-failure
RestartSec=5

# Hardening (sane defaults) — static server needs no write access
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ProtectKernelTunables=true
ProtectControlGroups=true
RestrictSUIDSGID=true

[Install]
WantedBy=multi-user.target
```

### 7.3 Enable + operate
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now sms-web-api sms-web

systemctl status sms-web-api sms-web
journalctl -u sms-web-api -f          # live logs
```
After redeploying the web app, rebuild and restart:
```bash
cd /opt/sms/sms_web_app && sudo -u sms npm ci && sudo -u sms npm run build
sudo systemctl restart sms-web
```

### 7.4 TLS / reverse proxy (recommended)
Both services above listen over plain HTTP. Because the Android app sends its Bearer token
and SMS text to the API, put a TLS-terminating reverse proxy in front for anything beyond a
trusted LAN, then point the apps at the `https://` URLs.

Minimal **Caddy** example (`/etc/caddy/Caddyfile`) — automatic HTTPS:
```caddy
sms.example.com {
    reverse_proxy localhost:5173          # web app
}
api.example.com {
    reverse_proxy localhost:3000          # API (proxies socket.io too)
}
```
If you use a proxy, update `CORS_ORIGIN` (API `.env`), the Keycloak client's redirect
URIs / web origins, `VITE_BACKEND_API` (web `.env`), and the Android **Server URL** /
**issuer** to the `https://` hostnames.

**nginx alternative for the web** (instead of the `sms-web` service): serve `dist/`
directly with an SPA fallback —
```nginx
root /opt/sms/sms_web_app/dist;
location / { try_files $uri /index.html; }
```
