import Foundation
import CryptoKit
import PDFKit

actor DeviceWorkspace {
    static let shared=DeviceWorkspace()
    let store: DeviceStore
    let ai: DeviceAI
    private init() {
        store=try! DeviceStore();ai=try! DeviceAI()
        for var s in (try? store.all("session")) ?? [] where s["status"] as? String=="processing" { s["status"]="failed";s["error"]="Processing was interrupted. Files remain on this device; retry when ready.";try? store.save("session",s) }
    }
    private func text(_ value: [String:Any],_ key: String,_ fallback: String="") -> String { value[key] as? String ?? fallback }
    private func editable(_ session: [String:Any]) throws { guard text(session,"status") != "processing" else { throw APIError.message("Wait for processing to finish") } }
    private func encoded(_ value: Any) throws -> Data { try JSONSerialization.data(withJSONObject:value,options:[.sortedKeys]) }
    func route(_ path: String,method: String,json: [String:Any]?) async throws -> Data {
        let u=URLComponents(string:"https://device.invalid"+path)!,parts=u.path.split(separator:"/").map(String.init)
        var value=json ?? [:]
        if path=="/health" { return try encoded(["mode":"live","ai_configured":["gemini","openai","claude"].contains { !Keychain.read($0).isEmpty }]) }
        if path=="/v1/providers" { return try encoded(["default_provider":"gemini","providers":["openai","gemini","claude"].map { p -> [String:Any] in ["id":p,"label":["openai":"OpenAI","gemini":"Gemini","claude":"Claude"][p]!,"configured":!Keychain.read(p).isEmpty,"audio_supported":p != "claude","model":ai.model(p)] }]) }
        if parts.count>=2 && parts[1]=="courses" {
            if parts.count==2 {
                if method=="GET" { return try encoded(store.all("course")) }
                guard !text(value,"title").trimmingCharacters(in:.whitespacesAndNewlines).isEmpty else { throw APIError.message("Enter a course title") }
                for key in ["university","degree","professor","code"] { value[key]=value[key] ?? "" }
                for key in ["lecture_language","output_language"] { value[key]=value[key] ?? "en" }
                return try encoded(store.save("course",value))
            }
            let id=parts[2];_=try store.get("course",id)
            let provider=text(value,"provider",u.queryItems?.first { $0.name=="provider" }?.value ?? "gemini")
            if parts.count>3 && parts[3]=="discover" { try await discover(id,provider:provider) }
            return try encoded((try? store.get("discovery",id+":"+provider)) ?? ["id":id,"summary":"","links":[]])
        }
        if parts.count>=2 && parts[1]=="sessions" {
            if parts.count==2 {
                if method=="GET" { return try encoded(store.all("session")) }
                _=try store.get("course",text(value,"course_id"))
                guard value["recording_permission"] as? Bool==true,!text(value,"title").isEmpty else { throw APIError.message("Enter a title and confirm permission to record") }
                value.merge(["status":"draft","transcript":"","handout":NSNull(),"sources":[],"error":NSNull()]) { _,new in new }
                return try encoded(store.save("session",value))
            }
            let id=parts[2];var s=try store.get("session",id)
            if parts.count==3 {
                if method=="DELETE" { try editable(s);for a in try store.all("asset") where text(a,"session_id")==id && text(a,"kind") != "resource" { try? FileManager.default.removeItem(at:store.file(text(a,"id")));try store.remove(text(a,"id")) };try store.remove(id);return Data() }
                s["assets"]=try store.assets(s);return try encoded(s)
            }
            if parts[3]=="handout.pdf" { guard text(s,"status")=="ready" else { throw APIError.message("Generate a handout first") };return try DevicePDF.render(s,course:store.get("course",text(s,"course_id")),assets:store.assets(s),store:store) }
            try editable(s)
            if parts[3]=="demo" { s.merge(["status":"ready","handout":ai.contract["demo_handout"]!,"sources":[],"generated_mode":"demo","generated_provider":"demo"]) { _,new in new };return try encoded(store.save("session",s)) }
            if parts[3]=="transcript" { guard !text(value,"text").isEmpty else { throw APIError.message("Enter a transcript") };s["transcript"]=text(value,"text");s["transcript_provider"]="manual";s["transcript_sources"]=[["id":"transcript:manual","label":"Student-edited transcript; audio timing not verified"]];try store.invalidate(s);return try encoded(store.get("session",id)) }
            if parts[3]=="generate" {
                let provider=text(value,"provider","gemini"),audio=text(value,"transcription_provider",provider=="claude" ? "":provider)
                guard !Keychain.read(provider).isEmpty else { throw APIError.message("Add your \(provider) key in Settings on this device") }
                if text(s,"transcript").isEmpty { guard ["openai","gemini"].contains(audio),!Keychain.read(audio).isEmpty else { throw APIError.message("Select an audio service and add its key in Settings") } }
                s["status"]="processing";s["handout"]=NSNull();s["error"]=NSNull();try store.save("session",s)
                Task { await self.generate(id,provider:provider,audioProvider:audio) };return try encoded(s)
            }
        }
        if parts.count==3 && parts[1]=="assets" {
            let a=try store.get("asset",parts[2])
            if method=="GET" { return try Data(contentsOf:store.file(parts[2])) }
            let related=try store.all("session").filter { text($0,"course_id")==text(a,"course_id") && (text(a,"kind")=="resource" || text($0,"id")==text(a,"session_id")) }
            for s in related { try editable(s) }
            try? FileManager.default.removeItem(at:store.file(parts[2]));try store.remove(parts[2])
            for var s in related { if text(a,"kind")=="audio" { s["transcript"]="" };try store.invalidate(s) };return Data()
        }
        throw APIError.message("Unsupported on-device operation")
    }
    func upload(session id: String,file input: URL,name: String,kind: String,offset: Int,duration: Int) throws {
        let s=try store.get("session",id);try editable(s)
        let size=try input.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
        guard size>0 && size <= (kind=="audio" ? 250:20)*1024*1024 else { throw APIError.message("Empty file or file exceeds the size limit") }
        let mime=["m4a":"audio/m4a","mp4":"audio/mp4","wav":"audio/wav","mp3":"audio/mpeg","webm":"audio/webm","ogg":"audio/ogg","pdf":"application/pdf","txt":"text/plain","md":"text/plain","jpg":"image/jpeg","jpeg":"image/jpeg","png":"image/png","webp":"image/webp"][URL(fileURLWithPath:name).pathExtension.lowercased()]
        guard let mime,kind=="audio" ? mime.hasPrefix("audio/"):kind=="photo" ? mime.hasPrefix("image/"):mime=="application/pdf" || mime=="text/plain" else { throw APIError.message("Unsupported attachment type") }
        if mime=="application/pdf" { guard let pdf=PDFDocument(url:input),pdf.pageCount<=300 else { throw APIError.message("Use an unlocked PDF with at most 300 pages") } }
        var hash=SHA256();let handle=try FileHandle(forReadingFrom:input);defer { try? handle.close() }
        while let data=try handle.read(upToCount:65536),!data.isEmpty { hash.update(data:data) }
        let digest=hash.finalize().map { String(format:"%02x",$0) }.joined()
        if try store.all("asset").contains(where:{ text($0,"session_id")==id && text($0,"sha256")==digest && ($0["offset_ms"] as? Int)==offset && text($0,"kind")==kind }) { return }
        let related=kind=="resource" ? try store.all("session").filter { text($0,"course_id")==text(s,"course_id") }:[s]
        for s in related { try editable(s) }
        let assetId=UUID().uuidString,destination=try store.file(assetId)
        try FileManager.default.copyItem(at:input,to:destination)
        try? FileManager.default.setAttributes([.protectionKey:FileProtectionType.completeUntilFirstUserAuthentication],ofItemAtPath:destination.path)
        try store.save("asset",["id":assetId,"session_id":id,"course_id":text(s,"course_id"),"kind":kind,"name":URL(fileURLWithPath:name).lastPathComponent,"mime":mime,"size":size,"offset_ms":offset,"duration_ms":duration,"sha256":digest])
        for var s in related { if kind=="audio" { s["transcript"]="" };try store.invalidate(s) }
    }
    func generate(_ id: String,provider: String,audioProvider: String) async {
        guard var s=try? store.get("session",id) else { return }
        do {
            let course=try store.get("course",text(s,"course_id")),assets=try store.assets(s)
            var sources=[[String:Any]](),attachments=[(URL,String)]()
            if text(s,"transcript").isEmpty {
                let audio=assets.filter { text($0,"kind")=="audio" }.sorted { ($0["offset_ms"] as? Int ?? 0)<($1["offset_ms"] as? Int ?? 0) }
                guard !audio.isEmpty else { throw APIError.message("Record audio or save a transcript first") }
                var chunks=[String](),refs=[[String:Any]]()
                for a in audio { let ref="audio:\(text(a,"id")):\(a["offset_ms"] as? Int ?? 0)";refs.append(["id":ref,"label":"Audio clip at \((a["offset_ms"] as? Int ?? 0)/1000)s (clip-level timing)"]);chunks.append("[\(ref)] "+(try await ai.transcribe(audioProvider,file:store.file(text(a,"id")),mime:text(a,"mime"),language:text(course,"lecture_language")))) }
                s["transcript"]=chunks.joined(separator:"\n\n");s["transcript_sources"]=refs;s["transcript_provider"]=audioProvider;try store.save("session",s)
            }
            sources += s["transcript_sources"] as? [[String:Any]] ?? [["id":"transcript:manual","label":"Student transcript"]]
            if let cached=try? store.get("discovery",text(s,"course_id")+":"+provider),let links=cached["links"] as? [[String:String]],!links.isEmpty { sources.append(["id":"web:"+text(s,"course_id"),"label":"Public-source search summary; verify originals","text":cached["summary"] ?? "","links":links]) }
            guard assets.filter({ text($0,"kind")=="photo" }).count<=20 else { throw APIError.message("Split sessions with more than 20 board photos") }
            var total=0
            for a in assets where text(a,"kind") != "audio" {
                let ref=text(a,"kind")=="photo" ? "photo:\(text(a,"id"))":"resource:\(text(a,"id")):1"
                var source: [String:Any]=["id":ref,"label":text(a,"name")+(text(a,"kind")=="photo" ? " · captured at \((a["offset_ms"] as? Int ?? 0)/1000)s":" · attached resource")]
                let file=try store.file(text(a,"id"))
                if text(a,"mime")=="text/plain" { let content=try String(contentsOf:file,encoding:.utf8);guard content.count<=100000 else { throw APIError.message("Attach a shorter text resource") };source["text"]=content }
                else { total += a["size"] as? Int ?? 0;guard total<=30*1024*1024 else { throw APIError.message("Combined images/PDFs must be under 30 MiB") };attachments.append((file,text(a,"mime")));source["attachment_index"]=attachments.count-1 }
                sources.append(source)
            }
            let note=try await ai.note(provider,evidence:["course":course,"session_title":text(s,"title"),"transcript":text(s,"transcript"),"sources":sources],attachments:attachments)
            try validateNote(note,sources:sources)
            s.merge(["status":"ready","handout":note,"sources":sources,"generated_provider":provider,"generated_mode":"live","error":NSNull()]) { _,new in new };try store.save("session",s)
        } catch { s["status"]="failed";s["error"]=error.localizedDescription;s["handout"]=NSNull();try? store.save("session",s) }
    }
    func validateNote(_ note: [String:Any],sources: [[String:Any]]) throws {
        _=try JSONDecoder().decode(Handout.self,from:encoded(note))
        let ids=Set(sources.compactMap { $0["id"] as? String })
        for section in note["sections"] as! [[String:Any]] {
            let kind=text(section,"kind"),citations=section["citations"] as! [String]
            guard ["lecture","supporting","explanation"].contains(kind),citations.allSatisfy({ids.contains($0)}),kind=="explanation" || !citations.isEmpty,kind != "lecture" || citations.contains(where:{$0.hasPrefix("audio:") || $0.hasPrefix("transcript:")}) else { throw APIError.message("AI returned invalid or unsupported citations; no handout was published") }
            if kind=="supporting" && !citations.contains(where:{$0.hasPrefix("photo:") || $0.hasPrefix("resource:") || $0.hasPrefix("web:")}) { throw APIError.message("Supporting statement lacks resource evidence") }
        }
    }
    func discover(_ id: String,provider: String) async throws {
        let course=try store.get("course",id),query=String(data:try encoded(course),encoding:.utf8)!
        var links=[[String:String]]();let output: String
        func add(_ url: String,_ title: String) { if let u=URL(string:url),u.scheme=="https",u.host != nil { links.append(["url":url,"title":title]) } }
        if provider=="gemini" {
            let result=try await ai.gemini([["text":query]],instructions:ai.contract["search_prompt"] as! String,search:true);output=try ai.geminiText(result)
            for candidate in result["candidates"] as? [[String:Any]] ?? [] { for chunk in (candidate["groundingMetadata"] as? [String:Any])?["groundingChunks"] as? [[String:Any]] ?? [] { if let web=chunk["web"] as? [String:String] { add(web["uri"] ?? "",web["title"] ?? "") } } }
        } else if provider=="openai" {
            let result=try await ai.request(provider,"responses",["model":ai.model(provider),"store":false,"instructions":ai.contract["search_prompt"]!,"input":query,"tools":[["type":"web_search"]]])
            let content=(result["output"] as? [[String:Any]] ?? []).flatMap { $0["content"] as? [[String:Any]] ?? [] };output=content.compactMap { $0["text"] as? String }.joined(separator:"\n")
            for p in content { for a in p["annotations"] as? [[String:Any]] ?? [] where text(a,"type")=="url_citation" { add(text(a,"url"),text(a,"title")) } }
        } else {
            let result=try await ai.request(provider,"messages",["model":ai.model(provider),"max_tokens":8192,"system":ai.contract["search_prompt"]!,"messages":[["role":"user","content":query]],"tools":[["type":"web_search_20250305","name":"web_search","max_uses":3]]])
            let content=result["content"] as? [[String:Any]] ?? [];output=content.compactMap { $0["text"] as? String }.joined(separator:"\n");for p in content { for a in p["citations"] as? [[String:Any]] ?? [] { add(text(a,"url"),text(a,"title")) } }
        }
        try store.save("discovery",["id":id+":"+provider,"summary":output,"links":links])
    }
}
