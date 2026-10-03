package org.stypox.dicio.skills.expenses

import java.text.Normalizer
import java.util.Locale

sealed interface ExpenseCommand {
    data class Open(val category: String? = null) : ExpenseCommand
    data class Add(val category: String?, val pesos: Long) : ExpenseCommand
    data class Total(val category: String?, val all: Boolean = false, val period: Period = Period.ALL) : ExpenseCommand
    data class History(val category: String?) : ExpenseCommand
    data object Undo : ExpenseCommand
    data class Invalid(val reason: String) : ExpenseCommand
    enum class Period { ALL, TODAY, MONTH }
}

object ExpenseParser {
    fun normalize(input: String): String = Normalizer.normalize(input, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[¿?¡!]"), "").trim().replace(Regex("\\s+"), " ")

    fun parse(input: String): ExpenseCommand? {
        var text = normalize(input).removePrefix("hey dicio ").removeSuffix(" por favor").trim()
        text = text.replace(Regex("^(?:abre|abrir) (?:mi )?nota(?: de gastos)? (?:y |y luego )"), "")
        if (text in listOf("abre nota", "abre mi nota", "abre mi registro", "abre registro de gastos", "abre nota de gastos")) return ExpenseCommand.Open()
        Regex("^(?:abre nota|abre la nota|abre mi nota) de (.+)$").matchEntire(text)?.let {
            return category(it.groupValues[1])?.let(ExpenseCommand::Open) ?: ExpenseCommand.Invalid("La categoría no es válida.")
        }
        if (text in listOf("deshaz el ultimo gasto", "deshacer ultimo gasto", "deshaz el ultimo registro", "deshacer ultimo registro")) return ExpenseCommand.Undo
        val add = Regex("^(?:en )?(.+?) (?:agrega|agregar|anade|suma|registra) (.+)$").matchEntire(text)
        val leading = Regex("^(?:agrega|agregar|anade|suma|registra) (.+?) (?:en|a) (.+)$").matchEntire(text)
        val contextual = Regex("^(?:agrega|agregar|anade|suma|registra) (.+)$").matchEntire(text)
        if (add != null || leading != null || contextual != null) {
            val rawCategory = add?.groupValues?.get(1) ?: leading?.groupValues?.get(2)
            val rawAmount = add?.groupValues?.get(2) ?: leading?.groupValues?.get(1) ?: contextual!!.groupValues[1]
            val cat = rawCategory?.let(::category)
            if (rawCategory != null && cat == null) return ExpenseCommand.Invalid("La categoría no es válida.")
            val amount = SpanishPesos.parse(rawAmount)
                ?: return ExpenseCommand.Invalid("No entendí el importe. Usa pesos enteros, por ejemplo tres mil quinientos o 3500.")
            return ExpenseCommand.Add(cat, amount)
        }
        var period = ExpenseCommand.Period.ALL
        if (text.endsWith(" de hoy") || text.endsWith(" hoy")) {
            period = ExpenseCommand.Period.TODAY
            text = text.removeSuffix(" de hoy").removeSuffix(" hoy")
        } else if (text.endsWith(" de este mes") || text.endsWith(" este mes")) {
            period = ExpenseCommand.Period.MONTH
            text = text.removeSuffix(" de este mes").removeSuffix(" este mes")
        }
        if (text in listOf("total general", "dime el total general", "cual es el total general", "cuanto he gastado en total", "total de todos los gastos")) return ExpenseCommand.Total(null, true, period)
        if (text in listOf("cual es el total", "cual es el total de eso", "cuanto llevo", "cuanto llevo en eso", "dime el total", "total de eso")) return ExpenseCommand.Total(null, false, period)
        Regex("^(?:cual es el total de|dime el total de|total de|cuanto llevo en|cuanto he gastado en) (.+)$").matchEntire(text)?.let {
            return category(it.groupValues[1])?.let { cat -> ExpenseCommand.Total(cat, false, period) }
        }
        Regex("^(?:muestra|dime|lee) (?:los )?(?:gastos|movimientos) de (.+)$").matchEntire(text)?.let {
            return category(it.groupValues[1])?.let(ExpenseCommand::History)
        }
        if (text in listOf("muestra mis gastos", "muestra los gastos", "lee mis gastos")) return ExpenseCommand.History(null)
        return null
    }

    private fun category(raw: String): String? = normalize(raw).removePrefix("la categoria ")
        .takeIf { it.length in 1..48 && it.matches(Regex("[a-z]+(?: [a-z]+)*")) }
}

/** COP amounts are whole pesos; ambiguous decimals are rejected rather than rounded. */
object SpanishPesos {
    private val small = listOf("cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco", "veintiseis", "veintisiete", "veintiocho", "veintinueve")
        .withIndex().associate { it.value to it.index.toLong() } + mapOf("un" to 1L, "una" to 1L)
    private val tens = mapOf("treinta" to 30L, "cuarenta" to 40L, "cincuenta" to 50L, "sesenta" to 60L, "setenta" to 70L, "ochenta" to 80L, "noventa" to 90L)
    private val hundreds = mapOf("ciento" to 100L, "doscientos" to 200L, "trescientos" to 300L, "cuatrocientos" to 400L, "quinientos" to 500L, "seiscientos" to 600L, "setecientos" to 700L, "ochocientos" to 800L, "novecientos" to 900L)
    fun parse(input: String): Long? {
        val text = ExpenseParser.normalize(input).removeSuffix(" pesos").removePrefix("$").trim()
        if (text.matches(Regex("[0-9]+|[0-9]{1,3}(?:[.,][0-9]{3})+")) && !(text.contains('.') && text.contains(','))) {
            return text.replace(".", "").replace(",", "").toLongOrNull()?.takeIf { it in 1..999_999_999_999L }
        }
        if (!text.matches(Regex("[a-z]+(?: [a-z]+)*"))) return null
        return words(text.split(' '))?.takeIf { it in 1..999_999_999_999L }
    }
    private fun words(w: List<String>): Long? {
        val million = w.indexOfFirst { it == "millon" || it == "millones" }
        if (million >= 0) {
            val multiplier = belowThousand(w.take(million)) ?: return null
            if (multiplier == 0L) return null
            val tail = if (million == w.lastIndex) 0L else belowMillion(w.drop(million + 1)) ?: return null
            return multiplier * 1_000_000L + tail
        }
        return belowMillion(w)
    }
    private fun belowMillion(w: List<String>): Long? {
        val at = w.indexOf("mil")
        if (at < 0) return belowThousand(w)
        val multiplier = if (at == 0) 1L else belowThousand(w.take(at)) ?: return null
        if (multiplier == 0L) return null
        val rest = if (at == w.lastIndex) 0L else belowThousand(w.drop(at + 1)) ?: return null
        return multiplier * 1000 + rest
    }
    private fun belowThousand(w: List<String>): Long? {
        if (w.isEmpty()) return null
        if (w == listOf("cien")) return 100L
        val h = hundreds[w[0]]
        if (h != null) return if (w.size == 1) h else belowHundred(w.drop(1))?.takeIf { it > 0 }?.let { h + it }
        return belowHundred(w)
    }
    private fun belowHundred(w: List<String>): Long? {
        if (w.size == 1) return small[w[0]] ?: tens[w[0]]
        if (w.size == 3 && w[1] == "y") return tens[w[0]]?.let { t -> small[w[2]]?.takeIf { it in 1..9 }?.let { t + it } }
        return null
    }
}
