/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.InlineSuggestionsStyleCache
import dev.patrickgold.florisboard.ime.smartbar.Smartbar
import dev.patrickgold.florisboard.ime.smartbar.WriterCatalogPanel
import dev.patrickgold.florisboard.ime.smartbar.WriterChildMenu
import dev.patrickgold.florisboard.ime.smartbar.WriterEditStore
import dev.patrickgold.florisboard.ime.smartbar.WriterLayer
import dev.patrickgold.florisboard.ime.smartbar.WriterNavStore
import dev.patrickgold.florisboard.ime.smartbar.VoiceDictationSheet
import dev.patrickgold.florisboard.ime.smartbar.WriterSuggestionsPanel
import dev.patrickgold.florisboard.ime.smartbar.WriterToolsPanel
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionsOverflowPanel
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardLayout
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.snygg.ui.SnyggIcon

@Composable
fun TextInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val prefs by FlorisPreferenceStore

    val state by keyboardManager.activeState.collectAsState()
    val evaluator by keyboardManager.activeEvaluator.collectAsState()
    val writerUi by WriterNavStore.ui.collectAsState()
    val toolsOpen by WriterEditStore.toolsOpen.collectAsState()
    val voicePresented by keyboardManager.voicePresented.collectAsState()
    val voiceTranscript by keyboardManager.voiceTranscript.collectAsState()
    val voiceNotice by keyboardManager.voiceNotice.collectAsState()

    InlineSuggestionsStyleCache()

    val showChildMenu = writerUi.layer == WriterLayer.VARIANTS &&
        !writerUi.catalogOpen &&
        !toolsOpen &&
        !voicePresented

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        // Tool row stays up. Output, the child menu, and the ⋯ panel sit under it.
        Smartbar()
        if (showChildMenu) {
            WriterChildMenu(mode = writerUi.mode)
        }
        if (voicePresented) {
            VoiceDictationSheet(
                transcript = voiceTranscript,
                notice = voiceNotice,
                onDone = { keyboardManager.finishVoiceInput() },
            )
        } else if (toolsOpen) {
            WriterToolsPanel()
        } else if (writerUi.catalogOpen) {
            WriterCatalogPanel()
        } else if (writerUi.coversKeys) {
            val app = context.applicationContext as? FlorisApplication
            val hostSheet = app?.hostSuggestSheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.keyboardUiHeight()),
            ) {
                if (app != null && hostSheet != null) {
                    hostSheet.Content(
                        onChip = { app.onHostGrammarChipAccepted(it) },
                        onDismiss = { app.onHostWriterBackRequested() },
                    )
                } else {
                    WriterSuggestionsPanel(writerUi)
                }
            }
        } else if (state.isActionsOverflowVisible) {
            QuickActionsOverflowPanel()
        } else {
            Box {
                val incognitoDisplayMode by prefs.keyboard.incognitoDisplayMode.collectAsState()
                val showIncognitoIcon = evaluator.state.isIncognitoMode &&
                    incognitoDisplayMode == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD
                if (showIncognitoIcon) {
                    SnyggIcon(
                        FlorisImeUi.IncognitoModeIndicator.elementName,
                        modifier = Modifier
                            .matchParentSize()
                            .align(Alignment.Center),
                        painter = painterResource(R.drawable.ic_incognito),
                    )
                }
                TextKeyboardLayout(evaluator = evaluator)
            }
        }
    }
}
