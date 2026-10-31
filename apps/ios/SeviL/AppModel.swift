import Foundation
import SwiftUI

@MainActor final class AppModel: ObservableObject {
    @Published var courses: [Course] = []
    @Published var sessions: [LectureSession] = []
    @Published var active: LectureSession?
    @Published var mode = "demo"
    @Published var error = ""
    @Published var busy = false
    @Published var publicSources: [PublicLink] = []
    @Published var providers: [AIProvider] = []
    @Published var provider = UserDefaults.standard.string(forKey:"provider") ?? "openai" { didSet { UserDefaults.standard.set(provider,forKey:"provider") } }
    @Published var audioProvider = UserDefaults.standard.string(forKey:"audioProvider") ?? "gemini" { didSet { UserDefaults.standard.set(audioProvider,forKey:"audioProvider") } }
    var generationOptions: [String:Any] { ["provider":provider,"transcription_provider":provider == "claude" ? audioProvider : provider] }
    @Published var locale: String = UserDefaults.standard.string(forKey: "locale") ?? "en" {
        didSet { UserDefaults.standard.set(locale, forKey: "locale") }
    }
    let api = API()
    private let catalog: [String: [String: String]] = {
        guard let url = Bundle.main.url(forResource: "locales", withExtension: "json"), let data = try? Data(contentsOf: url), let json = try? JSONDecoder().decode([String:[String:String]].self, from: data) else { return [:] }
        return json
    }()
    func t(_ key: String) -> String { catalog[locale]?[key] ?? catalog["en"]?[key] ?? key }
    func run(_ work: @escaping @MainActor () async throws -> Void) { Task { busy = true; error = ""; defer { busy = false }; do { try await work() } catch { self.error = error.localizedDescription } } }
    func refresh() async throws {
        async let c: [Course] = api.get("/v1/courses")
        async let s: [LectureSession] = api.get("/v1/sessions")
        async let h: Health = api.get("/health")
        async let p: ProviderCatalog = api.get("/v1/providers")
        let result = try await (c, s, h, p)
        courses = result.0; sessions = result.1; mode = result.2.mode
        providers = result.3.providers
        if UserDefaults.standard.string(forKey:"provider") == nil { provider = result.3.default_provider }
    }
    func open(_ id: String) async throws {
        active = try await api.get("/v1/sessions/\(id)")
        try await refreshSources()
    }
    func refreshSources() async throws {
        if let active { let discovery: Discovery = try await api.get("/v1/courses/\(active.course_id)/sources?provider=\(provider)"); publicSources = discovery.links }
    }
    func discover() async throws {
        guard let active else { return }
        let discovery: Discovery = try await api.get("/v1/courses/\(active.course_id)/discover",method:"POST",json:["provider":provider])
        publicSources = discovery.links
    }
    func createCourse(_ fields: [String: Any]) async throws -> Course { let course: Course = try await api.get("/v1/courses", method: "POST", json: fields); try await refresh(); return course }
    func createSession(course: String, title: String, permission: Bool) async throws {
        let session: LectureSession = try await api.get("/v1/sessions", method:"POST", json:["course_id":course,"title":title,"recording_permission":permission])
        try await refresh(); try await open(session.id)
    }
    func sample() async throws {
        let course: Course
        if let existing = courses.first(where: { $0.code == "DEMO" }) { course = existing }
        else { course = try await createCourse(["title":"Learning across languages","university":"SeviL sample university","code":"DEMO"]) }
        try await createSession(course: course.id, title: "From listening to understanding", permission: true)
        if mode == "demo" { try await generate() }
    }
    func generate() async throws { guard let active else { return }; _ = try await api.data("/v1/sessions/\(active.id)/generate", method:"POST", json:generationOptions); try await open(active.id) }
    func upload(_ file: URL, kind: String, name: String? = nil, offset: Int = 0, duration: Int = 0) async throws {
        guard let active else { return }
        try await api.upload(session:active.id, file:file, name:name ?? file.lastPathComponent, kind:kind, offset:offset, duration:duration)
        try await open(active.id); try await refresh()
    }
    func deleteAsset(_ id: String) async throws { _ = try await api.data("/v1/assets/\(id)", method:"DELETE"); if let active { try await open(active.id) } }
    func download(_ path: String, name: String) async throws -> URL {
        let data = try await api.data(path)
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory:true)
        try FileManager.default.createDirectory(at:folder, withIntermediateDirectories:true)
        let url = folder.appendingPathComponent(URL(fileURLWithPath:name).lastPathComponent)
        try data.write(to:url, options:.atomic)
        return url
    }
}
