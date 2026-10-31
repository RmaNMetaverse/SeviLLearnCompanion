import Foundation
import Security

enum APIError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case let .message(text) = self { return text }; return nil }
}
struct API {
    var server: String { UserDefaults.standard.string(forKey: "server") ?? "http://localhost:8000" }
    var token: String { Keychain.read() }
    func request(_ path: String, method: String = "GET", json: [String: Any]? = nil) throws -> URLRequest {
        guard let url = URL(string: server.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + path), ["http", "https"].contains(url.scheme ?? "") else { throw APIError.message("Invalid server URL") }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 300
        if !token.isEmpty { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let json { request.httpBody = try JSONSerialization.data(withJSONObject: json); request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        return request
    }
    func validate(_ data: Data, _ response: URLResponse) throws {
        guard let response = response as? HTTPURLResponse else { throw APIError.message("No server response") }
        guard (200..<300).contains(response.statusCode) else {
            let detail = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["detail"] as? String
            throw APIError.message(detail ?? "Server returned \(response.statusCode)")
        }
    }
    func data(_ path: String, method: String = "GET", json: [String: Any]? = nil) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request(path, method: method, json: json))
        try validate(data, response)
        return data
    }
    func get<T: Decodable>(_ path: String, method: String = "GET", json: [String: Any]? = nil) async throws -> T { try JSONDecoder().decode(T.self, from: await data(path, method: method, json: json)) }
    func upload(session: String, file: URL, name: String, kind: String, offset: Int = 0, duration: Int = 0) async throws {
        let boundary = "SeviL-\(UUID().uuidString)"
        let temporary = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        FileManager.default.createFile(atPath: temporary.path, contents: nil)
        defer { try? FileManager.default.removeItem(at: temporary) }
        let destination = try FileHandle(forWritingTo: temporary)
        func write(_ text: String) throws { try destination.write(contentsOf: Data(text.utf8)) }
        for (key, value) in [("kind",kind),("offset_ms",String(offset)),("duration_ms",String(duration))] { try write("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(key)\"\r\n\r\n\(value)\r\n") }
        let safeName = name.replacingOccurrences(of: "\"", with: "_").replacingOccurrences(of: "\r", with: "_").replacingOccurrences(of: "\n", with: "_")
        try write("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"\(safeName)\"\r\nContent-Type: application/octet-stream\r\n\r\n")
        let input = try FileHandle(forReadingFrom: file)
        do { while let chunk = try input.read(upToCount: 1024 * 1024), !chunk.isEmpty { try destination.write(contentsOf: chunk) } }
        catch { try? input.close(); try? destination.close(); throw error }
        try input.close()
        try write("\r\n--\(boundary)--\r\n")
        try destination.close()
        var req = try request("/v1/sessions/\(session)/assets", method: "POST")
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        let (data, response) = try await URLSession.shared.upload(for: req, fromFile: temporary)
        try validate(data, response)
    }
}
enum Keychain {
    static let service = "com.sevil.learncompanion.server-token"
    static func read() -> String {
        var result: CFTypeRef?
        let query: [String: Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:"server",kSecReturnData as String:true,kSecMatchLimit as String:kSecMatchLimitOne]
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }
    static func save(_ value: String) {
        let query: [String: Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:"server"]
        SecItemDelete(query as CFDictionary)
        guard !value.isEmpty else { return }
        var item = query
        item[kSecValueData as String] = Data(value.utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(item as CFDictionary, nil)
    }
}
