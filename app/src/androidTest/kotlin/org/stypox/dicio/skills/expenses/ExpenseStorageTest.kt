package org.stypox.dicio.skills.expenses

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Run on an emulator or phone; these tests exercise Android AtomicFile, not a mock. */
class ExpenseStorageTest {
    private fun withStorage(test: (AtomicLedgerStorage) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "expense-test-${UUID.randomUUID()}.bin")
        try { test(AtomicLedgerStorage(file)) }
        finally { file.delete(); File(file.path + ".bak").delete(); File(file.path + ".new").delete() }
    }

    @Test fun addRestartQueryAndUndo() = withStorage { store ->
        ExpenseLedger(store).execute(ExpenseCommand.Add("transporte", 3500))
        ExpenseLedger(store).execute(ExpenseCommand.Add(null, 2000))
        assertTrue(ExpenseLedger(store).execute(ExpenseCommand.Total(null)).contains("5.500 pesos"))
        ExpenseLedger(store).execute(ExpenseCommand.Undo)
        assertTrue(ExpenseLedger(store).execute(ExpenseCommand.Total("transporte")).contains("3.500 pesos"))
    }

    @Test fun concurrentAddsAreNotLost() = withStorage { store ->
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..40).map { pool.submit { ExpenseLedger(store).execute(ExpenseCommand.Add("transporte", 10)) } }
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
            val persisted = LedgerCodec.decode(requireNotNull(store.read()))
            assertEquals(40, persisted.entries.size)
            assertEquals(400L, persisted.entries.sumOf { it.pesos })
        } finally { pool.shutdownNow() }
    }
}
