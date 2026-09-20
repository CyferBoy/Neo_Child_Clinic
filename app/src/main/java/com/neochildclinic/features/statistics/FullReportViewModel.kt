package com.neochildclinic.features.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.usecase.patient.GetPatientsUseCase
import com.neochildclinic.domain.usecase.vaccination.GetVaccinationsUseCase
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.ExpenseRepository
import com.neochildclinic.domain.usecase.sync.RefreshDataUseCase
import com.neochildclinic.core.utils.PatientUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class FullReportDataPoint(
    val label: String,
    val patients: Float = 0f,
    val consultations: Float = 0f,
    val vaccinations: Float = 0f,
    val revenue: Float = 0f,
    val online: Float = 0f,
    val cash: Float = 0f,
    val netProfit: Float = 0f,
    val cogs: Float = 0f,
    val expenses: Float = 0f
)

data class FullReportUiState(
    val dataPoints: List<FullReportDataPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isDaily: Boolean = false,
    val periodLabel: String = ""
)

@HiltViewModel
class FullReportViewModel @Inject constructor(
    getPatientsUseCase: GetPatientsUseCase,
    getVaccinationsUseCase: GetVaccinationsUseCase,
    financeRepository: FinanceRepository,
    expenseRepository: ExpenseRepository,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FullReportUiState())
    val uiState: StateFlow<FullReportUiState> = _uiState.asStateFlow()

    private val _filterMode = MutableStateFlow("Overall")
    private val _fyQuarter = MutableStateFlow(0)
    private val _selectedMonth = MutableStateFlow(-1)

    init {
        viewModelScope.launch {
            combine(
                getPatientsUseCase(),
                getVaccinationsUseCase(),
                financeRepository.getAllTransactions(),
                expenseRepository.getAllExpenses(),
                _filterMode,
                _fyQuarter,
                _selectedMonth
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val patients = values[0] as List<Patient>
                @Suppress("UNCHECKED_CAST")
                val vaccinations = values[1] as List<Vaccination>
                @Suppress("UNCHECKED_CAST")
                val transactions = values[2] as List<FinanceEntity>
                @Suppress("UNCHECKED_CAST")
                val expenses = values[3] as List<Expense>
                val filterMode = values[4] as String
                val fyQuarter = values[5] as Int
                val selectedMonth = values[6] as Int

                computeReport(patients, vaccinations, transactions, expenses, filterMode, fyQuarter, selectedMonth)
            }.flowOn(Dispatchers.Default)
                .collect { _uiState.value = it }
        }
    }

    fun updateFilter(filterMode: String, fyQuarter: Int, selectedMonth: Int) {
        _filterMode.value = filterMode
        _fyQuarter.value = fyQuarter
        _selectedMonth.value = selectedMonth
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                refreshDataUseCase()
            } catch (_: Exception) {
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    private fun computeReport(
        patients: List<Patient>,
        vaccinations: List<Vaccination>,
        transactions: List<FinanceEntity>,
        expenses: List<Expense>,
        filterMode: String,
        fyQuarter: Int,
        selectedMonth: Int
    ): FullReportUiState {
        val validVaccinations = StatisticsUtils.filterValidVaccinations(vaccinations)
        val isDaily = filterMode != "Overall" && fyQuarter != 0 && selectedMonth != -1

        fun yearMonthKey(dateStr: String): Int? {
            val d = PatientUtils.parseDate(dateStr) ?: return null
            val c = Calendar.getInstance().apply { time = d }
            return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        }

        fun dayKey(dateStr: String): String? {
            val d = PatientUtils.parseDate(dateStr) ?: return null
            val c = Calendar.getInstance().apply { time = d }
            return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        }

        fun dayLabel(dateStr: String): String? {
            val d = PatientUtils.parseDate(dateStr) ?: return null
            val c = Calendar.getInstance().apply { time = d }
            return "${c.get(Calendar.DAY_OF_MONTH)}"
        }

        if (isDaily) {
            return computeDaily(patients, validVaccinations, transactions, expenses, filterMode, fyQuarter, selectedMonth)
        }

        return computeMonthly(patients, validVaccinations, transactions, expenses, filterMode, fyQuarter)
    }

    private fun computeMonthly(
        patients: List<Patient>,
        validVaccinations: List<Vaccination>,
        transactions: List<FinanceEntity>,
        expenses: List<Expense>,
        filterMode: String,
        fyQuarter: Int
    ): FullReportUiState {
        val cal = Calendar.getInstance()
        val monthKeys: List<Pair<Int, String>>
        val periodLabel: String

        if (filterMode == "Overall") {
            monthKeys = (0 until 6).reversed().map { offset ->
                val tempCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
                val key = tempCal.get(Calendar.YEAR) * 12 + tempCal.get(Calendar.MONTH)
                key to StatisticsUtils.monthNames[tempCal.get(Calendar.MONTH)]
            }
            periodLabel = "Last 6 Months"
        } else {
            val startYearShort = filterMode.substringAfter("FY ").substringBefore("-").toIntOrNull() ?: 0
            val fyStartYear = if (startYearShort > 80) 1900 + startYearShort else 2000 + startYearShort
            val startMonth = Calendar.APRIL

            val startCal = Calendar.getInstance().apply {
                set(fyStartYear, startMonth, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val monthCount = if (fyQuarter != 0) 3 else 12
            val quarterOffset = (fyQuarter - 1) * 3

            monthKeys = (0 until monthCount).map { offset ->
                val tempCal = (startCal.clone() as Calendar).apply { add(Calendar.MONTH, quarterOffset + offset) }
                val key = tempCal.get(Calendar.YEAR) * 12 + tempCal.get(Calendar.MONTH)
                key to StatisticsUtils.monthNames[tempCal.get(Calendar.MONTH)]
            }
            periodLabel = if (fyQuarter != 0) "FY ${filterMode.substringAfter("FY ")} Q$fyQuarter" else "FY ${filterMode.substringAfter("FY ")}"
        }

        val monthKeySet = monthKeys.map { it.first }.toSet()

        fun bucketMonth(dateStr: String): Int? {
            val d = PatientUtils.parseDate(dateStr) ?: return null
            val c = Calendar.getInstance().apply { time = d }
            return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        }

        val patientBuckets = patients.groupBy { bucketMonth(it.registrationDate ?: "") }
        val consultBuckets = validVaccinations.filter { it.visitType.equals("CONSULTATION", true) }.groupBy { bucketMonth(it.dateGiven) }
        val vaccBuckets = validVaccinations.filter { it.visitType.equals("VACCINATION", true) }.groupBy { bucketMonth(it.dateGiven) }

        data class MonthFinance(var revenue: Double = 0.0, var cash: Double = 0.0, var online: Double = 0.0, var cogs: Double = 0.0)
        val financeBuckets = mutableMapOf<Int, MonthFinance>()
        transactions.filter { it.type.equals("INCOME", true) }.forEach { tx ->
            val key = bucketMonth(FinanceCalculator.resolveReportingDate(tx)) ?: return@forEach
            if (key !in monthKeySet) return@forEach
            val mf = financeBuckets.getOrPut(key) { MonthFinance() }
            val amount = tx.amount.coerceAtLeast(0.0)
            mf.revenue += amount
            val cashAmt = if (tx.cashAmount > 0.0) tx.cashAmount else if (tx.paymentMethod.equals("CASH", true)) amount else 0.0
            val onlineAmt = if (tx.onlineAmount > 0.0) tx.onlineAmount else if (tx.paymentMethod.equals("ONLINE", true)) amount else 0.0
            mf.cash += cashAmt
            mf.online += onlineAmt
            val snapshot = tx.remarks?.substringAfter("[COGS_SNAPSHOT:", "")?.substringBefore("]")?.toDoubleOrNull()
            if (snapshot != null && snapshot >= 0.0) mf.cogs += snapshot
        }

        val expenseBuckets = mutableMapOf<Int, Double>()
        expenses.forEach { exp ->
            val key = bucketMonth(exp.expenseDate) ?: return@forEach
            if (key !in monthKeySet) return@forEach
            expenseBuckets[key] = (expenseBuckets[key] ?: 0.0) + exp.amountPaise / 100.0
        }

        val dataPoints = monthKeys.map { (key, label) ->
            val mf = financeBuckets[key] ?: MonthFinance()
            val exp = expenseBuckets[key] ?: 0.0
            val netProfit = mf.revenue - mf.cogs - exp
            FullReportDataPoint(
                label = label,
                patients = (patientBuckets[key]?.size ?: 0).toFloat(),
                consultations = (consultBuckets[key]?.size ?: 0).toFloat(),
                vaccinations = (vaccBuckets[key]?.sumOf { v -> v.items.sumOf { it.quantity.coerceAtLeast(0) } } ?: 0).toFloat(),
                revenue = (mf.revenue / 1000.0).toFloat(),
                online = (mf.online / 1000.0).toFloat(),
                cash = (mf.cash / 1000.0).toFloat(),
                netProfit = (netProfit / 1000.0).toFloat(),
                cogs = (mf.cogs / 1000.0).toFloat(),
                expenses = exp.toFloat()
            )
        }

        return FullReportUiState(dataPoints = dataPoints, isLoading = false, isDaily = false, periodLabel = periodLabel)
    }

    private fun computeDaily(
        patients: List<Patient>,
        validVaccinations: List<Vaccination>,
        transactions: List<FinanceEntity>,
        expenses: List<Expense>,
        filterMode: String,
        fyQuarter: Int,
        selectedMonth: Int
    ): FullReportUiState {
        val startYearShort = filterMode.substringAfter("FY ").substringBefore("-").toIntOrNull() ?: 0
        val fyStartYear = if (startYearShort > 80) 1900 + startYearShort else 2000 + startYearShort
        val quarterMonths = StatisticsUtils.fyQuarters[fyQuarter - 1].second
        val monthInQuarter = quarterMonths.indexOf(selectedMonth)
        val targetMonth = ((Calendar.APRIL) + ((fyQuarter - 1) * 3) + monthInQuarter + 12) % 12
        val targetYear = fyStartYear + if (targetMonth >= Calendar.APRIL) 0 else 1

        val daysInMonth = Calendar.getInstance().apply {
            set(targetYear, targetMonth, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)

        data class DayBucket(var patients: Int = 0, var consults: Int = 0, var vaccs: Int = 0, var revenue: Double = 0.0, var cash: Double = 0.0, var online: Double = 0.0, var cogs: Double = 0.0, var expenses: Double = 0.0)
        val dayBuckets = mutableMapOf<Int, DayBucket>()
        for (d in 1..daysInMonth) dayBuckets[d] = DayBucket()

        fun dayOfMonth(dateStr: String): Int? {
            val parsed = PatientUtils.parseDate(dateStr) ?: return null
            val c = Calendar.getInstance().apply { time = parsed }
            if (c.get(Calendar.YEAR) != targetYear || c.get(Calendar.MONTH) != targetMonth) return null
            return c.get(Calendar.DAY_OF_MONTH)
        }

        patients.forEach { p -> dayOfMonth(p.registrationDate ?: "")?.let { dayBuckets[it]?.patients++ } }
        validVaccinations.filter { it.visitType.equals("CONSULTATION", true) }.forEach { v -> dayOfMonth(v.dateGiven)?.let { dayBuckets[it]?.consults++ } }
        validVaccinations.filter { it.visitType.equals("VACCINATION", true) }.forEach { v -> dayOfMonth(v.dateGiven)?.let { dayBuckets[it]?.vaccs += v.items.sumOf { it.quantity.coerceAtLeast(0) } } }

        transactions.filter { it.type.equals("INCOME", true) }.forEach { tx ->
            dayOfMonth(FinanceCalculator.resolveReportingDate(tx))?.let { day ->
                val bucket = dayBuckets[day] ?: return@let
                val amount = tx.amount.coerceAtLeast(0.0)
                bucket.revenue += amount
                val cashAmt = if (tx.cashAmount > 0.0) tx.cashAmount else if (tx.paymentMethod.equals("CASH", true)) amount else 0.0
                val onlineAmt = if (tx.onlineAmount > 0.0) tx.onlineAmount else if (tx.paymentMethod.equals("ONLINE", true)) amount else 0.0
                bucket.cash += cashAmt
                bucket.online += onlineAmt
                val snapshot = tx.remarks?.substringAfter("[COGS_SNAPSHOT:", "")?.substringBefore("]")?.toDoubleOrNull()
                if (snapshot != null && snapshot >= 0.0) bucket.cogs += snapshot
            }
        }

        expenses.forEach { exp ->
            dayOfMonth(exp.expenseDate)?.let { dayBuckets[it]?.expenses += exp.amountPaise / 100.0 }
        }

        val monthName = StatisticsUtils.monthNames[targetMonth]
        val dataPoints = (1..daysInMonth).map { day ->
            val b = dayBuckets[day]!!
            FullReportDataPoint(
                label = "$day",
                patients = b.patients.toFloat(),
                consultations = b.consults.toFloat(),
                vaccinations = b.vaccs.toFloat(),
                revenue = (b.revenue / 1000.0).toFloat(),
                online = (b.online / 1000.0).toFloat(),
                cash = (b.cash / 1000.0).toFloat(),
                netProfit = ((b.revenue - b.cogs - b.expenses) / 1000.0).toFloat(),
                cogs = (b.cogs / 1000.0).toFloat(),
                expenses = b.expenses.toFloat()
            )
        }

        return FullReportUiState(dataPoints = dataPoints, isLoading = false, isDaily = true, periodLabel = "$monthName $targetYear (Daily)")
    }
}
