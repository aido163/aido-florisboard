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

/**
 * Prefix completions from the bundled Latin frequency map. Idle / blank
 * composing returns nothing so the writer chip row can keep the smartbar.
 */
internal object LatinWordSuggest {
    fun completions(
        words: Map<String, Int>,
        prefix: CharSequence,
        maxCandidateCount: Int,
    ): List<Pair<String, Int>> {
        val raw = prefix.trim().toString()
        if (raw.isEmpty() || maxCandidateCount <= 0) return emptyList()
        val folded = raw.lowercase()
        if (folded.none { it.isLetter() }) return emptyList()

        var exactFreq: Int? = null
        val longer = ArrayList<Pair<String, Int>>(32)
        for ((word, freq) in words) {
            if (!word.startsWith(folded)) continue
            if (word == folded) {
                exactFreq = freq
            } else {
                longer.add(word to freq)
            }
        }
        longer.sortWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenBy { it.first.length }
                .thenBy { it.first },
        )
        val out = ArrayList<Pair<String, Int>>(maxCandidateCount)
        if (exactFreq != null) {
            out.add(applyPrefixCase(raw, folded) to exactFreq)
        }
        for (match in longer) {
            if (out.size >= maxCandidateCount) break
            out.add(applyPrefixCase(raw, match.first) to match.second)
        }
        return out
    }

    internal fun applyPrefixCase(prefix: String, word: String): String {
        val letters = prefix.filter { it.isLetter() }
        if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) {
            return word.uppercase()
        }
        if (prefix.firstOrNull()?.isUpperCase() == true) {
            return word.replaceFirstChar { it.uppercase() }
        }
        return word
    }
}
