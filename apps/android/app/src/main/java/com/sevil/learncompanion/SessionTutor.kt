package com.sevil.learncompanion

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SessionTutor(val store: DeviceStore,val ai: DeviceAI,val keys: DeviceKeys,val search: (String,String,String)->Unit) {
    val pending=ConcurrentHashMap.newKeySet<String>()
    init { store.all("message").filter { it.optString("status")=="pending" }.forEach { store.save("message",it.put("status","failed").put("error","Reply interrupted. Retry your question.")) } }
    fun history(id: String)=store.all("message").filter { it.optString("session_id")==id }.reversed()
    fun links(id: String)=store.all("link").filter { it.optString("session_id")==id }
    fun clear(id: String) { check(!pending.contains(id));history(id).forEach { store.remove(it.getString("id")) } }
    fun removeSession(id: String) { clear(id);links(id).forEach { store.remove(it.getString("id")) } }
    fun addLink(session: JSONObject,v: JSONObject): JSONObject {
        val url=Uri.parse(v.optString("url"));require(url.scheme=="https" && !url.host.isNullOrBlank() && url.userInfo==null) { "Use a public HTTPS link without sign-in credentials." }
        require(v.optString("title").isNotBlank() && v.optString("text").length<=20000) { "Enter a title and an excerpt under 20,000 characters." }
        val result=store.save("link",JSONObject().put("session_id",session.getString("id")).put("title",v.getString("title").take(160)).put("url",url.toString()).put("text",v.optString("text")));store.invalidate(session);return result
    }
    fun send(session: JSONObject,v: JSONObject): JSONObject {
        val id=session.getString("id");check(!pending.contains(id)) { "Wait for the session tutor to finish." }
        val provider=v.optString("provider","gemini");require(provider in listOf("gemini","openai","claude"));require(keys.read(provider).isNotBlank()) { "Add your $provider API key in Settings on this device." }
        val question=v.optString("text").trim();require(question.isNotEmpty() && question.length<=4000) { "Enter a question under 4,000 characters." }
        val request=v.optString("request_id",UUID.randomUUID().toString());require(request.matches(Regex("[A-Za-z0-9-]{1,80}")))
        val prefix="chat:$id:$request";runCatching { store.get("message","$prefix:assistant") }.getOrNull()?.let { return it }
        val assets=store.assets(session);val transcript=session.optString("transcript")
        require(transcript.isNotBlank() || assets.none { it.optString("kind")=="audio" }) { "Generate notes or save a transcript first to chat about the recording." }
        require(transcript.isNotBlank() || session.optJSONObject("handout")!=null || assets.isNotEmpty() || links(id).isNotEmpty()) { "Add a transcript or session materials before asking the tutor." }
        val history=JSONArray(history(id).filter { it.optString("status")=="complete" }.takeLast(12).map { JSONObject().put("role",it.getString("role")).put("content",excerpt(it.getString("content"),question,4000)) })
        check(pending.add(id)) { "Wait for the session tutor to finish." }
        val user=JSONObject().put("id","$prefix:user").put("session_id",id).put("request_id",request).put("role","user").put("content",question).put("provider",provider).put("status","pending")
        try {
            store.save("message",user)
            val sources=JSONArray();(session.optJSONArray("transcript_sources") ?: JSONArray()).objects().forEach { sources.put(it) }
            if(transcript.isNotBlank() && sources.length()==0)sources.put(JSONObject().put("id","transcript:manual").put("label","Student transcript; timing not verified"))
            val attachments=mutableListOf<Pair<java.io.File,String>>();var bytes=0L
            require(assets.count { it.optString("kind")=="photo" }<=20) { "Split sessions with more than 20 board photos." }
            assets.filter { it.getString("kind")!="audio" }.forEach { a ->
                val ref=if(a.getString("kind")=="photo") "photo:${a.getString("id")}" else "resource:${a.getString("id")}:1"
                val source=JSONObject().put("id",ref).put("label",a.getString("name")+if(a.getString("kind")=="photo") " · captured at ${a.optLong("offset_ms")/1000}s" else "")
                val file=store.file(a.getString("id"));if(a.getString("mime")=="text/plain")source.put("text",excerpt(file.readText(),question,12000)) else { bytes+=file.length();require(bytes<=30L*1024*1024) { "Combined images/PDFs must be under 30 MiB." };source.put("attachment_index",attachments.size);attachments.add(file to a.getString("mime")) };sources.put(source)
            }
            links(id).forEach { l -> sources.put(JSONObject().put("id","link:"+l.getString("id")).put("label",l.getString("title")).put("text",excerpt(l.optString("text"),question,12000)).put("links",JSONArray().put(JSONObject().put("url",l.getString("url")).put("title",l.getString("title"))))) }
            if(v.optBoolean("search"))search(session.getString("course_id"),provider,question)
            runCatching { store.get("discovery",session.getString("course_id")+":"+provider) }.getOrNull()?.let { found -> if((found.optJSONArray("links")?.length() ?: 0)>0)sources.put(JSONObject().put("id","web:"+session.getString("course_id")).put("label","Public web search; verify original links").put("text",excerpt(found.optString("summary"),question,12000)).put("links",found.getJSONArray("links"))) }
            val evidence=JSONObject().put("course",store.get("course",session.getString("course_id"))).put("session_title",session.getString("title")).put("transcript",excerpt(transcript,question,32000)).put("handout",session.optJSONObject("handout") ?: JSONObject.NULL).put("history",history).put("question",question).put("sources",sources)
            val reply=ai.note(provider,evidence,attachments,chat=true);validate(reply,sources)
            val cited=sources.objects().filter { it.getString("id") in reply.getJSONArray("citations").strings() }.map { JSONObject().put("id",it.getString("id")).put("label",it.getString("label")) }
            store.save("message",user.put("status","complete"))
            val result=store.save("message",JSONObject().put("id","$prefix:assistant").put("session_id",id).put("request_id",request).put("role","assistant").put("content",reply.getString("answer")).put("provider",provider).put("model",ai.contract.getJSONObject("models").getString(provider)).put("status","complete").put("sources",JSONArray(cited)).put("links",reply.getJSONArray("links")))
            return result
        } catch(e: Exception) { store.save("message",user.put("status","failed").put("error",e.message));throw e } finally {pending.remove(id)}
    }
    companion object {
        fun excerpt(text: String,question: String,limit: Int): String {
            if(text.length<=limit)return text
            val terms=Regex("[\\p{L}\\p{N}]{2,}").findAll(question.lowercase()).map { it.value }.toSet()
            return "[Selected excerpts; some context is omitted]\n"+text.chunked(1200).mapIndexed { i,s -> Triple(i,s,terms.count { s.lowercase().contains(it) }) }.sortedWith(compareByDescending<Triple<Int,String,Int>> { it.third }.thenBy { it.first }).take(maxOf(1,(limit-100)/1250)).sortedBy { it.first }.joinToString("\n…\n") { "[character ${it.first*1200}] ${it.second}" }
        }
        fun validate(reply: JSONObject,sources: JSONArray) {
            require(reply.get("answer") is String && reply.getString("answer").isNotBlank() && reply.getString("answer").length<=30000)
            val ids=sources.objects().map { it.getString("id") }.toSet();val urls=sources.objects().flatMap { it.optJSONArray("links")?.objects() ?: emptyList() }.map { it.getString("url") }.toSet()
            require(reply.getJSONArray("citations").strings().all { it in ids } && reply.getJSONArray("links").objects().all { it.get("title") is String && it.getString("url") in urls }) { "The tutor returned an unsupported source or link. Please retry." }
        }
    }
}
