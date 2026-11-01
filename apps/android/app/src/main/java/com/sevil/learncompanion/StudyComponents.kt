package com.sevil.learncompanion

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Composable fun LabelChip(text: String,icon: ImageVector) {
    Surface(color=Design.mint,shape=RoundedCornerShape(20.dp)) {Row(Modifier.padding(horizontal=11.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {Icon(icon,null,modifier=Modifier.size(14.dp),tint=Design.primary);Text(text,style=MaterialTheme.typography.labelSmall,color=Design.forest)}}
}
@Composable fun EmptyStudyCard(title: String,body: String,icon: ImageVector) {
    Surface(color=Color.White,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Design.line)) {Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {Box(Modifier.size(64.dp).background(Design.mint,RoundedCornerShape(20.dp)),contentAlignment=Alignment.Center) {Icon(icon,null,tint=Design.primary,modifier=Modifier.size(30.dp))};Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(body,style=MaterialTheme.typography.bodyMedium,color=Design.muted)}}
}
@Composable fun CourseTile(course: JSONObject,modifier: Modifier,t:(String)->String,onOpen:()->Unit) {
    Surface(modifier,color=Color.White,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Design.line)) {Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(Design.mint,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.MenuBook,null,tint=Design.primary)}
        Text(course.optString("code").ifBlank {course.optString("university")},style=MaterialTheme.typography.labelSmall,color=Design.muted)
        Text(course.getString("title"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text(course.optString("professor"),style=MaterialTheme.typography.bodyMedium,color=Design.muted)
        Text(course.optString("lecture_language")+" → "+course.optString("output_language"),style=MaterialTheme.typography.labelMedium,color=Design.primary)
        TextButton(onClick=onOpen) {Icon(Icons.Outlined.Add,null,modifier=Modifier.size(17.dp));Text(t("newSession"),Modifier.padding(start=8.dp))}
    }}
}
@Composable fun SessionTile(session: JSONObject,course: String,t:(String)->String,onOpen:()->Unit) {
    Surface(onClick=onOpen,color=Color.White,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Design.line)) {Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(Design.mint,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Description,null,tint=Design.primary)}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(session.getString("title"),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold);Text(course,style=MaterialTheme.typography.labelSmall,color=Design.muted)}
        Text(t(session.getString("status")),style=MaterialTheme.typography.labelSmall,color=Design.primary)
    }}
}
@Composable fun TutorPanel(api: Api,id: String,provider: String,disabled: Boolean,t:(String)->String,onBusy:(Boolean)->Unit,onSources:()->Unit) {
    val scope=rememberCoroutineScope();val context=LocalContext.current
    var messages by remember(id) {mutableStateOf(listOf<JSONObject>())}
    var draft by remember(id) {mutableStateOf("")};var search by remember(id) {mutableStateOf(false)};var sending by remember(id) {mutableStateOf(false)};var error by remember(id) {mutableStateOf("")};var request by remember(id) {mutableStateOf<String?>(null)};var clear by remember {mutableStateOf(false)}
    val scroll=rememberScrollState()
    suspend fun load() {messages=JSONArray(api.json("/v1/sessions/$id/chat")).objects()}
    fun send(text: String=draft,retry: String?=request) {
        if(sending||disabled||text.isBlank())return
        val token=retry ?: UUID.randomUUID().toString();request=token;sending=true;onBusy(true);error=""
        scope.launch {try {api.json("/v1/sessions/$id/chat","POST",JSONObject().put("provider",provider).put("text",text).put("search",search).put("request_id",token));draft="";request=null} catch(e: Exception) {error=e.message ?: t("error")} finally {runCatching {load()};sending=false;onBusy(false)} }
    }
    LaunchedEffect(id) {runCatching {load()}.onFailure {error=it.message ?: t("error")}}
    LaunchedEffect(messages.size,sending) {scroll.animateScrollTo(scroll.maxValue)}
    Surface(color=Color.White,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Design.line)) {Column(Modifier.fillMaxWidth().height(640.dp)) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Box(Modifier.size(42.dp).background(Design.mint,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.AutoAwesome,null,tint=Design.primary)};Column(Modifier.weight(1f)) {Text(t("sessionTutor"),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold);Text(t("chatNotice"),style=MaterialTheme.typography.labelSmall,color=Design.muted)};IconButton(enabled=!sending&&!disabled&&messages.isNotEmpty(),onClick={clear=true}) {Icon(Icons.Outlined.DeleteOutline,t("clearChat"),modifier=Modifier.size(18.dp))}}
        HorizontalDivider(color=Design.line)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(messages.isEmpty()&&!sending) {
                Column(Modifier.fillMaxWidth().padding(vertical=22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(74.dp).background(Design.mint,RoundedCornerShape(25.dp)),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Forum,null,tint=Design.primary,modifier=Modifier.size(34.dp))};Text(t("askSession"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(t("chatIntro"),style=MaterialTheme.typography.bodySmall,color=Design.muted)
                }
                listOf("explainSimply","giveExample","quizMe","findResources").chunked(2).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {row.forEach {key->OutlinedButton(enabled=!disabled,onClick={draft=t(key);request=null;if(key=="findResources")search=true},modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)) {Text(t(key),style=MaterialTheme.typography.labelSmall)}}}}
            }
            messages.forEach {m ->val user=m.optString("role")=="user";Column(Modifier.fillMaxWidth(),horizontalAlignment=if(user)Alignment.End else Alignment.Start) {Surface(color=if(user)Design.primary else Design.background,shape=RoundedCornerShape(topStart=if(user)18.dp else 4.dp,topEnd=if(user)4.dp else 18.dp,bottomStart=18.dp,bottomEnd=18.dp),modifier=Modifier.fillMaxWidth(if(user).85f else .95f)) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(if(user)t("chat") else "SeviL · "+(mapOf("gemini" to "Gemini","openai" to "OpenAI","claude" to "Claude")[m.optString("provider")] ?: m.optString("provider")),style=MaterialTheme.typography.labelSmall,color=if(user)Design.accent else Design.primary)
                SelectionContainer {Text(m.getString("content"),style=MaterialTheme.typography.bodyMedium,color=if(user)Color.White else Design.ink)}
                (m.optJSONArray("sources") ?: JSONArray()).objects().forEach {source->TextButton(onClick=onSources,contentPadding=PaddingValues(0.dp)) {Icon(Icons.Outlined.MenuBook,null,modifier=Modifier.size(14.dp));Text(source.getString("label"),Modifier.padding(start=5.dp),style=MaterialTheme.typography.labelSmall)}}
                (m.optJSONArray("links") ?: JSONArray()).objects().forEach {link->TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link.getString("url"))))},contentPadding=PaddingValues(0.dp)) {Icon(Icons.Outlined.OpenInNew,null,modifier=Modifier.size(14.dp));Text(link.getString("title"),Modifier.padding(start=5.dp),style=MaterialTheme.typography.labelSmall)}}
                if(m.optString("status")=="failed") {Text(m.optString("error"),style=MaterialTheme.typography.labelSmall,color=if(user)Design.accent else Design.danger);TextButton(enabled=!sending&&!disabled,onClick={send(m.getString("content"),m.getString("request_id"))}) {Text(t("retry"),color=if(user)Color.White else Design.primary)}}
            }}} }
            if(sending)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Text(t("thinking"),style=MaterialTheme.typography.labelSmall,color=Design.muted)}
        }
        if(error.isNotBlank())Text(error,Modifier.padding(horizontal=16.dp,vertical=8.dp),color=Design.danger,style=MaterialTheme.typography.labelSmall)
        HorizontalDivider(color=Design.line)
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedTextField(draft,{draft=it.take(4000);request=null},placeholder={Text(t("chatPlaceholder"),style=MaterialTheme.typography.bodyMedium)},modifier=Modifier.weight(1f),maxLines=4,minLines=2,enabled=!sending&&!disabled,shape=RoundedCornerShape(16.dp));FilledIconButton(enabled=!sending&&!disabled&&draft.isNotBlank(),onClick={send()},modifier=Modifier.size(48.dp)) {Icon(Icons.Outlined.ArrowUpward,t("send"))}}
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(search,{search=it},enabled=!sending&&!disabled,modifier=Modifier.size(38.dp));Text(t("searchWeb"),style=MaterialTheme.typography.labelSmall);Spacer(Modifier.weight(1f));Text(mapOf("gemini" to "Gemini","openai" to "OpenAI","claude" to "Claude")[provider] ?: provider,style=MaterialTheme.typography.labelSmall,color=Design.muted)}
        }
    }}
    if(clear)AlertDialog(onDismissRequest={clear=false},text={Text(t("clearChatConfirm"))},confirmButton={TextButton(onClick={scope.launch {runCatching {api.json("/v1/sessions/$id/chat","DELETE");load()}.onFailure {error=it.message ?: t("error")};clear=false}}) {Text(t("clearChat"))}},dismissButton={TextButton(onClick={clear=false}) {Text(t("cancel"))}})
}
