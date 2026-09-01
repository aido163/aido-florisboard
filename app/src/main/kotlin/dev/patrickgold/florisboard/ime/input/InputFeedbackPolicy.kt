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

package dev.patrickgold.florisboard.ime.input

/**
 * Host apps can override FlorisBoard JetPref for audio/haptic feedback.
 * `null` means "use JetPref only" (stock FlorisBoard).
 *
 * A host `false` must win over a stale JetPref `true`: the RN shell writes
 * `keyboard_shared_config` in another process, and the JetPref copy can lag.
 */
object InputFeedbackPolicy {
    fun isEnabled(hostOverride: Boolean?, prefEnabled: Boolean): Boolean =
        hostOverride ?: prefEnabled
}
