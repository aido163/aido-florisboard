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
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
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
    private companion object {
        const val TAG = "AidoVoice"
        const val EARCON_PREFS = "aido_voice_earcon"
        const val EARCON_HOLD_MS = 800L
    }
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val listeningState = MutableStateFlow(false)
    private val presentedState = MutableStateFlow(false)
    private val transcriptState = MutableStateFlow("")
    private val noticeState = MutableStateFlow("")
    val listening: StateFlow<Boolean> = listeningState
    val presented: StateFlow<Boolean> = presentedState
    val transcript: StateFlow<String> = transcriptState
    val notice: StateFlow<String> = noticeState
    private val captureOnlyState = MutableStateFlow(false)
    val captureOnly: StateFlow<Boolean> = captureOnlyState

    private val segments = mutableListOf<String>()
    private var partial = ""
    private var restarts = 0
    private var session = 0
    private var listenGeneration = 0
    private val savedEarconVolumes = mutableMapOf<Int, Int>()
    private var earconRestore: Runnable? = null

    fun toggle() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { toggle() }
            return
        }
        if (presentedState.value) {
            finish()
        } else {
            captureOnlyState.value = false
            start()
        }
    }

    /**
     * Listen without inserting into the compose field. Live text stays on
     * [transcript]; call [takeTranscriptAndStop] when the panel is done.
     */
    fun startCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { startCapture() }
            return
        }
        if (sessionArmed()) {
            halt(clearSheet = true)
        }
        captureOnlyState.value = true
        start(showSheet = false)
    }

    /** Stop listening and return the transcript without inserting. */
    fun takeTranscriptAndStop(): String {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = ""
            val latch = java.util.concurrent.CountDownLatch(1)
            main.post {
                result = takeTranscriptAndStop()
                latch.countDown()
            }
            latch.await()
            return result
        }
        val spoken = transcriptState.value.trim()
        halt(clearSheet = true)
        return spoken
    }

    /** Stop listening and insert whatever was heard (draft mic). */
    fun finish() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { finish() }
            return
        }
        val spoken = transcriptState.value.trim()
        val insert = !captureOnlyState.value
        halt(clearSheet = true)
        if (insert && spoken.isNotEmpty()) {
            commitText(spoken)
        }
    }

    /** Release the mic without inserting. Used when the keyboard goes away. */
    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { stop() }
            return
        }
        halt(clearSheet = true)
    }

    private fun halt(clearSheet: Boolean) {
        session += 1
        listeningState.value = false
        restarts = 0
        captureOnlyState.value = false
        // Cover the ending chime, then put the volume back. Do not leave streams at 0.
        swallowEarcon()
        try {
            recognizer?.cancel()
        } catch (_: Throwable) {
        }
        destroyRecognizer()
        if (clearSheet) {
            presentedState.value = false
            transcriptState.value = ""
            noticeState.value = ""
            segments.clear()
            partial = ""
        }
    }

    private fun start(showSheet: Boolean = true) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            context.showShortToastSync("mic permission needed. grant it in aido.")
            captureOnlyState.value = false
            return
        }
        session += 1
        val token = session
        segments.clear()
        partial = ""
        restarts = 0
        transcriptState.value = ""
        noticeState.value = ""
        // Context capture keeps the Suggest sheet up. Flipping presented here
        // unmounted that sheet, stopped the mic in onDispose, and blinked.
        presentedState.value = showSheet
        beginListening(token, recreate = true)
    }

    private fun sessionArmed(): Boolean =
        voiceRecognizerArmed(presentedState.value, captureOnlyState.value)

    private fun beginListening(token: Int, recreate: Boolean) {
        if (token != session || !sessionArmed()) return
        listenGeneration += 1
        val generation = listenGeneration
        // A new SpeechRecognizer plays Google's mic chime and drops the first
        // words of the next phrase. Reuse the open one between phrases.
        if (recreate || recognizer == null) {
            destroyRecognizer()
            val speech = createRecognizer() ?: run {
                listeningState.value = false
                noticeState.value = "voice input is not available on this phone"
                return
            }
            recognizer = speech
        }
        val speech = recognizer ?: return
        listeningState.value = true
        speech.setRecognitionListener(listener(token, generation))
        swallowEarcon()
        try {
            speech.startListening(listenIntent())
        } catch (_: Throwable) {
            listeningState.value = false
            destroyRecognizer()
            noticeState.value = "could not start the mic"
        }
    }

    private fun listener(token: Int, generation: Int) = object : RecognitionListener {
        private fun live(): Boolean {
            return token == session && generation == listenGeneration && sessionArmed()
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            if (!live()) return
            swallowEarcon()
        }
        override fun onError(error: Int) {
            if (!live()) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    listeningState.value = false
                    destroyRecognizer()
                    noticeState.value = "mic permission needed. grant it in aido."
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    restarts += 1
                    scheduleContinue(token, recreate = false)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    restarts += 1
                    scheduleContinue(token, recreate = false)
                }
                SpeechRecognizer.ERROR_CLIENT -> {
                    restarts += 1
                    scheduleContinue(token, recreate = true)
                }
                else -> {
                    listeningState.value = false
                    destroyRecognizer()
                    if (transcriptState.value.isBlank()) {
                        noticeState.value = "could not hear that. try again."
                    }
                }
            }
        }
        override fun onResults(results: Bundle?) {
            if (!live()) return
            val text = spokenDictationText(
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
            )
            if (text.isNotBlank()) {
                segments.add(text)
                partial = ""
                restarts = 0
                publish()
            } else {
                restarts += 1
            }
            scheduleContinue(token, recreate = false)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            if (!live()) return
            val text = spokenDictationText(
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
            )
            if (text.isBlank()) return
            partial = text
            publish()
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun publish() {
        transcriptState.value = dictationTranscript(segments, partial)
        if (transcriptState.value.isNotBlank()) {
            noticeState.value = ""
        }
    }

    private fun scheduleContinue(token: Int, recreate: Boolean) {
        if (!sessionArmed() || token != session) return
        if (restarts >= 6) {
            listeningState.value = false
            destroyRecognizer()
            if (transcriptState.value.isBlank() && noticeState.value.isBlank()) {
                noticeState.value = "could not hear that. try again."
            }
            return
        }
        main.postDelayed({
            if (token == session && sessionArmed()) {
                beginListening(token, recreate)
            }
        }, 200)
    }

    private fun listenIntent(): Intent {
        val window = dictationListenWindow()
        val locales = context.resources.configuration.locales
        val language = if (locales.isEmpty) null else locales[0].toLanguageTag()
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (!language.isNullOrBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            }
            // Speech Services plays its mic chime unless dictation mode is set.
            putExtra("android.speech.extra.DICTATION_MODE", window.dictationMode)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, window.minimumLengthMillis)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, window.completeSilenceMillis)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                window.possibleSilenceMillis,
            )
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, window.preferOffline)
        }
    }

    /**
     * Speech Services chimes when a listen starts and when a phrase ends.
     * Duck only for that blip. Holding the duck for the whole session zeros
     * the streams onboarding voice plays on.
     */
    private fun swallowEarcon() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val prefs = context.getSharedPreferences(EARCON_PREFS, Context.MODE_PRIVATE)
        for (stream in earconStreamIds()) {
            if (savedEarconVolumes.containsKey(stream)) continue
            try {
                val current = audio.getStreamVolume(stream)
                if (current <= 0) continue
                savedEarconVolumes[stream] = current
                prefs.edit().putInt("s$stream", current).apply()
                audio.setStreamVolume(stream, 0, 0)
            } catch (_: Throwable) {
            }
        }
        scheduleEarconRestore(EARCON_HOLD_MS)
    }

    private fun scheduleEarconRestore(delayMs: Long) {
        earconRestore?.let { main.removeCallbacks(it) }
        val restore = Runnable { restoreEarcon() }
        earconRestore = restore
        main.postDelayed(restore, delayMs)
    }

    private fun restoreEarcon() {
        earconRestore?.let { main.removeCallbacks(it) }
        earconRestore = null
        if (savedEarconVolumes.isEmpty()) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio != null) {
            for ((stream, volume) in savedEarconVolumes) {
                try {
                    audio.setStreamVolume(stream, volume, 0)
                } catch (_: Throwable) {
                }
            }
        }
        savedEarconVolumes.clear()
        context.getSharedPreferences(EARCON_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null
    }

    private fun createRecognizer(): SpeechRecognizer? {
        val preferred = Settings.Secure.getString(
            context.contentResolver,
            "voice_recognition_service",
        )
        val external = pickExternalRecognitionService(
            context.packageName,
            installedRecognitionServices(),
            preferred,
        )
        if (external != null) {
            Log.i(TAG, "recognition service ${external.packageName}/${external.className}")
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

/** If a dictation session died while the chime was ducked, put those streams back. */
fun recoverKeyboardEarconVolumes(context: Context) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val prefs = context.getSharedPreferences("aido_voice_earcon", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    var changed = false
    for (stream in earconStreamIds()) {
        val restore = stuckEarconVolume(
            current = try {
                audio.getStreamVolume(stream)
            } catch (_: Throwable) {
                continue
            },
            remembered = prefs.getInt("s$stream", -1),
        ) ?: continue
        try {
            audio.setStreamVolume(stream, restore, 0)
        } catch (_: Throwable) {
            continue
        }
        editor.remove("s$stream")
        changed = true
    }
    // Older builds set these to 0 and exited before restoring them, so onboarding
    // playback stayed silent. Raise each one once if it is still sitting at 0.
    if (!prefs.getBoolean("recovered_zero", false)) {
        for (stream in intArrayOf(3, 1, 5, 11)) {
            try {
                if (audio.getStreamVolume(stream) != 0) continue
                val half = (audio.getStreamMaxVolume(stream) / 2).coerceAtLeast(1)
                audio.setStreamVolume(stream, half, 0)
            } catch (_: Throwable) {
            }
        }
        editor.putBoolean("recovered_zero", true)
        changed = true
    }
    if (changed) editor.apply()
}
