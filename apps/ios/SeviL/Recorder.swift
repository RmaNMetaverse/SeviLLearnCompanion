import AVFoundation
import Foundation

struct PendingRecording: Codable, Identifiable {
    let id: String
    let session: String
    let offset: Int
    var duration: Int
    var url: URL { Recorder.folder.appendingPathComponent(id + ".m4a") }
    var metadata: URL { Recorder.folder.appendingPathComponent(id + ".json") }
}

@MainActor final class Recorder: NSObject, ObservableObject, AVAudioRecorderDelegate {
    nonisolated static var folder: URL {
        let path = FileManager.default.urls(for:.applicationSupportDirectory, in:.userDomainMask)[0].appendingPathComponent("Recordings", isDirectory:true)
        try? FileManager.default.createDirectory(at:path, withIntermediateDirectories:true)
        return path
    }
    @Published var recording = false
    @Published var elapsed = 0
    @Published var pending: [PendingRecording] = []
    @Published var error = ""
    private var audio: AVAudioRecorder?
    private var clip: PendingRecording?
    private var timer: Timer?
    private var started = 0.0
    private var interrupted: NSObjectProtocol?
    override init() {
        super.init()
        interrupted = NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object:nil, queue:.main) { [weak self] note in
            guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt, raw == AVAudioSession.InterruptionType.began.rawValue else { return }
            Task { @MainActor in self?.stop(); self?.error = "Recording interrupted. Review the saved audio before continuing." }
        }
    }
    func start(session: String, offset: Int) async throws {
        let granted = await withCheckedContinuation { continuation in AVAudioSession.sharedInstance().requestRecordPermission { continuation.resume(returning:$0) } }
        guard granted else { throw APIError.message("Microphone permission is required") }
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playAndRecord, mode:.default, options:[.defaultToSpeaker])
        try audioSession.setActive(true)
        let clip = PendingRecording(id:UUID().uuidString, session:session, offset:offset, duration:0)
        let recorder = try AVAudioRecorder(url:clip.url, settings:[AVFormatIDKey:kAudioFormatMPEG4AAC, AVSampleRateKey:44100, AVNumberOfChannelsKey:1, AVEncoderBitRateKey:96000])
        recorder.delegate = self
        recorder.prepareToRecord()
        guard recorder.record() else { throw APIError.message("Unable to start recording") }
        self.clip = clip; audio = recorder
        try JSONEncoder().encode(clip).write(to:clip.metadata, options:.atomic)
        try? FileManager.default.setAttributes([.protectionKey:FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath:clip.url.path)
        recording = true; error = ""; started = ProcessInfo.processInfo.systemUptime; elapsed = offset
        timer = Timer.scheduledTimer(withTimeInterval:0.25, repeats:true) { [weak self] _ in Task { @MainActor in guard let self else { return }; self.elapsed = self.position } }
    }
    var position: Int { guard let clip else { return elapsed }; return clip.offset + Int((ProcessInfo.processInfo.systemUptime - started) * 1000) }
    func stop() {
        guard recording, var clip else { return }
        clip.duration = Int((audio?.currentTime ?? 0) * 1000)
        audio?.stop(); audio = nil
        timer?.invalidate(); timer = nil
        try? JSONEncoder().encode(clip).write(to:clip.metadata, options:.atomic)
        recording = false; elapsed = clip.offset + clip.duration; self.clip = nil
        try? AVAudioSession.sharedInstance().setActive(false, options:.notifyOthersOnDeactivation)
        load(session:clip.session)
    }
    func load(session: String) {
        pending = ((try? FileManager.default.contentsOfDirectory(at:Self.folder, includingPropertiesForKeys:nil)) ?? []).filter { $0.pathExtension == "json" }.compactMap { url -> PendingRecording? in
            guard let data = try? Data(contentsOf:url), var clip = try? JSONDecoder().decode(PendingRecording.self, from:data), clip.session == session, FileManager.default.fileExists(atPath:clip.url.path) else { return nil }
            if clip.duration == 0, let recovered = try? AVAudioPlayer(contentsOf:clip.url) { clip.duration = Int(recovered.duration * 1000) }
            return clip
        }
    }
    func remove(_ clip: PendingRecording) { try? FileManager.default.removeItem(at:clip.url); try? FileManager.default.removeItem(at:clip.metadata); load(session:clip.session) }
    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) { Task { @MainActor in self.stop(); self.error = error?.localizedDescription ?? "Recording interrupted" } }
    deinit { if let interrupted { NotificationCenter.default.removeObserver(interrupted) }; timer?.invalidate() }
}
