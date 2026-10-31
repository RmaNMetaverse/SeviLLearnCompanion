package com.sevil.learncompanion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import okio.source

class Api(private val context: Context) {
    val prefs = context.getSharedPreferences("sevil", Context.MODE_PRIVATE)
    var server: String
        get() = prefs.getString("server", "http://10.0.2.2:8000")!!.trimEnd('/')
        set(value) { prefs.edit().putString("server", value.trimEnd('/')).apply() }
    var token: String
        get() = prefs.getString("token", "")!!
        set(value) { prefs.edit().putString("token", value).apply() }
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).build()
    suspend fun bytes(path: String, method: String = "GET", body: RequestBody? = null): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(server + path).header("Authorization", "Bearer $token").method(method, body).build()
        client.newCall(request).execute().use { response ->
            val data = response.body?.bytes() ?: byteArrayOf()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(String(data)).optString("detail") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "Server returned ${response.code}" })
            }
            data
        }
    }
    suspend fun json(path: String, method: String = "GET", value: JSONObject? = null): String {
        val body = if (method in listOf("POST", "PUT")) (value?.toString() ?: "{}").toRequestBody("application/json".toMediaType()) else null
        return String(bytes(path, method, body), Charsets.UTF_8)
    }
    suspend fun upload(session: String, file: File, name: String, kind: String, offset: Long = 0, duration: Long = 0) {
        val mime = when(kind) { "photo" -> "image/jpeg"; "audio" -> "audio/mp4"; else -> "application/octet-stream" }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("kind", kind).addFormDataPart("offset_ms", offset.toString()).addFormDataPart("duration_ms", duration.toString()).addFormDataPart("file", name, object: RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: okio.BufferedSink) { file.inputStream().use { sink.writeAll(it.source()) } }
        }).build()
        bytes("/v1/sessions/$session/assets", "POST", body)
    }
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
