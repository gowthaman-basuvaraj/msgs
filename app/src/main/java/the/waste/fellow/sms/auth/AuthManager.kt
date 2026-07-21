package the.waste.fellow.sms.auth

import android.content.Context
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenResponse
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Persists the AppAuth [AuthState] (access + refresh tokens, token endpoint config) across
 * process restarts and hands out fresh access tokens, transparently refreshing via the
 * refresh token when the access token has expired. This replaces the manual token paste.
 */
class AuthManager(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var authState: AuthState = readState()
        private set

    val isAuthorized: Boolean
        get() = authState.isAuthorized

    /** The signed-in username (preferred_username claim from the id token), if available. */
    val userName: String?
        get() = authState.parsedIdToken?.additionalClaims?.get("preferred_username") as? String

    fun updateAfterAuthorization(response: AuthorizationResponse?, ex: Exception?) {
        authState.update(response, ex as? net.openid.appauth.AuthorizationException)
        persist()
    }

    fun updateAfterTokenResponse(response: TokenResponse?, ex: Exception?) {
        authState.update(response, ex as? net.openid.appauth.AuthorizationException)
        persist()
    }

    fun signOut() {
        authState = AuthState()
        persist()
    }

    /**
     * Returns a valid access token, refreshing it first if needed, or null if not signed in
     * or the refresh failed. Safe to call from a background coroutine (e.g. SyncWorker).
     */
    suspend fun freshAccessToken(): String? {
        if (!authState.isAuthorized) return null
        val service = AuthorizationService(app)
        return try {
            suspendCancellableCoroutine { cont ->
                authState.performActionWithFreshTokens(service) { accessToken, _, _ ->
                    persist()
                    cont.resume(accessToken)
                }
            }
        } finally {
            service.dispose()
        }
    }

    private fun persist() {
        prefs.edit().putString(KEY_STATE, authState.jsonSerializeString()).apply()
    }

    private fun readState(): AuthState = try {
        val json = prefs.getString(KEY_STATE, null)
        if (json.isNullOrBlank()) AuthState() else AuthState.jsonDeserialize(json)
    } catch (e: Exception) {
        AuthState()
    }

    companion object {
        private const val PREFS_NAME = "auth_state"
        private const val KEY_STATE = "state"

        /** Custom-scheme redirect registered with Keycloak (see manifestPlaceholders). */
        const val REDIRECT_URI = "the.waste.fellow.sms:/oauth2redirect"
    }
}
