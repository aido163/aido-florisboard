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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.computeImageVector
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionButton
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow

private data class WriterChip(
    val id: String,
    val labelRes: Int,
    val key: TextKeyData,
    val children: String? = null,
)

private val CATEGORIES = listOf(
    WriterChip("suggest", R.string.quick_action__suggest, TextKeyData.SUGGEST),
    WriterChip("fix", R.string.writer_tools__fix, TextKeyData.GRAMMAR),
    WriterChip("rewrite", R.string.quick_action__rewrite, TextKeyData.REWRITE),
    WriterChip("translate", R.string.quick_action__translate, TextKeyData.TRANSLATE),
)

private val TREE = mapOf(
    "suggest" to listOf(
        WriterChip("reply", R.string.writer_tools__reply, TextKeyData.SUGGEST),
        WriterChip("continue", R.string.writer_tools__continue, TextKeyData.SUGGEST),
        WriterChip("ideas", R.string.writer_tools__ideas, TextKeyData.SUGGEST),
        WriterChip("warm", R.string.writer_tools__warm, TextKeyData.SUGGEST),
        WriterChip("short-reply", R.string.writer_tools__short, TextKeyData.SUGGEST),
    ),
    "fix" to listOf(
        WriterChip("grammar", R.string.quick_action__grammar, TextKeyData.GRAMMAR, children = "grammar"),
        WriterChip("spelling", R.string.writer_tools__spelling, TextKeyData.GRAMMAR),
        WriterChip("tighten", R.string.writer_tools__tighten, TextKeyData.GRAMMAR),
        WriterChip("detect-ai", R.string.writer_tools__detect_ai, TextKeyData.DETECT_AI),
    ),
    "grammar" to listOf(
        WriterChip("faithful", R.string.writer_tools__faithful, TextKeyData.GRAMMAR),
        WriterChip("tighter", R.string.writer_tools__tighter, TextKeyData.GRAMMAR),
        WriterChip("shortest", R.string.writer_tools__shortest, TextKeyData.GRAMMAR),
    ),
    "rewrite" to listOf(
        WriterChip("humanize", R.string.quick_action__humanize, TextKeyData.HUMANIZE, children = "humanize"),
        WriterChip("professional", R.string.writer_tools__professional, TextKeyData.REWRITE),
        WriterChip("casual", R.string.writer_tools__casual, TextKeyData.REWRITE),
        WriterChip("shorter", R.string.writer_tools__shorter, TextKeyData.REWRITE),
        WriterChip("longer", R.string.writer_tools__longer, TextKeyData.REWRITE),
        WriterChip("simpler", R.string.writer_tools__simpler, TextKeyData.REWRITE),
    ),
    "humanize" to listOf(
        WriterChip("like-a-text", R.string.writer_tools__like_a_text, TextKeyData.HUMANIZE),
        WriterChip("warmer", R.string.writer_tools__warmer, TextKeyData.HUMANIZE),
        WriterChip("less-ai", R.string.writer_tools__less_ai, TextKeyData.HUMANIZE),
        WriterChip("punchier", R.string.writer_tools__punchier, TextKeyData.HUMANIZE),
    ),
    "translate" to listOf(
        WriterChip("hinglish", R.string.writer_tools__hinglish, TextKeyData.TRANSLATE),
        WriterChip("hindi", R.string.writer_tools__hindi, TextKeyData.TRANSLATE),
        WriterChip("spanish", R.string.writer_tools__spanish, TextKeyData.TRANSLATE),
        WriterChip("french", R.string.writer_tools__french, TextKeyData.TRANSLATE),
        WriterChip("more-langs", R.string.writer_tools__more, TextKeyData.TRANSLATE, children = "more-langs"),
    ),
    "more-langs" to listOf(
        WriterChip("german", R.string.writer_tools__german, TextKeyData.TRANSLATE),
        WriterChip("japanese", R.string.writer_tools__japanese, TextKeyData.TRANSLATE),
        WriterChip("portuguese", R.string.writer_tools__portuguese, TextKeyData.TRANSLATE),
        WriterChip("arabic", R.string.writer_tools__arabic, TextKeyData.TRANSLATE),
    ),
)

private val FOLDER_BACK = mapOf(
    "grammar" to (R.string.quick_action__grammar to "fix"),
    "humanize" to (R.string.quick_action__humanize to "rewrite"),
    "more-langs" to (R.string.quick_action__translate to "translate"),
)

@Composable
fun WriterToolsBar(
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()

    var openId by remember { mutableStateOf<String?>(null) }
    var folderId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openId) {
        keyboardManager.writerToolsNestedOpen.value = openId != null
    }
    DisposableEffect(Unit) {
        onDispose {
            keyboardManager.writerToolsNestedOpen.value = false
        }
    }

    fun fire(data: TextKeyData) {
        keyboardManager.inputEventDispatcher.sendDownUp(data)
    }

    val nestedItems = remember(openId, folderId) {
        val key = folderId ?: openId
        if (key == null) emptyList() else TREE[key].orEmpty()
    }
    val back = folderId?.let { FOLDER_BACK[it] }

    SnyggColumn(
        elementName = FlorisImeUi.SmartbarSharedActionsRow.elementName,
        modifier = modifier.fillMaxWidth(),
    ) {
        SnyggRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leading?.let { it() }
            CATEGORIES.forEach { cat ->
                val selected = openId == cat.id
                SnyggChip(
                    elementName = FlorisImeUi.SmartbarActionKey.elementName,
                    attributes = mapOf(FlorisImeUi.Attr.Code to cat.key.code),
                    selector = if (selected) SnyggSelector.FOCUS else null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    onClick = {
                        if (openId == cat.id) {
                            openId = null
                            folderId = null
                        } else {
                            openId = cat.id
                            folderId = null
                        }
                    },
                    imageVector = evaluator.computeImageVector(cat.key),
                    text = stringRes(cat.labelRes),
                )
            }
            QuickActionButton(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .padding(vertical = 2.dp),
                action = QuickAction.InsertKey(TextKeyData.VOICE_INPUT),
                evaluator = evaluator,
            )
        }

        if (openId != null && nestedItems.isNotEmpty()) {
            SnyggRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.smartbarHeight)
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (back != null) {
                    SnyggChip(
                        elementName = FlorisImeUi.SmartbarActionKey.elementName,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 6.dp),
                        onClick = { folderId = null },
                        text = "‹ ${stringRes(back.first)}",
                    )
                }
                nestedItems.forEach { item ->
                    val folder = item.children != null
                    SnyggChip(
                        elementName = FlorisImeUi.SmartbarActionKey.elementName,
                        attributes = mapOf(FlorisImeUi.Attr.Code to item.key.code),
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 6.dp),
                        onClick = {
                            if (item.children != null) {
                                folderId = item.children
                            } else {
                                fire(item.key)
                            }
                        },
                        text = if (folder) {
                            "${stringRes(item.labelRes)} ›"
                        } else {
                            stringRes(item.labelRes)
                        },
                    )
                }
            }
        }
    }
}
