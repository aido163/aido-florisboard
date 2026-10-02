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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/** Compact reply cards. The back control shares the top corner, not its own row. */
internal const val WriterSuggestionRowHeightDp = 44
internal const val WriterThinkingRowHeightDp = 40

/**
 * Vertical rewrite list under the smart bar. The host assistant sheet is
 * the primary surface; this panel is the fallback if that sheet is not bound.
 */
@Composable
fun WriterSuggestionsPanel(
    ui: WriterUi,
) {
    val context = LocalContext.current

    fun accept(text: String) {
        val app = context.applicationContext as? FlorisApplication ?: return
        app.onHostGrammarChipAccepted(text)
    }

    WriterKeySheet {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (ui.thinking) {
                WriterSuggestionLine(
                    text = stringRes(R.string.writer_tools__thinking),
                    enabled = false,
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WriterThinkingRowHeightDp.dp)
                        .padding(end = 40.dp),
                )
            } else {
                SnyggColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ui.texts.forEach { text ->
                        WriterSuggestionLine(
                            text = text,
                            enabled = ui.selectable,
                            onClick = { accept(text) },
                            modifier = Modifier.heightIn(min = WriterSuggestionRowHeightDp.dp),
                        )
                    }
                }
            }
            WriterSheetBackButton(
                onClick = {
                    (context.applicationContext as? FlorisApplication)?.onHostWriterBackRequested()
                },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun WriterSuggestionLine(
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
