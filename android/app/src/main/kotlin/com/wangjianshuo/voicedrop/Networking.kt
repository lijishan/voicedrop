package com.wangjianshuo.voicedrop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

class API {
    companion object {
        const val HOST = "jianshuo.dev"
        const val FILES_BASE = "https://$HOST/files/api"
        const val AGENT_BASE = "https://$HOST/agent"
        const val RECO_BASE = "https://$HOST/reco"

        fun wsEdit(stem: String) = "wss://$HOST/agent/edit?stem=$stem"
        fun wsCommand() = "wss://$HOST/agent/command"
        fun wsStatus() = "wss://$HOST/agent/status"
        fun wsAsr() = "wss://$HOST/agent/asr"
    }
}

class HttpClient(private val auth: AuthStore) {
    private val gson = com.google.gson.Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @PublishedApi
    internal fun buildRequest(method: String, path: String, body: Any? = null): Request {
        return Request.Builder()
            .url(path)
            .header("Authorization", "Bearer ${auth.anonToken}")
            .also { builder ->
                when {
                    body is ByteArray -> {
                        builder.method(method, RequestBody.create("application/octet-stream".toMediaType(), body))
                    }
                    body != null -> {
                        val json = gson.toJson(body)
                        builder.method(method, RequestBody.create(jsonType, json))
                    }
                    method == "GET" || method == "DELETE" -> builder.method(method, null)
                    else -> builder.method(method, RequestBody.create(jsonType, "{}"))
                }
            }
            .build()
    }

    @PublishedApi
    internal fun <T> parseResponse(response: Response, type: java.lang.reflect.Type): T {
        val bodyString = response.body?.string() ?: ""
        return when {
            response.isSuccessful -> {
                if (type == Unit::class.java) {
                    @Suppress("UNCHECKED_CAST")
                    (Unit as T)
                } else if (bodyString.isBlank()) {
                    @Suppress("UNCHECKED_CAST")
                    (null as T)
                } else {
                    gson.fromJson(bodyString, type)
                }
            }
            response.code == 401 -> throw AuthException("unauthorized")
            else -> throw ApiException(response.code, bodyString)
        }
    }

    internal suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val request = buildRequest("GET", path)
        client.newCall(request).execute().use { response ->
            val type = object : com.google.gson.reflect.TypeToken<T>() {}.type
            parseResponse(response, type)
        }
    }

    suspend fun getRaw(path: String): Response = withContext(Dispatchers.IO) {
        val request = buildRequest("GET", path)
        client.newCall(request).execute()
    }

    internal suspend inline fun <reified T> post(path: String, body: Any? = null): T = withContext(Dispatchers.IO) {
        val request = buildRequest("POST", path, body)
        client.newCall(request).execute().use { response ->
            val type = object : com.google.gson.reflect.TypeToken<T>() {}.type
            parseResponse(response, type)
        }
    }

    internal suspend inline fun <reified T> putJson(path: String, body: Any? = null): T = withContext(Dispatchers.IO) {
        val request = buildRequest("PUT", path, body)
        client.newCall(request).execute().use { response ->
            val type = object : com.google.gson.reflect.TypeToken<T>() {}.type
            parseResponse(response, type)
        }
    }

    suspend fun put(path: String, body: ByteArray): Response = withContext(Dispatchers.IO) {
        val request = buildRequest("PUT", path, body)
        client.newCall(request).execute()
    }

    internal suspend inline fun <reified T> delete(path: String): T = withContext(Dispatchers.IO) {
        val request = buildRequest("DELETE", path)
        client.newCall(request).execute().use { response ->
            val type = object : com.google.gson.reflect.TypeToken<T>() {}.type
            parseResponse(response, type)
        }
    }

    internal suspend inline fun <reified T> patch(path: String, body: Any? = null): T = withContext(Dispatchers.IO) {
        val request = buildRequest("PATCH", path, body)
        client.newCall(request).execute().use { response ->
            val type = object : com.google.gson.reflect.TypeToken<T>() {}.type
            parseResponse(response, type)
        }
    }

    fun webSocket(url: String, listener: WebSocketListener): WebSocket {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${auth.anonToken}")
            .build()
        return client.newWebSocket(request, listener)
    }
}

class AuthException(message: String) : Exception(message)
class ApiException(val code: Int, val body: String) : Exception("HTTP $code: $body")
