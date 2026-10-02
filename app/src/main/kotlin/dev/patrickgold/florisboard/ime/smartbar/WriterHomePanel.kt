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

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.input.InputFeedbackController
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Tool panel over the keyboard. Header is fix / rewrite / translate / humanize.
 * A tool with children swaps that header for its saved chips. Results and
 * empty copy sit in the middle. Suggest stays on the resting bar.
 */
@Composable
fun WriterHomePanel(ui: WriterUi) {
    val context = LocalContext.current
    val app = context.applicationContext as? FlorisApplication

    fun fire(action: WriterBarAction) {
        WriterEditStore.hideFlash()
        WriterEditStore.closeTools()
        when (action.data.code) {
            KeyCode.GRAMMAR -> app?.onHostGrammarRequested()
            KeyCode.REWRITE -> app?.onHostRewriteRequested()
            KeyCode.TRANSLATE -> app?.onHostTranslateRequested()
            KeyCode.HUMANIZE -> app?.onHostHumanizeRequested()
        }
    }

    fun fireVariant(variant: WriterVariant) {
        WriterEditStore.hideFlash()
        WriterEditStore.closeTools()
        app?.onHostWriterVariantRequested(variant.id, variant.label)
    }

    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = Modifier.fillMaxSize(),
    ) {
        SnyggColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ui.childrenOpen && !WriterNav.skipsVariants(ui.mode)) {
                ChildCapsule(
                    ui = ui,
                    onVariant = ::fireVariant,
                    onAdd = { app?.onHostWriterCatalogRequested() },
                    onClose = { WriterNavStore.update(WriterNav::closeChildren) },
                )
            } else {
                ToolHeader(
                    ui = ui,
                    onTool = { action ->
                        val mode = modeForAction(action)
                        if (mode == ui.mode && !WriterNav.skipsVariants(mode)) {
                            WriterNavStore.update(WriterNav::toggleChildren)
                        } else {
                            fire(action)
                        }
                    },
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PanelBody(ui = ui, onAccept = { app?.onHostGrammarChipAccepted(it) }, onAdd = {
                    app?.onHostWriterChipAdded(it)
                })
            }
            BackPill(onClick = { app?.onHostWriterDismissRequested() })
        }
    }
}

private fun modeForAction(action: WriterBarAction): String = when (action.data.code) {
    KeyCode.GRAMMAR -> "grammar"
    KeyCode.REWRITE -> "rewrite"
    KeyCode.TRANSLATE -> "translate"
    KeyCode.HUMANIZE -> "humanize"
    else -> ""
}

@Composable
private fun ToolHeader(
    ui: WriterUi,
    onTool: (WriterBarAction) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WriterBarTools.forEach { action ->
            val mode = modeForAction(action)
            val selected = mode == ui.mode
            HeaderChip(
                label = stringRes(action.labelRes),
                code = action.data.code,
                selected = selected,
                showChevron = selected && !WriterNav.skipsVariants(mode),
                onClick = { onTool(action) },
            )
        }
    }
}

@Composable
private fun ChildCapsule(
    ui: WriterUi,
    onVariant: (WriterVariant) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    val scroll = rememberScrollState()
    val variants = WriterNav.variantsFor(ui.mode)
    val code = keyCodeForWriterMode(ui.mode)
    val canAdd = WriterNav.canAdd(ui.mode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggRow(
            elementName = FlorisImeUi.SmartbarActionKey.elementName,
            attributes = mapOf(FlorisImeUi.Attr.Code to code),
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                variants.forEach { variant ->
                    HeaderChip(
                        label = variant.label,
                        code = code,
                        selected = variant.id == ui.variant?.id,
                        showChevron = false,
                        onClick = { onVariant(variant) },
                    )
                }
                if (canAdd) {
                    HeaderChip(
                        label = "add",
                        code = code,
                        selected = ui.catalogOpen,
                        showChevron = false,
                        onClick = onAdd,
                    )
                }
            }
            HeaderIconButton(
                image = Icons.Default.Close,
                description = stringRes(R.string.writer_home__back),
                onClick = onClose,
            )
        }
    }
}

@Composable
private fun PanelBody(
    ui: WriterUi,
    onAccept: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    when {
        ui.catalogOpen -> {
            val options = WriterNav.availableFor(ui.mode)
            val code = keyCodeForWriterMode(ui.mode)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    HeaderChip(
                        label = option.label,
                        code = code,
                        selected = false,
                        showChevron = false,
                        fillWidth = true,
                        onClick = { onAdd(option.id) },
                    )
                }
            }
        }
        ui.thinking -> {
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionKey.elementName,
                text = stringRes(R.string.writer_tools__thinking),
            )
        }
        ui.texts.isNotEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ui.texts.forEach { text ->
                    WriterSuggestionLine(
                        text = text,
                        enabled = ui.selectable,
                        onClick = { onAccept(text) },
                        modifier = Modifier.height(WriterSuggestionRowHeightDp.dp),
                    )
                }
            }
        }
        else -> EmptyCopy(ui)
    }
}

@Composable
private fun EmptyCopy(ui: WriterUi) {
    val (titleRes, bodyRes) = when {
        ui.emptyDraft && ui.mode == "grammar" ->
            R.string.writer_home__grammar_title to R.string.writer_home__grammar_body
        ui.emptyDraft && ui.mode == "translate" ->
            R.string.writer_home__translate_title to R.string.writer_home__translate_body
        ui.emptyDraft && ui.mode == "humanize" ->
            R.string.writer_home__humanize_title to R.string.writer_home__humanize_body
        ui.emptyDraft ->
            R.string.writer_home__rewrite_title to R.string.writer_home__rewrite_body
        ui.mode == "translate" ->
            R.string.writer_home__pick_language to R.string.writer_home__pick_language_body
        else ->
            R.string.writer_home__pick_style to R.string.writer_home__pick_style_body
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SnyggText(
            elementName = FlorisImeUi.SmartbarActionTile.elementName,
            text = stringRes(titleRes),
        )
        SnyggText(
            elementName = FlorisImeUi.SmartbarActionKey.elementName,
            text = stringRes(bodyRes),
        )
    }
}

@Composable
private fun BackPill(onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        HeaderChip(
            label = stringRes(R.string.writer_home__back),
            code = KeyCode.GRAMMAR,
            selected = true,
            showChevron = false,
            onClick = onClick,
        )
    }
}

@Composable
private fun HeaderChip(
    label: String,
    code: Int,
    selected: Boolean,
    showChevron: Boolean,
    fillWidth: Boolean = false,
    onClick: () -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    val paintCode = if (selected) code else KeyCode.UNSPECIFIED
    val attributes = mapOf(FlorisImeUi.Attr.Code to paintCode)
    val selector = if (selected) SnyggSelector.FOCUS else null
    val chipModifier = when {
        fillWidth ->
            Modifier
                .fillMaxWidth()
                .height(WriterCatalogRowHeightDp.dp)
        selected ->
            Modifier
                .widthIn(min = WriterChipMinWidthDp.dp)
                .height(WriterChipHeightDp.dp)
        else ->
            Modifier
                .height(WriterChipHeightDp.dp)
                .padding(horizontal = 8.dp)
    }
    SnyggRow(
        elementName = FlorisImeUi.SmartbarActionKey.elementName,
        attributes = attributes,
        selector = selector,
        modifier = chipModifier,
        clickAndSemanticsModifier = Modifier.writerPress(interactionSource, inputFeedbackController, onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        SnyggText(
            elementName = FlorisImeUi.SmartbarActionKey.elementName + "-text",
            attributes = attributes,
            selector = selector,
            text = label,
        )
        if (showChevron) {
            SnyggIcon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionKey.elementName,
        modifier = Modifier.height(WriterChipHeightDp.dp),
        contentAlignment = Alignment.Center,
        clickAndSemanticsModifier = Modifier.writerPress(interactionSource, inputFeedbackController, onClick),
    ) {
        SnyggIcon(
            imageVector = image,
            contentDescription = description,
        )
    }
}

@Composable
private fun Modifier.writerPress(
    interactionSource: MutableInteractionSource,
    inputFeedbackController: InputFeedbackController,
    onClick: () -> Unit,
): Modifier = this
    .indication(interactionSource, LocalIndication.current)
    .pointerInput(onClick) {
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
    }
