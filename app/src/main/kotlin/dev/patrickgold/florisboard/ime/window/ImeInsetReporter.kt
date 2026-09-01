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

/**
 * Insets reported back to [android.inputmethodservice.InputMethodService.onComputeInsets].
 *
 * [contentTopInsets] / [visibleTopInsets] are distances from the top of the IME window. A value of
 * `0` means the IME covers the entire screen; using that while the keyboard is only at the bottom
 * makes the host app collapse and shows the IME window chrome (often an opaque black panel) in
 * the remaining space.
 */
data class ReportedImeInsets(
    val contentTopInsets: Int,
    val visibleTopInsets: Int,
    val touchable: IntRect,
)

/**
 * Computes IME content/visible/touchable insets from laid-out window bounds.
 *
 * If [windowBounds] is unknown, the host app is not covered: claiming `0` would make the IME
 * look like a full-screen black overlay until the first layout pass.
 */
fun computeReportedImeInsets(
    rootBounds: IntRect,
    windowBounds: IntRect?,
    isFloating: Boolean,
    fullscreenTouchable: Boolean,
): ReportedImeInsets {
    val coverFromTop = when {
        windowBounds == null || isFloating -> rootBounds.bottom
        else -> windowBounds.top
    }
    val touchable = when {
        fullscreenTouchable -> rootBounds
        windowBounds != null -> windowBounds
        else -> IntRect.Zero
    }
    return ReportedImeInsets(
        contentTopInsets = coverFromTop,
        visibleTopInsets = coverFromTop,
        touchable = touchable,
    )
}
