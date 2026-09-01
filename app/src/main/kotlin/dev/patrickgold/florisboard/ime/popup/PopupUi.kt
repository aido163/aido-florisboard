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

package dev.patrickgold.florisboard.ime.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.keyboard.Key
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.florisboard.lib.snygg.SnyggQueryAttributes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import org.florisboard.lib.snygg.value.SnyggDpSizeValue
import org.florisboard.lib.snygg.value.SnyggStaticColorValue

val GlobalStateNumPopupsShowing = MutableStateFlow(0)

@Composable
fun PopupBaseBox(
    modifier: Modifier = Modifier,
    attributes: SnyggQueryAttributes,
    key: Key,
    shouldIndicateExtendedPopups: Boolean,
): Unit = with(LocalDensity.current) {
    DisposableEffect(key) {
        GlobalStateNumPopupsShowing.update { it + 1 }
        onDispose {
            GlobalStateNumPopupsShowing.update { it - 1 }
        }
    }

    val style = rememberSnyggThemeQuery(FlorisImeUi.KeyPopupBox.elementName, attributes)
    val background = style.background(default = Color(0xFF2A2A2A))
    val foreground = style.foreground(default = Color.White)
    val elevation = style.shadowElevation(default = 3.dp).coerceAtLeast(0.dp)
    val borderWidth = when (val width = style.borderWidth) {
        is SnyggDpSizeValue -> width.dp
        else -> 0.dp
    }
    val borderColor = when (val color = style.borderColor) {
        is SnyggStaticColorValue -> color.color
        else -> Color.Unspecified
    }
    val pointerHeight = (key.visibleBounds.height * KeyPreviewPopupLayout.POINTER_RATIO).toDp()
    val cornerRadiusPx = key.visibleBounds.height * KeyPreviewPopupLayout.CORNER_RADIUS_RATIO
    val pointerHeightPx = key.visibleBounds.height * KeyPreviewPopupLayout.POINTER_RATIO
    val callout = remember(cornerRadiusPx, pointerHeightPx) {
        KeyPreviewPopupLayout.calloutShape(cornerRadiusPx, pointerHeightPx)
    }

    Box(
        modifier = modifier
            .shadow(elevation, callout)
            .then(
                if (borderWidth > 0.dp && borderColor.isSpecified) {
                    Modifier.border(borderWidth, borderColor, callout)
                } else {
                    Modifier
                },
            )
            .background(background, callout)
            .clip(callout),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = pointerHeight),
        ) {
            key.label?.let { label ->
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = label,
                    color = foreground,
                    fontSize = style.fontSize(default = 24.sp),
                    fontWeight = style.fontWeight(default = FontWeight.Bold) ?: FontWeight.Bold,
                    maxLines = 1,
                )
            }
            if (shouldIndicateExtendedPopups) {
                SnyggIcon(
                    elementName = FlorisImeUi.KeyPopupExtendedIndicator.elementName,
                    attributes = attributes,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    imageVector = Icons.Default.MoreHoriz,
                )
            }
        }
    }
}

@Composable
fun PopupExtBox(
    modifier: Modifier = Modifier,
    attributes: SnyggQueryAttributes,
    elements: List<List<PopupUiController.Element>>,
    elemArrangement: Arrangement.Horizontal,
    elemWidth: Dp,
    elemHeight: Dp,
    activeElementIndex: Int,
): Unit = with(LocalDensity.current) {
    SnyggColumn(FlorisImeUi.KeyPopupBox.elementName, attributes, modifier = modifier) {
        for (row in elements.asReversed()) {
            SnyggRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(elemHeight),
                horizontalArrangement = elemArrangement,
            ) {
                for (element in row) {
                    val selector = if (activeElementIndex == element.orderedIndex) {
                        SnyggSelector.FOCUS
                    } else {
                        null
                    }
                    val localAttrs = attributes.plus(FlorisImeUi.Attr.Code to element.data.code)
                    SnyggBox(
                        elementName = FlorisImeUi.KeyPopupElement.elementName,
                        attributes = localAttrs,
                        selector = selector,
                        modifier = Modifier.size(elemWidth, elemHeight),
                    ) {
                        element.label?.let { label ->
                            SnyggText(
                                modifier = Modifier.align(Alignment.Center),
                                text = label,
                            )
                        }
                        element.icon?.let { icon ->
                            SnyggIcon(
                                modifier = Modifier.align(Alignment.Center),
                                imageVector = icon,
                            )
                        }
                    }
                }
            }
        }
    }
}
