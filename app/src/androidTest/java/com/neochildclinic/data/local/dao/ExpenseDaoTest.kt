package com.neochildclinic.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task section 18 coverage for: creating an expense, updating an expense, soft-deleting
 * an expense, offline expense creation, expense filtering, expense sorting - exercised
 * directly against ExpenseDao with a real in-memory Room database (no mocking library is
 * available in this project's test dependencies, so this uses the real DAO/DB rather
 * than a mock repository - see the "Testing" note in the final summary for what this
 * does and doesn't cover).
 */
@RunWith(AndroidJUnit4::class)
class ExpenseDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ExpenseDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.expenseDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sample(
        id: String,
        date: String,
        category: String = "RENT",
        paymentMethod: String = "CASH",
        amountPaise: Long = 100000L,
        title: String = "Sample expense",
        isDeleted: Boolean = false
    ) = ExpenseEntity(
        id = id,
        expenseDate = date,
        category = category,
        title = title,
        amountPaise = amountPaise,
        paymentMethod = paymentMethod,
        isDeleted = isDeleted,
        isSynced = false
    )

    @Test
    fun creatingAnExpense_isRetrievableById() = runBlocking {
        val expense = sample("e1", "2026-04-01")
        dao.insertExpense(expense)

        val fetched = dao.getExpenseById("e1")
        assertNotNull(fetched)
        assertEquals("Sample expense", fetched?.title)
        assertEquals(100000L, fetched?.amountPaise)
        // Offline expense creation (task section 10 step 1): inserted with isSynced =
        // false, exactly as ExpenseRepositoryImpl.addExpense does before the sync queue
        // entry is processed.
        assertFalse(fetched!!.isSynced)
    }

    @Test
    fun updatingAnExpense_replacesThePreviousRow() = runBlocking {
        dao.insertExpense(sample("e1", "2026-04-01", amountPaise = 100000L, title = "Original"))
        dao.insertExpense(sample("e1", "2026-04-01", amountPaise = 250000L, title = "Updated"))

        val fetched = dao.getExpenseById("e1")
        assertEquals("Updated", fetched?.title)
        assertEquals(250000L, fetched?.amountPaise)
        // REPLACE strategy - still exactly one row for this id, not a duplicate.
        assertEquals(1, dao.getAllExpensesSnapshot().size)
    }

    @Test
    fun softDeletingAnExpense_hidesItFromListsButKeepsTheRow() = runBlocking {
        val original = sample("e1", "2026-04-01")
        dao.insertExpense(original)
        assertEquals(1, dao.getAllExpenses().first().size)

        dao.insertExpense(original.copy(isDeleted = true))

        // Excluded from every "active" query...
        assertEquals(0, dao.getAllExpenses().first().size)
        assertEquals(0, dao.getExpenseCount().first())
        // ...but the row itself still exists (soft delete, not a physical DELETE) - task
        // section 5/20: "do not permanently delete synchronized records".
        val stillPresent = dao.getExpenseById("e1")
        assertNotNull(stillPresent)
        assertTrue(stillPresent!!.isDeleted)
    }

    @Test
    fun filtering_byCategoryPaymentMethodAndDateRange_matchesOnlyExpectedRows() = runBlocking {
        dao.insertExpense(sample("rent-1", "2026-04-05", category = "RENT", paymentMethod = "BANK"))
        dao.insertExpense(sample("elec-1", "2026-04-10", category = "ELECTRICITY", paymentMethod = "CASH"))
        dao.insertExpense(sample("rent-2", "2026-05-05", category = "RENT", paymentMethod = "CASH"))

        val rentOnly = dao.getFilteredExpensesPage(
            category = "RENT", paymentMethod = null, fromDate = null, toDate = null,
            query = null, sortBy = "DATE_DESC", limit = 50, offset = 0
        )
        assertEquals(setOf("rent-1", "rent-2"), rentOnly.map { it.id }.toSet())

        val bankOnly = dao.getFilteredExpensesPage(
            category = null, paymentMethod = "BANK", fromDate = null, toDate = null,
            query = null, sortBy = "DATE_DESC", limit = 50, offset = 0
        )
        assertEquals(listOf("rent-1"), bankOnly.map { it.id })

        val aprilOnly = dao.getFilteredExpensesPage(
            category = null, paymentMethod = null, fromDate = "2026-04-01", toDate = "2026-04-30",
            query = null, sortBy = "DATE_DESC", limit = 50, offset = 0
        )
        assertEquals(setOf("rent-1", "elec-1"), aprilOnly.map { it.id }.toSet())
    }

    @Test
    fun filtering_bySearchQuery_matchesTitleDescriptionOrReference() = runBlocking {
        dao.insertExpense(sample("e1", "2026-04-01", title = "Clinic Rent"))
        dao.insertExpense(sample("e2", "2026-04-02", title = "Electricity Bill"))

        val results = dao.getFilteredExpensesPage(
            category = null, paymentMethod = null, fromDate = null, toDate = null,
            query = "rent", sortBy = "DATE_DESC", limit = 50, offset = 0
        )
        assertEquals(listOf("e1"), results.map { it.id })
    }

    @Test
    fun sorting_byAmountAscendingAndDescending() = runBlocking {
        dao.insertExpense(sample("low", "2026-04-01", amountPaise = 5000L))
        dao.insertExpense(sample("mid", "2026-04-02", amountPaise = 15000L))
        dao.insertExpense(sample("high", "2026-04-03", amountPaise = 25000L))

        val ascending = dao.getFilteredExpensesPage(
            category = null, paymentMethod = null, fromDate = null, toDate = null,
            query = null, sortBy = "AMOUNT_ASC", limit = 50, offset = 0
        )
        assertEquals(listOf("low", "mid", "high"), ascending.map { it.id })

        val descending = dao.getFilteredExpensesPage(
            category = null, paymentMethod = null, fromDate = null, toDate = null,
            query = null, sortBy = "AMOUNT_DESC", limit = 50, offset = 0
        )
        assertEquals(listOf("high", "mid", "low"), descending.map { it.id })
    }

    @Test
    fun sorting_byDateAscendingAndDefaultDescending() = runBlocking {
        dao.insertExpense(sample("first", "2026-04-01"))
        dao.insertExpense(sample("second", "2026-04-15"))
        dao.insertExpense(sample("third", "2026-04-30"))

        val ascending = dao.getFilteredExpensesPage(
            category = null, paymentMethod = null, fromDate = null, toDate = null,
            query = null, sortBy = "DATE_ASC", limit = 50, offset = 0
        )
        assertEquals(listOf("first", "second", "third"), ascending.map { it.id })

        val defaultDescending = dao.getFilteredExpensesPage(
            category = null, paymentMethod = null, fromDate = null, toDate = null,
            query = null, sortBy = "DATE_DESC", limit = 50, offset = 0
        )
        assertEquals(listOf("third", "second", "first"), defaultDescending.map { it.id })
    }

    @Test
    fun pagination_limitAndOffsetWalkThroughAllRows() = runBlocking {
        (1..5).forEach { i -> dao.insertExpense(sample("e$i", "2026-04-0$i")) }

        val page1 = dao.getFilteredExpensesPage(
            category = null, paymentMethod = null, fromDate = null, toDate = null,
            query = null, sortBy = "DATE_ASC", limit = 2, offset = 0
        )
        val page2 = dao.getFilteredExpensesPage(
            category = null, paymentMethod = null, fromDate = null, toDate = null,
            query = null, sortBy = "DATE_ASC", limit = 2, offset = 2
        )
        assertEquals(listOf("e1", "e2"), page1.map { it.id })
        assertEquals(listOf("e3", "e4"), page2.map { it.id })
    }
}
