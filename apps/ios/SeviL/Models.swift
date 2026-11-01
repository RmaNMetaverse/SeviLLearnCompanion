import Foundation

struct Course: Codable, Identifiable {
    let id: String
    let title: String
    let university: String
    let degree: String
    let professor: String
    let code: String
    let lecture_language: String
    let output_language: String
}
struct Asset: Codable, Identifiable {
    let id: String
    let name: String
    let kind: String
    let mime: String
    let offset_ms: Int
    let duration_ms: Int
}
struct Source: Codable, Identifiable { let id: String; let label: String }
struct NoteSection: Codable { let heading: String; let body: String; let kind: String; let citations: [String] }
struct Term: Codable { let term: String; let translation: String; let definition: String }
struct Handout: Codable {
    let title: String
    let summary: String
    let objectives: [String]
    let sections: [NoteSection]
    let glossary: [Term]
    let questions: [String]
    let uncertainties: [String]
}
struct LectureSession: Codable, Identifiable {
    let id: String
    let course_id: String
    let title: String
    let created_at: String
    let status: String
    let transcript: String
    let handout: Handout?
    let error: String?
    let assets: [Asset]?
    let sources: [Source]?
    let generated_mode: String?
    let generated_provider: String?
}
struct AIProvider: Decodable, Identifiable { let id: String; let label: String; let configured: Bool; let audio_supported: Bool; let model: String }
struct ProviderCatalog: Decodable { let default_provider: String; let providers: [AIProvider] }
struct Health: Decodable { let mode: String; let ai_configured: Bool }
struct PublicLink: Codable, Identifiable { let url: String; let title: String; var id: String { url } }
struct Discovery: Decodable { let links: [PublicLink]; let summary: String }
let languages: [(String, String)] = [("en","English"),("tr","Türkçe"),("zh-Hans","简体中文"),("ja","日本語"),("ko","한국어"),("de","Deutsch"),("fr","Français"),("es","Español")]
func clock(_ milliseconds: Int) -> String { String(format: "%02d:%02d", milliseconds / 60000, milliseconds / 1000 % 60) }

struct SessionLink: Codable, Identifiable {let id: String;let title: String;let url: String;let text: String}
struct ChatMessage: Codable, Identifiable {let id: String;let role: String;let content: String;let provider: String;let status: String;let request_id: String;let error: String?;let sources: [Source]?;let links: [PublicLink]?}
