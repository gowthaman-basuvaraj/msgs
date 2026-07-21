package the.waste.fellow.sms.sync

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal HTTP client for the sms_web_api server — no networking dependency, just
 * HttpURLConnection + org.json. Posts a received message to `POST /send-message`.
 */
object SyncApi {

    private const val TAG = "SyncApi"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    /** Classifies an upload attempt so the worker knows whether to drop, keep, or retry. */
    enum class Outcome {
        /** 2xx — uploaded, drop from queue. */
        SUCCESS,

        /** Permanent client error (e.g. 400/404 — unknown user) — dropping avoids a poison pill. */
        PERMANENT_FAILURE,

        /** Transient (network, 401/403 expired token, 5xx) — keep and retry later. */
        TRANSIENT_FAILURE,
    }

    fun postSendMessage(
        baseUrl: String,
        token: String,
        userName: String,
        sender: String,
        text: String,
        sim: String,
    ): Outcome {
        val body = JSONObject()
            .put("userName", userName)
            .put("sender", sender)
            .put("text", text)
            .put("sim", sim)
            .toString()

        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$baseUrl/send-message")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "SMS Forwarder App")
                if (token.isNotEmpty()) setRequestProperty("Authorization", "Bearer $token")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            when {
                code in 200..299 -> Outcome.SUCCESS
                code == 401 || code == 403 -> {
                    Log.w(TAG, "Auth rejected ($code) — token likely expired; will retry")
                    Outcome.TRANSIENT_FAILURE
                }
                code in 400..499 -> {
                    val err = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                    Log.w(TAG, "Permanent failure $code: $err")
                    Outcome.PERMANENT_FAILURE
                }
                else -> {
                    Log.w(TAG, "Server error $code — will retry")
                    Outcome.TRANSIENT_FAILURE
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network failure — will retry", e)
            Outcome.TRANSIENT_FAILURE
        } finally {
            connection?.disconnect()
        }
    }
}
