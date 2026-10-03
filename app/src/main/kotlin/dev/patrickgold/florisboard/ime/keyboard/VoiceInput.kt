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

fun spokenDictationText(results: List<String>?): String {
    return results?.firstOrNull().orEmpty()
}

/**
 * Draft mic owns the key-area dictation sheet. Conversation-context listen
 * stays on the Suggest panel — showing this sheet unmounts that panel.
 */
fun voiceDictationSheetVisible(presented: Boolean, captureOnly: Boolean): Boolean =
    presented && !captureOnly

/** Draft sheet or in-place context listen — both must run SpeechRecognizer. */
fun voiceRecognizerArmed(presented: Boolean, captureOnly: Boolean): Boolean =
    presented || captureOnly

/** Finished phrases plus the live hypothesis, as one readable line. */
fun dictationTranscript(segments: List<String>, partial: String): String {
    return (segments + partial)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

data class RecognitionServiceTarget(
    val packageName: String,
    val className: String,
)

/**
 * Never bind AIDO's stub [android.speech.RecognitionService].
 *
 * [preferredComponent] is `Settings.Secure.VOICE_RECOGNITION_SERVICE`
 * (`package/class`). Query order is not the user's recognizer: on Samsung
 * the first hit is Android System Intelligence, which opens the mic and
 * then drops the audio without a transcript.
 */
fun pickExternalRecognitionService(
    appPackage: String,
    services: List<RecognitionServiceTarget>,
    preferredComponent: String? = null,
): RecognitionServiceTarget? {
    val candidates = services.filter { target ->
        target.packageName != appPackage &&
            !target.className.contains("AidoRecognitionService")
    }
    if (candidates.isEmpty()) return null
    val preferred = preferredComponent
        ?.let { component ->
            val slash = component.indexOf('/')
            if (slash <= 0 || slash == component.lastIndex) {
                null
            } else {
                RecognitionServiceTarget(
                    component.substring(0, slash),
                    component.substring(slash + 1),
                )
            }
        }
        ?.let { wanted ->
            candidates.firstOrNull { target ->
                target.packageName == wanted.packageName &&
                    target.className == wanted.className
            }
        }
    if (preferred != null) return preferred
    return candidates.firstOrNull { it.packageName in DictationRecognitionPackages }
        ?: candidates.firstOrNull { it.packageName !in NonDictationRecognitionPackages }
        ?: candidates.first()
}

/** Services that actually return dictation to a third-party IME. */
private val DictationRecognitionPackages = listOf(
    "com.google.android.googlequicksearchbox",
    "com.google.android.tts",
)

/**
 * These advertise [android.speech.RecognitionService] but do not deliver
 * speech text to this keyboard. System Intelligence records for a few
 * seconds and then stops. Bixby only forwards a Bixby session.
 */
private val NonDictationRecognitionPackages = setOf(
    "com.google.android.as",
    "com.samsung.android.bixby.agent",
)
