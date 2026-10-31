package com.sevil.learncompanion

import android.app.*
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File

data class RecordingState(val session: String = "", val started: Long = 0, val offset: Long = 0, val error: String? = null)

class RecorderService: Service() {
    companion object { val state = MutableStateFlow(RecordingState()) }
    private var recorder: MediaRecorder? = null
    private var metadata: File? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { finish(); stopSelf(); return START_NOT_STICKY }
        if (recorder != null) return START_NOT_STICKY
        val session = intent?.getStringExtra("session") ?: run { stopSelf(); return START_NOT_STICKY }
        val offset = intent.getLongExtra("offset", 0)
        val channel = NotificationChannel("lecture", "Lecture recording", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecorderService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val catalog = JSONObject(assets.open("locales.json").bufferedReader().use { it.readText() })
        val language = getSharedPreferences("sevil", MODE_PRIVATE).getString("locale", "en") ?: "en"
        val strings = catalog.optJSONObject(language) ?: catalog.getJSONObject("en")
        val notification = Notification.Builder(this, "lecture").setContentTitle("SeviL · " + strings.getString("recording")).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, strings.getString("stop"), stop).build()).build()
        startForeground(1, notification)
        val folder = File(filesDir, "recordings").apply { mkdirs() }
        val output = File(folder, "${System.currentTimeMillis()}.m4a")
        metadata = File(output.path + ".json")
        metadata!!.writeText(JSONObject().put("session", session).put("offset", offset).put("duration", 0).toString())
        try {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(output.path)
            r.setOnErrorListener { _, _, _ -> finish("Audio recording was interrupted. Check the saved recording before continuing."); stopSelf() }
            r.prepare(); r.start(); recorder = r
            state.value = RecordingState(session, SystemClock.elapsedRealtime(), offset)
        } catch (e: Exception) { state.value = RecordingState(error = e.message); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        return START_NOT_STICKY
    }
    private fun finish(error: String? = null) {
        val current = state.value
        runCatching { recorder?.stop() }
        recorder?.release(); recorder = null
        metadata?.let { file ->
            runCatching { val data = JSONObject(file.readText()); data.put("duration", (SystemClock.elapsedRealtime() - current.started).coerceAtLeast(0)); file.writeText(data.toString()) }
        }
        state.value = RecordingState(error = error)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onDestroy() { if (recorder != null) finish("Recording ended. Review the saved audio."); super.onDestroy() }
}

fun pendingRecordings(context: android.content.Context, session: String): List<Pair<File, JSONObject>> =
    File(context.filesDir, "recordings").listFiles()?.filter { it.extension == "json" }?.mapNotNull { metadata ->
        runCatching {
            val data = JSONObject(metadata.readText())
            val file = File(metadata.path.removeSuffix(".json"))
            if (data.getString("session") != session || !file.exists() || file.length() == 0L) return@runCatching null
            if (data.optLong("duration") == 0L) runCatching { MediaMetadataRetriever().use { r -> r.setDataSource(file.path); data.put("duration", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) } }
            file to data
        }.getOrNull()
    } ?: emptyList()
