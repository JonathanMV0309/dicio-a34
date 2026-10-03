import org.stypox.dicio.skills.expenses.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

fun main() {
    var passed = 0
    fun test(name: String, body: () -> Unit) { body(); passed++; println("PASS $name") }
    fun parse(text: String) = requireNotNull(ExpenseParser.parse(text))
    test("user combined request") { check(parse("abre nota y en transporte agrega 3500") == ExpenseCommand.Add("transporte", 3500)) }
    test("category-first request") { check(parse("en transporte agrega 3500") == ExpenseCommand.Add("transporte", 3500)) }
    test("amount-first request") { check(parse("agrega 3500 en transporte") == ExpenseCommand.Add("transporte", 3500)) }
    test("spoken Spanish pesos") { check(parse("en transporte añade tres mil quinientos pesos") == ExpenseCommand.Add("transporte", 3500)) }
    test("contextual amount") { check(parse("agrega dos mil") == ExpenseCommand.Add(null, 2000)) }
    test("contextual total") { check(parse("¿Cuál es el total de eso?") == ExpenseCommand.Total(null)) }
    test("general total") { check(parse("cuál es el total general") == ExpenseCommand.Total(null, true)) }
    test("today total") { check(parse("total de transporte hoy") == ExpenseCommand.Total("transporte", false, ExpenseCommand.Period.TODAY)) }
    test("month total") { check(parse("total de transporte de este mes") == ExpenseCommand.Total("transporte", false, ExpenseCommand.Period.MONTH)) }
    test("open a category") { check(parse("abre nota de transporte") == ExpenseCommand.Open("transporte")) }
    test("does not hijack app opening") { check(ExpenseParser.parse("abre Samsung Notes") == null); check(ExpenseParser.parse("abre notas") == null) }
    test("Spanish hundreds and tens") { check(SpanishPesos.parse("novecientos treinta y dos") == 932L) }
    test("Spanish millions") { check(SpanishPesos.parse("dos millones trescientos mil quinientos") == 2_300_500L) }
    test("COP thousands separators") { check(SpanishPesos.parse("3.500") == 3500L); check(SpanishPesos.parse("3,500") == 3500L) }
    test("no rounding decimals") { check(SpanishPesos.parse("3,50") == null); check(SpanishPesos.parse("3.5") == null) }
    test("reject negative zero and overflow") { for (s in listOf("-3500", "0", "cero", "999999999999999999999")) check(SpanishPesos.parse(s) == null) }
    test("reject malformed spoken amounts") { check(SpanishPesos.parse("tres tres") == null); check(SpanishPesos.parse("mil mil") == null) }
    test("invalid expense is recognized without inventing amount") { check(parse("en transporte agrega algo") is ExpenseCommand.Invalid) }
    test("punctuation and accents") { check(parse("¡En TRANSPORTE añade 3500 pesos!") == ExpenseCommand.Add("transporte", 3500)) }
    val dir = Files.createTempDirectory("expense-tests").toFile()
    val file = File(dir, "ledger.bin")
    val storage = object : LedgerStorage {
        override fun read(): ByteArray? = if (file.exists()) file.readBytes() else null
        override fun write(data: ByteArray) { file.writeBytes(data) }
    }
    var clock = Instant.parse("2026-10-03T15:00:00Z").toEpochMilli()
    fun ledger() = ExpenseLedger(storage, { clock }, ZoneId.of("America/Bogota"))
    try {
        test("open, add, restart, query user scenario") {
            ledger().execute(parse("abre nota"))
            check(ledger().execute(parse("en transporte agrega 3500")).contains("3.500 pesos"))
            check(ledger().execute(parse("cual es el total de eso")).contains("3.500 pesos"))
        }
        test("add in remembered category and exact total") {
            ledger().execute(parse("agrega 2000"))
            check(ledger().execute(parse("total de transporte")).contains("5.500 pesos"))
        }
        test("independent category and general total") {
            ledger().execute(parse("en comida agrega 10000"))
            check(ledger().execute(parse("total de transporte")).contains("5.500 pesos"))
            check(ledger().execute(parse("total general")).contains("15.500 pesos"))
        }
        test("history survives reconstruction") { check(ledger().execute(parse("muestra los gastos de transporte")).contains("3.500 pesos")) }
        test("undo last addition persists") {
            check(ledger().execute(parse("deshaz el ultimo gasto")).contains("10.000 pesos"))
            check(ledger().execute(parse("total general")).contains("5.500 pesos"))
        }
        test("invalid amount never changes journal") {
            val previous = file.readBytes()
            ledger().execute(parse("en transporte agrega -3500"))
            check(file.readBytes().contentEquals(previous))
        }
        test("today and month use local date") {
            clock = Instant.parse("2026-11-01T02:00:00Z").toEpochMilli() // Oct 31 in Bogota
            ledger().execute(parse("en transporte agrega 1000"))
            check(ledger().execute(parse("total de transporte hoy")).contains("1.000 pesos"))
            check(ledger().execute(parse("total de transporte este mes")).contains("6.500 pesos"))
            clock = Instant.parse("2026-11-01T15:00:00Z").toEpochMilli()
            check(ledger().execute(parse("total de transporte este mes")).contains("0 pesos"))
        }
        test("no missing category is invented") {
            val empty = object : LedgerStorage { override fun read(): ByteArray? = null; override fun write(data: ByteArray) { error("Must not write") } }
            check(ExpenseLedger(empty).execute(parse("agrega 3500")).contains("categoría"))
            check(ExpenseLedger(empty).execute(parse("cual es el total de eso")).contains("categoría"))
        }
        test("storage failure cannot report saved") {
            val broken = object : LedgerStorage { override fun read(): ByteArray? = null; override fun write(data: ByteArray) { throw IOException("Disk full") } }
            var threw = false
            try { ExpenseLedger(broken).execute(parse("en transporte agrega 3500")) } catch (e: IOException) { threw = true }
            check(threw)
        }
        test("corrupt journal is never overwritten") {
            file.writeBytes(byteArrayOf(1, 2, 3))
            var threw = false
            try { ledger().execute(parse("en transporte agrega 3500")) } catch (e: IOException) { threw = true }
            check(threw); check(file.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        }
        test("unknown file version rejected") {
            val bytes = LedgerCodec.encode(LedgerState()); bytes[3] = 9
            var threw = false; try { LedgerCodec.decode(bytes) } catch (e: IOException) { threw = true }; check(threw)
        }
        test("undo on empty journal is harmless") {
            val empty = object : LedgerStorage { override fun read(): ByteArray? = null; override fun write(data: ByteArray) { error("Must not write") } }
            check(ExpenseLedger(empty).execute(ExpenseCommand.Undo).contains("No hay"))
        }
    } finally { dir.deleteRecursively() }
    println("$passed expense checks passed")
}
