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
 * Suggest and grammar skip VARIANTS and open RESULTS thinking on the same stack.
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
    /** Variant row is showing catalog ids the user has not saved yet. */
    val catalogOpen: Boolean = false,
    /** Bumped when saved chips change so the bar recomposes. */
    val chipEpoch: Int = 0,
) {
    val nestedOpen: Boolean get() = layer != WriterLayer.TOOLS

    /** RESULTS (Suggest and writer transforms) cover smartbar + keys in place. */
    val coversIme: Boolean get() = layer == WriterLayer.RESULTS

    val coversKeys: Boolean get() = false
}

object WriterNav {
    val DEFAULT_TRANSLATE: List<WriterVariant> = listOf(
        WriterVariant("english", "english"),
        WriterVariant("hindi", "hindi"),
        WriterVariant("hinglish", "hinglish"),
    )

    val DEFAULT_REWRITE: List<WriterVariant> = listOf(
        WriterVariant("warm", "warm"),
        WriterVariant("humor", "humor"),
        WriterVariant("professional", "professional"),
    )

    val DEFAULT_HUMANIZE: List<WriterVariant> = listOf(
        WriterVariant("natural", "natural"),
        WriterVariant("shorter", "shorter"),
        WriterVariant("casual", "casual"),
    )

    val TRANSLATE_CATALOG: List<WriterVariant> = listOf(
        WriterVariant("english", "english"),
        WriterVariant("hindi", "hindi"),
        WriterVariant("hinglish", "hinglish"),
        WriterVariant("french", "french"),
        WriterVariant("german", "german"),
        WriterVariant("spanish", "spanish"),
        WriterVariant("portuguese", "portuguese"),
        WriterVariant("tamil", "tamil"),
        WriterVariant("telugu", "telugu"),
        WriterVariant("bengali", "bengali"),
        WriterVariant("marathi", "marathi"),
        WriterVariant("gujarati", "gujarati"),
    )

    val REWRITE_CATALOG: List<WriterVariant> = listOf(
        WriterVariant("warm", "warm"),
        WriterVariant("humor", "humor"),
        WriterVariant("professional", "professional"),
        WriterVariant("casual", "casual"),
        WriterVariant("tighter", "tighter"),
        WriterVariant("sharper", "sharper"),
        WriterVariant("flirty", "flirty"),
        WriterVariant("dry", "dry"),
    )

    val HUMANIZE_CATALOG: List<WriterVariant> = listOf(
        WriterVariant("natural", "natural"),
        WriterVariant("shorter", "shorter"),
        WriterVariant("casual", "casual"),
        WriterVariant("warmer", "warmer"),
        WriterVariant("looser", "looser"),
        WriterVariant("spoken", "spoken"),
        WriterVariant("punchier", "punchier"),
        WriterVariant("messy", "messy"),
    )

    const val MAX = 8

    @Volatile
    private var translateVariants: List<WriterVariant> = DEFAULT_TRANSLATE

    @Volatile
    private var rewriteVariants: List<WriterVariant> = DEFAULT_REWRITE

    @Volatile
    private var humanizeVariants: List<WriterVariant> = DEFAULT_HUMANIZE

    fun setTranslateLanguages(ids: List<String>) {
        translateVariants = variantsFromIds(ids, DEFAULT_TRANSLATE)
    }

    fun setRewriteStyles(ids: List<String>) {
        rewriteVariants = variantsFromIds(ids, DEFAULT_REWRITE)
    }

    fun setHumanizeStyles(ids: List<String>) {
        humanizeVariants = variantsFromIds(ids, DEFAULT_HUMANIZE)
    }

    fun variantsFor(mode: String): List<WriterVariant> = when (mode) {
        "rewrite" -> rewriteVariants
        "translate" -> translateVariants
        "humanize" -> humanizeVariants
        else -> emptyList()
    }

    fun catalogFor(mode: String): List<WriterVariant> = when (mode) {
        "rewrite" -> REWRITE_CATALOG
        "translate" -> TRANSLATE_CATALOG
        "humanize" -> HUMANIZE_CATALOG
        else -> emptyList()
    }

    /** Catalog ids that are not already on the saved chip row. */
    fun availableFor(mode: String): List<WriterVariant> {
        val saved = variantsFor(mode).map { it.id }.toSet()
        return catalogFor(mode).filter { it.id !in saved }
    }

    fun canAdd(mode: String): Boolean =
        variantsFor(mode).size < MAX && availableFor(mode).isNotEmpty()

    fun openCatalog(current: WriterUi): WriterUi {
        if (current.layer != WriterLayer.VARIANTS || current.mode.isBlank()) return current
        if (!canAdd(current.mode)) return current
        return current.copy(catalogOpen = true)
    }

    fun noteChipsChanged(current: WriterUi): WriterUi {
        val next = current.copy(chipEpoch = current.chipEpoch + 1)
        return if (next.catalogOpen && !canAdd(next.mode)) {
            next.copy(catalogOpen = false)
        } else {
            next
        }
    }

    private fun variantsFromIds(
        ids: List<String>,
        fallback: List<WriterVariant>,
    ): List<WriterVariant> {
        val next = ids.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)
            .map { WriterVariant(it, it) }
        return next.ifEmpty { fallback }
    }

    fun skipsVariants(mode: String): Boolean = variantsFor(mode).isEmpty()

    /** Grammar and translate replace the draft; rewrite / humanize keep a chip list. */
    fun autoReplacesDraft(mode: String): Boolean =
        mode == "grammar" || mode == "translate"

    fun openVariants(mode: String): WriterUi {
        if (skipsVariants(mode)) return WriterUi()
        return WriterUi(layer = WriterLayer.VARIANTS, mode = mode)
    }

    /** Suggest / grammar have no child chips — RESULTS thinking, then the list. */
    fun startResults(mode: String, variant: WriterVariant): WriterUi =
        WriterUi(
            layer = WriterLayer.RESULTS,
            mode = mode,
            variant = variant,
            texts = emptyList(),
            thinking = true,
        )

    fun startSuggest(): WriterUi =
        startResults("suggest", WriterVariant("suggest", "suggest"))

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
        val cap = when (current.mode) {
            "suggest" -> 6
            "translate", "grammar" -> 1
            else -> 3
        }
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
        if (current.mode.isBlank() || skipsVariants(current.mode)) return WriterUi()
        return WriterUi(layer = WriterLayer.VARIANTS, mode = current.mode)
    }

    fun back(current: WriterUi): WriterUi {
        if (current.catalogOpen) return current.copy(catalogOpen = false)
        return when (current.layer) {
            WriterLayer.RESULTS ->
                if (skipsVariants(current.mode)) WriterUi()
                else WriterUi(layer = WriterLayer.VARIANTS, mode = current.mode)
            WriterLayer.VARIANTS -> WriterUi()
            WriterLayer.TOOLS -> WriterUi()
        }
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
