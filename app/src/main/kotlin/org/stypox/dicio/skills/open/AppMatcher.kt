package org.stypox.dicio.skills.open

import java.text.Normalizer
import java.util.Locale

/** Conservative matching: a similar spelling alone must not launch another app. */
object AppMatcher {
    data class App(val packageName: String, val label: String)
    private val aliases = mapOf(
        "notas" to setOf("com.samsung.android.app.notes", "com.google.android.keep"),
        "notes" to setOf("com.samsung.android.app.notes", "com.google.android.keep"),
        "samsung notes" to setOf("com.samsung.android.app.notes"),
        "keep" to setOf("com.google.android.keep"),
        "fotos" to setOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
        "galeria" to setOf("com.sec.android.gallery3d"),
        "camara" to setOf("com.sec.android.app.camera", "com.google.android.GoogleCamera"),
        "calculadora" to setOf("com.sec.android.app.popupcalculator", "com.google.android.calculator"),
        "reloj" to setOf("com.sec.android.app.clockpackage", "com.google.android.deskclock"),
        "whatsapp" to setOf("com.whatsapp"),
    )

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Multiple results require an explicit choice. No results mean no launch. */
    fun candidates(query: String, apps: List<App>): List<App> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        val unique = apps.distinctBy { it.packageName }
        val knownPackages = aliases[q].orEmpty()
        val exact = unique.filter { normalize(it.label) == q || it.packageName in knownPackages }
        if (exact.isNotEmpty()) return exact.sortedBy { it.label }
        // Only whole words, never edit-distance guessing (e.g. notas -> fotos).
        return unique.filter { q.split(' ').all { word -> word in normalize(it.label).split(' ') } }
            .sortedBy { it.label }
    }
}
