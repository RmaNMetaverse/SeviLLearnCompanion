import SwiftUI
import UniformTypeIdentifiers
import QuickLook

struct LanguagePicker: View {
    let label: String
    @Binding var selection: String
    var body: some View { Picker(label,selection:$selection) { ForEach(languages,id:\.0) { code, name in Text(name).tag(code) } } }
}
struct CourseForm: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @State private var title = ""
    @State private var university = ""
    @State private var degree = ""
    @State private var professor = ""
    @State private var code = ""
    @State private var lecture = "en"
    @State private var output = "en"
    var body: some View {
        NavigationStack { Form {
            TextField(model.t("title"),text:$title); TextField(model.t("university"),text:$university); TextField(model.t("degree"),text:$degree); TextField(model.t("professor"),text:$professor); TextField(model.t("code"),text:$code)
            LanguagePicker(label:model.t("lectureLanguage"),selection:$lecture); LanguagePicker(label:model.t("outputLanguage"),selection:$output)
            if !model.error.isEmpty { Text(model.error).foregroundStyle(.red) }
        }.navigationTitle(model.t("newCourse")).toolbar {
            ToolbarItem(placement:.cancellationAction) { Button(model.t("cancel")) { dismiss() } }
            ToolbarItem(placement:.confirmationAction) { Button(model.t("save")) { model.run { _ = try await model.createCourse(["title":title,"university":university,"degree":degree,"professor":professor,"code":code,"lecture_language":lecture,"output_language":output]); dismiss() } }.disabled(model.busy || title.trimmingCharacters(in:.whitespaces).isEmpty) }
        } }
    }
}
struct SessionForm: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @State private var title = ""
    @State private var course = ""
    @State private var permission = false
    var body: some View {
        NavigationStack { Form {
            Picker(model.t("selectCourse"),selection:$course) { ForEach(model.courses) { Text($0.title).tag($0.id) } }
            TextField(model.t("sessionTitle"),text:$title)
            Toggle(model.t("permission"),isOn:$permission)
            if !model.error.isEmpty { Text(model.error).foregroundStyle(.red) }
        }.navigationTitle(model.t("newSession")).toolbar {
            ToolbarItem(placement:.cancellationAction) { Button(model.t("cancel")) { dismiss() } }
            ToolbarItem(placement:.confirmationAction) { Button(model.t("save")) { model.run { try await model.createSession(course:course,title:title,permission:permission); dismiss() } }.disabled(model.busy || title.trimmingCharacters(in:.whitespaces).isEmpty || course.isEmpty || !permission) }
        }.onAppear { course = model.courses.first?.id ?? "" } }
    }
}
struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @State private var server = UserDefaults.standard.string(forKey:"server") ?? "http://localhost:8000"
    @State private var token = Keychain.read()
    var body: some View {
        NavigationStack { Form {
            LanguagePicker(label:model.t("language"),selection:$model.locale)
            TextField(model.t("server"),text:$server).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
            SecureField(model.t("token"),text:$token)
            if !model.error.isEmpty { Text(model.error).foregroundStyle(.red) }
        }.navigationTitle(model.t("settings")).toolbar {
            ToolbarItem(placement:.cancellationAction) { Button(model.t("cancel")) { dismiss() } }
            ToolbarItem(placement:.confirmationAction) { Button(model.t("save")) { UserDefaults.standard.set(server,forKey:"server"); Keychain.save(token); model.run { try await model.refresh(); dismiss() } } }
        } }
    }
}
