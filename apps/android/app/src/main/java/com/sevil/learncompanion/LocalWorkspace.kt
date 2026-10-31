package com.sevil.learncompanion

import android.content.Context
import android.net.Uri
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class LocalWorkspace private constructor(context: Context) {
    companion object { @Volatile private var instance: LocalWorkspace?=null; fun get(context: Context): LocalWorkspace = instance ?: synchronized(this) { instance ?: LocalWorkspace(context.applicationContext).also { instance=it } } }
    val store=DeviceStore(context)
    val keys=DeviceKeys(context)
    val ai=DeviceAI(context,keys)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    init { store.all("session").filter { it.optString("status")=="processing" }.forEach { store.save("session",it.put("status","failed").put("error","Processing was interrupted. Files are saved on this device; retry when ready.")) } }
    fun file(id: String)=store.file(id)
    private fun editable(s: JSONObject) { require(s.optString("status")!="processing") { "Wait for processing to finish." } }
    fun route(path: String,method: String,body: JSONObject?): String {
        val u=Uri.parse("https://device.invalid$path"); val parts=u.pathSegments; val v=body ?: JSONObject()
        if(path=="/health") return JSONObject().put("mode","live").put("ai_configured",listOf("gemini","openai","claude").any { keys.read(it).isNotBlank() }).toString()
        if(path=="/v1/providers") return JSONObject().put("default_provider","gemini").put("providers",JSONArray(listOf("openai","gemini","claude").map { JSONObject().put("id",it).put("label",mapOf("openai" to "OpenAI","gemini" to "Gemini","claude" to "Claude")[it]).put("configured",keys.read(it).isNotBlank()).put("audio_supported",it!="claude").put("model",ai.contract.getJSONObject("models").getString(it)) })).toString()
        if(parts.getOrNull(1)=="courses") {
            if(parts.size==2) { if(method=="GET") return JSONArray(store.all("course")).toString(); require(v.optString("title").isNotBlank()) { "Enter a course title." }; listOf("university","degree","professor","code").forEach { if(!v.has(it)) v.put(it,"") }; listOf("lecture_language","output_language").forEach { if(!v.has(it)) v.put(it,"en") }; return store.save("course",v).toString() }
            val id=parts[2]; store.get("course",id); val provider=v.optString("provider",u.getQueryParameter("provider") ?: "gemini"); val key="$id:$provider"
            if(parts.getOrNull(3)=="discover") discover(id,provider)
            return runCatching { store.get("discovery",key) }.getOrElse { JSONObject().put("id",key).put("summary","").put("links",JSONArray()) }.toString()
        }
        if(parts.getOrNull(1)=="sessions") {
            if(parts.size==2) { if(method=="GET") return JSONArray(store.all("session")).toString(); store.get("course",v.getString("course_id")); require(v.optBoolean("recording_permission")) { "Confirm you have permission to record this class." }; require(v.optString("title").isNotBlank()); return store.save("session",v.put("status","draft").put("transcript","").put("handout",JSONObject.NULL).put("sources",JSONArray()).put("error",JSONObject.NULL)).toString() }
            val id=parts[2]; val s=store.get("session",id)
            if(parts.size==3) {
                if(method=="DELETE") { editable(s); store.all("asset").filter { it.optString("session_id")==id && it.getString("kind")!="resource" }.forEach { store.file(it.getString("id")).delete();store.remove(it.getString("id")) }; store.remove(id); return "{}" }
                return s.put("assets",JSONArray(store.assets(s))).toString()
            }
            if(parts[3]=="transcript") { editable(s); require(v.optString("text").isNotBlank()); s.put("transcript",v.getString("text")).put("transcript_provider","manual").put("transcript_sources",JSONArray().put(JSONObject().put("id","transcript:manual").put("label","Student-edited transcript; audio timing not verified"))); store.invalidate(s);return s.toString() }
            if(parts[3]=="demo") { editable(s);return store.save("session",s.put("status","ready").put("handout",JSONObject(ai.contract.getJSONObject("demo_handout").toString())).put("sources",JSONArray()).put("generated_mode","demo").put("generated_provider","demo")).toString() }
            if(parts[3]=="generate") { editable(s); val provider=v.optString("provider","gemini"); require(keys.read(provider).isNotBlank()) { "Add your $provider API key in Settings on this device." }; val audio=v.optString("transcription_provider",if(provider=="claude") "" else provider); if(s.optString("transcript").isBlank()) { require(audio in listOf("gemini","openai")) { "Choose an audio service or save a transcript." }; require(keys.read(audio).isNotBlank()) { "Add your $audio key in Settings." } }; store.save("session",s.put("status","processing").put("handout",JSONObject.NULL).put("error",JSONObject.NULL));scope.launch { generate(id,provider,audio) };return s.toString() }
        }
        if(parts.getOrNull(1)=="assets" && method=="DELETE") {
            val a=store.get("asset",parts[2]); val related=store.all("session").filter { it.getString("course_id")==a.getString("course_id") && (a.getString("kind")=="resource" || it.getString("id")==a.getString("session_id")) };related.forEach { editable(it) };store.file(a.getString("id")).delete();store.remove(a.getString("id"));related.forEach { if(a.getString("kind")=="audio") it.put("transcript","");store.invalidate(it) };return "{}"
        }
        error("Unsupported local operation")
    }
    fun upload(sessionId: String,input: File,name: String,kind: String,offset: Long,duration: Long) {
        val s=store.get("session",sessionId);editable(s)
        val limit=if(kind=="audio") 250L*1024*1024 else 20L*1024*1024;require(input.length() in 1..limit) { "Empty file or file exceeds the size limit." }
        val ext=name.substringAfterLast('.').lowercase(); val mime=mapOf("m4a" to "audio/m4a","mp4" to "audio/mp4","wav" to "audio/wav","mp3" to "audio/mpeg","webm" to "audio/webm","ogg" to "audio/ogg","pdf" to "application/pdf","txt" to "text/plain","md" to "text/plain","jpg" to "image/jpeg","jpeg" to "image/jpeg","png" to "image/png","webp" to "image/webp")[ext] ?: error("Unsupported file type")
        require(when(kind) { "audio"->mime.startsWith("audio/");"photo"->mime.startsWith("image/");"resource"->mime in listOf("application/pdf","text/plain");else->false }) { "File type does not match attachment." }
        if(mime=="application/pdf") PdfRenderer(ParcelFileDescriptor.open(input,ParcelFileDescriptor.MODE_READ_ONLY)).use { require(it.pageCount<=300) { "Attach chapters of at most 300 pages." } }
        val hash=MessageDigest.getInstance("SHA-256");input.inputStream().use { stream -> val b=ByteArray(65536);var n=stream.read(b);while(n>0) { hash.update(b,0,n);n=stream.read(b) } };val digest=hash.digest().joinToString("") { "%02x".format(it) }
        if(store.all("asset").any { it.optString("session_id")==sessionId && it.optString("sha256")==digest && it.optLong("offset_ms")==offset && it.optString("kind")==kind }) return
        val related=if(kind=="resource") store.all("session").filter { it.getString("course_id")==s.getString("course_id") } else listOf(s);related.forEach { editable(it) }
        val a=JSONObject().put("session_id",sessionId).put("course_id",s.getString("course_id")).put("name",name.substringAfterLast('/').substringAfterLast('\\')).put("kind",kind).put("offset_ms",offset).put("duration_ms",duration).put("mime",mime).put("sha256",digest).put("size",input.length())
        a.put("id",java.util.UUID.randomUUID().toString());input.copyTo(store.file(a.getString("id")),overwrite=false);store.save("asset",a)
        related.forEach { if(kind=="audio") it.put("transcript","");store.invalidate(it) }
    }
    fun generate(id: String,provider: String,audioProvider: String) {
        val s=store.get("session",id)
        try {
            val course=store.get("course",s.getString("course_id"));val assets=store.assets(s); val sources=JSONArray();val attachments=mutableListOf<Pair<File,String>>()
            if(s.optString("transcript").isBlank()) {
                val audio=assets.filter { it.getString("kind")=="audio" }.sortedBy { it.optLong("offset_ms") };require(audio.isNotEmpty()) { "Record audio or save a transcript first." }
                val refs=JSONArray(); val text=audio.joinToString("\n\n") { a -> val ref="audio:${a.getString("id")}:${a.optLong("offset_ms")}";refs.put(JSONObject().put("id",ref).put("label","Audio clip at ${a.optLong("offset_ms")/1000}s (clip-level timing)")); "[$ref] "+ai.transcribe(audioProvider,store.file(a.getString("id")),a.getString("mime"),course.getString("lecture_language")) }
                s.put("transcript",text).put("transcript_sources",refs).put("transcript_provider",audioProvider);store.save("session",s)
            }
            (s.optJSONArray("transcript_sources") ?: JSONArray().put(JSONObject().put("id","transcript:manual").put("label","Student transcript"))).objects().forEach { sources.put(it) }
            runCatching { store.get("discovery",s.getString("course_id")+":"+provider) }.getOrNull()?.let { cached -> if(cached.optJSONArray("links")?.length() ?: 0 > 0) sources.put(JSONObject().put("id","web:"+s.getString("course_id")).put("label","Public-source search summary; verify originals").put("text",cached.getString("summary")).put("links",cached.getJSONArray("links"))) }
            require(assets.count { it.getString("kind")=="photo" }<=20) { "Split sessions with more than 20 board photos." }
            var attachedBytes=0L
            assets.filter { it.getString("kind")!="audio" }.forEach { a ->
                val ref=if(a.getString("kind")=="photo") "photo:${a.getString("id")}" else "resource:${a.getString("id")}:1"
                val file=store.file(a.getString("id"));val source=JSONObject().put("id",ref).put("label",a.getString("name")+if(a.getString("kind")=="photo") " · captured at ${a.optLong("offset_ms")/1000}s" else " · attached resource")
                if(a.getString("mime")=="text/plain") source.put("text",file.readText().also { require(it.length<=100000) { "Attach a shorter text resource." } }) else { attachedBytes+=file.length();require(attachedBytes<=30L*1024*1024) { "Select smaller attachments; combined images/PDFs must be under 30 MiB." };attachments.add(file to a.getString("mime"));source.put("attachment_index",attachments.size-1) }
                sources.put(source)
            }
            val evidence=JSONObject().put("course",course).put("session_title",s.getString("title")).put("transcript",s.getString("transcript")).put("sources",sources)
            val note=ai.note(provider,evidence,attachments);validateNote(note,sources)
            store.save("session",s.put("status","ready").put("handout",note).put("sources",sources).put("generated_provider",provider).put("generated_mode","live").put("error",JSONObject.NULL))
        } catch(e: Exception) { store.save("session",s.put("status","failed").put("error",e.message ?: "Processing failed; your files remain on this device.").put("handout",JSONObject.NULL)) }
    }
    fun validateNote(note: JSONObject,sources: JSONArray) {
        listOf("title","summary","objectives","sections","glossary","questions","uncertainties").forEach { require(note.has(it)) { "AI returned incomplete notes. Retry; no partial handout was published." } }
        val ids=sources.objects().map { it.getString("id") }.toSet()
        listOf("title","summary").forEach { require(note.get(it) is String) }
        listOf("objectives","questions","uncertainties").forEach { key -> val a=note.getJSONArray(key);for(i in 0 until a.length()) require(a.get(i) is String) }
        note.getJSONArray("glossary").objects().forEach { t -> listOf("term","translation","definition").forEach { require(t.get(it) is String) } }
        note.getJSONArray("sections").objects().forEach { t -> listOf("heading","body").forEach { require(t.get(it) is String) };if(t.getString("kind")=="supporting") require(t.getJSONArray("citations").strings().any { it.startsWith("photo:") || it.startsWith("resource:") || it.startsWith("web:") }) { "Supporting statement lacks resource evidence." } }
        note.getJSONArray("sections").objects().forEach { section -> val citations=section.getJSONArray("citations").strings();val kind=section.getString("kind");require(kind in listOf("lecture","supporting","explanation"));require(citations.all { it in ids }) { "AI returned an unknown citation. Retry generation." };require(kind=="explanation" || citations.isNotEmpty()) { "AI omitted evidence." };require(kind!="lecture" || citations.any { it.startsWith("audio:") || it.startsWith("transcript:") }) { "Lecture statement lacks transcript evidence." } }
    }
    fun discover(id: String,provider: String) {
        val course=store.get("course",id);val query=course.toString();val links=JSONArray();val text: String
        fun add(url: String,title: String) { if(Uri.parse(url).scheme=="https" && !Uri.parse(url).host.isNullOrBlank()) links.put(JSONObject().put("url",url).put("title",title)) }
        if(provider=="gemini") { val result=ai.gemini(JSONArray().put(JSONObject().put("text",query)),ai.contract.getString("search_prompt"),search=true);text=ai.geminiText(result);result.getJSONArray("candidates").objects().forEach { c -> c.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")?.objects()?.forEach { it.optJSONObject("web")?.let { web -> add(web.optString("uri"),web.optString("title")) } } } }
        else if(provider=="openai") { val r=ai.request(provider,"responses",JSONObject().put("model",ai.contract.getJSONObject("models").getString(provider)).put("store",false).put("instructions",ai.contract.getString("search_prompt")).put("input",query).put("tools",JSONArray().put(JSONObject().put("type","web_search"))));text=r.getJSONArray("output").objects().flatMap { it.optJSONArray("content")?.objects() ?: emptyList() }.joinToString("\n") { p -> p.optJSONArray("annotations")?.objects()?.filter { it.optString("type")=="url_citation" }?.forEach { add(it.optString("url"),it.optString("title")) };p.optString("text") } }
        else { val r=ai.request(provider,"messages",JSONObject().put("model",ai.contract.getJSONObject("models").getString(provider)).put("max_tokens",8192).put("system",ai.contract.getString("search_prompt")).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",query))).put("tools",JSONArray().put(JSONObject().put("type","web_search_20250305").put("name","web_search").put("max_uses",3))));text=r.getJSONArray("content").objects().joinToString("\n") { p ->p.optJSONArray("citations")?.objects()?.forEach { add(it.optString("url"),it.optString("title")) };p.optString("text") } }
        store.save("discovery",JSONObject().put("id","$id:$provider").put("summary",text).put("links",links))
    }
    fun pdf(id: String): ByteArray { val s=store.get("session",id);require(s.optString("status")=="ready") { "Generate a handout first." };return DevicePDF.render(s,store.get("course",s.getString("course_id")),store.assets(s),store) }
}
