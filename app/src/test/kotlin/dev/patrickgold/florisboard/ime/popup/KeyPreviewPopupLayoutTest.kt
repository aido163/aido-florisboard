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

import dev.patrickgold.florisboard.lib.FlorisRect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class KeyPreviewPopupLayoutTest : FunSpec({
    val key = FlorisRect.new(left = 100f, top = 200f, right = 180f, bottom = 280f)
    val template = FlorisRect.new(width = 80f, height = 80f)

    test("preview sits fully above the key with a gap") {
        val bounds = KeyPreviewPopupLayout.previewBounds(key, template, landscape = false)

        bounds.bottom shouldBeLessThanOrEqualTo key.top
        val expectedGap = template.height * KeyPreviewPopupLayout.GAP_RATIO
        bounds.bottom shouldBe ((key.top - expectedGap) plusOrMinus 0.01f)
        bounds.top shouldBeLessThanOrEqualTo bounds.bottom
    }

    test("preview does not cover the pressed key") {
        val bounds = KeyPreviewPopupLayout.previewBounds(key, template, landscape = false)

        (bounds.bottom <= key.top) shouldBe true
        bounds.height shouldBeLessThanOrEqualTo template.height * 2.0f
    }

    test("preview is centered on the key") {
        val bounds = KeyPreviewPopupLayout.previewBounds(key, template, landscape = false)

        val keyCenter = (key.left + key.right) / 2.0f
        val popupCenter = (bounds.left + bounds.right) / 2.0f
        popupCenter shouldBe (keyCenter plusOrMinus 0.01f)
    }

    test("portrait popup is slightly wider than the key template") {
        val bounds = KeyPreviewPopupLayout.previewBounds(key, template, landscape = false)

        bounds.width shouldBe ((template.width * KeyPreviewPopupLayout.PORTRAIT_WIDTH_SCALE) plusOrMinus 0.01f)
        bounds.width shouldBeGreaterThan template.width
    }

    test("landscape uses the narrower width scale") {
        val portrait = KeyPreviewPopupLayout.previewBounds(key, template, landscape = false)
        val landscape = KeyPreviewPopupLayout.previewBounds(key, template, landscape = true)

        landscape.width shouldBeLessThanOrEqualTo portrait.width
        landscape.width shouldBe ((template.width * KeyPreviewPopupLayout.LANDSCAPE_WIDTH_SCALE) plusOrMinus 0.01f)
    }

    test("height is body plus pointer, not a full-key overlay") {
        val bounds = KeyPreviewPopupLayout.previewBounds(key, template, landscape = false)
        val expected = template.height * (
            KeyPreviewPopupLayout.BODY_HEIGHT_SCALE + KeyPreviewPopupLayout.POINTER_RATIO
        )

        bounds.height shouldBe (expected plusOrMinus 0.01f)
        bounds.height shouldBeLessThanOrEqualTo key.height * 2.0f
    }
})
