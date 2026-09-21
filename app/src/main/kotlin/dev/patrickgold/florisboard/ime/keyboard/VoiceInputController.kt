/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.keyboard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.florisboard.lib.android.showShortToastSync

/**
 * In-IME dictation. Must not start an Activity — that hides the keyboard.
 * Never bind AIDO's stub [RecognitionService].
 */
class VoiceInputController(
    private val context: Context,
    private val commitText: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val listeningState = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = listeningState

    fun toggle() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { toggle() }
            return
        }
        if (listeningState.value) {
            stop()
        } else {
            start()
        }
    }

    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { stop() }
            return
        }
        listeningState.value = false
        try {
            recognizer?.stopListening()
        } catch (_: Throwable) {
        }
        destroyRecognizer()
    }

    private fun start() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            context.showShortToastSync("mic permission needed. grant it in aido.")
            return
        }
        val speech = createRecognizer() ?: run {
            context.showShortToastSync("voice input is not available on this phone")
            return
        }
        recognizer = speech
        listeningState.value = true
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                listeningState.value = false
                destroyRecognizer()
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        context.showShortToastSync("mic permission needed. grant it in aido.")
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_CLIENT -> Unit
                    else -> context.showShortToastSync("could not hear that. try again.")
                }
            }
            override fun onResults(results: Bundle?) {
                listeningState.value = false
                val text = spokenDictationText(
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                )
                destroyRecognizer()
                if (text.isNotBlank()) {
                    commitText(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try {
            speech.startListening(intent)
        } catch (_: Throwable) {
            listeningState.value = false
            destroyRecognizer()
            context.showShortToastSync("could not start the mic")
        }
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null
    }

    private fun createRecognizer(): SpeechRecognizer? {
        val external = pickExternalRecognitionService(context.packageName, installedRecognitionServices())
        if (external != null) {
            try {
                return SpeechRecognizer.createSpeechRecognizer(
                    context,
                    ComponentName(external.packageName, external.className),
                )
            } catch (_: Throwable) {
            }
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun installedRecognitionServices(): List<RecognitionServiceTarget> {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        return context.packageManager.queryIntentServices(intent, 0).mapNotNull { resolve ->
            val info = resolve.serviceInfo ?: return@mapNotNull null
            RecognitionServiceTarget(info.packageName, info.name)
        }
    }
}
