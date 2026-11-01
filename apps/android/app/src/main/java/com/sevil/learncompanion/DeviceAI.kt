package com.sevil.learncompanion

import android.content.Context
import android.util.Base64
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class DeviceAI(context: Context,val keys: DeviceKeys) {
    val contract=JSONObject(context.assets.open("ai-contract.json").bufferedReader().use { it.readText() })
    private val client=OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(5,TimeUnit.MINUTES).callTimeout(10,TimeUnit.MINUTES).build()
    fun request(provider: String,path: String,body: JSONObject): JSONObject {
        val key=keys.read(provider); require(key.isNotBlank()) { "Add your $provider API key in Settings on this device. No laptop is needed." }
        val base=mapOf("gemini" to "https://generativelanguage.googleapis.com/v1beta/","openai" to "https://api.openai.com/v1/","claude" to "https://api.anthropic.com/v1/")[provider] ?: error("Unknown AI service")
        val builder=Request.Builder().url(base+path).post(body.toString().toRequestBody("application/json".toMediaType()))
        when(provider) { "gemini" -> builder.header("x-goog-api-key",key); "openai" -> builder.header("Authorization","Bearer $key"); else -> builder.header("x-api-key",key).header("anthropic-version","2023-06-01").header("anthropic-dangerous-direct-browser-access","true") }
        client.newCall(builder.build()).execute().use { response ->
            require(response.isSuccessful) { "$provider returned HTTP ${response.code}. Check your device key, internet connection, model access and quota. No other service was used." }
            return JSONObject(response.body?.string() ?: error("Empty AI response"))
        }
    }
    private fun part(file: File,mime: String): JSONObject = JSONObject().put("inlineData",JSONObject().put("mimeType",mime).put("data",Base64.encodeToString(file.readBytes(),Base64.NO_WRAP)))
    fun gemini(parts: JSONArray,instructions: String="",schema: Boolean=false,search: Boolean=false,responseSchema: JSONObject=contract.getJSONObject("schema"),maxTokens: Int=65536): JSONObject {
        val config=JSONObject().put("maxOutputTokens",maxTokens)
        if(schema) config.put("responseMimeType","application/json").put("responseJsonSchema",responseSchema)
        val body=JSONObject().put("contents",JSONArray().put(JSONObject().put("role","user").put("parts",parts))).put("generationConfig",config)
        if(instructions.isNotEmpty()) body.put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",instructions))))
        if(search) body.put("tools",JSONArray().put(JSONObject().put("google_search",JSONObject())))
        return request("gemini","models/${contract.getJSONObject("models").getString("gemini")}:generateContent",body)
    }
    fun geminiText(result: JSONObject): String {
        val candidate=result.optJSONArray("candidates")?.optJSONObject(0) ?: error("Gemini returned no content. Your files are still saved on this device.")
        require(candidate.optString("finishReason","STOP")=="STOP") { "Gemini could not finish this response. Split the session or retry; no partial handout was published." }
        return candidate.getJSONObject("content").getJSONArray("parts").objects().filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }
    }
    // Files API avoids loading a full classroom recording into the request body.
    fun uploaded(file: File,mime: String,action: (JSONObject)->String): String {
        if(file.length()<8*1024*1024) return action(part(file,mime))
        val key=keys.read("gemini"); require(key.isNotBlank()) { "Add your Gemini key in Settings." }
        val start=Request.Builder().url("https://generativelanguage.googleapis.com/upload/v1beta/files").header("x-goog-api-key",key).header("X-Goog-Upload-Protocol","resumable").header("X-Goog-Upload-Command","start").header("X-Goog-Upload-Header-Content-Length",file.length().toString()).header("X-Goog-Upload-Header-Content-Type",mime).post("{\"file\":{\"display_name\":\"SeviL lecture\"}}".toRequestBody("application/json".toMediaType())).build()
        val url=client.newCall(start).execute().use { r -> require(r.isSuccessful) { "Gemini upload failed (${r.code})." }; r.header("X-Goog-Upload-URL") ?: error("Gemini did not provide an upload address") }
        require(url.startsWith("https://") && url.toHttpUrl().host.endsWith(".googleapis.com")) { "Unexpected upload address" }
        val upload=Request.Builder().url(url).header("x-goog-api-key",key).header("X-Goog-Upload-Offset","0").header("X-Goog-Upload-Command","upload, finalize").post(file.asRequestBody(mime.toMediaType())).build()
        var item=client.newCall(upload).execute().use { r -> require(r.isSuccessful) { "Gemini upload failed (${r.code})." }; JSONObject(r.body!!.string()).getJSONObject("file") }
        val name=item.getString("name")
        try {
            repeat(90) { if(item.optString("state")=="PROCESSING") { Thread.sleep(1000); val get=Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/$name").header("x-goog-api-key",key).build(); item=client.newCall(get).execute().use { r -> require(r.isSuccessful); JSONObject(r.body!!.string()) } } }
            require(item.optString("state")!in listOf("PROCESSING","FAILED")) { "Gemini could not prepare the recording. Original audio remains on this device." }
            return action(JSONObject().put("fileData",JSONObject().put("mimeType",mime).put("fileUri",item.getString("uri"))))
        } finally { runCatching { client.newCall(Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/$name").header("x-goog-api-key",key).delete().build()).execute().close() } }
    }
    fun transcribe(provider: String,file: File,mime: String,language: String): String {
        val prompt=contract.getString("transcript_prompt")+" Expected language: $language."
        if(provider=="gemini") return uploaded(file,mime) { media -> geminiText(gemini(JSONArray().put(JSONObject().put("text",prompt)).put(media))) }
        require(provider=="openai") { "Select Gemini or OpenAI for audio transcription." }
        val key=keys.read(provider); require(key.isNotBlank()) { "Add your OpenAI key in Settings." }
        require(file.length()<24*1024*1024) { "For OpenAI, split audio into files under 24 MiB or choose Gemini for long recordings." }
        val body=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("model","gpt-transcribe").addFormDataPart("prompt",prompt).addFormDataPart("file",file.name,file.asRequestBody(mime.toMediaType())).build()
        client.newCall(Request.Builder().url("https://api.openai.com/v1/audio/transcriptions").header("Authorization","Bearer $key").post(body).build()).execute().use { r -> require(r.isSuccessful) { "OpenAI transcription failed (${r.code})." }; return JSONObject(r.body!!.string()).getString("text") }
    }
    fun note(provider: String,evidence: JSONObject,attachments: List<Pair<File,String>>,chat: Boolean=false): JSONObject {
        val schema=contract.getJSONObject(if(chat) "chat_schema" else "schema")
        val instructions=contract.getString(if(chat) "chat_prompt" else "notes_prompt")
        val parts=JSONArray().put(JSONObject().put("text",evidence.toString()))
        attachments.forEach { (file,mime) -> require(file.length()<=20*1024*1024) { "Use attachments under 20 MiB." }; parts.put(part(file,mime)) }
        if(provider=="gemini") return JSONObject(geminiText(gemini(parts,instructions,schema=true,responseSchema=schema,maxTokens=if(chat) 8192 else 65536)))
        val content=JSONArray()
        parts.objects().forEach { p -> if(p.has("text")) content.put(JSONObject().put("type",if(provider=="openai") "input_text" else "text").put("text",p.getString("text"))) else {
            val inline=p.getJSONObject("inlineData"); val mime=inline.getString("mimeType"); val data=inline.getString("data")
            if(provider=="openai") content.put(if(mime=="application/pdf") JSONObject().put("type","input_file").put("filename","course.pdf").put("file_data","data:$mime;base64,$data") else JSONObject().put("type","input_image").put("image_url","data:$mime;base64,$data"))
            else content.put(JSONObject().put("type",if(mime=="application/pdf") "document" else "image").put("source",JSONObject().put("type","base64").put("media_type",mime).put("data",data)))
        } }
        if(provider=="openai") { val body=JSONObject().put("model",contract.getJSONObject("models").getString(provider)).put("store",false).put("instructions",instructions).put("input",JSONArray().put(JSONObject().put("role","user").put("content",content))).put("text",JSONObject().put("format",JSONObject().put("type","json_schema").put("name","lecture_handout").put("strict",true).put("schema",schema))); val r=request(provider,"responses",body); return JSONObject(r.getJSONArray("output").objects().flatMap { it.optJSONArray("content")?.objects() ?: emptyList() }.filter { it.optString("type")=="output_text" }.joinToString("") { it.getString("text") }) }
        val r=request(provider,"messages",JSONObject().put("model",contract.getJSONObject("models").getString(provider)).put("max_tokens",if(chat) 4096 else 16384).put("system",instructions).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",content))).put("output_config",JSONObject().put("format",JSONObject().put("type","json_schema").put("schema",schema))))
        require(r.optString("stop_reason")=="end_turn") { "Claude response incomplete." }; return JSONObject(r.getJSONArray("content").objects().filter { it.optString("type")=="text" }.joinToString("") { it.getString("text") })
    }
}
