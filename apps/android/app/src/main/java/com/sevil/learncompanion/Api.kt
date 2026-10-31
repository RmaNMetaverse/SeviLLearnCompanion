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
    val workspace = LocalWorkspace.get(context)
    val keys get() = workspace.keys
    fun assetFile(id: String) = workspace.file(id)
    suspend fun bytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        val parts = path.substringBefore('?').split('/').filter { it.isNotBlank() }
        when {
            parts.getOrNull(1) == "assets" -> workspace.file(parts[2]).readBytes()
            parts.getOrNull(3) == "handout.pdf" -> workspace.pdf(parts[2])
            else -> workspace.route(path,"GET",null).toByteArray(Charsets.UTF_8)
        }
    }
    suspend fun json(path: String, method: String = "GET", value: JSONObject? = null): String = withContext(Dispatchers.IO) { workspace.route(path,method,value) }
    suspend fun upload(session: String, file: File, name: String, kind: String, offset: Long = 0, duration: Long = 0) = withContext(Dispatchers.IO) { workspace.upload(session,file,name,kind,offset,duration) }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
