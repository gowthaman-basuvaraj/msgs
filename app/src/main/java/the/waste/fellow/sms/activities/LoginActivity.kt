package the.waste.fellow.sms.activities

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import the.waste.fellow.sms.auth.AuthManager
import the.waste.fellow.sms.sync.HttpSmsSyncRepository
import the.waste.fellow.sms.utils.AppSettings

/**
 * Headless activity that drives the Keycloak Authorization Code + PKCE login: discovers the
 * realm endpoints, opens the login in a Custom Tab, then exchanges the returned code for
 * tokens and stores them via [AuthManager]. Started from Settings' "Sign in".
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var authService: AuthorizationService
    private lateinit var authManager: AuthManager

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data == null) {
            finishWith("Login cancelled")
            return@registerForActivityResult
        }
        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        authManager.updateAfterAuthorization(response, ex)

        if (response != null) {
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenEx ->
                authManager.updateAfterTokenResponse(tokenResponse, tokenEx)
                if (tokenResponse != null && authManager.isAuthorized) {
                    HttpSmsSyncRepository.scheduleSync(this, immediate = true)
                    finishWith("Signed in as ${authManager.userName ?: "user"}")
                } else {
                    Log.w(TAG, "Token exchange failed", tokenEx)
                    finishWith("Sign-in failed: ${tokenEx?.errorDescription ?: "token exchange error"}")
                }
            }
        } else {
            Log.w(TAG, "Authorization failed", ex)
            finishWith("Sign-in failed: ${ex?.errorDescription ?: "authorization error"}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)
        authService = AuthorizationService(this)

        val settings = AppSettings(this)
        if (!settings.oidcConfigured) {
            finishWith("Set the Keycloak issuer and client id first")
            return
        }

        AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(settings.syncIssuer)) { config, ex ->
            if (config == null) {
                Log.w(TAG, "Discovery failed", ex)
                finishWith("Could not reach Keycloak: ${ex?.errorDescription ?: "discovery failed"}")
                return@fetchFromIssuer
            }
            val request = AuthorizationRequest.Builder(
                config,
                settings.syncClientId,
                ResponseTypeValues.CODE,
                Uri.parse(AuthManager.REDIRECT_URI)
            ).setScopes("openid", "profile", "email", "offline_access").build()

            authLauncher.launch(authService.getAuthorizationRequestIntent(request))
        }
    }

    private fun finishWith(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        if (this::authService.isInitialized) authService.dispose()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}
