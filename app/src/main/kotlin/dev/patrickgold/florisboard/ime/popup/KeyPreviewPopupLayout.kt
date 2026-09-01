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

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Shape
import dev.patrickgold.florisboard.lib.FlorisRect
import kotlin.math.min

/**
 * Gboard-style key preview: a compact callout that sits above the pressed key
 * instead of a tall rectangle covering it.
 */
object KeyPreviewPopupLayout {
    const val PORTRAIT_WIDTH_SCALE = 1.18f
    const val LANDSCAPE_WIDTH_SCALE = 1.06f
    const val BODY_HEIGHT_SCALE = 1.18f
    /** Gap between the callout tip and the top of the key, as a fraction of key height. */
    const val GAP_RATIO = 0.14f
    /** Downward pointer height as a fraction of key height. */
    const val POINTER_RATIO = 0.22f
    const val POINTER_WIDTH_FRACTION = 0.42f
    const val CORNER_RADIUS_RATIO = 0.16f

    fun previewBounds(
        keyVisible: FlorisRect,
        templateVisible: FlorisRect,
        landscape: Boolean,
    ): FlorisRect {
        val widthScale = if (landscape) LANDSCAPE_WIDTH_SCALE else PORTRAIT_WIDTH_SCALE
        val popupWidth = templateVisible.width * widthScale
        val bodyHeight = templateVisible.height * BODY_HEIGHT_SCALE
        val pointerHeight = templateVisible.height * POINTER_RATIO
        val gap = templateVisible.height * GAP_RATIO
        val popupHeight = bodyHeight + pointerHeight
        val left = keyVisible.left + (keyVisible.width - popupWidth) / 2.0f
        val bottom = keyVisible.top - gap
        val top = bottom - popupHeight
        return FlorisRect.new(
            left = left,
            top = top,
            right = left + popupWidth,
            bottom = bottom,
        )
    }

    fun calloutShape(
        cornerRadiusPx: Float,
        pointerHeightPx: Float,
        pointerWidthFraction: Float = POINTER_WIDTH_FRACTION,
    ): Shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) {
            return@GenericShape
        }
        val pointerH = pointerHeightPx.coerceIn(0f, h * 0.35f)
        val bodyBottom = (h - pointerH).coerceAtLeast(0f)
        val r = cornerRadiusPx.coerceAtMost(min(w, bodyBottom) / 2.0f).coerceAtLeast(0f)
        val ptrHalf = (w * pointerWidthFraction / 2.0f).coerceAtMost((w / 2.0f - r).coerceAtLeast(0f))
        val cx = w / 2.0f

        moveTo(r, 0f)
        lineTo(w - r, 0f)
        quadraticTo(w, 0f, w, r)
        lineTo(w, (bodyBottom - r).coerceAtLeast(r))
        quadraticTo(w, bodyBottom, (w - r).coerceAtLeast(0f), bodyBottom)
        lineTo(cx + ptrHalf, bodyBottom)
        lineTo(cx, h)
        lineTo(cx - ptrHalf, bodyBottom)
        lineTo(r, bodyBottom)
        quadraticTo(0f, bodyBottom, 0f, (bodyBottom - r).coerceAtLeast(0f))
        lineTo(0f, r)
        quadraticTo(0f, 0f, r, 0f)
        close()
    }
}
