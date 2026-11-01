import SwiftUI
import UniformTypeIdentifiers
import QuickLook
import UIKit

@main struct SeviLApp: App {
    @StateObject private var model = AppModel()
    @StateObject private var recorder = Recorder()
    var body: some Scene { WindowGroup { RootView().environmentObject(model).environmentObject(recorder).tint(Design.primary).preferredColorScheme(.light) } }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var recorder: Recorder
    @State private var sheet: String?
    @State private var destination="overview"
    let destinations=["overview","courses","sessions","settings"]
    let icons=["square.grid.2x2","books.vertical","rectangle.stack","gearshape"]
    func navigate(_ key: String) {if key=="settings" {sheet="settings"} else {model.active=nil;destination=key}}
    var body: some View {
        GeometryReader { geometry in
            HStack(spacing:0) {
                if geometry.size.width>=Design.compactBelow {sidebar(expanded:geometry.size.width>=Design.expandedFrom)}
                NavigationStack {
                    ScrollView {
                        VStack(alignment:.leading,spacing:20) {
                            if !model.error.isEmpty {Label(model.error,systemImage:"exclamationmark.circle").font(.callout).foregroundStyle(Design.danger).padding(16).frame(maxWidth:.infinity,alignment:.leading).background(Color.red.opacity(0.06),in:RoundedRectangle(cornerRadius:14))}
                            if model.busy {ProgressView().frame(maxWidth:.infinity)}
                            if let session=model.active {SessionView().id(session.id)} else {LibraryView(sheet:$sheet,destination:destination,width:geometry.size.width)}
                        }.padding(geometry.size.width<600 ? 18:28).frame(maxWidth:1200).frame(maxWidth:.infinity)
                    }.background(Design.background)
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement:.principal) {HStack(spacing:8) {Image(systemName:"waveform").font(.title3);Text("SeviL").font(.title2.weight(.heavy))}.foregroundStyle(Design.forest)}
                        ToolbarItem(placement:.topBarLeading) {if model.active != nil {Button {model.active=nil;destination="sessions"} label:{Image(systemName:"arrow.left")}.accessibilityLabel(model.t("back")).disabled(recorder.recording || model.busy)}}
                        ToolbarItem(placement:.topBarTrailing) {Button {sheet="settings"} label:{Image(systemName:"gearshape")}.accessibilityLabel(model.t("settings"))}
                    }
                    .safeAreaInset(edge:.bottom,spacing:0) {if geometry.size.width<Design.compactBelow {bottomNavigation}}
                    .sheet(isPresented:Binding(get:{sheet != nil},set:{if !$0 {sheet=nil}})) {if sheet=="settings" {SettingsView()} else if sheet=="course" {CourseForm()} else {SessionForm()}}
                    .task {model.run {try await model.refresh()}}
                    .task(id:model.provider) {do {try await model.refreshSources()} catch {model.error=error.localizedDescription}}
                }
            }.foregroundStyle(Design.ink)
        }
    }
    var bottomNavigation: some View {
        HStack(spacing:6) {ForEach(Array(destinations.enumerated()),id:\.offset) {index,key in
            Button {navigate(key)} label:{VStack(spacing:5) {Image(systemName:icons[index]).font(.system(size:21));Text(model.t(key)).font(.system(size:10,weight:.medium)).lineLimit(2).multilineTextAlignment(.center)}.frame(maxWidth:.infinity,minHeight:56).foregroundStyle(selected(key) ? Design.forest:Design.muted).background(selected(key) ? Design.mint:Color.clear,in:RoundedRectangle(cornerRadius:14))}.buttonStyle(.plain).disabled(recorder.recording || model.busy)
        }}.padding(.horizontal,8).padding(.vertical,8).background(.regularMaterial).overlay(alignment:.top) {Rectangle().fill(Design.line).frame(height:1)}
    }
    func selected(_ key: String) -> Bool {key=="settings" ? sheet=="settings":model.active != nil ? key=="sessions":destination==key}
    func sidebar(expanded: Bool) -> some View {
        VStack(alignment:expanded ? .leading:.center,spacing:10) {
            HStack(spacing:10) {Image(systemName:"waveform").font(.title2);if expanded {Text("SeviL").font(.largeTitle.weight(.heavy))}}.foregroundStyle(Design.forest).padding(.vertical,24)
            ForEach(Array(destinations.enumerated()),id:\.offset) {index,key in
                Button {navigate(key)} label:{Group {if expanded {HStack(spacing:12) {Image(systemName:icons[index]).font(.title3);Text(model.t(key)).font(.subheadline.weight(.semibold));Spacer()}} else {VStack(spacing:6) {Image(systemName:icons[index]).font(.title3);Text(model.t(key)).font(.system(size:10)).multilineTextAlignment(.center)}}}.padding(12).frame(maxWidth:.infinity,minHeight:50).foregroundStyle(selected(key) ? Design.forest:Design.muted).background(selected(key) ? Design.mint:Color.clear,in:RoundedRectangle(cornerRadius:14))}.buttonStyle(.plain).disabled(recorder.recording || model.busy)
            }
            Spacer()
            if expanded {Label(model.t("localBadge"),systemImage:"checkmark.shield").font(.caption).foregroundStyle(Design.primary).padding(14).background(Design.mint,in:RoundedRectangle(cornerRadius:16))}
        }.padding(expanded ? 20:10).frame(width:expanded ? 240:88).background(Design.surface).overlay(alignment:.trailing) {Rectangle().fill(Design.line).frame(width:1)}
    }
}

struct LibraryView: View {
    @EnvironmentObject var model: AppModel
    @Binding var sheet: String?
    let destination: String
    let width: CGFloat
    @State private var search=""
    var filteredSessions: [LectureSession] {let list=model.sessions.filter {session in search.isEmpty || (session.title+" "+(model.courses.first {c in c.id==session.course_id}?.title ?? "")).localizedCaseInsensitiveContains(search)};return destination=="overview" ? Array(list.prefix(3)):list}
    var body: some View {
        VStack(alignment:.leading,spacing:20) {
            HStack(alignment:.top,spacing:12) {VStack(alignment:.leading,spacing:8) {Text("SEVIL / "+model.t("workspace").uppercased()).font(.system(size:10,weight:.semibold)).tracking(1.5).foregroundStyle(Design.primary);Text(model.t(destination=="overview" ? "welcome":destination)).font(.system(size:width<600 ? 28:32,weight:.bold));if destination=="overview" {Text(model.t("subtitle")).font(.subheadline).foregroundStyle(Design.muted)}};Spacer();Button {sheet=destination=="courses" || model.courses.isEmpty ? "course":"session"} label:{Image(systemName:"plus").font(.title3).frame(width:48,height:48).background(Design.mint,in:RoundedRectangle(cornerRadius:14))}.accessibilityLabel(model.t(destination=="courses" || model.courses.isEmpty ? "newCourse":"newSession"))}
            if destination=="overview" {
                VStack(alignment:.leading,spacing:14) {Label(model.t("local"),systemImage:"sparkles").font(.caption).foregroundStyle(Design.accent);Text(model.t("empty")).font(.title2.bold()).foregroundStyle(.white);Text(model.t("emptyBody")).font(.subheadline).foregroundStyle(Color.white.opacity(0.8));Button {model.run {try await model.sample()}} label:{HStack {Text(model.t("demo"));Image(systemName:"arrow.right")}.font(.subheadline.weight(.semibold)).foregroundStyle(.white).padding(.vertical,8)}}.padding(26).frame(maxWidth:.infinity,alignment:.leading).background(Design.forest,in:RoundedRectangle(cornerRadius:24))
                HStack(spacing:10) {stat("courses",model.courses.count,"books.vertical");stat("sessions",model.sessions.count,"mic");stat("notes",model.sessions.filter {$0.status=="ready"}.count,"doc.text")}
            } else {HStack {Image(systemName:"magnifyingglass").foregroundStyle(Design.muted);TextField(model.t("searchLibrary"),text:$search).font(.body)}.padding(14).background(.white,in:RoundedRectangle(cornerRadius:14)).overlay {RoundedRectangle(cornerRadius:14).stroke(Design.line,lineWidth:1)}}
            if destination=="courses" {
                LazyVGrid(columns:Array(repeating:GridItem(.flexible(),spacing:16),count:width>=1024 ? 3:width>=600 ? 2:1),spacing:16) {ForEach(model.courses.filter {search.isEmpty || ($0.title+" "+$0.professor+" "+$0.university).localizedCaseInsensitiveContains(search)}) {course in
                    VStack(alignment:.leading,spacing:12) {Image(systemName:"books.vertical").font(.title3).foregroundStyle(Design.primary).frame(width:44,height:44).background(Design.mint,in:RoundedRectangle(cornerRadius:14));Text(course.code.isEmpty ? course.university:course.code).font(.caption).foregroundStyle(Design.muted);Text(course.title).font(.title3.bold());Text(course.professor).font(.subheadline).foregroundStyle(Design.muted);Text(course.lecture_language+" → "+course.output_language).font(.caption).foregroundStyle(Design.primary);Button {model.selectedCourseId=course.id;sheet="session"} label:{Label(model.t("newSession"),systemImage:"plus").font(.subheadline.weight(.semibold)).padding(.vertical,8)}}.card()
                }}
                Button {sheet="course"} label:{Label(model.t("newCourse"),systemImage:"plus").frame(maxWidth:.infinity,minHeight:40)}.buttonStyle(.borderedProminent).controlSize(.large)
            } else {
                HStack {Text(model.t(destination=="overview" ? "recentSessions":"sessions")).font(.title3.bold());Spacer();Button {model.run {try await model.refresh()}} label:{Image(systemName:"arrow.clockwise")}.accessibilityLabel(model.t("refresh"))}
                if filteredSessions.isEmpty {ContentUnavailableView(model.t("sessions"),systemImage:"books.vertical",description:Text(model.t("emptyBody"))).card()}
                ForEach(filteredSessions) {session in Button {model.run {try await model.open(session.id)}} label:{HStack(spacing:12) {Image(systemName:"doc.text").font(.title3).foregroundStyle(Design.primary).frame(width:44,height:46).background(Design.mint,in:RoundedRectangle(cornerRadius:14));VStack(alignment:.leading,spacing:6) {Text(session.title).font(.subheadline.bold());Text(model.courses.first {$0.id==session.course_id}?.title ?? "").font(.caption).foregroundStyle(Design.muted)};Spacer();Text(model.t(session.status)).font(.system(size:10,weight:.semibold)).foregroundStyle(Design.primary)}.card()}.buttonStyle(.plain)}
                Button {sheet="session"} label:{Label(model.t("newSession"),systemImage:"plus").frame(maxWidth:.infinity,minHeight:40)}.buttonStyle(.borderedProminent).controlSize(.large).disabled(model.courses.isEmpty)
            }
        }.disabled(model.busy)
    }
    func stat(_ label: String,_ count: Int,_ icon: String) -> some View {VStack(alignment:.leading,spacing:10) {Image(systemName:icon).font(.subheadline).foregroundStyle(Design.primary).frame(width:32,height:32).background(Design.mint,in:RoundedRectangle(cornerRadius:10));Text(String(count)).font(.title2.bold());Text(model.t(label)).font(.system(size:10)).foregroundStyle(Design.muted).lineLimit(2)}.padding(15).frame(maxWidth:.infinity,alignment:.leading).background(.white,in:RoundedRectangle(cornerRadius:18)).overlay {RoundedRectangle(cornerRadius:18).stroke(Design.line,lineWidth:1)}}
}

struct ProviderControls: View {
    @EnvironmentObject var model: AppModel
    func label(_ id: String) -> String {
        let info = model.providers.first { $0.id == id }
        let name = info?.label ?? (["openai":"OpenAI","gemini":"Gemini","claude":"Claude"][id] ?? id)
        return name + " · " + model.t(info?.configured == true ? "configured" : "notConfigured")
    }
    var body: some View {
        VStack(alignment:.leading,spacing:10) {
            Picker(model.t("aiProvider"),selection:$model.provider) { ForEach(["openai","gemini","claude"],id:\.self) { id in Text(label(id)).tag(id) } }.pickerStyle(.menu)
            if model.provider == "claude" { Picker(model.t("audioProvider"),selection:$model.audioProvider) { Text("Gemini").tag("gemini"); Text("OpenAI").tag("openai") }.pickerStyle(.menu) }
            Text(model.t(model.provider == "claude" ? "claudeAudioNotice" : "providerNotice")).font(.caption).foregroundStyle(.secondary)
        }.card()
    }
}

struct SessionView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var recorder: Recorder
    @State private var tab = "notes"
    @State private var providerExpanded=false
    @State private var addLink=false
    @State private var transcript = ""
    @State private var importKind = "resource"
    @State private var importing = false
    @State private var camera = false
    @State private var offset = "0"
    @State private var previewURL: URL?
    @State private var deleteConfirmation = false
    var session: LectureSession { model.active! }
    var assets: [Asset] { session.assets ?? [] }
    var audioEnd: Int { max(assets.filter { $0.kind == "audio" }.map { $0.offset_ms + $0.duration_ms }.max() ?? 0, recorder.pending.map { $0.offset + $0.duration }.max() ?? 0) }
    var disabled: Bool { model.busy || session.status == "processing" }
    var body: some View {
        VStack(alignment:.leading,spacing:20) {
            DisclosureGroup(isExpanded:$providerExpanded) {ProviderControls().disabled(disabled || recorder.recording)} label:{Label(["gemini":"Gemini","openai":"OpenAI","claude":"Claude"][model.provider] ?? model.provider,systemImage:"sparkles").font(.subheadline.weight(.semibold)).foregroundStyle(Design.primary)}.padding(14).background(.white,in:RoundedRectangle(cornerRadius:14))
            Text(session.title).font(.system(size:28,weight:.bold))
            Text(model.t(session.status)).font(.caption).foregroundStyle(.secondary)
            if session.handout != nil, session.generated_mode == "live", let provider = session.generated_provider { Text(model.t("generatedWith") + ": " + (["openai":"OpenAI","gemini":"Gemini","claude":"Claude"][provider] ?? provider)).font(.caption).foregroundStyle(.secondary) }
            recordingPanel
            if !recorder.error.isEmpty { Text(recorder.error).foregroundStyle(.red).font(.caption) }
            if !recorder.recording { ForEach(recorder.pending) { clip in HStack {
                Label(clock(clip.duration),systemImage:"waveform"); Spacer()
                Button(model.t("retry")) { model.run { try await model.upload(clip.url,kind:"audio",offset:clip.offset,duration:clip.duration); recorder.remove(clip) } }
                ShareLink(item:clip.url) { Image(systemName:"square.and.arrow.up") }
            }.card().disabled(disabled) } }
            if let error = session.error { Text(error).foregroundStyle(.red).font(.callout) }
            ScrollView(.horizontal,showsIndicators:false) {HStack(spacing:4) {ForEach(["notes","chat","transcript","materials"],id:\.self) {key in Button {tab=key} label:{Text(model.t(key)).font(.subheadline.weight(.semibold)).padding(.horizontal,14).frame(minHeight:50).foregroundStyle(tab==key ? Design.forest:Design.muted).background(alignment:.bottom) {if tab==key {Rectangle().fill(Design.primary).frame(height:3)}}}.buttonStyle(.plain)}}}.background(.white,in:RoundedRectangle(cornerRadius:14))
            if tab=="notes" {
            HStack {
                Button { model.run { try await model.generate() } } label: { Label(model.t("generate"),systemImage:"sparkles") }.buttonStyle(.borderedProminent).disabled(disabled || recorder.recording || !recorder.pending.isEmpty)
                Button { model.run { previewURL = try await model.download("/v1/sessions/\(session.id)/handout.pdf",name:"sevil-handout.pdf") } } label: { Image(systemName:"arrow.down.doc") }.buttonStyle(.bordered).accessibilityLabel(model.t("download")).disabled(session.status != "ready" || model.busy)
            }
                if let note=session.handout {HandoutView(note:note,sources:session.sources ?? [])} else {ContentUnavailableView(model.t("notes"),systemImage:"sparkles",description:Text(model.t("noNotes"))).card()}
            }
            if tab=="chat" {SessionChatView(id:session.id,onSources:{tab="materials"})}
            if tab == "transcript" { TextEditor(text:$transcript).frame(minHeight:300).padding(10).background(.white,in:RoundedRectangle(cornerRadius:12)); Button(model.t("editTranscript")) { model.run { _ = try await model.api.data("/v1/sessions/\(session.id)/transcript",method:"PUT",json:["text":transcript]); try await model.open(session.id) } }.disabled(disabled || recorder.recording || transcript.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty) }
            if tab == "materials" { ForEach(assets.filter { $0.kind != "resource" }) { asset in AssetCard(asset:asset,disabled:disabled || recorder.recording,preview:$previewURL) }; ForEach(session.sources ?? []) { source in Text(source.label).font(.caption).foregroundStyle(.secondary) } }
            if tab=="materials" {materialPanel}
            if tab=="materials" {Button(role:.destructive) { deleteConfirmation = true } label: { Label(model.t("delete"),systemImage:"trash") }.disabled(disabled || recorder.recording)}
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
        .sheet(isPresented:$addLink) {AddSessionLinkView(id:session.id)}
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
            Button(model.t("delete"),role:.destructive) { model.run { _ = try await model.api.data("/v1/sessions/\(session.id)",method:"DELETE"); let clips = recorder.pending; for clip in clips { recorder.remove(clip) }; model.active = nil; try await model.refresh() } }
        }
    }
    var recordingPanel: some View {
        VStack(alignment:.leading,spacing:18) {
            HStack { Image(systemName:"mic.fill").font(.title3).foregroundStyle(recorder.recording ? Design.danger:Design.primary).frame(width:50,height:50).background(Design.mint,in:RoundedRectangle(cornerRadius:16)); Text(clock(recorder.recording ? recorder.elapsed : audioEnd)).font(.system(size:28,weight:.bold).monospacedDigit()); Spacer(); if recorder.recording { Circle().fill(.red).frame(width:9,height:9) } }
            HStack {
                Button(model.t(recorder.recording ? "stop" : "record")) { if recorder.recording { recorder.stop() } else { model.run { try await recorder.start(session:session.id,offset:audioEnd) } } }.buttonStyle(.borderedProminent).disabled(disabled && !recorder.recording)
                Button { camera = true } label: { Label(model.t("photo"),systemImage:"camera") }.buttonStyle(.bordered).disabled(disabled)
            }
        }.card()
    }
    var materialPanel: some View {
        VStack(alignment:.leading,spacing:16) {
            Text(model.t("resources")).font(.title2.bold())
            ForEach(model.sessionLinks) {link in HStack {if let url=URL(string:link.url) {Link(destination:url) {Label(link.title,systemImage:"link").font(.subheadline)}};Spacer();Button(role:.destructive) {model.run {_=try await model.api.data("/v1/links/"+link.id,method:"DELETE");try await model.open(session.id)}} label:{Image(systemName:"xmark")}.disabled(disabled || recorder.recording)}}
            Button {addLink=true} label:{Label(model.t("addLink"),systemImage:"link.badge.plus")}.buttonStyle(.bordered).disabled(disabled || recorder.recording)
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
    func card() -> some View { padding(20).frame(maxWidth:.infinity,alignment:.leading).background(Design.surface,in:RoundedRectangle(cornerRadius:20)).overlay {RoundedRectangle(cornerRadius:20).stroke(Design.line,lineWidth:1)} }
}
