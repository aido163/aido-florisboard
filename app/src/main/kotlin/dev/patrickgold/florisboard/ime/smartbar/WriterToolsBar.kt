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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import java.util.Locale
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

internal data class WriterBarAction(
    val data: TextKeyData,
    val labelRes: Int,
)

/** Stitch smartbar: 34dp ghost pills, 40dp back, compact selected variant. */
internal const val WriterChipHeightDp = 34
internal const val WriterBackSizeDp = 40
/** Child chips sit in a short track under the smart bar, same idea as the iOS row. */
internal const val WriterMenuChipHeightDp = 40
/** Track around the child chips. Just tall enough for the chip and a little inset. */
internal const val WriterChildTrackHeightDp = 44
/** Add options stay compact so the list fits the key area. */
internal const val WriterCatalogRowHeightDp = 36
/** Back arrow sits on a content row. It does not get a row of its own. */
internal const val WriterSheetBackSizeDp = 32

/** Draft transforms. Rewrite and translate open a child menu. Grammar and suggest open the sheet under this row. */
internal val WriterBarTools = listOf(
    WriterBarAction(TextKeyData.GRAMMAR, R.string.writer_tools__fix),
    WriterBarAction(TextKeyData.REWRITE, R.string.quick_action__rewrite),
    WriterBarAction(TextKeyData.TRANSLATE, R.string.quick_action__translate),
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
        modifier = modifier
            .size(width = WriterBackSizeDp.dp, height = WriterChipHeightDp.dp)
            .clip(CircleShape),
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
                        if (WriterNavStore.ui.value.layer != WriterLayer.TOOLS) {
                            WriterNavStore.set(WriterUi())
                        }
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
        onClick = {
            val ui = WriterNavStore.ui.value
            if (ui.layer != WriterLayer.TOOLS || ui.catalogOpen) {
                WriterNavStore.set(WriterUi())
            }
            WriterEditStore.toggleTools()
        },
    )
}

@Composable
internal fun WriterSettingsButton(
    modifier: Modifier = Modifier,
) {
    WriterIconSlot(
        modifier = modifier,
        imageVector = Icons.Default.Settings,
        contentDescription = "settings",
        onClick = { FlorisImeService.launchSettings() },
    )
}

/** Cursor, undo, and clipboard actions. Fills the key area and leaves the smart bar up. */
@Composable
fun WriterToolsPanel() {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val app = context.applicationContext as? FlorisApplication

    WriterKeySheet {
        PanelRow(verticalAlignment = Alignment.Top) {
            PanelChip("Left", Icons.AutoMirrored.Filled.KeyboardArrowLeft) {
                keyboardManager.handleArrow(KeyCode.ARROW_LEFT)
            }
            PanelChip("Right", Icons.AutoMirrored.Filled.KeyboardArrowRight) {
                keyboardManager.handleArrow(KeyCode.ARROW_RIGHT)
            }
            PanelChip("Up", Icons.Filled.KeyboardArrowUp) {
                keyboardManager.handleArrow(KeyCode.ARROW_UP)
            }
            PanelChip("Down", Icons.Filled.KeyboardArrowDown) {
                keyboardManager.handleArrow(KeyCode.ARROW_DOWN)
            }
            WriterSheetBackButton(onClick = { WriterEditStore.closeTools() })
        }
        PanelRow {
            PanelChip("Start", Icons.Filled.FirstPage) {
                keyboardManager.handleArrow(KeyCode.MOVE_START_OF_LINE)
            }
            PanelChip("End", Icons.Filled.LastPage) {
                keyboardManager.handleArrow(KeyCode.MOVE_END_OF_LINE)
            }
        }
        PanelRow {
            PanelChip("Undo", Icons.AutoMirrored.Filled.Undo) {
                app?.onHostWriterUndoRequested()
            }
            PanelChip("Redo", Icons.AutoMirrored.Filled.Redo) {
                app?.onHostWriterRedoRequested()
            }
        }
        PanelRow {
            PanelChip("Paste", Icons.Filled.ContentPaste) { editorInstance.performClipboardPaste() }
            PanelChip("Copy", Icons.Filled.ContentCopy) { editorInstance.performClipboardCopy() }
            PanelChip("Cut", Icons.Filled.ContentCut) { editorInstance.performClipboardCut() }
            PanelChip("Select all", Icons.Filled.SelectAll) { editorInstance.performClipboardSelectAll() }
        }
    }
}

/** Key-area sheet. Same height as the keys, under the smart bar. */
@Composable
internal fun WriterKeySheet(content: @Composable ColumnScope.() -> Unit) {
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
            .background(windowStyle.background(default = Color.Black)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            content = content,
        )
    }
}

/** Arrow that closes the sheet. Callers place it; it does not claim a full row. */
@Composable
internal fun WriterSheetBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val pressedStyle = rememberSnyggThemeQuery(
        FlorisImeUi.Key.elementName,
        selector = SnyggSelector.PRESSED,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val fill = if (pressed) {
        pressedStyle.background(default = keyStyle.background(default = Color.DarkGray))
    } else {
        keyStyle.background(default = Color.DarkGray)
    }
    Box(
        modifier = modifier
            .size(WriterSheetBackSizeDp.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "back",
            tint = keyStyle.foreground(default = Color.White),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ColumnScope.PanelRow(
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

@Composable
private fun RowScope.PanelChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val pressedStyle = rememberSnyggThemeQuery(
        FlorisImeUi.Key.elementName,
        selector = SnyggSelector.PRESSED,
    )
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val fill = if (pressed) {
        pressedStyle.background(default = keyStyle.background(default = Color.DarkGray))
    } else {
        keyStyle.background(default = Color.DarkGray)
    }
    val ink = if (pressed) {
        pressedStyle.foreground(default = keyStyle.foreground(default = Color.White))
    } else {
        keyStyle.foreground(default = Color.White)
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    onClick()
                },
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = writerChipTitle(label),
            color = ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
        modifier = modifier
            .size(width = WriterBackSizeDp.dp, height = WriterChipHeightDp.dp)
            .clip(CircleShape),
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

internal fun writerChipTitle(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    return trimmed.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
    }
}

internal fun writerModeIcon(mode: String): ImageVector = when (mode) {
    "grammar" -> Icons.Filled.Spellcheck
    "rewrite", "humanize" -> Icons.Filled.AutoFixHigh
    "translate" -> Icons.Filled.Translate
    "suggest" -> Icons.Filled.AutoAwesome
    else -> Icons.Filled.AutoAwesome
}

private fun modeForWriterAction(action: WriterBarAction): String = when (action.data.code) {
    KeyCode.GRAMMAR -> "grammar"
    KeyCode.REWRITE -> "rewrite"
    KeyCode.TRANSLATE -> "translate"
    KeyCode.SUGGEST -> "suggest"
    else -> ""
}

/** Space inside a pill, and between pills. Matches a roomy chip track. */
internal val WriterChipPadH = 14.dp
internal val WriterChipGap = 10.dp

@Composable
fun WriterToolsBar(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val writerUi by WriterNavStore.ui.collectAsState()
    val edit by WriterEditStore.ui.collectAsState()
    val chipScroll = rememberScrollState()
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
    @Composable
    fun ToolChip(
        label: String,
        icon: ImageVector,
        code: Int,
        onClick: () -> Unit,
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
        SnyggRow(
            elementName = elementName,
            attributes = attributes,
            selector = selector,
            modifier = Modifier
                .height(WriterChipHeightDp.dp)
                .clip(CircleShape),
            clickAndSemanticsModifier = Modifier
                .padding(horizontal = WriterChipPadH)
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
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            SnyggIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            SnyggText(
                elementName = "$elementName-text",
                attributes = attributes,
                selector = selector,
                text = writerChipTitle(label),
            )
        }
    }

    val menuOpen = writerUi.layer == WriterLayer.VARIANTS || writerUi.layer == WriterLayer.RESULTS
    val leading = listOf(WriterBarPrimary) + WriterBarTools
    SnyggBox(
        elementName = FlorisImeUi.SmartbarSharedActionsRow.elementName,
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            val viewport = this.maxWidth
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(chipScroll),
                horizontalArrangement = Arrangement.spacedBy(WriterChipGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.widthIn(min = viewport),
                    horizontalArrangement = Arrangement.spacedBy(WriterChipGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leading.forEach { action ->
                        val mode = modeForWriterAction(action)
                        val offeringUndo = edit.flashVisible && edit.flashMode == mode
                        ToolChip(
                            label = if (offeringUndo) {
                                stringRes(R.string.quick_action__undo)
                            } else {
                                stringRes(action.labelRes)
                            },
                            icon = if (offeringUndo) Icons.AutoMirrored.Filled.Undo else writerModeIcon(mode),
                            code = action.data.code,
                            selected = offeringUndo || (menuOpen && writerUi.mode == mode),
                            onClick = {
                                if (offeringUndo) {
                                    (context.applicationContext as? FlorisApplication)
                                        ?.onHostWriterUndoRequested()
                                } else {
                                    fire(action.data)
                                }
                            },
                        )
                    }
                    WriterMicButton()
                }
                WriterSettingsButton()
                WriterMoreButton()
            }
        }
    }
}

/**
 * Saved child chips in a horizontal track under the tool row.
 * Add opens the catalog in the key area.
 */
@Composable
fun WriterChildMenu(
    mode: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val variants = WriterNav.variantsFor(mode)
    val icon = writerModeIcon(mode)
    val code = keyCodeForWriterMode(mode)
    val scroll = rememberScrollState()

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

    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(WriterChildTrackHeightDp.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(WriterChipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            variants.forEach { variant ->
                WriterMenuChip(
                    label = variant.label,
                    icon = icon,
                    code = code,
                    onClick = { fireVariant(variant) },
                )
            }
            if (WriterNav.canAdd(mode)) {
                WriterMenuChip(
                    label = "add",
                    icon = Icons.Filled.Add,
                    code = code,
                    onClick = { fireCatalog() },
                )
            }
        }
    }
}

@Composable
private fun WriterMenuChip(
    label: String,
    icon: ImageVector,
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
            .height(WriterMenuChipHeightDp.dp)
            .clip(CircleShape),
        clickAndSemanticsModifier = Modifier
            .padding(horizontal = WriterChipPadH)
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
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        SnyggIcon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        SnyggText(
            elementName = "$elementName-text",
            attributes = attributes,
            selector = selector,
            text = writerChipTitle(label),
        )
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

    fun back() {
        val app = context.applicationContext as? FlorisApplication ?: return
        app.onHostWriterBackRequested()
    }

    WriterKeySheet {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            SnyggColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEach { option ->
                    WriterCatalogRow(
                        label = option.label,
                        mode = writerUi.mode,
                        code = code,
                        onClick = { add(option) },
                    )
                }
            }
            WriterSheetBackButton(
                onClick = { back() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun WriterCatalogRow(
    label: String,
    mode: String,
    code: Int,
    icon: ImageVector = writerModeIcon(mode),
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxWidth()
            .height(WriterCatalogRowHeightDp.dp)
            .clip(CircleShape),
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
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        SnyggIcon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        SnyggText(
            elementName = "$elementName-text",
            attributes = attributes,
            selector = selector,
            text = writerChipTitle(label),
        )
    }
}
