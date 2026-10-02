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

/** Never bind AIDO's stub [android.speech.RecognitionService]. */
fun pickExternalRecognitionService(
    appPackage: String,
    services: List<RecognitionServiceTarget>,
): RecognitionServiceTarget? {
    return services.firstOrNull { target ->
        target.packageName != appPackage &&
            !target.className.contains("AidoRecognitionService")
    }
}
