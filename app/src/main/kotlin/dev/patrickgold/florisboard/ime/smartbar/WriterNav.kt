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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Nested writer-tool navigation: tools → child chips → result list.
 * Suggest skips VARIANTS and opens RESULTS thinking on the same stack.
 */
enum class WriterLayer {
    TOOLS,
    VARIANTS,
    RESULTS,
}

data class WriterVariant(
    val id: String,
    val label: String,
)

data class WriterUi(
    val layer: WriterLayer = WriterLayer.TOOLS,
    val mode: String = "",
    val variant: WriterVariant? = null,
    val texts: List<String> = emptyList(),
    val thinking: Boolean = false,
    val selectable: Boolean = true,
) {
    val nestedOpen: Boolean get() = layer != WriterLayer.TOOLS

    /** Suggest covers smartbar + keys in place. Rewrite/fix still cover keys only. */
    val coversIme: Boolean get() = mode == "suggest" && layer == WriterLayer.RESULTS

    val coversKeys: Boolean get() = layer == WriterLayer.RESULTS && mode != "suggest"
}

object WriterNav {
    fun variantsFor(mode: String): List<WriterVariant> = when (mode) {
        "grammar" -> listOf(
            WriterVariant("keep", "keep"),
            WriterVariant("tighter", "tighter"),
            WriterVariant("shortest", "shortest"),
        )
        "rewrite" -> listOf(
            WriterVariant("warm", "warm"),
            WriterVariant("humor", "humor"),
            WriterVariant("professional", "professional"),
        )
        "translate" -> listOf(
            WriterVariant("english", "english"),
            WriterVariant("hinglish", "hinglish"),
            WriterVariant("keep", "keep"),
        )
        "humanize" -> listOf(
            WriterVariant("natural", "natural"),
            WriterVariant("shorter", "shorter"),
            WriterVariant("casual", "casual"),
        )
        else -> emptyList()
    }

    fun openVariants(mode: String): WriterUi {
        if (variantsFor(mode).isEmpty()) return WriterUi()
        return WriterUi(layer = WriterLayer.VARIANTS, mode = mode)
    }

    /** Suggest has no child chips — RESULTS thinking, then the vertical list. */
    fun startSuggest(): WriterUi =
        WriterUi(
            layer = WriterLayer.RESULTS,
            mode = "suggest",
            variant = WriterVariant("suggest", "suggest"),
            texts = emptyList(),
            thinking = true,
        )

    fun startFetch(current: WriterUi, variant: WriterVariant): WriterUi {
        if (current.layer == WriterLayer.TOOLS || current.mode.isBlank()) return current
        return current.copy(
            layer = WriterLayer.RESULTS,
            variant = variant,
            texts = emptyList(),
            thinking = true,
        )
    }

    fun showResults(current: WriterUi, texts: List<String>): WriterUi {
        val cap = if (current.mode == "suggest") 6 else 3
        val cleaned = texts.map { it.trim() }.filter { it.isNotEmpty() }.take(cap)
        if (cleaned.isEmpty()) return failFetch(current)
        return current.copy(
            layer = WriterLayer.RESULTS,
            texts = cleaned,
            thinking = false,
            selectable = true,
        )
    }

    /** Keep RESULTS with a non-insertable line so Suggest thinking does not vanish. */
    fun showStatus(current: WriterUi, message: String): WriterUi {
        val text = message.trim()
        if (text.isEmpty()) return failFetch(current)
        if (current.mode != "suggest") return failFetch(current)
        return current.copy(
            layer = WriterLayer.RESULTS,
            texts = listOf(text),
            thinking = false,
            selectable = false,
        )
    }

    fun failFetch(current: WriterUi): WriterUi {
        if (current.mode.isBlank() || current.mode == "suggest") return WriterUi()
        return WriterUi(layer = WriterLayer.VARIANTS, mode = current.mode)
    }

    fun back(current: WriterUi): WriterUi = when (current.layer) {
        WriterLayer.RESULTS ->
            if (current.mode == "suggest") WriterUi()
            else WriterUi(layer = WriterLayer.VARIANTS, mode = current.mode)
        WriterLayer.VARIANTS -> WriterUi()
        WriterLayer.TOOLS -> WriterUi()
    }

    fun reset(): WriterUi = WriterUi()
}

object WriterNavStore {
    private val _ui = MutableStateFlow(WriterUi())
    val ui: StateFlow<WriterUi> = _ui.asStateFlow()

    fun set(value: WriterUi) {
        _ui.value = value
    }

    fun update(transform: (WriterUi) -> WriterUi) {
        _ui.update(transform)
    }
}
