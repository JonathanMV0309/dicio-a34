package org.stypox.dicio.skills.open

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AppMatcherTest : StringSpec({
    val notes = AppMatcher.App("com.samsung.android.app.notes", "Samsung Notes")
    val photos = AppMatcher.App("com.google.android.apps.photos", "Fotos")
    val keep = AppMatcher.App("com.google.android.keep", "Google Keep")
    "notas opens Samsung Notes, never Fotos" {
        AppMatcher.candidates("notas", listOf(photos, notes)) shouldBe listOf(notes)
    }
    "missing notes never guesses photos" {
        AppMatcher.candidates("notas", listOf(photos)) shouldBe emptyList()
    }
    "multiple notes require selection" {
        AppMatcher.candidates("notas", listOf(notes, keep, photos)).toSet() shouldBe setOf(notes, keep)
    }
    "full name disambiguates" {
        AppMatcher.candidates("Samsung Notes", listOf(notes, keep)) shouldBe listOf(notes)
    }
    "accents and case are normalized" {
        AppMatcher.candidates("  GALERÍA! ", listOf(AppMatcher.App("com.sec.android.gallery3d", "Gallery")))
            .map { it.packageName } shouldBe listOf("com.sec.android.gallery3d")
    }
    "blank input does not open anything" {
        AppMatcher.candidates("  ", listOf(notes, photos)) shouldBe emptyList()
    }
    "launcher duplicates do not cause ambiguity" {
        AppMatcher.candidates("notas", listOf(notes, notes)) shouldBe listOf(notes)
    }
    "substring is not a whole word" {
        AppMatcher.candidates("foto", listOf(photos)) shouldBe emptyList()
    }
})
