import AVFoundation
import SwiftUI
import UIKit

final class BoardCameraController: NSObject, ObservableObject, AVCapturePhotoCaptureDelegate {
    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private let queue = DispatchQueue(label:"com.sevil.camera")
    @Published var error = ""
    @Published var ready = false
    var onCapture: ((Data) -> Void)?
    func start() {
        AVCaptureDevice.requestAccess(for:.video) { granted in
            guard granted else { DispatchQueue.main.async { self.error = "Camera permission is required" }; return }
            self.queue.async {
                do {
                    self.session.beginConfiguration()
                    // The camera never owns microphone input or reconfigures the lecture audio session.
                    self.session.automaticallyConfiguresApplicationAudioSession = false
                    self.session.sessionPreset = .photo
                    guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for:.video, position:.back) else { throw APIError.message("Camera unavailable") }
                    let input = try AVCaptureDeviceInput(device:camera)
                    guard self.session.canAddInput(input), self.session.canAddOutput(self.output) else { throw APIError.message("Camera unavailable") }
                    self.session.addInput(input); self.session.addOutput(self.output)
                    self.session.commitConfiguration(); self.session.startRunning()
                    DispatchQueue.main.async { self.ready = true }
                } catch { self.session.commitConfiguration(); DispatchQueue.main.async { self.error = error.localizedDescription } }
            }
        }
    }
    func stop() { queue.async { if self.session.isRunning { self.session.stopRunning() } } }
    func capture() { output.capturePhoto(with:AVCapturePhotoSettings(format:[AVVideoCodecKey:AVVideoCodecType.jpeg]), delegate:self) }
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        DispatchQueue.main.async { if let error { self.error = error.localizedDescription }; if let data = photo.fileDataRepresentation() { self.onCapture?(data) } }
    }
}
private final class PreviewUIView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}
private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context:Context) -> UIView { let view = PreviewUIView(); view.previewLayer.session = session; view.previewLayer.videoGravity = .resizeAspectFill; return view }
    func updateUIView(_ uiView:UIView, context:Context) {}
}
struct BoardCamera: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @StateObject private var camera = BoardCameraController()
    @State private var offset = 0
    let position: () -> Int
    let onPhoto: (URL, Int) -> Void
    var body: some View {
        NavigationStack { VStack(spacing:20) {
            CameraPreview(session:camera.session).clipShape(RoundedRectangle(cornerRadius:16))
            if !camera.error.isEmpty { Text(camera.error).foregroundStyle(.red) }
            Button { offset = position(); camera.capture() } label: { Label(model.t("capture"), systemImage:"camera.fill").frame(maxWidth:.infinity) }.buttonStyle(.borderedProminent).disabled(!camera.ready)
        }.padding().navigationTitle(model.t("photo")).toolbar { Button(model.t("close")) { dismiss() } } }
        .onAppear { camera.onCapture = { data in
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("board-\(UUID().uuidString).jpg")
            do { try data.write(to:url); onPhoto(url,offset); dismiss() } catch { camera.error = error.localizedDescription }
        }; camera.start() }
        .onDisappear { camera.stop() }
    }
}
