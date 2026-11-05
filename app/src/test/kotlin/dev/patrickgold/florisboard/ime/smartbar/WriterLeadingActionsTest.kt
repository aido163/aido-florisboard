package dev.patrickgold.florisboard.ime.smartbar

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WriterLeadingActionsTest : FunSpec({
    test("empty field keeps suggest only") {
        writerLeadingActions(hasDraft = false).map { it.data.code } shouldBe listOf(KeyCode.SUGGEST)
    }

    test("draft shows grammar rewrite and translate") {
        writerLeadingActions(hasDraft = true).map { it.data.code } shouldBe listOf(
            KeyCode.SUGGEST,
            KeyCode.GRAMMAR,
            KeyCode.REWRITE,
            KeyCode.TRANSLATE,
        )
    }

    test("suggest is hidden when accessibility is off") {
        writerLeadingActions(hasDraft = false, suggestVisible = false).map { it.data.code } shouldBe emptyList()
        writerLeadingActions(hasDraft = true, suggestVisible = false).map { it.data.code } shouldBe listOf(
            KeyCode.GRAMMAR,
            KeyCode.REWRITE,
            KeyCode.TRANSLATE,
        )
    }
})
