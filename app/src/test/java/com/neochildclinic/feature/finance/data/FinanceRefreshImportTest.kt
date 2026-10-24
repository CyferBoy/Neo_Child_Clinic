package com.neochildclinic.feature.finance.data

import com.neochildclinic.data.local.entity.FinanceEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for NCC-044: refreshTransactions re-imported a locally deleted finance row
 * because the `local == null` case skipped the pending-queue guard, so income deleted just
 * before a pull reappeared in Statistics > Finance and Monthly Finance Details.
 */
class FinanceRefreshImportTest {

    private fun row(id: String, isSynced: Boolean) = FinanceEntity(
        id = id,
        type = "INCOME",
        category = "VACCINATION",
        amount = 500.0,
        paymentMethod = "CASH",
        recordedBy = "nurse",
        isSynced = isSynced
    )

    @Test
    fun `row pending a remote DELETE is not re-imported after the local delete`() {
        // The exact defect: local copy gone (hard-deleted) while its DELETE upload is in flight.
        assertFalse(financeImportAllowed(local = null, remoteId = "tx1", pendingIds = setOf("tx1")))
    }

    @Test
    fun `genuinely new remote row with no queue entry is imported`() {
        assertTrue(financeImportAllowed(local = null, remoteId = "tx1", pendingIds = emptySet()))
    }

    @Test
    fun `locally edited row is not overwritten by a pull`() {
        assertFalse(financeImportAllowed(local = row("tx1", isSynced = false), remoteId = "tx1", pendingIds = emptySet()))
    }

    @Test
    fun `clean synced row accepts the remote copy`() {
        assertTrue(financeImportAllowed(local = row("tx1", isSynced = true), remoteId = "tx1", pendingIds = emptySet()))
    }

    @Test
    fun `clean synced row with a pending entry elsewhere stays guarded`() {
        assertFalse(financeImportAllowed(local = row("tx1", isSynced = true), remoteId = "tx1", pendingIds = setOf("tx1")))
    }
}
