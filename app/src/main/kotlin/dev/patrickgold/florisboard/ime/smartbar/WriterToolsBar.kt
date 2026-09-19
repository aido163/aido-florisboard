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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

internal data class WriterBarAction(
    val data: TextKeyData,
    val labelRes: Int,
)

/** Draft transforms. Tap runs the tool — no nested folders. */
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

internal fun showWriterToolsRow(
    layout: SmartbarLayout,
    sharedActionsExpanded: Boolean,
    hasPinnedWriterChips: Boolean,
): Boolean = layout == SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED &&
    !sharedActionsExpanded &&
    !hasPinnedWriterChips

@Composable
fun WriterToolsBar(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun fire(data: TextKeyData) {
        val app = context.applicationContext as? FlorisApplication ?: return
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
    fun RowScope.ToolChip(action: WriterBarAction) {
        val inputFeedbackController = LocalInputFeedbackController.current
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val elementName = FlorisImeUi.SmartbarActionKey.elementName
        val attributes = mapOf(FlorisImeUi.Attr.Code to action.data.code)
        val selector = if (isPressed) SnyggSelector.PRESSED else null
        SnyggRow(
            elementName = elementName,
            attributes = attributes,
            selector = selector,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(vertical = 5.dp),
            clickAndSemanticsModifier = Modifier
                .indication(interactionSource, LocalIndication.current)
                .pointerInput(action.data.code) {
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
                            fire(action.data)
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
                text = stringRes(action.labelRes),
            )
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
        (WriterBarTools + WriterBarPrimary).forEach { action ->
            ToolChip(action)
        }
    }
}
