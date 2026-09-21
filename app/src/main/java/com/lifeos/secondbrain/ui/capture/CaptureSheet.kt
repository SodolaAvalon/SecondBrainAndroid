package com.lifeos.secondbrain.ui.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lifeos.secondbrain.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var usedVoice by remember { mutableStateOf(false) }
    var rms by remember { mutableFloatStateOf(0f) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; voiceError = null }
            override fun onBeginningOfSpeech() { listening = true }
            override fun onRmsChanged(rmsdB: Float) { rms = rmsdB.coerceIn(0f, 12f) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false; rms = 0f }
            override fun onError(error: Int) {
                listening = false
                rms = 0f
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    voiceError = "语音识别暂时不可用，你仍然可以直接输入。"
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                rms = 0f
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text = it }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { recognizer.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            usedVoice = true
            recognizer.startListening(voiceIntent)
        } else voiceError = "需要麦克风权限才能使用语音记录。"
    }

    fun startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            voiceError = "这台设备没有可用的系统语音识别服务。"
            return
        }
        usedVoice = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recognizer.startListening(voiceIntent)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.24f)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("说点什么", style = MaterialTheme.typography.titleLarge)
                    Text("不用整理，先留下来。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { if (listening) recognizer.stopListening() else startVoice() }) {
                    Icon(if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic, contentDescription = if (listening) "停止录音" else "语音输入")
                }
            }
            if (listening) {
                val width by animateDpAsState((40 + rms * 14).dp, label = "voice-level")
                Box(
                    Modifier.height(8.dp).fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(Modifier.height(8.dp).then(Modifier.fillMaxWidth(0.18f)).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(999.dp)))
                    Box(Modifier.height(8.dp).then(Modifier.fillMaxWidth((width.value / 220f).coerceIn(0.18f, 1f))).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp)))
                }
                Text("正在听……", style = MaterialTheme.typography.bodySmall)
            }
            voiceError?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp).focusRequester(focusRequester),
                placeholder = { Text("想到什么就扔进来，不用整理。") }
            )
            Button(
                onClick = { if (text.isNotBlank()) vm.capture(text.trim(), voice = usedVoice, onSaved = onDismiss) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
            Box(Modifier.height(12.dp))
        }
    }
}
