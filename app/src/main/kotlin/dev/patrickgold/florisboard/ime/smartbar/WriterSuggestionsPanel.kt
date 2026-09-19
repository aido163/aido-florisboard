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
import androidx.compose.foundation.layout.padding
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
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Vertical rewrite list covering the keys (same overflow slot as the three-dot
 * action grid). Tap a line to replace the whole compose box.
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

    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight()),
    ) {
        SnyggColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ui.thinking) {
                WriterSuggestionLine(
                    text = stringRes(R.string.writer_tools__thinking),
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            } else {
                ui.texts.forEach { text ->
                    WriterSuggestionLine(
                        text = text,
                        enabled = true,
                        onClick = { accept(text) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
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
    val selector = if (isPressed && enabled) SnyggSelector.PRESSED else null
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
        horizontalArrangement = Arrangement.Start,
    ) {
        SnyggText(
            elementName = "$elementName-text",
            selector = selector,
            text = text,
        )
    }
}
