package com.dartvision.mobile

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Supabase Auth REST adapter.
 * Call from a background thread; never log passwords, access tokens or refresh tokens.
 * The publishable key is designed to be embedded in client applications.
 */
class SupabaseAuthClient(
    private val projectUrl: String = BuildConfig.SUPABASE_URL,
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
) {
    data class AuthResult(
        val accessToken: String?,
        val refreshToken: String?,
        val userId: String?,
        val email: String?
    )

    fun signUp(email: String, password: String): AuthResult =
        request("/auth/v1/signup", JSONObject().put("email", email).put("password", password))

    fun signIn(email: String, password: String): AuthResult =
        request("/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password))

    private fun request(path: String, body: JSONObject): AuthResult {
        val connection = (URL(projectUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("msg").ifBlank { JSONObject(response).optString("message") } }.getOrDefault("")
                throw IllegalStateException(message.ifBlank { "Erreur Supabase (HTTP $status)" })
            }
            val json = JSONObject(response)
            val user = json.optJSONObject("user") ?: JSONObject()
            return AuthResult(
                json.optString("access_token").ifBlank { null },
                json.optString("refresh_token").ifBlank { null },
                user.optString("id").ifBlank { null },
                user.optString("email").ifBlank { null }
            )
        } finally {
            connection.disconnect()
        }
    }
}
