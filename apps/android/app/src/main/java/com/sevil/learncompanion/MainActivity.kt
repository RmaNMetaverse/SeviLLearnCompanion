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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextOverflow
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
        MaterialTheme(colorScheme = lightColorScheme(primary=Design.primary,secondary=Design.forest,background=Design.background,surface=Design.surface,onSurface=Design.ink,onBackground=Design.ink,primaryContainer=Design.mint,onPrimaryContainer=Design.forest,secondaryContainer=Design.mint,surfaceVariant=Design.mint,outline=Design.line)) { SevilApp() }
    } }
}

private val languages = linkedMapOf("en" to "English", "tr" to "Türkçe", "zh-Hans" to "简体中文", "ja" to "日本語", "ko" to "한국어", "de" to "Deutsch", "fr" to "Français", "es" to "Español")
private fun clock(ms: Long) = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)

@Composable
fun ProviderPicker(label: String, selected: String, choices: Map<String,String>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = Design.muted); OutlinedButton(enabled = enabled, onClick = { expanded = true }) { Text(choices[selected] ?: selected) }; DropdownMenu(expanded, onDismissRequest = { expanded = false }) { choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) } } }
}

@Composable
fun LanguagePicker(label: String, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Design.muted)
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
    var savedSessionId by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable { mutableStateOf("overview") }
    var librarySearch by remember { mutableStateOf("") }
    var providerExpanded by remember { mutableStateOf(false) }
    var links by remember { mutableStateOf(listOf<JSONObject>()) }
    var linkTitle by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var linkExcerpt by remember { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("notes") }
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
        savedSessionId=id
        active = JSONObject(api.json("/v1/sessions/$id"))
        publicSources = JSONObject(api.json("/v1/courses/${active!!.getString("course_id")}/sources?provider=$provider")).getJSONArray("links").objects()
        links=JSONArray(api.json("/v1/sessions/$id/links")).objects()
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
    LaunchedEffect(Unit) { work { refresh(); if(recording.session.isNotBlank()) open(recording.session) else if(savedSessionId.isNotBlank()) open(savedSessionId) } }
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
    BackHandler(enabled=active!=null&&!isRecording&&!busy) {active=null;savedSessionId="";view="sessions"}
    val width=LocalConfiguration.current.screenWidthDp
    val compact=width<Design.compactBelow
    val destinations=listOf("overview","courses","sessions","settings")
    val navIcons=listOf(Icons.Outlined.Dashboard,Icons.Outlined.MenuBook,Icons.Outlined.VideoLibrary,Icons.Outlined.Settings)
    fun navigate(key: String) { if(key=="settings")dialog="settings" else { active=null;savedSessionId="";view=key;librarySearch="" } }
    @Composable fun NavigationContent(rail: Boolean=false) {
        destinations.forEachIndexed { index,key ->
            val selected=if(key=="settings")dialog=="settings" else if(active!=null)key=="sessions" else view==key
            if(rail) NavigationRailItem(selected=selected,onClick={navigate(key)},enabled=!isRecording&&!busy,icon={Icon(navIcons[index],contentDescription=null)},label={Text(t(key),maxLines=2,style=MaterialTheme.typography.labelSmall)},modifier=Modifier.padding(vertical=6.dp))

        }
    }
    Scaffold(containerColor=Design.background,topBar={ TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Icon(Icons.Outlined.GraphicEq,null,tint=Design.primary);Text("SeviL",fontWeight=FontWeight.ExtraBold,color=Design.forest)}},navigationIcon={if(active!=null)IconButton(enabled=!isRecording&&!busy,onClick={active=null;savedSessionId="";view="sessions";work {refresh()}}) {Icon(Icons.Outlined.ArrowBack,t("back"))}},actions={IconButton(onClick={dialog="settings"}) {Icon(Icons.Outlined.Settings,t("settings"),tint=Design.primary)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.White)) },bottomBar={if(compact)NavigationBar(containerColor=Color.White,tonalElevation=0.dp) {destinations.forEachIndexed {index,key -> NavigationBarItem(modifier=Modifier.testTag("nav-"+key),selected=if(key=="settings")dialog=="settings" else if(active!=null)key=="sessions" else view==key,onClick={navigate(key)},enabled=!isRecording&&!busy,icon={Icon(navIcons[index],null)},label={Text(t(key),maxLines=2,style=MaterialTheme.typography.labelSmall)})}} }) { insets ->
        Row(Modifier.fillMaxSize().padding(insets)) {
            if(!compact) {
                if(width>=Design.expandedFrom) Column(Modifier.width(240.dp).fillMaxHeight().background(Color.White).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(t("workspace").uppercase(),style=MaterialTheme.typography.labelSmall,color=Design.muted,modifier=Modifier.padding(vertical=18.dp))
                    destinations.forEachIndexed { index,key -> Surface(onClick={navigate(key)},enabled=!isRecording&&!busy,color=if((active==null&&view==key)||(active!=null&&key=="sessions"))Design.mint else Color.White,shape=RoundedCornerShape(14.dp)) {Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Icon(navIcons[index],null,tint=Design.primary);Text(t(key),style=MaterialTheme.typography.labelLarge)}} }
                    Spacer(Modifier.weight(1f));LabelChip(t("localBadge"),Icons.Outlined.VerifiedUser)
                } else NavigationRail(containerColor=Color.White,modifier=Modifier.width(88.dp).fillMaxHeight()) {NavigationContent(true)}
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal=if(compact)18.dp else 28.dp,vertical=22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                if(error.isNotEmpty()) Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(14.dp)) {Text(error,Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)}
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Design.primary)
                if(session==null) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text("SEVIL / "+t("workspace").uppercase(),style=MaterialTheme.typography.labelSmall,color=Design.primary);Text(t(if(view=="overview")"welcome" else view),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)};IconButton(enabled=!busy,onClick={title="";permission=false;dialog=if(view=="courses"||courses.isEmpty())"course" else "session"}) {Icon(Icons.Outlined.Add, t(if(view=="courses"||courses.isEmpty())"newCourse" else "newSession"),tint=Design.primary)}}
                    if(view=="overview") {
                        Surface(color=Design.forest,shape=RoundedCornerShape(24.dp)) {Column(Modifier.fillMaxWidth().padding(26.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {Icon(Icons.Outlined.AutoAwesome,null,tint=Design.accent,modifier=Modifier.size(16.dp));Text(t("local"),style=MaterialTheme.typography.labelSmall,color=Design.accent)}
                            Text(t("empty"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Color.White)
                            Text(t("emptyBody"),color=Color(0xFFD0E1D4),style=MaterialTheme.typography.bodyMedium)
                            TextButton(enabled=!busy,onClick={work {val c=courses.firstOrNull {it.optString("code")=="DEMO"} ?: JSONObject(api.json("/v1/courses","POST",JSONObject().put("title","Learning across languages").put("university","SeviL sample university").put("code","DEMO")));val sample=JSONObject(api.json("/v1/sessions","POST",JSONObject().put("course_id",c.getString("id")).put("title","From listening to understanding").put("recording_permission",true)));api.json("/v1/sessions/${sample.getString("id")}/demo","POST");refresh();open(sample.getString("id"))}}) {Text(t("demo"),color=Color.White);Icon(Icons.Outlined.ArrowForward,null,tint=Color.White,modifier=Modifier.padding(start=8.dp).size(16.dp))}
                        } }
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {listOf("courses" to courses.size,"sessions" to sessions.size,"notes" to sessions.count {it.optString("status")=="ready"}).forEach { (label,count) -> Surface(Modifier.weight(1f),color=Color.White,shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Design.line)) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(count.toString(),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(t(label),style=MaterialTheme.typography.labelSmall,color=Design.muted)}} } }
                    } else OutlinedTextField(librarySearch,{librarySearch=it},modifier=Modifier.fillMaxWidth(),leadingIcon={Icon(Icons.Outlined.Search,null)},placeholder={Text(t("searchLibrary"))},singleLine=true,shape=RoundedCornerShape(14.dp))
                    if(view=="courses") {
                        val columns=if(width>=1024)3 else if(width>=600)2 else 1
                        val filtered=courses.filter {(it.optString("title")+" "+it.optString("university")+" "+it.optString("professor")).contains(librarySearch,true)}
                        filtered.chunked(columns).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {row.forEach { course -> CourseTile(course,Modifier.weight(1f),::t) {courseId=course.getString("id");title="";permission=false;dialog="session"} };repeat(columns-row.size) {Spacer(Modifier.weight(1f))} } }
                        Button(enabled=!busy,onClick={title="";dialog="course"},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)) {Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(t("newCourse"))}
                    } else {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text(t(if(view=="overview")"recentSessions" else "sessions"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);IconButton(onClick={work {refresh()}}) {Icon(Icons.Outlined.Refresh,t("refresh"),tint=Design.primary)}}
                        val filtered=sessions.filter {it.optString("title").contains(librarySearch,true)||(courses.firstOrNull {c->c.getString("id")==it.getString("course_id")}?.optString("title") ?: "").contains(librarySearch,true)}.let {if(view=="overview")it.take(3) else it}
                        if(filtered.isEmpty()) EmptyStudyCard(t("sessions"),t("emptyBody"),Icons.Outlined.MenuBook)
                        filtered.forEach { item -> SessionTile(item,courses.firstOrNull {it.getString("id")==item.getString("course_id")}?.optString("title") ?: "",::t) {work {open(item.getString("id"))}} }
                        Button(enabled=courses.isNotEmpty()&&!busy,onClick={title="";permission=false;dialog="session"},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)) {Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(t("newSession"))}
                    }
                } else {
                    val id=session.getString("id")
                    Surface(color=Color.White,shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Design.line)) {Column(Modifier.fillMaxWidth()) {
                        TextButton(onClick={providerExpanded=!providerExpanded},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Outlined.AutoAwesome,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(8.dp));Text(mapOf("gemini" to "Gemini","openai" to "OpenAI","claude" to "Claude")[provider] ?: provider);Spacer(Modifier.weight(1f));Text(t("aiProvider"),style=MaterialTheme.typography.bodySmall,color=Design.muted);Icon(if(providerExpanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)}
                        if(providerExpanded)Column(Modifier.padding(16.dp)) {ProviderPicker(t("aiProvider"),provider,linkedMapOf("gemini" to "Gemini","openai" to "OpenAI","claude" to "Claude"),!disabled&&!isRecording) {provider=it;api.prefs.edit().putString("provider",it).apply()};if(provider=="claude")ProviderPicker(t("audioProvider"),audioProvider,linkedMapOf("gemini" to "Gemini","openai" to "OpenAI"),!disabled&&!isRecording) {audioProvider=it;api.prefs.edit().putString("audioProvider",it).apply()} }
                    } }
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(session.getString("title"),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(courses.firstOrNull {it.getString("id")==session.getString("course_id")}?.optString("title") ?: "",style=MaterialTheme.typography.bodyMedium,color=Design.muted);LabelChip(t(session.optString("status")),Icons.Outlined.Description)}
                    Surface(color=Color.White,shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Design.line)) {Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {Box(Modifier.size(50.dp).background(if(isRecording)Color(0xFFFAE7E4) else Design.mint,RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Mic,null,tint=if(isRecording)Design.danger else Design.primary)};Column {Text(clock(if(isRecording)elapsed else audioEnd),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(t(if(isRecording)"recording" else "audio"),style=MaterialTheme.typography.labelSmall,color=Design.muted)}}
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Button(enabled=isRecording||!disabled,onClick={if(isRecording)context.startService(Intent(context,RecorderService::class.java).setAction("STOP")) else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)},modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)) {Icon(if(isRecording)Icons.Outlined.Stop else Icons.Outlined.Mic,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(7.dp));Text(t(if(isRecording)"stop" else "record"))}
                            OutlinedButton(enabled=!disabled,onClick={if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)dialog="camera" else cameraPermission.launch(Manifest.permission.CAMERA)},modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)) {Icon(Icons.Outlined.PhotoCamera,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(7.dp));Text(t("photo"))}
                        }
                    } }
                    pending.forEach { (file,data) -> if(!isRecording)Surface(color=Color(0xFFF8F3E6),shape=RoundedCornerShape(14.dp)) {Column(Modifier.fillMaxWidth().padding(16.dp)) {Text(t("audio")+" · "+clock(data.optLong("duration")));TextButton(enabled=!disabled,onClick={work {upload(file,"audio",file.name,data.getLong("offset"),data.optLong("duration"));file.delete();File(file.path+".json").delete();pending=pendingRecordings(context,id)}}) {Text(t("retry"))}} } }
                    ScrollableTabRow(selectedTabIndex=listOf("notes","chat","transcript","materials").indexOf(tab).coerceAtLeast(0),edgePadding=0.dp,containerColor=Color.White,contentColor=Design.primary) {listOf("notes","chat","transcript","materials").forEach { key -> Tab(selected=tab==key,onClick={tab=key},text={Text(t(key),style=MaterialTheme.typography.labelMedium)})} }
                    if(tab=="notes") {
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {Button(enabled=!disabled&&!isRecording&&pending.isEmpty(),onClick={work {api.json("/v1/sessions/$id/generate","POST",generationOptions());open(id)}},modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)) {Icon(Icons.Outlined.AutoAwesome,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(7.dp));Text(t("generate"))};OutlinedButton(enabled=session.optString("status")=="ready"&&!busy,onClick={pdfPicker.launch("sevil-handout.pdf")},modifier=Modifier.heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)) {Icon(Icons.Outlined.FileDownload,t("download"))} }
                        session.optString("error").takeIf {it.isNotEmpty()&&it!="null"}?.let {Text(it,color=Design.danger)}
                        val note=session.optJSONObject("handout");if(note==null)EmptyStudyCard(t("notes"),t("noNotes"),Icons.Outlined.AutoAwesome) else HandoutView(note,session.optJSONArray("sources") ?: JSONArray(),::t)
                    }
                    if(tab=="chat")TutorPanel(api,id,provider,disabled||isRecording,::t,onBusy={busy=it},onSources={tab="materials"})
                    if(tab=="transcript") {OutlinedTextField(transcript,{transcript=it},modifier=Modifier.fillMaxWidth().heightIn(min=300.dp),label={Text(t("transcript"))},shape=RoundedCornerShape(16.dp));Button(enabled=!disabled&&!isRecording&&transcript.isNotBlank(),onClick={work {api.json("/v1/sessions/$id/transcript","PUT",JSONObject().put("text",transcript));open(id)}},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)) {Text(t("editTranscript"))} }
                    if(tab=="materials") {
                        Text(t("resources"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {OutlinedButton(enabled=!disabled,onClick={pendingKind="resource";filePicker.launch(arrayOf("application/pdf","text/plain","text/markdown"))},modifier=Modifier.weight(1f),shape=RoundedCornerShape(13.dp)) {Icon(Icons.Outlined.AttachFile,null);Text(t("attach"))};OutlinedButton(enabled=!disabled,onClick={dialog="link"},shape=RoundedCornerShape(13.dp)) {Icon(Icons.Outlined.AddLink,t("addLink"))} }
                        assets.forEach {asset -> Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Design.line)) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {Icon(if(asset.getString("kind")=="photo")Icons.Outlined.Image else if(asset.getString("kind")=="audio")Icons.Outlined.GraphicEq else Icons.Outlined.Description,null,tint=Design.primary);Text(asset.getString("name"),modifier=Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium);IconButton(enabled=!disabled&&!isRecording,onClick={work {api.json("/v1/assets/${asset.getString("id")}","DELETE");open(id)}}) {Icon(Icons.Outlined.Close,t("delete"),modifier=Modifier.size(18.dp))}}
                            if(asset.getString("kind")=="photo") {AsyncImage(model=api.assetFile(asset.getString("id")),contentDescription=t("photo"),modifier=Modifier.fillMaxWidth().heightIn(max=260.dp));Text(clock(asset.optLong("offset_ms")),style=MaterialTheme.typography.labelSmall,color=Design.muted)}
                            if(asset.getString("kind")=="audio")OutlinedButton(onClick={if(playingId==asset.getString("id")){player?.release();player=null;playingId=""} else {player?.release();runCatching {player=MediaPlayer().apply {setDataSource(api.assetFile(asset.getString("id")).absolutePath);setOnPreparedListener {it.start()};setOnCompletionListener {playingId=""};prepareAsync()};playingId=asset.getString("id")}.onFailure {error=it.message ?: t("error")}}}) {Icon(if(playingId==asset.getString("id"))Icons.Outlined.Stop else Icons.Outlined.PlayArrow,null);Text(t("audio")+" · "+clock(asset.optLong("offset_ms")))}
                        }} }
                        links.forEach {link -> Surface(color=Color.White,shape=RoundedCornerShape(16.dp)) {Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link.getString("url"))))},modifier=Modifier.weight(1f)) {Icon(Icons.Outlined.Link,null);Text(link.getString("title"),Modifier.padding(start=8.dp))};IconButton(enabled=!disabled,onClick={work {api.json("/v1/links/${link.getString("id")}","DELETE");open(id)}}) {Icon(Icons.Outlined.Close,t("delete"))}}} }
                        OutlinedButton(enabled=!disabled&&!isRecording,onClick={pendingKind="audio";filePicker.launch(arrayOf("audio/*"))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)) {Icon(Icons.Outlined.UploadFile,null);Text(t("uploadAudio"),Modifier.padding(start=8.dp))}
                        if(!isRecording) {OutlinedTextField(photoOffset,{photoOffset=it.filter(Char::isDigit)},label={Text(t("photoOffset"))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp));OutlinedButton(enabled=!disabled,onClick={pendingKind="photo";filePicker.launch(arrayOf("image/jpeg","image/png","image/webp"))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)) {Icon(Icons.Outlined.Image,null);Text(t("photo"),Modifier.padding(start=8.dp))}}
                        Text(t("publicSources"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(t("sourceNotice"),style=MaterialTheme.typography.bodySmall,color=Design.muted)
                        publicSources.forEach {source -> TextButton(onClick={val url=Uri.parse(source.getString("url"));if(url.scheme=="https")context.startActivity(Intent(Intent.ACTION_VIEW,url))}) {Icon(Icons.Outlined.OpenInNew,null,modifier=Modifier.size(16.dp));Text(source.getString("title"),Modifier.padding(start=8.dp))} }
                        OutlinedButton(enabled=!disabled&&!isRecording,onClick={work {publicSources=JSONObject(api.json("/v1/courses/${session.getString("course_id")}/discover","POST",JSONObject().put("provider",provider))).getJSONArray("links").objects()}},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)) {Text(t("discover"))}
                        TextButton(enabled=!disabled&&!isRecording,onClick={dialog="delete"}) {Icon(Icons.Outlined.DeleteOutline,null,tint=Design.danger);Text(t("delete"),color=Design.danger,modifier=Modifier.padding(start=8.dp))}
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if(dialog in listOf("course", "session", "settings")) ModalBottomSheet(onDismissRequest = { dialog = "" }, containerColor=Color.White) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(t(when(dialog){ "course" -> "newCourse"; "session" -> "newSession"; else -> "settings" }),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            when(dialog) {
                "course" -> { OutlinedTextField(title, { title = it }, label = { Text(t("title")) },modifier=Modifier.fillMaxWidth()); OutlinedTextField(university, { university = it }, label = { Text(t("university")) },modifier=Modifier.fillMaxWidth()); OutlinedTextField(degree, { degree = it }, label = { Text(t("degree")) },modifier=Modifier.fillMaxWidth()); OutlinedTextField(professor, { professor = it }, label = { Text(t("professor")) },modifier=Modifier.fillMaxWidth()); OutlinedTextField(code, { code = it }, label = { Text(t("code")) },modifier=Modifier.fillMaxWidth()); LanguagePicker(t("lectureLanguage"), lectureLang) { lectureLang = it }; LanguagePicker(t("outputLanguage"), outputLang) { outputLang = it } }
                "session" -> { courses.forEach { c -> Row { RadioButton(selected = courseId == c.getString("id"), onClick = { courseId = c.getString("id") }); Text(c.getString("title"), Modifier.padding(top = 12.dp)) } }; OutlinedTextField(title, { title = it }, label = { Text(t("sessionTitle")) },modifier=Modifier.fillMaxWidth()); Row { Checkbox(permission, { permission = it }); Text(t("permission"), style = MaterialTheme.typography.bodySmall) } }
                else -> { ProviderPicker(t("aiProvider"),provider,linkedMapOf("gemini" to "Gemini","openai" to "OpenAI","claude" to "Claude")) {provider=it;api.prefs.edit().putString("provider",it).apply()};if(provider=="claude")ProviderPicker(t("audioProvider"),audioProvider,linkedMapOf("gemini" to "Gemini","openai" to "OpenAI")) {audioProvider=it;api.prefs.edit().putString("audioProvider",it).apply()};LanguagePicker(t("language"), locale) { locale = it; api.prefs.edit().putString("locale", it).apply() }; Text(t("deviceStorageNotice"), style = MaterialTheme.typography.bodySmall); OutlinedTextField(geminiKey, { geminiKey = it }, label = { Text("Gemini " + t("apiKey")) }, modifier=Modifier.fillMaxWidth(),visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()); OutlinedTextField(openaiKey, { openaiKey = it }, label = { Text("OpenAI " + t("apiKey")) }, modifier=Modifier.fillMaxWidth(),visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()); OutlinedTextField(claudeKey, { claudeKey = it }, label = { Text("Claude " + t("apiKey")) }, modifier=Modifier.fillMaxWidth(),visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()) }
            }
            Button(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled = !busy && when(dialog) { "course" -> title.isNotBlank(); "session" -> title.isNotBlank() && courseId.isNotBlank() && permission; else -> true }, onClick = { work {
        when(dialog) {
            "course" -> { val c = JSONObject(api.json("/v1/courses", "POST", JSONObject().put("title", title).put("university", university).put("degree", degree).put("professor", professor).put("code", code).put("lecture_language", lectureLang).put("output_language", outputLang))); courseId = c.getString("id"); refresh() }
            "session" -> { val s = JSONObject(api.json("/v1/sessions", "POST", JSONObject().put("course_id", courseId).put("title", title).put("recording_permission", permission))); refresh(); open(s.getString("id")) }
            else -> { api.keys.save("gemini",geminiKey); api.keys.save("openai",openaiKey); api.keys.save("claude",claudeKey); refresh() }
        }; dialog = ""
    } }) { Text(t("save")) }; TextButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),onClick = { dialog = "" }) { Text(t("cancel")) };Spacer(Modifier.height(16.dp))
        }
    }
    if(dialog=="link")ModalBottomSheet(onDismissRequest={dialog=""},containerColor=Color.White) {Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {Text(t("addLink"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(t("linkNotice"),color=Design.muted,style=MaterialTheme.typography.bodySmall);OutlinedTextField(linkTitle,{linkTitle=it},label={Text(t("title"))},modifier=Modifier.fillMaxWidth());OutlinedTextField(linkUrl,{linkUrl=it},label={Text(t("linkUrl"))},placeholder={Text("https://")},modifier=Modifier.fillMaxWidth());OutlinedTextField(linkExcerpt,{linkExcerpt=it},label={Text(t("linkExcerpt"))},minLines=3,modifier=Modifier.fillMaxWidth());Button(enabled=!busy&&linkTitle.isNotBlank()&&linkUrl.isNotBlank(),onClick={work {api.json("/v1/sessions/${active!!.getString("id")}/links","POST",JSONObject().put("title",linkTitle).put("url",linkUrl).put("text",linkExcerpt));linkTitle="";linkUrl="";linkExcerpt="";open(active!!.getString("id"));dialog=""}},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(t("save"))};Spacer(Modifier.height(16.dp))}}
    if(dialog == "camera") ModalBottomSheet(onDismissRequest = { dialog = "" },containerColor=Color.White) { Column(Modifier.fillMaxWidth().padding(20.dp)) { BoardCamera(t("capture"), t("close"), offset = { if(isRecording) recording.offset + SystemClock.elapsedRealtime() - recording.started else (photoOffset.toLongOrNull() ?: 0) * 1000 }, onPhoto = { file, offset -> work { upload(file, "photo", file.name, offset); file.delete() } }, onError = { error = it }, close = { dialog = "" }) } }
    if(dialog == "delete") AlertDialog(onDismissRequest = { dialog = "" }, text = { Text(t("deleteConfirm")) }, confirmButton = { TextButton(onClick = { work { api.json("/v1/sessions/${active!!.getString("id")}", "DELETE"); pending.forEach { (file, _) -> file.delete(); File(file.path + ".json").delete() }; pending = emptyList(); active = null;savedSessionId=""; dialog = ""; refresh() } }) { Text(t("delete")) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("cancel")) } })
}

@Composable
private fun HandoutView(note: JSONObject, sources: JSONArray, t: (String) -> String) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(20.dp)) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(note.getString("title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(note.getString("summary"))
        Text(t("objectives"), style = MaterialTheme.typography.titleMedium)
        note.getJSONArray("objectives").strings().forEach { Text("• $it") }
        note.getJSONArray("sections").objects().forEach { section ->
            Text(section.getString("kind").uppercase(), style = MaterialTheme.typography.labelSmall, color = Design.muted)
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
