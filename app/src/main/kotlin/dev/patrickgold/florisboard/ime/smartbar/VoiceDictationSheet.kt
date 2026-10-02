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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * Key-area sheet for the mic. The live transcript fills this surface.
 * Back inserts the words and closes.
 */
@Composable
fun VoiceDictationSheet(
    transcript: String,
    notice: String,
    onDone: () -> Unit,
) {
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val ink = keyStyle.foreground(default = Color.White)
    val spoken = transcript.isNotBlank()
    val scroll = rememberScrollState()
    LaunchedEffect(transcript) {
        scroll.scrollTo(scroll.maxValue)
    }
    WriterKeySheet {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "listening",
                color = ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            WriterSheetBackButton(onClick = onDone)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
        ) {
            Text(
                text = if (spoken) transcript else "speak now",
                color = if (spoken) ink else ink.copy(alpha = 0.72f),
                fontSize = if (spoken) 18.sp else 14.sp,
                fontWeight = if (spoken) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (notice.isNotBlank()) {
            Text(
                text = notice,
                color = ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}
