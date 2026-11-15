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
        return request("/auth/v1/signup?redirect_to=" + java.net.URLEncoder.encode("dartvision://auth-callback", "UTF-8"), JSONObject().put("email", email).put("password", password))
    }

    fun getUser(accessToken: String): AuthResult {
        val json = requestRawGet("/auth/v1/user", accessToken)
        return AuthResult(accessToken, null, json.optString("id").ifBlank { null }, json.optString("email").ifBlank { null })
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

    fun loadProfile(accessToken: String, userId: String): JSONObject? {
        val id = URLEncoder.encode(userId, "UTF-8")
        val rows = restRequest("GET", "/rest/v1/profiles?id=eq.$id&select=id,display_name,username", accessToken)
        return rows.optJSONObject(0)
    }

    fun saveProfile(accessToken: String, userId: String, displayName: String): JSONObject {
        require(displayName.isNotBlank()) { "Le nom du profil ne peut pas être vide." }
        val body = JSONObject().put("id", userId).put("display_name", displayName.trim())
        val rows = restRequest("POST", "/rest/v1/profiles?on_conflict=id", accessToken, body,
            "resolution=merge-duplicates,return=representation")
        return rows.optJSONObject(0) ?: body
    }

    fun savePlayerGame(accessToken: String, userId: String, game: JSONObject): JSONObject {
        val body = JSONObject()
            .put("user_id", userId)
            .put("game_mode", game.optString("game_mode", "X01"))
            .put("result", game.optString("result", "completed"))
            .put("started_at", game.optString("started_at"))
            .put("finished_at", game.optString("finished_at"))
            .put("summary", game.optJSONObject("summary") ?: JSONObject())
            .put("game_data", game.optJSONObject("game_data") ?: JSONObject())
        val rows = restRequest("POST", "/rest/v1/player_game_history", accessToken, body, "return=representation")
        return rows.optJSONObject(0) ?: body
    }

    fun loadPlayerGames(accessToken: String, userId: String, limit: Int = 50): JSONArray {
        val id = URLEncoder.encode(userId, "UTF-8")
        return restRequest("GET", "/rest/v1/player_game_history?user_id=eq.$id&select=*&order=finished_at.desc&limit=${limit.coerceIn(1,100)}", accessToken)
    }

    private fun restRequest(method: String, path: String, accessToken: String, body: JSONObject? = null, prefer: String? = null): JSONArray {
        val connection = (URL(projectUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (prefer != null) setRequestProperty("Prefer", prefer)
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val error = runCatching { JSONObject(response).let { it.optString("message").ifBlank { it.optString("hint") } } }.getOrDefault("")
                throw IllegalStateException(error.ifBlank { "Synchronisation du compte impossible (HTTP $status)." })
            }
            return if (response.isBlank()) JSONArray() else JSONArray(response)
        } finally { connection.disconnect() }
    }

    private fun requestRawGet(path: String, accessToken: String): JSONObject {
        val connection = (URL(projectUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Impossible de valider le compte (HTTP $status).")
            return JSONObject(response)
        } finally { connection.disconnect() }
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
