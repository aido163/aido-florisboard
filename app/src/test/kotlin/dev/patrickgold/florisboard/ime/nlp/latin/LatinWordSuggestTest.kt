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

package dev.patrickgold.florisboard.ime.nlp.latin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LatinWordSuggestTest : FunSpec({
    val words = mapOf(
        "the" to 255,
        "then" to 180,
        "there" to 160,
        "hello" to 90,
        "help" to 80,
        "held" to 40,
        "a" to 200,
        "to" to 210,
    )

    test("blank prefix stays empty so the writer row can keep the smartbar") {
        LatinWordSuggest.completions(words, "", 8) shouldBe emptyList()
        LatinWordSuggest.completions(words, "   ", 8) shouldBe emptyList()
        LatinWordSuggest.completions(words, "12", 8) shouldBe emptyList()
    }

    test("prefix completions rank by frequency and keep the typed word first") {
        LatinWordSuggest.completions(words, "the", 3).map { it.first } shouldBe listOf(
            "the",
            "then",
            "there",
        )
        LatinWordSuggest.completions(words, "hel", 3).map { it.first } shouldBe listOf(
            "hello",
            "help",
            "held",
        )
    }

    test("respects max count and prefix case") {
        LatinWordSuggest.completions(words, "t", 1).map { it.first } shouldBe listOf("the")
        LatinWordSuggest.completions(words, "Hel", 2).map { it.first } shouldBe listOf("Hello", "Help")
        LatinWordSuggest.completions(words, "HEL", 1).map { it.first } shouldBe listOf("HELLO")
    }
})
