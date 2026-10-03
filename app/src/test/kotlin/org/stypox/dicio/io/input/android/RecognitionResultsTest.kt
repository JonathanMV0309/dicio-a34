package org.stypox.dicio.io.input.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RecognitionResultsTest : StringSpec({
    "valid alternatives are ranked by confidence" {
        RecognitionResults.prepare(listOf("abre fotos", "abre notas"), floatArrayOf(0.2f, 0.9f))
            .first().first shouldBe "abre notas"
    }
    "unknown confidence preserves provider ranking" {
        RecognitionResults.prepare(listOf("abre notas", "abre fotos"), floatArrayOf(-1f, -1f))
            .map { it.first } shouldBe listOf("abre notas", "abre fotos")
    }
    "missing scores and duplicate or blank results are handled" {
        RecognitionResults.prepare(listOf("  abre notas ", "", "abre notas"), null)
            .map { it.first } shouldBe listOf("abre notas")
    }
    "mismatched scores do not crash" {
        RecognitionResults.prepare(listOf("a", "b"), floatArrayOf(0.9f)).map { it.first } shouldBe listOf("a", "b")
    }
    "empty results are empty" { RecognitionResults.prepare(emptyList(), null) shouldBe emptyList() }
})
