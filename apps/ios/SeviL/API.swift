import Foundation
import Security

enum APIError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case let .message(text) = self { return text }; return nil }
}
struct API {
    func data(_ path: String, method: String = "GET", json: [String: Any]? = nil) async throws -> Data { try await DeviceWorkspace.shared.route(path,method:method,json:json) }
    func get<T: Decodable>(_ path: String, method: String = "GET", json: [String: Any]? = nil) async throws -> T { try JSONDecoder().decode(T.self,from:await data(path,method:method,json:json)) }
    func upload(session: String, file: URL, name: String, kind: String, offset: Int = 0, duration: Int = 0) async throws { try await DeviceWorkspace.shared.upload(session:session,file:file,name:name,kind:kind,offset:offset,duration:duration) }
}

enum Keychain {
    static let service = "com.sevil.learncompanion.device-keys"
    static func read(_ account: String = "server") -> String {
        var result: CFTypeRef?
        let query: [String: Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:account,kSecReturnData as String:true,kSecMatchLimit as String:kSecMatchLimitOne]
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }
    static func save(_ value: String, account: String = "server") {
        let value=value.trimmingCharacters(in:.whitespacesAndNewlines)
        let query: [String: Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:account]
        SecItemDelete(query as CFDictionary)
        guard !value.isEmpty else { return }
        var item = query
        item[kSecValueData as String] = Data(value.utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(item as CFDictionary, nil)
    }
}
