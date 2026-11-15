package com.dartvision.mobile

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Supabase Auth REST client. Call only from a background thread. */
class SupabaseAuthClient(
    private val projectUrl: String = BuildConfig.SUPABASE_URL,
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
) {
    data class AuthResult(val accessToken: String?, val refreshToken: String?, val userId: String?, val email: String?)

    fun signUp(email: String, password: String): AuthResult {
        require(email.contains("@")) { "Adresse e-mail invalide." }
        require(password.length >= 8) { "Le mot de passe doit contenir au moins 8 caractères." }
        return request("/auth/v1/signup", JSONObject().put("email", email).put("password", password))
    }

    fun signIn(email: String, password: String): AuthResult =
        request("/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password))

    fun requestPhoneOtp(phone: String): AuthResult =
        request("/auth/v1/otp", JSONObject().put("phone", phone).put("create_user", true))

    fun verifyPhoneOtp(phone: String, token: String): AuthResult {
        require(token.length in 4..10) { "Code SMS invalide." }
        return request("/auth/v1/verify", JSONObject().put("phone", phone).put("token", token).put("type", "sms"))
    }

    private fun request(path: String, body: JSONObject): AuthResult {
        val json = requestRaw(path, body)
        val user = json.optJSONObject("user") ?: JSONObject()
        return AuthResult(json.optString("access_token").ifBlank { null },
            json.optString("refresh_token").ifBlank { null },
            user.optString("id").ifBlank { null },
            user.optString("email").ifBlank { null })
    }

    private fun requestRaw(path: String, body: JSONObject): JSONObject {
        val connection = (URL(projectUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 20000; doOutput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val error = runCatching { JSONObject(response).let { it.optString("msg").ifBlank { it.optString("message").ifBlank { it.optString("error_description") } } } }.getOrDefault("")
                throw IllegalStateException(error.ifBlank { "Erreur Supabase (HTTP $status)." })
            }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally { connection.disconnect() }
    }
}
