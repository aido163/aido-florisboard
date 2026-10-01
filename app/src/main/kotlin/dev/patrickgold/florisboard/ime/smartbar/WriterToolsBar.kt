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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
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
    fun fireBack() {
        val app = context.applicationContext as? FlorisApplication ?: return
        app.onHostWriterBackRequested()
    }
    fun fireVariant(variant: WriterVariant) {
        val app = context.applicationContext as? FlorisApplication ?: return
        app.onHostWriterVariantRequested(variant.id, variant.label)
    }
    fun fireCatalog() {
        val app = context.applicationContext as? FlorisApplication ?: return
        app.onHostWriterCatalogRequested()
    }
    fun fireAdd(variant: WriterVariant) {
        val app = context.applicationContext as? FlorisApplication ?: return
        app.onHostWriterChipAdded(variant.id)
    }

    @Composable
    fun RowScope.ToolChip(
        label: String,
        code: Int,
        onClick: () -> Unit,
        fill: Boolean = true,
        lamp: Boolean = false,
    ) {
        val inputFeedbackController = LocalInputFeedbackController.current
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val elementName = FlorisImeUi.SmartbarActionKey.elementName
        val attributes = mapOf(FlorisImeUi.Attr.Code to code)
        val selector = if (isPressed) SnyggSelector.PRESSED else null
        val chipModifier = if (fill) {
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .height(WriterChipHeightDp.dp)
        } else {
            Modifier
                .height(WriterSelectedChipHeightDp.dp)
                .padding(horizontal = if (lamp) 0.dp else 10.dp)
        }
        SnyggRow(
            elementName = elementName,
            attributes = attributes,
            selector = selector,
            modifier = chipModifier,
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
            }
            WriterLayer.VARIANTS -> {
                WriterBackButton()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (writerUi.catalogOpen) {
                        WriterNav.availableFor(writerUi.mode).forEach { option ->
                            ToolChip(
                                label = option.label,
                                code = keyCodeForWriterMode(writerUi.mode),
                                onClick = { fireAdd(option) },
                                fill = false,
                            )
                        }
                    } else {
                        WriterNav.variantsFor(writerUi.mode).forEach { variant ->
                            ToolChip(
                                label = variant.label,
                                code = keyCodeForWriterMode(writerUi.mode),
                                onClick = { fireVariant(variant) },
                            )
                        }
                        if (WriterNav.canAdd(writerUi.mode)) {
                            ToolChip(
                                label = "add",
                                code = keyCodeForWriterMode(writerUi.mode),
                                onClick = { fireCatalog() },
                                fill = false,
                            )
                        }
                    }
                }
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
