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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardVoice
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ComputingEvaluatorTest : FunSpec({
    test("voice key is icon-only even when layout JSON defaults to character") {
        val data = TextKeyData(
            type = KeyType.CHARACTER,
            code = KeyCode.VOICE_INPUT,
            label = "voice_input",
        )
        DefaultComputingEvaluator.computeLabel(data) shouldBe null
        DefaultComputingEvaluator.computeImageVector(data) shouldBe Icons.Default.KeyboardVoice
    }

    test("spoken dictation text uses the first recognition result") {
        spokenDictationText(listOf("hello maya", "hello")) shouldBe "hello maya"
        spokenDictationText(emptyList()) shouldBe ""
        spokenDictationText(null) shouldBe ""
    }

    test("never binds this app's stub recognition service") {
        pickExternalRecognitionService(
            appPackage = "com.aido.type",
            services = listOf(
                RecognitionServiceTarget(
                    "com.aido.type",
                    "com.aido.type.assist.AidoRecognitionService",
                ),
                RecognitionServiceTarget(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.voicesearch.serviceapi.GoogleRecognitionService",
                ),
            ),
        ) shouldBe RecognitionServiceTarget(
            "com.google.android.googlequicksearchbox",
            "com.google.android.voicesearch.serviceapi.GoogleRecognitionService",
        )
        pickExternalRecognitionService(
            appPackage = "com.aido.type",
            services = listOf(
                RecognitionServiceTarget(
                    "com.aido.type",
                    "com.aido.type.assist.AidoRecognitionService",
                ),
            ),
        ) shouldBe null
    }
})
