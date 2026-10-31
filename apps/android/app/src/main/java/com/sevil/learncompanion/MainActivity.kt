package com.sevil.learncompanion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent {
        MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF246D5E), secondary = Color(0xFF8E83AB), background = Color(0xFFF7F8F4), surface = Color.White)) { SevilApp() }
    } }
}

private val languages = linkedMapOf("en" to "English", "tr" to "Türkçe", "zh-Hans" to "简体中文", "ja" to "日本語", "ko" to "한국어", "de" to "Deutsch", "fr" to "Français", "es" to "Español")
private fun clock(ms: Long) = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)

@Composable
fun ProviderPicker(label: String, selected: String, choices: Map<String,String>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray); OutlinedButton(enabled = enabled, onClick = { expanded = true }) { Text(choices[selected] ?: selected) }; DropdownMenu(expanded, onDismissRequest = { expanded = false }) { choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) } } }
}

@Composable
fun LanguagePicker(label: String, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        OutlinedButton(onClick = { expanded = true }) { Text(languages[selected] ?: selected) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) { languages.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(code); expanded = false }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SevilApp() {
    val context = LocalContext.current
    val api = remember { Api(context) }
    val scope = rememberCoroutineScope()
    val catalog = remember { JSONObject(context.assets.open("locales.json").bufferedReader().use { it.readText() }) }
    var locale by remember { mutableStateOf(api.prefs.getString("locale", "en") ?: "en") }
    fun t(key: String): String = catalog.optJSONObject(locale)?.optString(key)?.ifBlank { null } ?: catalog.getJSONObject("en").optString(key, key)
    var courses by remember { mutableStateOf(listOf<JSONObject>()) }
    var sessions by remember { mutableStateOf(listOf<JSONObject>()) }
    var publicSources by remember { mutableStateOf(listOf<JSONObject>()) }
    var provider by remember { mutableStateOf(api.prefs.getString("provider", "gemini") ?: "gemini") }
    var audioProvider by remember { mutableStateOf(api.prefs.getString("audioProvider", "gemini") ?: "gemini") }
    var providers by remember { mutableStateOf(listOf<JSONObject>()) }
    fun generationOptions() = JSONObject().put("provider", provider).put("transcription_provider", if(provider == "claude") audioProvider else provider)
    var active by remember { mutableStateOf<JSONObject?>(null) }
    var mode by remember { mutableStateOf("live") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var university by remember { mutableStateOf("") }
    var degree by remember { mutableStateOf("") }
    var professor by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var lectureLang by remember { mutableStateOf("en") }
    var outputLang by remember { mutableStateOf("en") }
    var courseId by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf(false) }
    var geminiKey by remember { mutableStateOf(api.keys.read("gemini")) }
    var openaiKey by remember { mutableStateOf(api.keys.read("openai")) }
    var claudeKey by remember { mutableStateOf(api.keys.read("claude")) }
    var transcript by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("notes") }
    var photoOffset by remember { mutableStateOf("0") }
    var pendingKind by remember { mutableStateOf("resource") }
    var pending by remember { mutableStateOf(listOf<Pair<File, JSONObject>>()) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf("") }
    val recording by RecorderService.state.collectAsState()
    val isRecording = recording.session.isNotBlank()
    val session = active
    val assets = session?.optJSONArray("assets")?.objects() ?: emptyList()
    val audioEnd = maxOf(assets.filter { it.optString("kind") == "audio" }.maxOfOrNull { it.optLong("offset_ms") + it.optLong("duration_ms") } ?: 0L, pending.maxOfOrNull { it.second.optLong("offset") + it.second.optLong("duration") } ?: 0L)
    val disabled = busy || session?.optString("status") == "processing"
    suspend fun refresh() {
        courses = JSONArray(api.json("/v1/courses")).objects()
        sessions = JSONArray(api.json("/v1/sessions")).objects()
        mode = JSONObject(api.json("/health")).getString("mode")
        val config = JSONObject(api.json("/v1/providers"))
        providers = config.getJSONArray("providers").objects()
        if(!api.prefs.contains("provider")) provider = config.getString("default_provider")
        if (courseId.isBlank() && courses.isNotEmpty()) courseId = courses.first().getString("id")
    }
    suspend fun open(id: String) {
        active = JSONObject(api.json("/v1/sessions/$id"))
        publicSources = JSONObject(api.json("/v1/courses/${active!!.getString("course_id")}/sources?provider=$provider")).getJSONArray("links").objects()
        transcript = active!!.optString("transcript")
        pending = pendingRecordings(context, id)
    }
    fun work(block: suspend () -> Unit) { scope.launch { busy = true; error = ""; try { block() } catch(e: Exception) { error = e.message ?: t("error") } finally { busy = false } } }
    suspend fun upload(file: File, kind: String, name: String = file.name, offset: Long = 0, duration: Long = 0) {
        val id = active!!.getString("id")
        api.upload(id, file, name, kind, offset, duration)
        open(id); refresh()
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) {
        work {
            var name = "attachment"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> if(cursor.moveToFirst()) { val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(column >= 0) name = cursor.getString(column) } }
            val file = File.createTempFile("upload", ".bin", context.cacheDir)
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } } }
                val offset = if(pendingKind == "photo") (photoOffset.toLongOrNull() ?: 0) * 1000 else if(pendingKind == "audio") audioEnd else 0
                upload(file, pendingKind, name, offset)
            } finally { file.delete() }
        }
    } }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if(uri != null && active != null) work {
        val bytes = api.bytes("/v1/sessions/${active!!.getString("id")}/handout.pdf")
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) } }
    } }
    fun startRecording() { ContextCompat.startForegroundService(context, Intent(context, RecorderService::class.java).putExtra("session", active!!.getString("id")).putExtra("offset", audioEnd)) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted) startRecording() else error = t("error") }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted) dialog = "camera" else error = t("cameraUnavailable") }
    LaunchedEffect(Unit) { work { refresh(); if(recording.session.isNotBlank()) open(recording.session) } }
    LaunchedEffect(recording.session) {
        if (recording.session.isNotBlank()) {
            while(true) { elapsed = recording.offset + SystemClock.elapsedRealtime() - recording.started; delay(250) }
        } else { active?.let { pending = pendingRecordings(context, it.getString("id")) } }
    }
    LaunchedEffect(recording.error) { recording.error?.let { error = it } }
    LaunchedEffect(provider) { active?.let { session -> runCatching { publicSources = JSONObject(api.json("/v1/courses/${session.getString("course_id")}/sources?provider=$provider")).getJSONArray("links").objects() }.onFailure { error = it.message ?: t("error") } } }
    LaunchedEffect(active?.getString("id"), active?.optString("status")) {
        val id = active?.getString("id")
        if(active?.optString("status") == "processing" && id != null) while(true) { delay(2000); try { open(id); refresh() } catch(e: Exception) { error = e.message ?: t("error"); break } }
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    Scaffold(topBar = { TopAppBar(title = { Text("SeviL", fontWeight = FontWeight.Bold) }, navigationIcon = { if(active != null) TextButton(enabled = !isRecording, onClick = { active = null; work { refresh() } }) { Text(t("back")) } }, actions = { TextButton(onClick = { dialog = "settings" }) { Text(t("settings")) } }) }) { insets ->
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(insets).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if(mode == "demo") Surface(color = Color(0xFFF1EDDF), shape = MaterialTheme.shapes.medium) { Text(t("demoNotice"), Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall) }
            Text(t("deviceStorageNotice"), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if(error.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) { Text(error, Modifier.padding(14.dp)) }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val choices = linkedMapOf("openai" to "OpenAI", "gemini" to "Gemini", "claude" to "Claude").mapValues { (id, label) -> label + " · " + t(if(providers.firstOrNull { it.getString("id") == id }?.optBoolean("configured") == true) "configured" else "notConfigured") }
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    ProviderPicker(t("aiProvider"), provider, choices, enabled = !disabled && !isRecording) { provider = it; api.prefs.edit().putString("provider",it).apply() }
                    if(provider == "claude") ProviderPicker(t("audioProvider"), audioProvider, linkedMapOf("gemini" to "Gemini", "openai" to "OpenAI"), enabled = !disabled && !isRecording) { audioProvider = it; api.prefs.edit().putString("audioProvider",it).apply() }
                }
                Text(t(if(provider == "claude") "claudeAudioNotice" else "providerNotice"), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } }
            if(session == null) {
                Text(t("welcome"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(t("subtitle"), color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(enabled = !busy, onClick = { title = ""; dialog = "course" }) { Text(t("newCourse")) }
                    OutlinedButton(enabled = courses.isNotEmpty() && !busy, onClick = { title = ""; permission = false; dialog = "session" }) { Text(t("newSession")) }
                }
                OutlinedButton(enabled = !busy, onClick = { work {
                    val c = courses.firstOrNull { it.optString("code") == "DEMO" } ?: JSONObject(api.json("/v1/courses", "POST", JSONObject().put("title", "Learning across languages").put("university", "SeviL sample university").put("code", "DEMO")))
                    val s = JSONObject(api.json("/v1/sessions", "POST", JSONObject().put("course_id", c.getString("id")).put("title", "From listening to understanding").put("recording_permission", true)))
                    api.json("/v1/sessions/${s.getString("id")}/demo", "POST")
                    refresh(); open(s.getString("id"))
                } }) { Text(t("demo")) }
                Text(t("courses"), style = MaterialTheme.typography.titleLarge)
                courses.forEach { course -> Card(modifier = Modifier.fillMaxWidth(), onClick = { courseId = course.getString("id"); title = ""; permission = false; dialog = "session" }) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(course.getString("title"), fontWeight = FontWeight.Bold); Text(course.optString("university") + " · " + course.optString("professor"), style = MaterialTheme.typography.bodySmall); Text("${languages[course.optString("lecture_language")]} → ${languages[course.optString("output_language")]}", style = MaterialTheme.typography.labelSmall) }
                } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(t("sessions"), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { work { refresh() } }) { Text(t("refresh")) } }
                if(sessions.isEmpty()) Text(t("emptyBody"), color = Color.Gray)
                sessions.forEach { s -> Card(modifier = Modifier.fillMaxWidth(), onClick = { work { open(s.getString("id")) } }) { Column(Modifier.padding(18.dp)) { Text(s.getString("title"), fontWeight = FontWeight.Bold); Text(t(s.getString("status")), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) } } }
            } else {
                val id = session.getString("id")
                Text(session.getString("title"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(t(session.optString("status")), color = MaterialTheme.colorScheme.primary)
                if(session.optJSONObject("handout") != null && session.optString("generated_mode") == "live") Text(t("generatedWith") + ": " + (mapOf("openai" to "OpenAI", "gemini" to "Gemini", "claude" to "Claude")[session.optString("generated_provider")] ?: session.optString("generated_provider")), style = MaterialTheme.typography.labelMedium)
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(clock(if(isRecording) elapsed else audioEnd), style = MaterialTheme.typography.headlineLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = isRecording || !disabled, onClick = { if(isRecording) context.startService(Intent(context, RecorderService::class.java).setAction("STOP")) else if(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text(t(if(isRecording) "stop" else "record")) }
                        OutlinedButton(enabled = !disabled, onClick = { if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) dialog = "camera" else cameraPermission.launch(Manifest.permission.CAMERA) }) { Text(t("photo")) }
                    }
                } }
                pending.forEach { (file, data) -> if(!isRecording) Card { Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text("${t("audio")} · ${clock(data.optLong("duration"))}"); TextButton(enabled = !disabled, onClick = { work { upload(file, "audio", file.name, data.getLong("offset"), data.optLong("duration")); file.delete(); File(file.path + ".json").delete(); pending = pendingRecordings(context, id) } }) { Text(t("retry")) } } } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(enabled = !disabled && !isRecording && pending.isEmpty(), onClick = { work { api.json("/v1/sessions/$id/generate", "POST", generationOptions()); open(id) } }) { Text(t("generate")) }
                    OutlinedButton(enabled = session.optString("status") == "ready" && !busy, onClick = { pdfPicker.launch("sevil-handout.pdf") }) { Text(t("download")) }
                }
                session.optString("error").takeIf { it.isNotEmpty() && it != "null" }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("notes", "transcript", "evidence").forEach { key -> FilterChip(selected = tab == key, onClick = { tab = key }, label = { Text(t(key), style = MaterialTheme.typography.labelSmall) }) } }
                if(tab == "notes") {
                    val note = session.optJSONObject("handout")
                    if(note == null) Text(t("noNotes"), color = Color.Gray) else HandoutView(note, session.optJSONArray("sources") ?: JSONArray(), ::t)
                }
                if(tab == "transcript") { OutlinedTextField(value = transcript, onValueChange = { transcript = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp), label = { Text(t("transcript")) }); Button(enabled = !disabled && !isRecording && transcript.isNotBlank(), onClick = { work { api.json("/v1/sessions/$id/transcript", "PUT", JSONObject().put("text", transcript)); open(id) } }) { Text(t("editTranscript")) } }
                if(tab == "evidence") assets.filter { it.optString("kind") != "resource" }.forEach { asset ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(asset.getString("name"), fontWeight = FontWeight.Medium)
                        Text(clock(asset.optLong("offset_ms")), style = MaterialTheme.typography.labelSmall)
                        if(asset.getString("kind") == "photo") AsyncImage(model = ImageRequest.Builder(context).data(api.assetFile(asset.getString("id"))).build(), contentDescription = t("photo"), modifier = Modifier.fillMaxWidth().height(230.dp))
                        else OutlinedButton(onClick = {
                            if(playingId == asset.getString("id")) { player?.release(); player = null; playingId = "" } else {
                                player?.release()
                                runCatching { player = MediaPlayer().apply { setDataSource(api.assetFile(asset.getString("id")).absolutePath); setOnPreparedListener { it.start() }; setOnCompletionListener { playingId = "" }; prepareAsync() }; playingId = asset.getString("id") }.onFailure { error = it.message ?: t("error") }
                            }
                        }) { Text(if(playingId == asset.getString("id")) "■" else "▶ " + t("audio")) }
                        TextButton(enabled = !disabled && !isRecording, onClick = { work { api.json("/v1/assets/${asset.getString("id")}", "DELETE"); open(id) } }) { Text(t("delete")) }
                    } }
                }
                Text(t("resources"), style = MaterialTheme.typography.titleLarge)
                assets.filter { it.optString("kind") == "resource" }.forEach { asset -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(asset.getString("name"), Modifier.weight(1f)); TextButton(enabled = !disabled && !isRecording, onClick = { work { api.json("/v1/assets/${asset.getString("id")}", "DELETE"); open(id) } }) { Text(t("delete")) } } }
                OutlinedButton(enabled = !disabled, onClick = { pendingKind = "resource"; filePicker.launch(arrayOf("application/pdf", "text/plain", "text/markdown")) }) { Text(t("attach")) }
                Text(t("publicSources"), style = MaterialTheme.typography.titleMedium)
                Text(t("sourceNotice"), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                publicSources.forEach { source -> TextButton(onClick = { val url = Uri.parse(source.getString("url")); if(url.scheme == "https") context.startActivity(Intent(Intent.ACTION_VIEW, url)) }) { Text(source.getString("title")) } }
                OutlinedButton(enabled = !disabled && !isRecording && mode != "demo", onClick = { work { val found = JSONObject(api.json("/v1/courses/${session.getString("course_id")}/discover", "POST", JSONObject().put("provider",provider))); publicSources = found.getJSONArray("links").objects() } }) { Text(t("discover")) }
                OutlinedButton(enabled = !disabled && !isRecording, onClick = { pendingKind = "audio"; filePicker.launch(arrayOf("audio/*")) }) { Text(t("uploadAudio")) }
                if(!isRecording) { OutlinedTextField(photoOffset, { photoOffset = it.filter(Char::isDigit) }, label = { Text(t("photoOffset")) }); OutlinedButton(enabled = !disabled, onClick = { pendingKind = "photo"; filePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }) { Text(t("photo")) } }
                TextButton(enabled = !disabled && !isRecording, onClick = { dialog = "delete" }) { Text(t("delete"), color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if(dialog in listOf("course", "session", "settings")) AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(t(when(dialog){ "course" -> "newCourse"; "session" -> "newSession"; else -> "settings" })) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when(dialog) {
                "course" -> { OutlinedTextField(title, { title = it }, label = { Text(t("title")) }); OutlinedTextField(university, { university = it }, label = { Text(t("university")) }); OutlinedTextField(degree, { degree = it }, label = { Text(t("degree")) }); OutlinedTextField(professor, { professor = it }, label = { Text(t("professor")) }); OutlinedTextField(code, { code = it }, label = { Text(t("code")) }); LanguagePicker(t("lectureLanguage"), lectureLang) { lectureLang = it }; LanguagePicker(t("outputLanguage"), outputLang) { outputLang = it } }
                "session" -> { courses.forEach { c -> Row { RadioButton(selected = courseId == c.getString("id"), onClick = { courseId = c.getString("id") }); Text(c.getString("title"), Modifier.padding(top = 12.dp)) } }; OutlinedTextField(title, { title = it }, label = { Text(t("sessionTitle")) }); Row { Checkbox(permission, { permission = it }); Text(t("permission"), style = MaterialTheme.typography.bodySmall) } }
                else -> { LanguagePicker(t("language"), locale) { locale = it; api.prefs.edit().putString("locale", it).apply() }; Text(t("deviceStorageNotice"), style = MaterialTheme.typography.bodySmall); OutlinedTextField(geminiKey, { geminiKey = it }, label = { Text("Gemini " + t("apiKey")) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()); OutlinedTextField(openaiKey, { openaiKey = it }, label = { Text("OpenAI " + t("apiKey")) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()); OutlinedTextField(claudeKey, { claudeKey = it }, label = { Text("Claude " + t("apiKey")) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()) }
            }
        }
    }, confirmButton = { TextButton(enabled = !busy && when(dialog) { "course" -> title.isNotBlank(); "session" -> title.isNotBlank() && courseId.isNotBlank() && permission; else -> true }, onClick = { work {
        when(dialog) {
            "course" -> { val c = JSONObject(api.json("/v1/courses", "POST", JSONObject().put("title", title).put("university", university).put("degree", degree).put("professor", professor).put("code", code).put("lecture_language", lectureLang).put("output_language", outputLang))); courseId = c.getString("id"); refresh() }
            "session" -> { val s = JSONObject(api.json("/v1/sessions", "POST", JSONObject().put("course_id", courseId).put("title", title).put("recording_permission", permission))); refresh(); open(s.getString("id")) }
            else -> { api.keys.save("gemini",geminiKey); api.keys.save("openai",openaiKey); api.keys.save("claude",claudeKey); refresh() }
        }; dialog = ""
    } }) { Text(t("save")) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("cancel")) } })
    if(dialog == "camera") AlertDialog(onDismissRequest = { dialog = "" }, confirmButton = {}, text = { BoardCamera(t("capture"), t("close"), offset = { if(isRecording) recording.offset + SystemClock.elapsedRealtime() - recording.started else (photoOffset.toLongOrNull() ?: 0) * 1000 }, onPhoto = { file, offset -> work { upload(file, "photo", file.name, offset); file.delete() } }, onError = { error = it }, close = { dialog = "" }) })
    if(dialog == "delete") AlertDialog(onDismissRequest = { dialog = "" }, text = { Text(t("deleteConfirm")) }, confirmButton = { TextButton(onClick = { work { api.json("/v1/sessions/${active!!.getString("id")}", "DELETE"); pending.forEach { (file, _) -> file.delete(); File(file.path + ".json").delete() }; pending = emptyList(); active = null; dialog = ""; refresh() } }) { Text(t("delete")) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("cancel")) } })
}

@Composable
private fun HandoutView(note: JSONObject, sources: JSONArray, t: (String) -> String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(note.getString("title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(note.getString("summary"))
        Text(t("objectives"), style = MaterialTheme.typography.titleMedium)
        note.getJSONArray("objectives").strings().forEach { Text("• $it") }
        note.getJSONArray("sections").objects().forEach { section ->
            Text(section.getString("kind").uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(section.getString("heading"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(section.getString("body"))
            section.getJSONArray("citations").strings().forEach { ref -> Text(sources.objects().firstOrNull { it.getString("id") == ref }?.getString("label") ?: ref, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
        Text(t("glossary"), style = MaterialTheme.typography.titleMedium)
        note.getJSONArray("glossary").objects().forEach { Text(it.getString("term") + " / " + it.getString("translation"), fontWeight = FontWeight.Medium); Text(it.getString("definition")) }
        Text(t("questions"), style = MaterialTheme.typography.titleMedium)
        note.getJSONArray("questions").strings().forEach { Text("• $it") }
        Text(t("uncertainties"), style = MaterialTheme.typography.titleMedium)
        note.getJSONArray("uncertainties").strings().forEach { Text("• $it", color = Color(0xFF967547)) }
    } }
}
