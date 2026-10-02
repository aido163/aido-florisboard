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
 * Writer navigation. The resting bar is [TOOLS]. A tool tap opens [HOME],
 * which covers the keyboard. Child chips replace the header inside that
 * panel. Suggest skips the panel and opens [RESULTS] on the chip sheet.
 */
enum class WriterLayer {
    TOOLS,
    HOME,
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
    /** Vertical catalog list is open in the writer panel. */
    val catalogOpen: Boolean = false,
    /** Bumped when saved chips change so the bar recomposes. */
    val chipEpoch: Int = 0,
    /** Child chips replace the tool header inside the writer panel. */
    val childrenOpen: Boolean = false,
    /** Focused field had no draft when the panel opened. */
    val emptyDraft: Boolean = false,
) {
    val nestedOpen: Boolean get() = layer != WriterLayer.TOOLS

    /** Suggest results and the writer home cover smartbar + keys in place. */
    val coversIme: Boolean get() = layer == WriterLayer.RESULTS || layer == WriterLayer.HOME

    /**
     * Fix, rewrite, translate, and humanize paint [WriterHomePanel].
     * Suggest results stay on the chip sheet.
     */
    val showsWriterPanel: Boolean
        get() = when (layer) {
            WriterLayer.HOME, WriterLayer.VARIANTS -> true
            WriterLayer.RESULTS -> mode.isNotBlank() && mode != "suggest"
            WriterLayer.TOOLS -> false
        }

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

    /** Opens the vertical catalog. A second call closes it. */
    fun openCatalog(current: WriterUi): WriterUi {
        val onPanel = current.layer == WriterLayer.VARIANTS ||
            current.layer == WriterLayer.HOME ||
            current.layer == WriterLayer.RESULTS
        if (!onPanel || current.mode.isBlank()) return current
        if (current.catalogOpen) return current.copy(catalogOpen = false)
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

    /** Full-keyboard tool panel. Children stay collapsed until the chevron opens them. */
    fun openHome(mode: String, emptyDraft: Boolean = false): WriterUi =
        WriterUi(
            layer = WriterLayer.HOME,
            mode = mode,
            emptyDraft = emptyDraft,
        )

    fun toggleChildren(current: WriterUi): WriterUi {
        if (current.mode.isBlank() || skipsVariants(current.mode)) return current
        if (current.layer == WriterLayer.TOOLS) return current
        return current.copy(childrenOpen = !current.childrenOpen, catalogOpen = false)
    }

    fun closeChildren(current: WriterUi): WriterUi =
        if (current.childrenOpen || current.catalogOpen) {
            current.copy(childrenOpen = false, catalogOpen = false)
        } else {
            current
        }

    fun dismiss(): WriterUi = WriterUi()

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
            catalogOpen = false,
            childrenOpen = false,
            emptyDraft = false,
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
        if (current.childrenOpen) return current.copy(childrenOpen = false)
        return when (current.layer) {
            WriterLayer.RESULTS ->
                if (skipsVariants(current.mode)) WriterUi()
                else WriterUi(layer = WriterLayer.VARIANTS, mode = current.mode)
            WriterLayer.HOME,
            WriterLayer.VARIANTS,
            WriterLayer.TOOLS -> WriterUi()
        }
    }

    fun reset(): WriterUi = WriterUi()
}

/**
 * Last whole-field writer commits. Newest entry is last.
 * A push stores the text from immediately before the replace and drops redo.
 * Empty or identical text does not push. Cap is [CAP].
 */
data class WriterEditUi(
    val undo: List<String> = emptyList(),
    val redo: List<String> = emptyList(),
    val flashEpoch: Int = 0,
    val flashVisible: Boolean = false,
) {
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
}

object WriterEdit {
    const val CAP = 10

    fun push(current: WriterEditUi, previous: String, committed: String): WriterEditUi {
        if (previous.isEmpty() || previous == committed) return current
        return current.copy(
            undo = (current.undo + previous).takeLast(CAP),
            redo = emptyList(),
            flashEpoch = current.flashEpoch + 1,
            flashVisible = true,
        )
    }

    /** Restores the newest commit. [field] is the text on screen, kept for redo. */
    fun undo(current: WriterEditUi, field: String): Pair<WriterEditUi, String?> {
        if (current.undo.isEmpty()) return current to null
        val restored = current.undo.last()
        val redo = if (field != restored) (current.redo + field).takeLast(CAP) else current.redo
        return current.copy(
            undo = current.undo.dropLast(1),
            redo = redo,
            flashVisible = false,
        ) to restored
    }

    fun redo(current: WriterEditUi, field: String): Pair<WriterEditUi, String?> {
        if (current.redo.isEmpty()) return current to null
        val restored = current.redo.last()
        val undo = if (field != restored) (current.undo + field).takeLast(CAP) else current.undo
        return current.copy(
            undo = undo,
            redo = current.redo.dropLast(1),
            flashVisible = false,
        ) to restored
    }

    fun hideFlash(current: WriterEditUi): WriterEditUi =
        if (current.flashVisible) current.copy(flashVisible = false) else current

    fun clear(current: WriterEditUi): WriterEditUi {
        if (current.undo.isEmpty() && current.redo.isEmpty() && !current.flashVisible) return current
        return current.copy(undo = emptyList(), redo = emptyList(), flashVisible = false)
    }
}

object WriterEditStore {
    private val _ui = MutableStateFlow(WriterEditUi())
    val ui: StateFlow<WriterEditUi> = _ui.asStateFlow()

    private val _toolsOpen = MutableStateFlow(false)
    val toolsOpen: StateFlow<Boolean> = _toolsOpen.asStateFlow()

    fun set(value: WriterEditUi) {
        _ui.value = value
    }

    fun push(previous: String, committed: String) {
        _ui.update { WriterEdit.push(it, previous, committed) }
    }

    fun hideFlash() {
        _ui.update(WriterEdit::hideFlash)
    }

    fun clear() {
        _ui.update(WriterEdit::clear)
        _toolsOpen.value = false
    }

    fun toggleTools() {
        val next = !_toolsOpen.value
        _toolsOpen.value = next
        if (next) {
            WriterNavStore.update { it.copy(catalogOpen = false) }
        }
    }

    fun closeTools() {
        _toolsOpen.value = false
    }
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
