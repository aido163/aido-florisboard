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

package dev.patrickgold.florisboard.ime.smartbar

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WriterToolsBarTest : FunSpec({
    test("one row of real tools, suggest pinned separately") {
        WriterBarTools.map { it.data.code } shouldBe listOf(
            KeyCode.GRAMMAR,
            KeyCode.REWRITE,
            KeyCode.TRANSLATE,
            KeyCode.HUMANIZE,
        )
        WriterBarPrimary.data.code shouldBe KeyCode.SUGGEST
    }

    test("writer row hides when result chips are pinned") {
        showWriterToolsRow(
            layout = SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED,
            hasSuggestionStrip = true,
        ) shouldBe false
        showWriterToolsRow(
            layout = SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED,
            hasSuggestionStrip = false,
        ) shouldBe true
    }

    test("idle keyboard always prefers the chip row") {
        showWriterToolsRow(
            layout = SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED,
            hasSuggestionStrip = false,
        ) shouldBe true
    }
})
