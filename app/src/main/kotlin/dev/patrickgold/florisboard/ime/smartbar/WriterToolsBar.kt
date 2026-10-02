/*
 * Copyright (C) 2022-2026 The FlorisBoard Contributors
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

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import kotlinx.coroutines.delay
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

internal data class WriterBarAction(
    val data: TextKeyData,
    val labelRes: Int,
)

/** Stitch smartbar: 34dp ghost pills, 40dp back, compact selected variant. */
internal const val WriterChipHeightDp = 34
internal const val WriterSelectedChipHeightDp = 32
internal const val WriterBackSizeDp = 40
internal const val WriterLampSizeDp = 6
/** Saved chips stay this wide before the row scrolls. Add options use a taller row. */
internal const val WriterChipMinWidthDp = 72
internal const val WriterCatalogRowHeightDp = 44

/** Draft transforms. Nested children live in [WriterNav]. Grammar skips children and applies. RESULTS cover the IME. */
internal val WriterBarTools = listOf(
    WriterBarAction(TextKeyData.GRAMMAR, R.string.writer_tools__fix),
    WriterBarAction(TextKeyData.REWRITE, R.string.quick_action__rewrite),
    WriterBarAction(TextKeyData.TRANSLATE, R.string.quick_action__translate),
    WriterBarAction(TextKeyData.HUMANIZE, R.string.quick_action__humanize),
)

internal val WriterBarPrimary = WriterBarAction(
    TextKeyData.SUGGEST,
    R.string.quick_action__suggest,
)

/** Pinned to the trailing edge of the smartbar, after Suggest. */
internal val WriterBarTrailing = WriterBarAction(
    TextKeyData.VOICE_INPUT,
    R.string.quick_action__voice_input,
)

internal fun showWriterToolsRow(
    layout: SmartbarLayout,
    hasSuggestionStrip: Boolean,
): Boolean = layout == SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED && !hasSuggestionStrip

/** Word completions and pinned grammar/pending share the candidate row. */
internal fun countsAsSuggestionStrip(
    pinnedWriter: Boolean,
    hasWordCompletions: Boolean,
): Boolean = pinnedWriter || hasWordCompletions

@Composable
internal fun WriterMicButton(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val listening by keyboardManager.voiceListening.collectAsState()
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val selector = if (isPressed || listening) SnyggSelector.PRESSED else null
    val attributes = mapOf(FlorisImeUi.Attr.Code to KeyCode.VOICE_INPUT)
    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionKey.elementName,
        attributes = attributes,
        selector = selector,
        modifier = modifier.size(width = WriterBackSizeDp.dp, height = WriterChipHeightDp.dp),
        contentAlignment = Alignment.Center,
        clickAndSemanticsModifier = Modifier
            .indication(interactionSource, LocalIndication.current)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val press = PressInteraction.Press(down.position)
                    inputFeedbackController.keyPress(TextKeyData.VOICE_INPUT)
                    interactionSource.tryEmit(press)
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        WriterEditStore.closeTools()
                        keyboardManager.toggleVoiceInput()
                    } else {
                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                    }
                }
            },
    ) {
        SnyggIcon(
            imageVector = Icons.Default.KeyboardVoice,
            contentDescription = stringRes(WriterBarTrailing.labelRes),
        )
    }
}

@Composable
internal fun WriterMoreButton(
    modifier: Modifier = Modifier,
) {
    WriterIconSlot(
        modifier = modifier,
        imageVector = Icons.Default.MoreVert,
        contentDescription = "more",
        onClick = { WriterEditStore.toggleTools() },
    )
}

/** Right-aligned undo. Shows when a writer commit is pushed, hides after 3 seconds. */
@Composable
fun WriterUndoStrip() {
    val edit by WriterEditStore.ui.collectAsState()
    LaunchedEffect(edit.flashEpoch) {
        if (!edit.flashVisible) return@LaunchedEffect
        delay(3_000)
        WriterEditStore.hideFlash()
    }
    if (!edit.flashVisible) return
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WriterBackSizeDp.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        WriterIconSlot(
            modifier = Modifier.padding(end = 8.dp),
            imageVector = Icons.AutoMirrored.Filled.Undo,
            contentDescription = stringRes(R.string.quick_action__undo),
            onClick = {
                val app = context.applicationContext as? FlorisApplication ?: return@WriterIconSlot
                app.onHostWriterUndoRequested()
            },
        )
    }
}

/** Cursor, undo, and clipboard actions. Covers the keys and leaves the smart bar up. */
@Composable
fun WriterToolsPanel() {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val app = context.applicationContext as? FlorisApplication

    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight()),
    ) {
        SnyggColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelRow {
                PanelChip("left") { keyboardManager.handleArrow(KeyCode.ARROW_LEFT) }
                PanelChip("right") { keyboardManager.handleArrow(KeyCode.ARROW_RIGHT) }
                PanelChip("up") { keyboardManager.handleArrow(KeyCode.ARROW_UP) }
                PanelChip("down") { keyboardManager.handleArrow(KeyCode.ARROW_DOWN) }
            }
            PanelRow {
                PanelChip("start") { keyboardManager.handleArrow(KeyCode.MOVE_START_OF_LINE) }
                PanelChip("end") { keyboardManager.handleArrow(KeyCode.MOVE_END_OF_LINE) }
            }
            PanelRow {
                PanelChip("undo") { app?.onHostWriterUndoRequested() }
                PanelChip("redo") { app?.onHostWriterRedoRequested() }
            }
            PanelRow {
                PanelChip("paste") { editorInstance.performClipboardPaste() }
                PanelChip("copy") { editorInstance.performClipboardCopy() }
                PanelChip("cut") { editorInstance.performClipboardCut() }
                PanelChip("select all") { editorInstance.performClipboardSelectAll() }
            }
        }
    }
}

@Composable
private fun PanelRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(WriterCatalogRowHeightDp.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.PanelChip(
    label: String,
    onClick: () -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elementName = FlorisImeUi.SmartbarActionKey.elementName
    val selector = if (isPressed) SnyggSelector.PRESSED else null
    SnyggRow(
        elementName = elementName,
        selector = selector,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .height(WriterCatalogRowHeightDp.dp),
        clickAndSemanticsModifier = Modifier
            .indication(interactionSource, LocalIndication.current)
            .pointerInput(label) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val press = PressInteraction.Press(down.position)
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    interactionSource.tryEmit(press)
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        onClick()
                    } else {
                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        SnyggText(
            elementName = "$elementName-text",
            selector = selector,
            text = label,
        )
    }
}

@Composable
private fun WriterIconSlot(
    modifier: Modifier = Modifier,
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val selector = if (isPressed) SnyggSelector.PRESSED else null
    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionKey.elementName,
        selector = selector,
        modifier = modifier.size(width = WriterBackSizeDp.dp, height = WriterChipHeightDp.dp),
        contentAlignment = Alignment.Center,
        clickAndSemanticsModifier = Modifier
            .indication(interactionSource, LocalIndication.current)
            .pointerInput(contentDescription) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val press = PressInteraction.Press(down.position)
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    interactionSource.tryEmit(press)
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        onClick()
                    } else {
                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                    }
                }
            },
    ) {
        SnyggIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
        )
    }
}

private fun keyCodeForWriterMode(mode: String): Int = when (mode) {
    "grammar" -> KeyCode.GRAMMAR
    "rewrite" -> KeyCode.REWRITE
    "translate" -> KeyCode.TRANSLATE
    "humanize" -> KeyCode.HUMANIZE
    "detect" -> KeyCode.DETECT_AI
    "suggest" -> KeyCode.SUGGEST
    else -> KeyCode.UNSPECIFIED
}

@Composable
fun WriterToolsBar(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val writerUi by WriterNavStore.ui.collectAsState()
    val variantScroll = rememberScrollState()
    fun fire(data: TextKeyData) {
        val app = context.applicationContext as? FlorisApplication ?: return
        WriterEditStore.hideFlash()
        WriterEditStore.closeTools()
        when (data.code) {
            KeyCode.SUGGEST -> app.onHostSuggestRequested()
            KeyCode.GRAMMAR -> app.onHostGrammarRequested()
            KeyCode.REWRITE -> app.onHostRewriteRequested()
            KeyCode.TRANSLATE -> app.onHostTranslateRequested()
            KeyCode.HUMANIZE -> app.onHostHumanizeRequested()
            KeyCode.DETECT_AI -> app.onHostDetectAiRequested()
        }
    }
    fun fireBack() {
        val app = context.applicationContext as? FlorisApplication ?: return
        WriterEditStore.hideFlash()
        if (WriterEditStore.toolsOpen.value) {
            WriterEditStore.closeTools()
            return
        }
        app.onHostWriterBackRequested()
    }
    fun fireVariant(variant: WriterVariant) {
        val app = context.applicationContext as? FlorisApplication ?: return
        WriterEditStore.hideFlash()
        WriterEditStore.closeTools()
        app.onHostWriterVariantRequested(variant.id, variant.label)
    }
    fun fireCatalog() {
        val app = context.applicationContext as? FlorisApplication ?: return
        WriterEditStore.hideFlash()
        WriterEditStore.closeTools()
        app.onHostWriterCatalogRequested()
    }

    @Composable
    fun RowScope.ToolChip(
        label: String,
        code: Int,
        onClick: () -> Unit,
        fill: Boolean = true,
        lamp: Boolean = false,
        minWidth: Boolean = false,
        selected: Boolean = false,
    ) {
        val inputFeedbackController = LocalInputFeedbackController.current
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val elementName = FlorisImeUi.SmartbarActionKey.elementName
        val attributes = mapOf(FlorisImeUi.Attr.Code to code)
        val selector = when {
            isPressed -> SnyggSelector.PRESSED
            selected -> SnyggSelector.FOCUS
            else -> null
        }
        val chipModifier = when {
            fill ->
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(WriterChipHeightDp.dp)
            minWidth ->
                Modifier
                    .widthIn(min = WriterChipMinWidthDp.dp)
                    .height(WriterChipHeightDp.dp)
            else ->
                Modifier.height(
                    if (lamp) WriterSelectedChipHeightDp.dp else WriterChipHeightDp.dp,
                )
        }
        SnyggRow(
            elementName = elementName,
            attributes = attributes,
            selector = selector,
            modifier = chipModifier,
            clickAndSemanticsModifier = Modifier
                .indication(interactionSource, LocalIndication.current)
                .pointerInput(code, label, selected) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val press = PressInteraction.Press(down.position)
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                        interactionSource.tryEmit(press)
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            interactionSource.tryEmit(PressInteraction.Release(press))
                            onClick()
                        } else {
                            interactionSource.tryEmit(PressInteraction.Cancel(press))
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (lamp) {
                Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.Center
            },
        ) {
            if (lamp) {
                SnyggBox(
                    elementName = FlorisImeUi.WindowResizeHandle.elementName,
                    modifier = Modifier
                        .size(WriterLampSizeDp.dp)
                        .clip(CircleShape),
                ) { }
            }
            SnyggText(
                elementName = "$elementName-text",
                attributes = attributes,
                selector = selector,
                text = label,
            )
        }
    }

    @Composable
    fun WriterBackButton() {
        val inputFeedbackController = LocalInputFeedbackController.current
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val selector = if (isPressed) SnyggSelector.PRESSED else null
        SnyggBox(
            elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
            selector = selector,
            modifier = Modifier.size(WriterBackSizeDp.dp),
            contentAlignment = Alignment.Center,
            clickAndSemanticsModifier = Modifier
                .indication(interactionSource, LocalIndication.current)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val press = PressInteraction.Press(down.position)
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                        interactionSource.tryEmit(press)
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            interactionSource.tryEmit(PressInteraction.Release(press))
                            fireBack()
                        } else {
                            interactionSource.tryEmit(PressInteraction.Cancel(press))
                        }
                    }
                },
        ) {
            SnyggIcon(imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft)
        }
    }

    SnyggRow(
        elementName = FlorisImeUi.SmartbarSharedActionsRow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (writerUi.layer) {
            WriterLayer.TOOLS -> {
                (WriterBarTools + WriterBarPrimary).forEach { action ->
                    ToolChip(
                        label = stringRes(action.labelRes),
                        code = action.data.code,
                        onClick = { fire(action.data) },
                    )
                }
                WriterMicButton()
                WriterMoreButton()
            }
            WriterLayer.VARIANTS -> {
                WriterBackButton()
                val variants = WriterNav.variantsFor(writerUi.mode)
                val canAdd = WriterNav.canAdd(writerUi.mode)
                val code = keyCodeForWriterMode(writerUi.mode)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    val count = variants.size + if (canAdd) 1 else 0
                    val gap = 6.dp
                    val each = if (count > 0) {
                        (maxWidth - gap * (count - 1).coerceAtLeast(0)) / count
                    } else {
                        maxWidth
                    }
                    val scroll = each < WriterChipMinWidthDp.dp
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (scroll) {
                                    Modifier.horizontalScroll(variantScroll)
                                } else {
                                    Modifier
                                },
                            ),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        variants.forEach { variant ->
                            ToolChip(
                                label = variant.label,
                                code = code,
                                onClick = { fireVariant(variant) },
                                fill = !scroll,
                                minWidth = scroll,
                            )
                        }
                        if (canAdd) {
                            ToolChip(
                                label = "add",
                                code = code,
                                onClick = { fireCatalog() },
                                fill = !scroll,
                                minWidth = scroll,
                                selected = writerUi.catalogOpen,
                            )
                        }
                    }
                }
                WriterMoreButton()
            }
            WriterLayer.RESULTS -> {
                WriterBackButton()
                ToolChip(
                    label = writerUi.variant?.label.orEmpty(),
                    code = keyCodeForWriterMode(writerUi.mode),
                    onClick = { },
                    fill = false,
                    lamp = true,
                )
            }
        }
    }
}

/** Vertical list of unsaved writer options, covering the keys. */
@Composable
fun WriterCatalogPanel() {
    val context = LocalContext.current
    val writerUi by WriterNavStore.ui.collectAsState()
    val options = WriterNav.availableFor(writerUi.mode)
    val code = keyCodeForWriterMode(writerUi.mode)

    fun add(option: WriterVariant) {
        val app = context.applicationContext as? FlorisApplication ?: return
        app.onHostWriterChipAdded(option.id)
    }

    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight()),
    ) {
        SnyggColumn(modifier = Modifier.fillMaxSize()) {
            SnyggColumn(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    WriterCatalogRow(
                        label = option.label,
                        code = code,
                        onClick = { add(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WriterCatalogRow(
    label: String,
    code: Int,
    onClick: () -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elementName = FlorisImeUi.SmartbarActionKey.elementName
    val attributes = mapOf(FlorisImeUi.Attr.Code to code)
    val selector = if (isPressed) SnyggSelector.PRESSED else null
    SnyggRow(
        elementName = elementName,
        attributes = attributes,
        selector = selector,
        modifier = Modifier
            .fillMaxWidth()
            .height(WriterCatalogRowHeightDp.dp),
        clickAndSemanticsModifier = Modifier
            .indication(interactionSource, LocalIndication.current)
            .pointerInput(code, label) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val press = PressInteraction.Press(down.position)
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    interactionSource.tryEmit(press)
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        onClick()
                    } else {
                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        SnyggText(
            elementName = "$elementName-text",
            attributes = attributes,
            selector = selector,
            text = label,
        )
    }
}
