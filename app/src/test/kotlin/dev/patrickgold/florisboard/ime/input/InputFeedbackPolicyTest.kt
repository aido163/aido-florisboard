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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InputFeedbackPolicyTest : FunSpec({
    test("host disabled wins over stale JetPref enabled") {
        InputFeedbackPolicy.isEnabled(hostOverride = false, prefEnabled = true) shouldBe false
    }

    test("host enabled wins over JetPref disabled") {
        InputFeedbackPolicy.isEnabled(hostOverride = true, prefEnabled = false) shouldBe true
    }

    test("null host defers to JetPref") {
        InputFeedbackPolicy.isEnabled(hostOverride = null, prefEnabled = true) shouldBe true
        InputFeedbackPolicy.isEnabled(hostOverride = null, prefEnabled = false) shouldBe false
    }
})
