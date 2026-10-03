package org.stypox.dicio.skills.expenses

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

interface LedgerStorage {
    fun read(): ByteArray?
    /** Must replace the journal atomically or throw; never report success before durable save. */
    fun write(data: ByteArray)
}

data class ExpenseEntry(val id: Long, val category: String, val pesos: Long, val time: Long)
data class LedgerState(val entries: List<ExpenseEntry> = emptyList(), val lastCategory: String? = null, val nextId: Long = 1)

object LedgerCodec {
    fun encode(state: LedgerState): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeUTF(state.lastCategory.orEmpty())
            out.writeLong(state.nextId)
            out.writeInt(state.entries.size)
            state.entries.forEach { out.writeLong(it.id); out.writeUTF(it.category); out.writeLong(it.pesos); out.writeLong(it.time) }
        }
        return bytes.toByteArray()
    }
    fun decode(bytes: ByteArray): LedgerState {
        if (bytes.size > 20_000_000) throw IOException("Registro demasiado grande")
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            if (data.readInt() != 1) throw IOException("Versión de registro desconocida")
            val category = data.readUTF().takeIf { it.isNotEmpty() }
            val nextId = data.readLong()
            val count = data.readInt()
            if (count !in 0..100_000 || nextId < 1 || (category != null && !validCategory(category))) throw IOException("Registro dañado")
            val entries = List(count) {
                ExpenseEntry(data.readLong(), data.readUTF(), data.readLong(), data.readLong()).also {
                    if (it.id < 1 || it.id >= nextId || it.pesos !in 1..999_999_999_999L || !validCategory(it.category) || it.time < 0) throw IOException("Movimiento dañado")
                }
            }
            if (entries.map { it.id }.distinct().size != count || data.available() != 0) throw IOException("Registro dañado")
            return LedgerState(entries, category, nextId)
        }
    }
    private fun validCategory(category: String) = category.length in 1..48 && category.matches(Regex("[a-z]+(?: [a-z]+)*"))
}

class ExpenseLedger(
    private val storage: LedgerStorage,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private fun money(pesos: Long) = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")).format(pesos) + " pesos"
    private fun date(time: Long) = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    fun execute(command: ExpenseCommand): String = synchronized(ExpenseLedger::class.java) {
        val state = storage.read()?.let(LedgerCodec::decode) ?: LedgerState()
        when (command) {
            is ExpenseCommand.Invalid -> command.reason
            is ExpenseCommand.Open -> {
                if (command.category != null) storage.write(LedgerCodec.encode(state.copy(lastCategory = command.category)))
                "Registro de gastos listo${command.category?.let { " para $it" }.orEmpty()}. Di: en transporte agrega 3500; o: cuál es el total de transporte."
            }
            is ExpenseCommand.Add -> {
                val category = command.category ?: state.lastCategory
                if (category == null) return@synchronized "¿En qué categoría? Di, por ejemplo: en transporte agrega 3500."
                if (state.entries.size >= 100_000) return@synchronized "El registro está lleno. No guardé el gasto."
                require(command.pesos in 1..999_999_999_999L)
                val entry = ExpenseEntry(state.nextId, category, command.pesos, now())
                val updated = state.copy(entries = state.entries + entry, lastCategory = category, nextId = Math.addExact(state.nextId, 1))
                storage.write(LedgerCodec.encode(updated))
                val total = updated.entries.filter { it.category == category }.sumOf { it.pesos }
                "Guardé ${money(entry.pesos)} en $category. El total es ${money(total)}. Puedes decir: deshaz el último gasto."
            }
            is ExpenseCommand.Total -> {
                val category = if (command.all) null else command.category ?: state.lastCategory
                if (!command.all && category == null) return@synchronized "¿De qué categoría? Di: total de transporte, o: total general."
                val today = date(now())
                val entries = state.entries.filter {
                    (category == null || it.category == category) && when (command.period) {
                        ExpenseCommand.Period.ALL -> true
                        ExpenseCommand.Period.TODAY -> date(it.time).isEqual(today)
                        ExpenseCommand.Period.MONTH -> date(it.time).let { day -> day.year == today.year && day.month == today.month }
                    }
                }
                val period = when (command.period) { ExpenseCommand.Period.ALL -> ""; ExpenseCommand.Period.TODAY -> " de hoy"; ExpenseCommand.Period.MONTH -> " de este mes" }
                if (category != null && category != state.lastCategory) storage.write(LedgerCodec.encode(state.copy(lastCategory = category)))
                "El total${category?.let { " de $it" } ?: " general"}$period es ${money(entries.sumOf { it.pesos })}, en ${entries.size} movimientos."
            }
            is ExpenseCommand.History -> {
                val entries = state.entries.filter { command.category == null || it.category == command.category }
                if (entries.isEmpty()) "No hay gastos${command.category?.let { " en $it" }.orEmpty()}."
                else "Últimos ${minOf(entries.size, 5)} gastos${command.category?.let { " de $it" }.orEmpty()}:\n" +
                    entries.takeLast(5).asReversed().joinToString("\n") { "${date(it.time)} · ${it.category}: ${money(it.pesos)}" }
            }
            ExpenseCommand.Undo -> {
                val entry = state.entries.lastOrNull() ?: return@synchronized "No hay un gasto que deshacer."
                storage.write(LedgerCodec.encode(state.copy(entries = state.entries.dropLast(1), lastCategory = entry.category)))
                "Deshice el último gasto: ${money(entry.pesos)} en ${entry.category}."
            }
        }
    }
}
