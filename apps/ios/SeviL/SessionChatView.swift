import SwiftUI

struct SessionChatView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var recorder: Recorder
    let id: String
    let onSources: () -> Void
    @State private var messages=[ChatMessage]()
    @State private var draft=""
    @State private var search=false
    @State private var sending=false
    @State private var error=""
    @State private var request: String?
    @State private var clear=false
    var disabled: Bool {sending || model.busy || recorder.recording || model.active?.status=="processing"}
    var body: some View {
        VStack(spacing:0) {
            HStack(spacing:12) {
                Image(systemName:"sparkles").font(.title3).foregroundStyle(Design.primary).frame(width:44,height:44).background(Design.mint,in:RoundedRectangle(cornerRadius:15))
                VStack(alignment:.leading,spacing:4) {Text(model.t("sessionTutor")).font(.subheadline.bold());Text(model.t("chatNotice")).font(.system(size:10)).foregroundStyle(Design.muted)}
                Spacer(minLength:0)
                Button {clear=true} label:{Image(systemName:"trash").font(.subheadline).frame(width:36,height:44)}.accessibilityLabel(model.t("clearChat")).disabled(disabled || messages.isEmpty)
            }.padding(16)
            Divider().overlay(Design.line)
            ScrollViewReader {proxy in
                ScrollView {
                    VStack(alignment:.leading,spacing:18) {
                        if messages.isEmpty && !sending {welcome}
                        ForEach(messages) {message in bubble(message)}
                        if sending {HStack(spacing:10) {ProgressView().controlSize(.small);Text(model.t("thinking")).font(.caption).foregroundStyle(Design.muted)}}
                        Color.clear.frame(height:1).id("end")
                    }.padding(16).frame(maxWidth:.infinity,alignment:.leading)
                }.onChange(of:messages.count) {_,_ in withAnimation {proxy.scrollTo("end",anchor:.bottom)}}.onChange(of:sending) {_,_ in withAnimation {proxy.scrollTo("end",anchor:.bottom)}}
            }
            if !error.isEmpty {Text(error).font(.caption).foregroundStyle(Design.danger).padding(12).frame(maxWidth:.infinity,alignment:.leading).background(Color.red.opacity(0.05))}
            Divider().overlay(Design.line)
            composer
        }.frame(height:min(720,max(420,UIScreen.main.bounds.height-240))).background(.white,in:RoundedRectangle(cornerRadius:20)).overlay {RoundedRectangle(cornerRadius:20).stroke(Design.line,lineWidth:1)}.clipShape(RoundedRectangle(cornerRadius:20))
        .task(id:id) {do {messages=try await model.api.get("/v1/sessions/"+id+"/chat")} catch {self.error=error.localizedDescription}}
        .confirmationDialog(model.t("clearChatConfirm"),isPresented:$clear,titleVisibility:.visible) {Button(model.t("clearChat"),role:.destructive) {Task {do {_=try await model.api.data("/v1/sessions/"+id+"/chat",method:"DELETE");messages=[];error="";request=nil} catch {self.error=error.localizedDescription}}}}
    }
    var welcome: some View {
        VStack(spacing:14) {
            Image(systemName:"bubble.left.and.text.bubble.right").font(.system(size:32)).foregroundStyle(Design.primary).frame(width:76,height:76).background(Design.mint,in:RoundedRectangle(cornerRadius:26))
            Text(model.t("askSession")).font(.title2.bold())
            Text(model.t("chatIntro")).font(.subheadline).foregroundStyle(Design.muted).multilineTextAlignment(.center)
            LazyVGrid(columns:[GridItem(.flexible()),GridItem(.flexible())],spacing:10) {ForEach(["explainSimply","giveExample","quizMe","findResources"],id:\.self) {key in
                Button {draft=model.t(key);request=nil;if key=="findResources" {search=true}} label:{HStack(spacing:7) {Image(systemName:"sparkles").font(.caption);Text(model.t(key)).font(.caption.weight(.medium));Spacer(minLength:0)}.padding(12).frame(maxWidth:.infinity,minHeight:50).background(Design.background,in:RoundedRectangle(cornerRadius:13)).overlay {RoundedRectangle(cornerRadius:13).stroke(Design.line,lineWidth:1)}}.buttonStyle(.plain).disabled(disabled)
            }}.padding(.top,12)
        }.padding(.vertical,24).frame(maxWidth:.infinity)
    }
    func bubble(_ message: ChatMessage) -> some View {
        let user=message.role=="user"
        return HStack {
            if user {Spacer(minLength:32)}
            VStack(alignment:.leading,spacing:10) {
                Text(user ? model.t("chat") : "SeviL · "+(["gemini":"Gemini","openai":"OpenAI","claude":"Claude"][message.provider] ?? message.provider)).font(.system(size:10,weight:.semibold)).foregroundStyle(user ? Design.accent:Design.primary)
                Text(message.content).font(.subheadline).lineSpacing(5).foregroundStyle(user ? .white:Design.ink).textSelection(.enabled)
                ForEach(message.sources ?? []) {source in Button(action:onSources) {Label(source.label,systemImage:"books.vertical").font(.caption).multilineTextAlignment(.leading)}.buttonStyle(.plain).foregroundStyle(Design.primary)}
                ForEach(message.links ?? []) {link in if let url=URL(string:link.url),url.scheme=="https" {Link(destination:url) {Label(link.title,systemImage:"arrow.up.right.square").font(.caption).multilineTextAlignment(.leading)}}}
                if message.status=="failed" {Text(message.error ?? "").font(.caption).foregroundStyle(user ? Design.accent:Design.danger);Button(model.t("retry")) {send(message.content,retry:message.request_id)}.font(.caption.bold()).foregroundStyle(user ? .white:Design.primary).disabled(disabled)}
            }.padding(16).background(user ? Design.primary:Design.background,in:RoundedRectangle(cornerRadius:18))
            if !user {Spacer(minLength:12)}
        }
    }
    var composer: some View {
        VStack(spacing:9) {
            HStack(alignment:.bottom,spacing:8) {
                TextField(model.t("chatPlaceholder"),text:$draft,axis:.vertical).lineLimit(2...4).font(.body).padding(12).disabled(disabled).onChange(of:draft) {_,value in if value.count>4000 {draft=String(value.prefix(4000))}}
                Button {send()} label:{Image(systemName:"arrow.up").font(.title3.bold()).foregroundStyle(.white).frame(width:44,height:44).background(Design.primary,in:RoundedRectangle(cornerRadius:13))}.disabled(disabled || draft.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty).accessibilityLabel(model.t("send")).padding(8)
            }.background(Design.background,in:RoundedRectangle(cornerRadius:16)).overlay {RoundedRectangle(cornerRadius:16).stroke(Design.line,lineWidth:1)}
            HStack {Toggle(isOn:$search) {Label(model.t("searchWeb"),systemImage:"globe").font(.caption)}.toggleStyle(.checkboxStyleForSevil).disabled(disabled);Spacer();Text(["gemini":"Gemini","openai":"OpenAI","claude":"Claude"][model.provider] ?? model.provider).font(.caption).foregroundStyle(Design.muted)}
        }.padding(14)
    }
    func send(_ text: String?=nil,retry: String?=nil) {
        let question=(text ?? draft).trimmingCharacters(in:.whitespacesAndNewlines)
        guard !disabled,!question.isEmpty else {return}
        let token=retry ?? request ?? UUID().uuidString
        request=token;sending=true;model.busy=true;error=""
        Task {
            defer {sending=false;model.busy=false}
            do {let _:ChatMessage=try await model.api.get("/v1/sessions/"+id+"/chat",method:"POST",json:["provider":model.provider,"text":question,"search":search,"request_id":token]);draft="";request=nil}
            catch {self.error=error.localizedDescription}
            if let saved:[ChatMessage]=try? await model.api.get("/v1/sessions/"+id+"/chat") {messages=saved}
        }
    }
}

struct SevilCheckboxStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {Button {configuration.isOn.toggle()} label:{HStack(spacing:7) {Image(systemName:configuration.isOn ? "checkmark.square.fill":"square").foregroundStyle(Design.primary);configuration.label}}.buttonStyle(.plain)}
}
extension ToggleStyle where Self==SevilCheckboxStyle {static var checkboxStyleForSevil: SevilCheckboxStyle {SevilCheckboxStyle()}}

struct AddSessionLinkView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    let id: String
    @State private var title=""
    @State private var url=""
    @State private var excerpt=""
    var body: some View {
        NavigationStack {Form {
            Section {Text(model.t("linkNotice")).font(.caption).foregroundStyle(Design.muted)}
            Section {TextField(model.t("title"),text:$title);TextField(model.t("linkUrl"),text:$url).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled();TextField(model.t("linkExcerpt"),text:$excerpt,axis:.vertical).lineLimit(4...8)}
            if !model.error.isEmpty {Text(model.error).foregroundStyle(Design.danger)}
        }.navigationTitle(model.t("addLink")).navigationBarTitleDisplayMode(.inline).toolbar {
            ToolbarItem(placement:.cancellationAction) {Button(model.t("cancel")) {dismiss()}}
            ToolbarItem(placement:.confirmationAction) {Button(model.t("save")) {model.run {_=try await model.api.data("/v1/sessions/"+id+"/links",method:"POST",json:["title":title,"url":url,"text":excerpt]);try await model.open(id);dismiss()}}.disabled(model.busy || title.isEmpty || url.isEmpty)}
        }}
    }
}
