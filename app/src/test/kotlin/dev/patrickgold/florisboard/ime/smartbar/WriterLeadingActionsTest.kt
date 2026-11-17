package dev.patrickgold.florisboard.ime.smartbar

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WriterLeadingActionsTest : FunSpec({
    test("tool row matches the keyboard preview") {
        writerLeadingActions().map { it.data.code } shouldBe listOf(
            KeyCode.GRAMMAR,
            KeyCode.REWRITE,
            KeyCode.TRANSLATE,
            KeyCode.SUGGEST,
        )
    }

    test("suggest is hidden when accessibility is off") {
        writerLeadingActions(suggestVisible = false).map { it.data.code } shouldBe listOf(
            KeyCode.GRAMMAR,
            KeyCode.REWRITE,
            KeyCode.TRANSLATE,
        )
    }
})
