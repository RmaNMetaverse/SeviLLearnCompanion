import SwiftUI
import UniformTypeIdentifiers
import QuickLook
import UIKit

@main struct SeviLApp: App {
    @StateObject private var model = AppModel()
    @StateObject private var recorder = Recorder()
    var body: some Scene { WindowGroup { RootView().environmentObject(model).environmentObject(recorder).tint(Color(red:0.14,green:0.43,blue:0.37)) } }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var recorder: Recorder
    @State private var sheet: String?
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment:.leading,spacing:20) {
                    if model.mode == "demo" { Text(model.t("demoNotice")).font(.caption).padding(15).frame(maxWidth:.infinity,alignment:.leading).background(Color.yellow.opacity(0.09),in:RoundedRectangle(cornerRadius:12)) }
                    if !model.error.isEmpty { Text(model.error).foregroundStyle(.red).font(.callout).textSelection(.enabled) }
                    if model.busy { ProgressView().frame(maxWidth:.infinity) }
                    if model.active != nil { SessionView() } else { HomeView(sheet:$sheet) }
                }.padding(22).frame(maxWidth:1000).frame(maxWidth:.infinity)
            }.background(Color(red:0.97,green:0.98,blue:0.96))
            .navigationTitle("SeviL")
            .toolbar {
                ToolbarItem(placement:.topBarLeading) { if model.active != nil { Button(model.t("back")) { model.active = nil; model.run { try await model.refresh() } }.disabled(recorder.recording) } }
                ToolbarItem(placement:.topBarTrailing) { Button { sheet = "settings" } label: { Image(systemName:"gearshape") }.accessibilityLabel(model.t("settings")) }
            }
            .sheet(isPresented:Binding(get:{ sheet != nil },set:{ if !$0 { sheet = nil } })) {
                if sheet == "settings" { SettingsView() } else if sheet == "course" { CourseForm() } else { SessionForm() }
            }
            .task { model.run { try await model.refresh() } }
        }
    }
}

struct HomeView: View {
    @EnvironmentObject var model: AppModel
    @Binding var sheet: String?
    var body: some View {
        VStack(alignment:.leading,spacing:20) {
            Text(model.t("welcome")).font(.largeTitle.bold())
            Text(model.t("subtitle")).foregroundStyle(.secondary)
            HStack { Button(model.t("newCourse")) { sheet = "course" }.buttonStyle(.borderedProminent); Button(model.t("newSession")) { sheet = "session" }.buttonStyle(.bordered).disabled(model.courses.isEmpty) }
            VStack(alignment:.leading,spacing:14) {
                Label(model.t("local"),systemImage:"sparkles").font(.caption)
                Text(model.t("empty")).font(.title2.bold())
                Text(model.t("emptyBody")).font(.callout).foregroundStyle(.secondary)
                Button(model.t("demo")) { model.run { try await model.sample() } }
            }.padding(24).frame(maxWidth:.infinity,alignment:.leading).background(Color.green.opacity(0.07),in:RoundedRectangle(cornerRadius:18))
            Text(model.t("courses")).font(.title2.bold())
            ForEach(model.courses) { course in VStack(alignment:.leading,spacing:8) {
                Text(course.title).font(.headline)
                Text("\(course.university) · \(course.professor)").font(.caption).foregroundStyle(.secondary)
                Text("\(languages.first { $0.0 == course.lecture_language }?.1 ?? course.lecture_language) → \(languages.first { $0.0 == course.output_language }?.1 ?? course.output_language)").font(.caption)
            }.card() }
            HStack { Text(model.t("sessions")).font(.title2.bold()); Spacer(); Button(model.t("refresh")) { model.run { try await model.refresh() } }.font(.caption) }
            ForEach(model.sessions) { session in Button { model.run { try await model.open(session.id) } } label: {
                HStack { Image(systemName:"doc.text").font(.title2).padding(.trailing,8); VStack(alignment:.leading,spacing:7) { Text(session.title).font(.headline); Text(model.t(session.status)).font(.caption).foregroundStyle(.secondary) }; Spacer(); Image(systemName:"chevron.right").font(.caption) }.card()
            }.buttonStyle(.plain) }
        }.disabled(model.busy)
    }
}

struct SessionView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var recorder: Recorder
    @State private var tab = "notes"
    @State private var transcript = ""
    @State private var importKind = "resource"
    @State private var importing = false
    @State private var camera = false
    @State private var offset = "0"
    @State private var previewURL: URL?
    @State private var deleteConfirmation = false
    var session: LectureSession { model.active! }
    var assets: [Asset] { session.assets ?? [] }
    var audioEnd: Int { assets.filter { $0.kind == "audio" }.map { $0.offset_ms + $0.duration_ms }.max() ?? 0 }
    var disabled: Bool { model.busy || session.status == "processing" }
    var body: some View {
        VStack(alignment:.leading,spacing:20) {
            Text(session.title).font(.largeTitle.bold())
            Text(model.t(session.status)).font(.caption).foregroundStyle(.secondary)
            recordingPanel
            if !recorder.error.isEmpty { Text(recorder.error).foregroundStyle(.red).font(.caption) }
            if !recorder.recording { ForEach(recorder.pending) { clip in HStack {
                Label(clock(clip.duration),systemImage:"waveform"); Spacer()
                Button(model.t("retry")) { model.run { try await model.upload(clip.url,kind:"audio",offset:clip.offset,duration:clip.duration); recorder.remove(clip) } }
                ShareLink(item:clip.url) { Image(systemName:"square.and.arrow.up") }
            }.card().disabled(disabled) } }
            HStack {
                Button { model.run { try await model.generate() } } label: { Label(model.t("generate"),systemImage:"sparkles") }.buttonStyle(.borderedProminent).disabled(disabled || recorder.recording || !recorder.pending.isEmpty)
                Button { model.run { previewURL = try await model.download("/v1/sessions/\(session.id)/handout.pdf",name:"sevil-handout.pdf") } } label: { Image(systemName:"arrow.down.doc") }.buttonStyle(.bordered).accessibilityLabel(model.t("download")).disabled(session.status != "ready" || model.busy)
            }
            if let error = session.error { Text(error).foregroundStyle(.red).font(.callout) }
            Picker("",selection:$tab) { ForEach(["notes","transcript","evidence"],id:\.self) { Text(model.t($0)).tag($0) } }.pickerStyle(.segmented)
            if tab == "notes" { if let note = session.handout { HandoutView(note:note,sources:session.sources ?? []) } else { ContentUnavailableView(model.t("notes"),systemImage:"sparkles",description:Text(model.t("noNotes"))) } }
            if tab == "transcript" { TextEditor(text:$transcript).frame(minHeight:300).padding(10).background(.white,in:RoundedRectangle(cornerRadius:12)); Button(model.t("editTranscript")) { model.run { _ = try await model.api.data("/v1/sessions/\(session.id)/transcript",method:"PUT",json:["text":transcript]); try await model.open(session.id) } }.disabled(disabled || recorder.recording || transcript.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty) }
            if tab == "evidence" { ForEach(assets.filter { $0.kind != "resource" }) { asset in AssetCard(asset:asset,disabled:disabled || recorder.recording,preview:$previewURL) }; ForEach(session.sources ?? []) { source in Text(source.label).font(.caption).foregroundStyle(.secondary) } }
            materialPanel
            Button(role:.destructive) { deleteConfirmation = true } label: { Label(model.t("delete"),systemImage:"trash") }.disabled(disabled || recorder.recording)
        }
        .task(id:session.id) { transcript = session.transcript; recorder.load(session:session.id) }
        .task(id:session.status) {
            guard session.status == "processing" else { return }
            let id = session.id
            while !Task.isCancelled {
                do { try await Task.sleep(for:.seconds(2)); try await model.open(id); try await model.refresh() }
                catch is CancellationError { return }
                catch { model.error = error.localizedDescription; return }
            }
        }
        .sheet(isPresented:$camera) { BoardCamera(position:{ recorder.recording ? recorder.position : (Int(offset) ?? 0) * 1000 }) { url, position in model.run { try await model.upload(url,kind:"photo",offset:position); try? FileManager.default.removeItem(at:url) } } }
        .fileImporter(isPresented:$importing,allowedContentTypes:importKind == "resource" ? [.pdf,.plainText] : importKind == "audio" ? [.audio,.mpeg4Movie] : [.jpeg,.png,.webP]) { result in
            switch result {
            case .success(let url): model.run {
                let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                try await model.upload(url,kind:importKind,offset:importKind == "photo" ? (Int(offset) ?? 0) * 1000 : importKind == "audio" ? audioEnd : 0)
            }
            case .failure(let error): model.error = error.localizedDescription
            }
        }
        .quickLookPreview($previewURL)
        .confirmationDialog(model.t("deleteConfirm"),isPresented:$deleteConfirmation,titleVisibility:.visible) {
            Button(model.t("delete"),role:.destructive) { model.run { _ = try await model.api.data("/v1/sessions/\(session.id)",method:"DELETE"); model.active = nil; try await model.refresh() } }
        }
    }
    var recordingPanel: some View {
        VStack(alignment:.leading,spacing:18) {
            HStack { Image(systemName:"mic.fill").font(.title).foregroundStyle(recorder.recording ? .red : .green); Text(clock(recorder.recording ? recorder.elapsed : audioEnd)).font(.largeTitle.monospacedDigit()); Spacer(); if recorder.recording { Circle().fill(.red).frame(width:9,height:9) } }
            HStack {
                Button(model.t(recorder.recording ? "stop" : "record")) { if recorder.recording { recorder.stop() } else { model.run { try await recorder.start(session:session.id,offset:audioEnd) } } }.buttonStyle(.borderedProminent).disabled(disabled && !recorder.recording)
                Button { camera = true } label: { Label(model.t("photo"),systemImage:"camera") }.buttonStyle(.bordered).disabled(disabled)
            }
        }.card()
    }
    var materialPanel: some View {
        VStack(alignment:.leading,spacing:16) {
            Text(model.t("resources")).font(.title2.bold())
            ForEach(assets.filter { $0.kind == "resource" }) { asset in HStack { Label(asset.name,systemImage:"doc").font(.caption); Spacer(); Button(role:.destructive) { model.run { try await model.deleteAsset(asset.id) } } label: { Image(systemName:"xmark") }.disabled(disabled || recorder.recording) } }
            Button(model.t("attach")) { importKind = "resource"; importing = true }.disabled(disabled)
            Text(model.t("publicSources")).font(.headline)
            Text(model.t("sourceNotice")).font(.caption).foregroundStyle(.secondary)
            ForEach(model.publicSources) { source in if let url = URL(string:source.url), url.scheme == "https" { Link(source.title,destination:url).font(.caption) } }
            Button(model.t("discover")) { model.run { try await model.discover() } }.disabled(disabled || recorder.recording || model.mode == "demo")
            Button(model.t("uploadAudio")) { importKind = "audio"; importing = true }.disabled(disabled || recorder.recording)
            if !recorder.recording { TextField(model.t("photoOffset"),text:$offset).keyboardType(.numberPad).textFieldStyle(.roundedBorder); Button(model.t("photo")) { importKind = "photo"; importing = true }.disabled(disabled) }
        }.card()
    }
}

struct AssetCard: View {
    @EnvironmentObject var model: AppModel
    let asset: Asset
    let disabled: Bool
    @Binding var preview: URL?
    @State private var image: UIImage?
    var body: some View {
        VStack(alignment:.leading,spacing:12) {
            if let image { Image(uiImage:image).resizable().scaledToFit().frame(maxHeight:320).clipShape(RoundedRectangle(cornerRadius:8)) }
            Text(asset.name).font(.headline)
            HStack { Text(clock(asset.offset_ms)).font(.caption).foregroundStyle(.secondary); Spacer(); Button { model.run { preview = try await model.download("/v1/assets/\(asset.id)",name:asset.name) } } label: { Image(systemName:asset.kind == "audio" ? "play.circle" : "square.and.arrow.up") }; Button(role:.destructive) { model.run { try await model.deleteAsset(asset.id) } } label: { Image(systemName:"trash") }.disabled(disabled) }
        }.card().task(id:asset.id) { if asset.kind == "photo" { do { image = UIImage(data:try await model.api.data("/v1/assets/\(asset.id)")) } catch { model.error = error.localizedDescription } } }
    }
}

struct HandoutView: View {
    @EnvironmentObject var model: AppModel
    let note: Handout
    let sources: [Source]
    var body: some View {
        VStack(alignment:.leading,spacing:18) {
            Text("SEVIL / " + model.t("notes")).font(.caption2).tracking(2).foregroundStyle(.secondary)
            Text(note.title).font(.title.bold()); Text(note.summary).foregroundStyle(.secondary)
            Text(model.t("objectives")).font(.title3.bold())
            ForEach(Array(note.objectives.enumerated()),id:\.offset) { _, item in Text("• " + item) }
            ForEach(Array(note.sections.enumerated()),id:\.offset) { _, section in VStack(alignment:.leading,spacing:10) {
                Text(section.kind.uppercased()).font(.caption2).foregroundStyle(.secondary)
                Text(section.heading).font(.title3.bold()); Text(section.body)
                ForEach(section.citations,id:\.self) { ref in Text(sources.first { $0.id == ref }?.label ?? ref).font(.caption).foregroundStyle(.secondary) }
            }.padding(.top,12) }
            Text(model.t("glossary")).font(.title3.bold())
            ForEach(Array(note.glossary.enumerated()),id:\.offset) { _, term in VStack(alignment:.leading,spacing:7) { Text(term.term + " / " + term.translation).font(.headline); Text(term.definition).foregroundStyle(.secondary); Divider() } }
            Text(model.t("questions")).font(.title3.bold()); ForEach(Array(note.questions.enumerated()),id:\.offset) { i, item in Text("\(i + 1). \(item)") }
            VStack(alignment:.leading,spacing:10) { Text(model.t("uncertainties")).font(.headline); ForEach(Array(note.uncertainties.enumerated()),id:\.offset) { _, item in Text("• " + item).font(.callout) } }.padding(18).background(Color.yellow.opacity(0.08),in:RoundedRectangle(cornerRadius:12))
        }.card().textSelection(.enabled)
    }
}

extension View {
    func card() -> some View { padding(20).frame(maxWidth:.infinity,alignment:.leading).background(Color.white,in:RoundedRectangle(cornerRadius:16)) }
}
