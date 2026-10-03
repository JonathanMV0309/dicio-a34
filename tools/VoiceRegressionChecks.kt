import org.stypox.dicio.skills.open.AppMatcher
import org.stypox.dicio.io.input.android.RecognitionResults

fun main() {
    var passed = 0
    fun test(name: String, body: () -> Unit) { body(); passed++; println("PASS $name") }
    val notes = AppMatcher.App("com.samsung.android.app.notes", "Samsung Notes")
    val photos = AppMatcher.App("com.google.android.apps.photos", "Fotos")
    val keep = AppMatcher.App("com.google.android.keep", "Google Keep")
    test("notas selects Samsung Notes") { check(AppMatcher.candidates("notas", listOf(photos, notes)) == listOf(notes)) }
    test("notas never guesses Fotos") { check(AppMatcher.candidates("notas", listOf(photos)).isEmpty()) }
    test("multiple notes require choice") { check(AppMatcher.candidates("notas", listOf(notes, keep, photos)).toSet() == setOf(notes, keep)) }
    test("full name disambiguates") { check(AppMatcher.candidates("Samsung Notes", listOf(notes, keep)) == listOf(notes)) }
    test("accent and case normalization") { check(AppMatcher.candidates(" GALERÍA! ", listOf(AppMatcher.App("com.sec.android.gallery3d", "Gallery"))).size == 1) }
    test("blank request launches nothing") { check(AppMatcher.candidates(" ", listOf(notes)).isEmpty()) }
    test("duplicate launcher entries") { check(AppMatcher.candidates("notas", listOf(notes, notes)) == listOf(notes)) }
    test("no partial-word guessing") { check(AppMatcher.candidates("foto", listOf(photos)).isEmpty()) }
    test("rank alternatives by valid confidence") { check(RecognitionResults.prepare(listOf("abre fotos", "abre notas"), floatArrayOf(0.2f, 0.9f)).first().first == "abre notas") }
    test("unknown confidence retains provider order") { check(RecognitionResults.prepare(listOf("abre notas", "abre fotos"), floatArrayOf(-1f, -1f)).map { it.first } == listOf("abre notas", "abre fotos")) }
    test("remove blanks and duplicates") { check(RecognitionResults.prepare(listOf(" abre notas ", "", "abre notas"), null).map { it.first } == listOf("abre notas")) }
    test("mismatched scores do not crash") { check(RecognitionResults.prepare(listOf("a", "b"), floatArrayOf(0.9f)).map { it.first } == listOf("a", "b")) }
    test("empty recognition") { check(RecognitionResults.prepare(emptyList(), null).isEmpty()) }
    println("$passed checks passed")
}
