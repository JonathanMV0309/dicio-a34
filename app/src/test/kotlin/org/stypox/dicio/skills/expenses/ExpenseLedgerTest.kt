package org.stypox.dicio.skills.expenses

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.IOException

private class MemoryLedger : LedgerStorage {
    var bytes: ByteArray? = null
    override fun read() = bytes?.copyOf()
    override fun write(data: ByteArray) { bytes = data.copyOf() }
}

class ExpenseLedgerTest : StringSpec({
    "combined request parses category and exact pesos" {
        ExpenseParser.parse("abre nota y en transporte agrega 3500") shouldBe ExpenseCommand.Add("transporte", 3500)
    }
    "spoken amount and accents" {
        ExpenseParser.parse("en transporte añade tres mil quinientos pesos") shouldBe ExpenseCommand.Add("transporte", 3500)
    }
    "restart preserves category and total" {
        val store = MemoryLedger()
        ExpenseLedger(store).execute(ExpenseCommand.Add("transporte", 3500))
        ExpenseLedger(store).execute(requireNotNull(ExpenseParser.parse("agrega 2000")))
        ExpenseLedger(store).execute(requireNotNull(ExpenseParser.parse("cual es el total de eso")))
            .contains("5.500 pesos") shouldBe true
    }
    "categories do not share totals" {
        val store = MemoryLedger()
        ExpenseLedger(store).execute(ExpenseCommand.Add("transporte", 3500))
        ExpenseLedger(store).execute(ExpenseCommand.Add("comida", 2000))
        ExpenseLedger(store).execute(ExpenseCommand.Total("transporte")).contains("3.500 pesos") shouldBe true
        ExpenseLedger(store).execute(ExpenseCommand.Total(null, true)).contains("5.500 pesos") shouldBe true
    }
    "undo removes only the latest entry" {
        val store = MemoryLedger()
        val ledger = ExpenseLedger(store)
        ledger.execute(ExpenseCommand.Add("transporte", 3500))
        ledger.execute(ExpenseCommand.Add("comida", 2000))
        ledger.execute(ExpenseCommand.Undo)
        ledger.execute(ExpenseCommand.Total(null, true)).contains("3.500 pesos") shouldBe true
    }
    "ambiguous decimal is rejected without a write" {
        val store = MemoryLedger()
        ExpenseLedger(store).execute(requireNotNull(ExpenseParser.parse("en transporte agrega 3,50")))
        store.bytes shouldBe null
    }
    "unknown category is not guessed" {
        val store = MemoryLedger()
        ExpenseLedger(store).execute(ExpenseCommand.Add(null, 3500)).contains("categoría") shouldBe true
        store.bytes shouldBe null
    }
    "failed storage cannot confirm saved" {
        val store = object : LedgerStorage {
            override fun read(): ByteArray? = null
            override fun write(data: ByteArray) { throw IOException("Full disk") }
        }
        var threw = false
        try { ExpenseLedger(store).execute(ExpenseCommand.Add("transporte", 3500)) } catch (e: IOException) { threw = true }
        threw shouldBe true
    }
    "corrupt bytes are preserved" {
        val store = MemoryLedger().apply { bytes = byteArrayOf(1, 2, 3) }
        var threw = false
        try { ExpenseLedger(store).execute(ExpenseCommand.Add("transporte", 3500)) } catch (e: IOException) { threw = true }
        threw shouldBe true
        store.bytes!!.toList() shouldBe listOf<Byte>(1, 2, 3)
    }
    "ordinary application opening stays with OpenSkill" {
        ExpenseParser.parse("abre Samsung Notes") shouldBe null
        ExpenseParser.parse("abre notas") shouldBe null
    }
})
