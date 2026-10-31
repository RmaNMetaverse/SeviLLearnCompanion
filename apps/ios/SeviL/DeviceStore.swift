import Foundation
import SQLite3

final class DeviceStore {
    private var db: OpaquePointer?
    let folder: URL
    let files: URL
    init() throws {
        folder=FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("SeviLLibrary",isDirectory:true)
        files=folder.appendingPathComponent("files",isDirectory:true)
        try FileManager.default.createDirectory(at:files,withIntermediateDirectories:true)
        var resource=URLResourceValues();resource.isExcludedFromBackup=true;var root=folder;try? root.setResourceValues(resource)
        guard sqlite3_open_v2(folder.appendingPathComponent("library.sqlite3").path,&db,SQLITE_OPEN_READWRITE|SQLITE_OPEN_CREATE|SQLITE_OPEN_FULLMUTEX,nil)==SQLITE_OK else { throw APIError.message("Cannot open the on-device library") }
        sqlite3_exec(db,"PRAGMA journal_mode=WAL; CREATE TABLE IF NOT EXISTS records (id TEXT PRIMARY KEY, kind TEXT NOT NULL, payload TEXT NOT NULL)",nil,nil,nil)
    }
    deinit { sqlite3_close(db) }
    private func execute(_ sql: String,_ values: [String]) throws -> [[String:Any]] {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db,sql,-1,&statement,nil)==SQLITE_OK else { throw APIError.message("Cannot read the local database") }
        defer { sqlite3_finalize(statement) }
        let transient=unsafeBitCast(-1,to:sqlite3_destructor_type.self)
        for (i,value) in values.enumerated() { sqlite3_bind_text(statement,Int32(i+1),value,-1,transient) }
        var results=[[String:Any]]();var status=sqlite3_step(statement)
        while status==SQLITE_ROW {
            if let text=sqlite3_column_text(statement,0),let data=String(cString:text).data(using:.utf8),let value=try JSONSerialization.jsonObject(with:data) as? [String:Any] { results.append(value) }
            status=sqlite3_step(statement)
        }
        guard status==SQLITE_DONE else { throw APIError.message("Local database operation failed") }
        return results
    }
    @discardableResult func save(_ kind: String,_ value: [String:Any]) throws -> [String:Any] {
        var value=value;value["id"]=value["id"] ?? UUID().uuidString;value["created_at"]=value["created_at"] ?? ISO8601DateFormatter().string(from:Date())
        let json=String(data:try JSONSerialization.data(withJSONObject:value),encoding:.utf8)!
        _=try execute("INSERT OR REPLACE INTO records VALUES (?,?,?)",[value["id"] as! String,kind,json]);return value
    }
    func all(_ kind: String) throws -> [[String:Any]] { try execute("SELECT payload FROM records WHERE kind=? ORDER BY rowid DESC",[kind]) }
    func get(_ kind: String,_ id: String) throws -> [String:Any] { guard let value=try execute("SELECT payload FROM records WHERE kind=? AND id=?",[kind,id]).first else { throw APIError.message("\(kind) not found on this device") };return value }
    func remove(_ id: String) throws { _=try execute("DELETE FROM records WHERE id=?",[id]) }
    func file(_ id: String) throws -> URL { guard !id.isEmpty && id.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0=="-") }) else { throw APIError.message("Invalid attachment ID") };return files.appendingPathComponent(id) }
    func assets(_ session: [String:Any]) throws -> [[String:Any]] { try all("asset").filter { ($0["session_id"] as? String)==(session["id"] as? String) || (($0["kind"] as? String)=="resource" && ($0["course_id"] as? String)==(session["course_id"] as? String)) } }
    func invalidate(_ session: [String:Any]) throws { var s=session;s["status"]="draft";s["handout"]=NSNull();s["sources"]=[];s["error"]=NSNull();try save("session",s) }
}
