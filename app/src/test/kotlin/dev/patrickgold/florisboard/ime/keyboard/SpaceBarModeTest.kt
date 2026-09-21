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

package dev.patrickgold.florisboard.ime.keyboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SpaceBarModeTest : FunSpec({
    test("space bar shows AIDO instead of the language name") {
        spaceBarDisplayLabel(SpaceBarMode.CURRENT_LANGUAGE) shouldBe "AIDO"
        spaceBarDisplayLabel(SpaceBarMode.SPACE_BAR_KEY) shouldBe "AIDO"
        spaceBarDisplayLabel(SpaceBarMode.NOTHING) shouldBe null
    }
})
