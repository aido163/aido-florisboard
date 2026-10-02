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
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/** Stitch suggestion list: 68dp rewrite cards, 72dp thinking card. */
internal const val WriterSuggestionRowHeightDp = 68
internal const val WriterThinkingRowHeightDp = 72

/**
 * Vertical rewrite list. Suggest and writer RESULTS cover the whole IME
 * via the host assistant sheet; this panel is the fallback if that sheet
 * is not bound.
 */
@Composable
fun WriterSuggestionsPanel(
    ui: WriterUi,
    fillIme: Boolean = false,
) {
    val context = LocalContext.current

    fun accept(text: String) {
        val app = context.applicationContext as? FlorisApplication ?: return
        app.onHostGrammarChipAccepted(text)
    }

    val panelHeight = if (fillIme) {
        FlorisImeSizing.imeUiHeight()
    } else {
        FlorisImeSizing.keyboardUiHeight()
    }

    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight),
    ) {
        SnyggColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ui.thinking) {
                WriterSuggestionLine(
                    text = stringRes(R.string.writer_tools__thinking),
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.height(WriterThinkingRowHeightDp.dp),
                )
            } else {
                SnyggColumn(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.texts.forEach { text ->
                        WriterSuggestionLine(
                            text = text,
                            enabled = ui.selectable,
                            onClick = { accept(text) },
                            modifier = Modifier.height(WriterSuggestionRowHeightDp.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WriterSuggestionLine(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elementName = FlorisImeUi.SmartbarActionTile.elementName
    val selector = when {
        !enabled -> SnyggSelector.DISABLED
        isPressed -> SnyggSelector.PRESSED
        else -> null
    }
    SnyggRow(
        elementName = elementName,
        selector = selector,
        modifier = modifier.fillMaxWidth(),
        clickAndSemanticsModifier = Modifier
            .indication(interactionSource, LocalIndication.current)
            .pointerInput(text, enabled) {
                if (!enabled) return@pointerInput
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SnyggText(
            elementName = "$elementName-text",
            selector = selector,
            modifier = Modifier.weight(1f),
            text = text,
        )
        if (isPressed && enabled) {
            SnyggIcon(
                elementName = FlorisImeUi.SmartbarActionTileIcon.elementName,
                selector = selector,
                imageVector = Icons.Default.Check,
                contentDescription = null,
            )
        }
    }
}
