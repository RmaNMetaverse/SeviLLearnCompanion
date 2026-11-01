import Foundation

enum ChatContext {
    static func excerpt(_ text: String,question: String,limit: Int) -> String {
        guard text.count>limit else {return text}
        let expression=try! NSRegularExpression(pattern:"[\\p{L}\\p{N}]{2,}")
        let q=question.lowercased() as NSString
        let terms=Set(expression.matches(in:q as String,range:NSRange(location:0,length:q.length)).map {q.substring(with:$0.range)})
        let characters=Array(text)
        let chunks=stride(from:0,to:characters.count,by:1200).map { i -> (Int,String,Int) in let value=String(characters[i..<min(i+1200,characters.count)]);return(i,value,terms.filter {value.lowercased().contains($0)}.count) }
        return "[Selected excerpts; some context is omitted]\n"+chunks.sorted {$0.2==$1.2 ? $0.0<$1.0:$0.2>$1.2}.prefix(max(1,(limit-100)/1250)).sorted {$0.0<$1.0}.map {"[character \($0.0)] \($0.1)"}.joined(separator:"\n…\n")
    }
    static func validate(_ reply: [String:Any],sources: [[String:Any]]) throws {
        let ids=Set(sources.compactMap {$0["id"] as? String})
        let urls=Set(sources.flatMap {$0["links"] as? [[String:String]] ?? []}.compactMap {$0["url"]})
        guard let answer=reply["answer"] as? String,!answer.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty,answer.count<=30000,let citations=reply["citations"] as? [String],citations.allSatisfy({ids.contains($0)}),let links=reply["links"] as? [[String:String]],links.allSatisfy({$0["title"] != nil && urls.contains($0["url"] ?? "")}) else {throw APIError.message("The tutor returned an unsupported source or link. Please retry.")}
    }
}

extension DeviceWorkspace {
    func chatMessages(_ id: String) throws -> [[String:Any]] {Array(try store.all("message").filter {text($0,"session_id")==id}.reversed())}
    func sessionLinks(_ id: String) throws -> [[String:Any]] {try store.all("link").filter {text($0,"session_id")==id}}
    func addSessionLink(_ session: [String:Any],value: [String:Any]) throws -> [String:Any] {
        guard let url=URL(string:text(value,"url")),url.scheme=="https",url.host != nil,url.user==nil,url.password==nil,!text(value,"title").isEmpty,text(value,"text").count<=20000 else {throw APIError.message("Enter a title, a public HTTPS link and an excerpt under 20,000 characters")}
        let link=try store.save("link",["session_id":text(session,"id"),"title":String(text(value,"title").prefix(160)),"url":url.absoluteString,"text":text(value,"text")]);try store.invalidate(session);return link
    }
    func sendChat(_ session: [String:Any],value: [String:Any]) async throws -> [String:Any] {
        let id=text(session,"id"),provider=text(value,"provider","gemini"),question=text(value,"text").trimmingCharacters(in:.whitespacesAndNewlines),request=text(value,"request_id",UUID().uuidString)
        guard ["gemini","openai","claude"].contains(provider),!Keychain.read(provider).isEmpty else {throw APIError.message("Add your selected AI service key in Settings on this device")}
        guard !question.isEmpty,question.count<=4000,request.range(of:"^[A-Za-z0-9-]{1,80}$",options:.regularExpression) != nil else {throw APIError.message("Enter a question under 4,000 characters")}
        let prefix="chat:\(id):\(request)"
        if let existing=try? store.get("message",prefix+":assistant") {return existing}
        let attachedLinks=try sessionLinks(id)
        let assets=try store.assets(session),transcript=text(session,"transcript")
        guard !transcript.isEmpty || !assets.contains(where:{text($0,"kind")=="audio"}) else {throw APIError.message("Generate notes or save a transcript first to chat about the recording")}
        guard !transcript.isEmpty || session["handout"] is [String:Any] || !assets.isEmpty || !attachedLinks.isEmpty else {throw APIError.message("Add a transcript or session materials before asking the tutor")}
        let history=try chatMessages(id).filter {text($0,"status")=="complete"}.suffix(12).map {["role":text($0,"role"),"content":ChatContext.excerpt(text($0,"content"),question:question,limit:4000)]}
        guard chatPending.insert(id).inserted else {throw APIError.message("Wait for the session tutor to finish")}
        defer {chatPending.remove(id)}
        var user: [String:Any]=["id":prefix+":user","session_id":id,"request_id":request,"role":"user","content":question,"provider":provider,"status":"pending"]
        do {
            user=try store.save("message",user)
            var sources=session["transcript_sources"] as? [[String:Any]] ?? [],attachments=[(URL,String)](),total=0
            if !transcript.isEmpty && sources.isEmpty {sources.append(["id":"transcript:manual","label":"Student transcript; timing not verified"])}
            guard assets.filter({text($0,"kind")=="photo"}).count<=20 else {throw APIError.message("Split sessions with more than 20 board photos")}
            for a in assets where text(a,"kind") != "audio" {
                let ref=text(a,"kind")=="photo" ? "photo:"+text(a,"id"):"resource:"+text(a,"id")+":1"
                var source: [String:Any]=["id":ref,"label":text(a,"name")+(text(a,"kind")=="photo" ? " · captured at \((a["offset_ms"] as? Int ?? 0)/1000)s":"")]
                let file=try store.file(text(a,"id"))
                if text(a,"mime")=="text/plain" {source["text"]=ChatContext.excerpt(try String(contentsOf:file,encoding:.utf8),question:question,limit:12000)}
                else {total += a["size"] as? Int ?? 0;guard total<=30*1024*1024 else {throw APIError.message("Combined images/PDFs must be under 30 MiB")};source["attachment_index"]=attachments.count;attachments.append((file,text(a,"mime")))}
                sources.append(source)
            }
            for link in try sessionLinks(id) {sources.append(["id":"link:"+text(link,"id"),"label":text(link,"title"),"text":ChatContext.excerpt(text(link,"text"),question:question,limit:12000),"links":[["url":text(link,"url"),"title":text(link,"title")]]])}
            let course=try store.get("course",text(session,"course_id"))
            if value["search"] as? Bool==true {try await discover(text(session,"course_id"),provider:provider,question:question)}
            if let found=try? store.get("discovery",text(session,"course_id")+":"+provider),let links=found["links"] as? [[String:String]],!links.isEmpty {sources.append(["id":"web:"+text(session,"course_id"),"label":"Public web search; verify original links","text":ChatContext.excerpt(text(found,"summary"),question:question,limit:12000),"links":links])}
            let evidence: [String:Any]=["course":course,"session_title":text(session,"title"),"transcript":ChatContext.excerpt(transcript,question:question,limit:32000),"handout":session["handout"] ?? NSNull(),"history":history,"question":question,"sources":sources]
            let reply=try await ai.note(provider,evidence:evidence,attachments:attachments,chat:true);try ChatContext.validate(reply,sources:sources)
            let citations=reply["citations"] as! [String]
            let cited=sources.filter {citations.contains(text($0,"id"))}.map {["id":text($0,"id"),"label":text($0,"label")]}
            user["status"]="complete";try store.save("message",user)
            let result=try store.save("message",["id":prefix+":assistant","session_id":id,"request_id":request,"role":"assistant","content":reply["answer"]!,"provider":provider,"model":ai.model(provider),"status":"complete","sources":cited,"links":reply["links"]!])
            return result
        } catch {user["status"]="failed";user["error"]=error.localizedDescription;try? store.save("message",user);throw error}
    }
}
