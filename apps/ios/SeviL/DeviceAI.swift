import Foundation

struct DeviceAI {
    let contract: [String:Any]
    init() throws {
        guard let url=Bundle.main.url(forResource:"ai-contract",withExtension:"json"),let value=try JSONSerialization.jsonObject(with:Data(contentsOf:url)) as? [String:Any] else { throw APIError.message("Missing AI contract") };contract=value
    }
    func model(_ provider: String) -> String { (contract["models"] as! [String:String])[provider]! }
    func request(_ provider: String,_ path: String,_ body: [String:Any]) async throws -> [String:Any] {
        let key=Keychain.read(provider);guard !key.isEmpty else { throw APIError.message("Add your \(provider) API key in Settings on this device. No laptop is needed.") }
        let base=["gemini":"https://generativelanguage.googleapis.com/v1beta/","openai":"https://api.openai.com/v1/","claude":"https://api.anthropic.com/v1/"][provider]!
        var req=URLRequest(url:URL(string:base+path)!);req.httpMethod="POST";req.timeoutInterval=600;req.httpBody=try JSONSerialization.data(withJSONObject:body);req.setValue("application/json",forHTTPHeaderField:"Content-Type")
        if provider=="gemini" { req.setValue(key,forHTTPHeaderField:"x-goog-api-key") }
        else if provider=="openai" { req.setValue("Bearer \(key)",forHTTPHeaderField:"Authorization") }
        else { req.setValue(key,forHTTPHeaderField:"x-api-key");req.setValue("2023-06-01",forHTTPHeaderField:"anthropic-version") }
        let (data,response)=try await URLSession.shared.data(for:req);try validate(provider,response)
        guard let value=try JSONSerialization.jsonObject(with:data) as? [String:Any] else { throw APIError.message("Empty AI response") };return value
    }
    func validate(_ provider: String,_ response: URLResponse) throws { guard let r=response as? HTTPURLResponse,(200..<300).contains(r.statusCode) else { throw APIError.message("\(provider) returned HTTP \((response as? HTTPURLResponse)?.statusCode ?? 0). Check your device key, internet connection and quota. No other service was used.") } }
    func part(_ file: URL,_ mime: String) throws -> [String:Any] { ["inlineData":["mimeType":mime,"data":try Data(contentsOf:file).base64EncodedString()]] }
    func gemini(_ parts: [[String:Any]],instructions: String="",schema: Bool=false,search: Bool=false) async throws -> [String:Any] {
        var config: [String:Any]=["maxOutputTokens":65536]
        if schema { config["responseMimeType"]="application/json";config["responseJsonSchema"]=contract["schema"] }
        var body: [String:Any]=["contents":[["role":"user","parts":parts]],"generationConfig":config]
        if !instructions.isEmpty { body["systemInstruction"]=["parts":[["text":instructions]]] }
        if search { body["tools"]=[["google_search":[:]]] }
        return try await request("gemini","models/\(model("gemini")):generateContent",body)
    }
    func geminiText(_ response: [String:Any]) throws -> String {
        guard let candidate=(response["candidates"] as? [[String:Any]])?.first,let content=candidate["content"] as? [String:Any],let parts=content["parts"] as? [[String:Any]] else { throw APIError.message("Gemini returned no content. Your files remain on this device.") }
        guard candidate["finishReason"] as? String=="STOP" else { throw APIError.message("Gemini response incomplete. Split the session or retry; no partial handout was published.") }
        return parts.filter { $0["thought"] as? Bool != true }.compactMap { $0["text"] as? String }.joined()
    }
    func media(_ file: URL,_ mime: String,operation: ([String:Any]) async throws -> String) async throws -> String {
        let size=(try file.resourceValues(forKeys:[.fileSizeKey])).fileSize ?? 0
        if size<8*1024*1024 { return try await operation(part(file,mime)) }
        let key=Keychain.read("gemini");guard !key.isEmpty else { throw APIError.message("Add your Gemini key in Settings") }
        var start=URLRequest(url:URL(string:"https://generativelanguage.googleapis.com/upload/v1beta/files")!);start.httpMethod="POST";start.setValue(key,forHTTPHeaderField:"x-goog-api-key");start.setValue("application/json",forHTTPHeaderField:"Content-Type");start.setValue("resumable",forHTTPHeaderField:"X-Goog-Upload-Protocol");start.setValue("start",forHTTPHeaderField:"X-Goog-Upload-Command");start.setValue(String(size),forHTTPHeaderField:"X-Goog-Upload-Header-Content-Length");start.setValue(mime,forHTTPHeaderField:"X-Goog-Upload-Header-Content-Type");start.httpBody=try JSONSerialization.data(withJSONObject:["file":["display_name":"SeviL lecture"]])
        let (_,response)=try await URLSession.shared.data(for:start);try validate("Gemini",response)
        guard let address=(response as? HTTPURLResponse)?.value(forHTTPHeaderField:"X-Goog-Upload-URL"),let url=URL(string:address),url.scheme=="https",url.host?.hasSuffix(".googleapis.com")==true else { throw APIError.message("Unexpected upload address") }
        var upload=URLRequest(url:url);upload.httpMethod="POST";upload.timeoutInterval=600;upload.setValue(key,forHTTPHeaderField:"x-goog-api-key");upload.setValue("0",forHTTPHeaderField:"X-Goog-Upload-Offset");upload.setValue("upload, finalize",forHTTPHeaderField:"X-Goog-Upload-Command");upload.setValue(mime,forHTTPHeaderField:"Content-Type")
        let (data,uploaded)=try await URLSession.shared.upload(for:upload,fromFile:file);try validate("Gemini",uploaded)
        guard var item=(try JSONSerialization.jsonObject(with:data) as? [String:Any])?["file"] as? [String:Any],let name=item["name"] as? String,name.hasPrefix("files/") else { throw APIError.message("Gemini upload did not complete") }
        func fileRequest(_ method: String) -> URLRequest { var req=URLRequest(url:URL(string:"https://generativelanguage.googleapis.com/v1beta/"+name)!);req.httpMethod=method;req.setValue(key,forHTTPHeaderField:"x-goog-api-key");return req }
        do {
            for _ in 0..<90 { if item["state"] as? String != "PROCESSING" { break };try await Task.sleep(for:.seconds(1));let (d,r)=try await URLSession.shared.data(for:fileRequest("GET"));try validate("Gemini",r);item=try JSONSerialization.jsonObject(with:d) as! [String:Any] }
            guard !["PROCESSING","FAILED"].contains(item["state"] as? String ?? ""),let uri=item["uri"] as? String else { throw APIError.message("Gemini could not prepare the audio; the original remains saved on this device") }
            let value=try await operation(["fileData":["mimeType":mime,"fileUri":uri]])
            _=try? await URLSession.shared.data(for:fileRequest("DELETE"));return value
        } catch { _=try? await URLSession.shared.data(for:fileRequest("DELETE"));throw error }
    }
    func transcribe(_ provider: String,file: URL,mime: String,language: String) async throws -> String {
        let prompt=(contract["transcript_prompt"] as! String)+" Expected language: \(language)."
        if provider=="gemini" { return try await media(file,mime) { part in try geminiText(await gemini([["text":prompt],part])) } }
        guard provider=="openai" else { throw APIError.message("Select Gemini or OpenAI for audio") }
        let data=try Data(contentsOf:file);guard data.count<24*1024*1024 else { throw APIError.message("For OpenAI, use files under 24 MiB or choose Gemini for long recordings") }
        let key=Keychain.read("openai");guard !key.isEmpty else { throw APIError.message("Add your OpenAI key in Settings") }
        let boundary=UUID().uuidString
        var body=Data();func append(_ text: String) { body.append(Data(text.utf8)) }
        for (name,value) in [("model","gpt-transcribe"),("prompt",prompt)] { append("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"\r\n\r\n\(value)\r\n") }
        append("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"lecture.m4a\"\r\nContent-Type: \(mime)\r\n\r\n");body.append(data);append("\r\n--\(boundary)--\r\n")
        var req=URLRequest(url:URL(string:"https://api.openai.com/v1/audio/transcriptions")!);req.httpMethod="POST";req.timeoutInterval=600;req.setValue("Bearer \(key)",forHTTPHeaderField:"Authorization");req.setValue("multipart/form-data; boundary=\(boundary)",forHTTPHeaderField:"Content-Type");req.httpBody=body
        let (result,response)=try await URLSession.shared.data(for:req);try validate(provider,response)
        return (try JSONSerialization.jsonObject(with:result) as? [String:Any])?["text"] as? String ?? ""
    }
    func note(_ provider: String,evidence: [String:Any],attachments: [(URL,String)]) async throws -> [String:Any] {
        let text=String(data:try JSONSerialization.data(withJSONObject:evidence),encoding:.utf8)!
        var parts: [[String:Any]]=[["text":text]]
        for (url,mime) in attachments { parts.append(try part(url,mime)) }
        let output: String
        if provider=="gemini" { output=try geminiText(await gemini(parts,instructions:contract["notes_prompt"] as! String,schema:true)) }
        else {
            let content: [[String:Any]]=parts.map { part in
                if let text=part["text"] as? String { return ["type":provider=="openai" ? "input_text":"text","text":text] }
                let inline=part["inlineData"] as! [String:String];let mime=inline["mimeType"]!,data=inline["data"]!
                if provider=="openai" { return mime=="application/pdf" ? ["type":"input_file","filename":"course.pdf","file_data":"data:\(mime);base64,\(data)"] : ["type":"input_image","image_url":"data:\(mime);base64,\(data)"] }
                return ["type":mime=="application/pdf" ? "document":"image","source":["type":"base64","media_type":mime,"data":data]]
            }
            if provider=="openai" {
                let result=try await request(provider,"responses",["model":model(provider),"store":false,"instructions":contract["notes_prompt"]!,"input":[["role":"user","content":content]],"text":["format":["type":"json_schema","name":"lecture_handout","strict":true,"schema":contract["schema"]!]]])
                output=(result["output"] as? [[String:Any]] ?? []).flatMap { $0["content"] as? [[String:Any]] ?? [] }.filter { $0["type"] as? String=="output_text" }.compactMap { $0["text"] as? String }.joined()
            } else {
                let result=try await request(provider,"messages",["model":model(provider),"max_tokens":16384,"system":contract["notes_prompt"]!,"messages":[["role":"user","content":content]],"output_config":["format":["type":"json_schema","schema":contract["schema"]!]]])
                guard result["stop_reason"] as? String=="end_turn" else { throw APIError.message("Claude response incomplete") }
                output=(result["content"] as? [[String:Any]] ?? []).filter { $0["type"] as? String=="text" }.compactMap { $0["text"] as? String }.joined()
            }
        }
        guard let value=try JSONSerialization.jsonObject(with:Data(output.utf8)) as? [String:Any] else { throw APIError.message("AI returned invalid notes") };return value
    }
}
