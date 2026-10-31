package com.sevil.learncompanion

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@Composable
fun BoardCamera(captureLabel: String, closeLabel: String, offset: () -> Long, onPhoto: (File, Long) -> Unit, onError: (String) -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = ContextCompat.getMainExecutor(context)
    val photo = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val previewView = remember { PreviewView(context) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        future.addListener({ if (!disposed) runCatching {
            val p = future.get(); provider = p
            val preview = androidx.camera.core.Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, photo)
        }.onFailure { onError(it.message ?: "Camera unavailable") } }, executor)
        onDispose { disposed = true; provider?.unbindAll() }
    }
    Column(Modifier.fillMaxWidth()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(350.dp))
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val capturedAt = offset()
                val file = File(context.cacheDir, "board-${System.currentTimeMillis()}.jpg")
                photo.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object: ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) { onPhoto(file, capturedAt); close() }
                    override fun onError(exception: ImageCaptureException) { onError(exception.message ?: "Capture failed") }
                })
            }) { Text(captureLabel) }
            OutlinedButton(onClick = close) { Text(closeLabel) }
        }
    }
}
