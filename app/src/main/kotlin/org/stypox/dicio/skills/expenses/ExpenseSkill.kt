package org.stypox.dicio.skills.expenses

import android.content.Context
import android.util.AtomicFile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.FloatScore
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class AtomicLedgerStorage(file: File) : LedgerStorage {
    private val atomic = AtomicFile(file)
    override fun read(): ByteArray? = try { atomic.readFully() } catch (e: FileNotFoundException) {
        if (atomic.baseFile.exists() || File(atomic.baseFile.path + ".bak").exists()) throw e
        null
    }
    override fun write(data: ByteArray) {
        val stream = atomic.startWrite()
        try {
            stream.write(data)
            stream.flush()
            stream.fd.sync()
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
        atomic.finishWrite(stream)
        if (!atomic.readFully().contentEquals(data)) throw IOException("No se confirmó la escritura del registro")
    }
}

object ExpenseInfo : SkillInfo("expenses") {
    override fun name(context: Context) = "Registro de gastos"
    override fun sentenceExample(context: Context) = "En transporte agrega 3500"
    @Composable override fun icon() = rememberVectorPainter(Icons.Default.Calculate)
    override fun build(ctx: SkillContext): Skill<*>? =
        if (ctx.locale.language == "es") ExpenseSkill(this) else null
}

class ExpenseSkill(info: SkillInfo) : Skill<ExpenseCommand?>(info, Specificity.HIGH) {
    override fun score(ctx: SkillContext, input: String): Pair<FloatScore, ExpenseCommand?> {
        val command = ExpenseParser.parse(input)
        return FloatScore(if (command != null) 1f else 0f) to command
    }
    override suspend fun generateOutput(ctx: SkillContext, inputData: ExpenseCommand?): SkillOutput {
        var succeeded = true
        val text = try {
            ExpenseLedger(AtomicLedgerStorage(File(ctx.android.filesDir, "expense-ledger.bin")))
                .execute(requireNotNull(inputData))
        } catch (e: IOException) {
            succeeded = false
            "No pude leer o guardar el registro. No confirmaré ningún cambio; tus datos existentes se conservan."
        }
        return ExpenseOutput(text, succeeded && (inputData is ExpenseCommand.Open ||
            (inputData is ExpenseCommand.Add && text.startsWith("Guardé"))))
    }
}

class ExpenseOutput(private val message: String, private val followUp: Boolean = false) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext) = message
    override fun getInteractionPlan(ctx: SkillContext) =
        if (followUp) InteractionPlan.Continue(reopenMicrophone = true) else InteractionPlan.FinishInteraction
}
