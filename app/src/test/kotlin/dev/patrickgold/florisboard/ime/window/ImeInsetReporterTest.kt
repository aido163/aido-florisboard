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

package dev.patrickgold.florisboard.ime.window

import androidx.compose.ui.unit.IntRect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ImeInsetReporterTest : FunSpec({
    val root = IntRect(left = 0, top = 0, right = 1080, bottom = 2340)
    val keyboard = IntRect(left = 0, top = 1400, right = 1080, bottom = 2340)

    test("fixed keyboard only covers from the keyboard top") {
        val reported = computeReportedImeInsets(
            rootBounds = root,
            windowBounds = keyboard,
            isFloating = false,
            fullscreenTouchable = false,
        )
        reported.contentTopInsets shouldBe 1400
        reported.visibleTopInsets shouldBe 1400
        reported.touchable shouldBe keyboard
    }

    test("unknown window bounds must not cover the host app") {
        val reported = computeReportedImeInsets(
            rootBounds = root,
            windowBounds = null,
            isFloating = false,
            fullscreenTouchable = false,
        )
        reported.contentTopInsets shouldBe 2340
        reported.visibleTopInsets shouldBe 2340
        reported.touchable shouldBe IntRect.Zero
    }

    test("floating keyboard does not resize the host app") {
        val reported = computeReportedImeInsets(
            rootBounds = root,
            windowBounds = keyboard,
            isFloating = true,
            fullscreenTouchable = false,
        )
        reported.contentTopInsets shouldBe 2340
        reported.visibleTopInsets shouldBe 2340
        reported.touchable shouldBe keyboard
    }

    test("fullscreen overlay keeps keyboard content top") {
        val reported = computeReportedImeInsets(
            rootBounds = root,
            windowBounds = keyboard,
            isFloating = false,
            fullscreenTouchable = true,
        )
        reported.contentTopInsets shouldBe 1400
        reported.touchable shouldBe root
    }
})
