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
    static func save(_ value: String, account: String = "server") throws {
        let value=value.trimmingCharacters(in:.whitespacesAndNewlines)
        let query: [String: Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:account]
        guard !value.isEmpty else {
            let status=SecItemDelete(query as CFDictionary)
            guard status==errSecSuccess || status==errSecItemNotFound else { throw APIError.message("Cannot clear the saved key (Keychain \(status))") }
            return
        }
        var item = query
        item[kSecValueData as String] = Data(value.utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        var status=SecItemUpdate(query as CFDictionary,[kSecValueData as String:Data(value.utf8)] as CFDictionary)
        if status==errSecItemNotFound { status=SecItemAdd(item as CFDictionary,nil) }
        guard status==errSecSuccess else { throw APIError.message("Cannot save the API key securely (Keychain \(status)). Check that this app was signed correctly.") }
    }
}
