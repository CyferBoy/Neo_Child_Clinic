package com.neochildclinic.feature.statistics.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.finance.domain.repository.ExpenseRepository
import com.neochildclinic.feature.finance.domain.repository.FinanceRepository
import com.neochildclinic.feature.patient.domain.repository.PatientRepository
import com.neochildclinic.feature.statistics.domain.FinanceCalculator
import com.neochildclinic.feature.statistics.domain.StatisticsDateUtils
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import com.neochildclinic.feature.sync.domain.RefreshDataUseCase
import com.neochildclinic.feature.vaccination.domain.repository.VaccinationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
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

enum class ChartMode { BAR, LINE }

enum class ReportViewMode { TABLE, LINE, BAR }

data class FullReportSummary(
    val totalPatients: Int = 0,
    val newPatientsInPeriod: Int = 0,
    val consultationCount: Int = 0,
    val consultationRevenue: Double = 0.0,
    val vaccinationCount: Int = 0,
    val vaccinationRevenue: Double = 0.0,
    val cogsAmount: Double = 0.0,
    val pendingCogsCount: Int = 0,
    val isCogsComplete: Boolean = true,
    val expenseItemCount: Int = 0,
    val totalExpenses: Double = 0.0,
    val totalRevenue: Double = 0.0,
    val cashTotal: Double = 0.0,
    val onlineTotal: Double = 0.0,
    val netProfit: Double = 0.0
)

data class FullReportUiState(
    val dataPoints: List<FullReportDataPoint> = emptyList(),
    val summary: FullReportSummary = FullReportSummary(),
    val viewMode: ReportViewMode = ReportViewMode.TABLE,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isDaily: Boolean = false,
    val periodLabel: String = "",
    val chartMode: ChartMode = ChartMode.BAR,
    val availableFinancialYears: List<String> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class FullReportViewModel @Inject constructor(
    patientRepository: PatientRepository,
    vaccinationRepository: VaccinationRepository,
    financeRepository: FinanceRepository,
    expenseRepository: ExpenseRepository,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FullReportUiState())
    val uiState: StateFlow<FullReportUiState> = _uiState.asStateFlow()

    private val _filterMode = MutableStateFlow(StatisticsUtils.getCurrentFinancialYear())
    private val _fyQuarter = MutableStateFlow(0)
    private val _selectedMonth = MutableStateFlow(-1)
    private val _chartMode = MutableStateFlow(ChartMode.BAR)
    private val _viewMode = MutableStateFlow(ReportViewMode.TABLE)

    init {
        viewModelScope.launch {
            combine(
                patientRepository.allPatients,
                vaccinationRepository.allVaccinations,
                financeRepository.getAllTransactions(),
                expenseRepository.getAllExpenses(),
                _filterMode,
                _fyQuarter,
                _selectedMonth,
                _chartMode,
                _viewMode
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val patients = values[0] as List<Patient>
                @Suppress("UNCHECKED_CAST")
                val vaccinations = values[1] as List<Vaccination>
                @Suppress("UNCHECKED_CAST")
                val transactions = values[2] as List<FinanceTransaction>
                @Suppress("UNCHECKED_CAST")
                val expenses = values[3] as List<Expense>
                val filterMode = values[4] as String
                val fyQuarter = values[5] as Int
                val selectedMonth = values[6] as Int
                val chartMode = values[7] as ChartMode
                val viewMode = values[8] as ReportViewMode

                computeReport(
                    patients,
                    vaccinations,
                    transactions,
                    expenses,
                    filterMode,
                    fyQuarter,
                    selectedMonth,
                    chartMode,
                    viewMode
                )
            }.flowOn(Dispatchers.Default)
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Full report combine failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Could not load the report."
                    )
                }
                .collect { _uiState.value = it }
        }
    }

    fun updateFilter(filterMode: String, fyQuarter: Int, selectedMonth: Int) {
        _filterMode.value = filterMode
        _fyQuarter.value = fyQuarter
        _selectedMonth.value = selectedMonth
    }

    fun setChartMode(mode: ChartMode) {
        _chartMode.value = mode
        _viewMode.value = if (mode == ChartMode.LINE) ReportViewMode.LINE else ReportViewMode.BAR
    }

    fun setViewMode(mode: ReportViewMode) {
        _viewMode.value = mode
        when (mode) {
            ReportViewMode.LINE -> _chartMode.value = ChartMode.LINE
            ReportViewMode.BAR -> _chartMode.value = ChartMode.BAR
            ReportViewMode.TABLE -> Unit
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                refreshDataUseCase()
            } catch (e: Exception) {
                android.util.Log.e("FullReportVM", "Refresh failed", e)
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    private fun computeReport(
        patients: List<Patient>,
        vaccinations: List<Vaccination>,
        transactions: List<FinanceTransaction>,
        expenses: List<Expense>,
        filterMode: String,
        fyQuarter: Int,
        selectedMonth: Int,
        chartMode: ChartMode,
        viewMode: ReportViewMode
    ): FullReportUiState {
        val validVaccinations = StatisticsUtils.filterValidVaccinations(vaccinations)
        val isDaily = filterMode != "Overall" && fyQuarter != 0 && selectedMonth != -1

        val allDates = patients.mapNotNull { it.registrationDate } +
                validVaccinations.map { it.dateGiven } +
                transactions.map { FinanceCalculator.resolveReportingDate(it) } +
                expenses.map { it.expenseDate }
        val availableYears = StatisticsUtils.getAvailableFinancialYears(allDates).sorted().reversed()

        return if (isDaily) {
            computeDaily(
                patients,
                validVaccinations,
                vaccinations,
                transactions,
                expenses,
                filterMode,
                fyQuarter,
                selectedMonth,
                chartMode,
                viewMode,
                availableYears
            )
        } else {
            computeMonthly(
                patients,
                validVaccinations,
                vaccinations,
                transactions,
                expenses,
                filterMode,
                fyQuarter,
                chartMode,
                viewMode,
                availableYears
            )
        }
    }

    private fun computeMonthly(
        patients: List<Patient>,
        validVaccinations: List<Vaccination>,
        allVisits: List<Vaccination>,
        transactions: List<FinanceTransaction>,
        expenses: List<Expense>,
        filterMode: String,
        fyQuarter: Int,
        chartMode: ChartMode,
        viewMode: ReportViewMode,
        availableYears: List<String>
    ): FullReportUiState {
        val effectiveRegDates = StatisticsDateUtils.computeEffectiveRegistrationDates(patients, allVisits)

        val monthKeys: List<YearMonth>
        val periodLabel: String

        if (filterMode == "Overall") {
            monthKeys = StatisticsDateUtils.lastNMonths(6)
            periodLabel = "Last 6 Months"
        } else {
            val startYearShort = filterMode.substringAfter("FY ").substringBefore("-").toIntOrNull() ?: 0
            val fyStartYear = if (startYearShort > 80) 1900 + startYearShort else 2000 + startYearShort
            val monthCount = if (fyQuarter != 0) 3 else 12
            val quarterOffset = (fyQuarter - 1) * 3

            val fyStart = YearMonth.of(fyStartYear, 4)
            monthKeys = (0 until monthCount).map { offset ->
                val rawMonth = (if (fyQuarter == 0) 0 else quarterOffset) + offset
                fyStart.plusMonths(rawMonth.toLong())
            }
            periodLabel = if (fyQuarter != 0) "FY ${filterMode.substringAfter("FY ")} Q$fyQuarter" else "FY ${filterMode.substringAfter("FY ")}"
        }

        val monthKeySet = monthKeys.toSet()

        val patientBuckets = effectiveRegDates.values.filterNotNull()
            .map { YearMonth.from(it) }
            .filter { it in monthKeySet }
            .groupingBy { it }.eachCount()

        val consultBuckets = allVisits
            .filter { it.visitType.equals("CONSULTATION", true) }
            .groupBy { StatisticsDateUtils.monthOfIST(it.dateGiven) }
        val vaccBuckets = validVaccinations
            .filter { it.visitType.equals("VACCINATION", true) }
            .groupBy { StatisticsDateUtils.monthOfIST(it.dateGiven) }

        data class MonthFinance(var revenue: Double = 0.0, var cash: Double = 0.0, var online: Double = 0.0, var cogs: Double = 0.0)
        val financeBuckets = mutableMapOf<YearMonth, MonthFinance>()
        val effectiveIncome = FinanceCalculator.effectiveIncome(transactions.filter { it.type.equals("INCOME", true) })

        effectiveIncome.forEach { tx ->
            val key = StatisticsDateUtils.monthOfIST(FinanceCalculator.resolveReportingDate(tx)) ?: return@forEach
            if (key !in monthKeySet) return@forEach
            val mf = financeBuckets.getOrPut(key) { MonthFinance() }
            val amount = tx.amount.coerceAtLeast(0.0)
            mf.revenue += amount
            val (cashAmt, onlineAmt) = FinanceCalculator.cashAndOnlineOf(tx)
            mf.cash += cashAmt
            mf.online += onlineAmt
            val snapshot = FinanceCalculator.cogsOf(tx.remarks)
            if (snapshot != null && snapshot >= 0.0) mf.cogs += snapshot
        }

        val expenseBuckets = mutableMapOf<YearMonth, Double>()
        expenses.forEach { exp ->
            val key = StatisticsDateUtils.monthOfIST(exp.expenseDate) ?: return@forEach
            if (key !in monthKeySet) return@forEach
            expenseBuckets[key] = (expenseBuckets[key] ?: 0.0) + exp.amountPaise / 100.0
        }

        val dataPoints = monthKeys.map { key ->
            val mf = financeBuckets[key] ?: MonthFinance()
            val exp = expenseBuckets[key] ?: 0.0
            val netProfit = mf.revenue - mf.cogs - exp
            FullReportDataPoint(
                label = StatisticsUtils.monthNames[key.monthValue - 1],
                patients = (patientBuckets[key] ?: 0).toFloat(),
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

        // Period Summary calculations
        val totalPatients = patients.size
        val newPatientsInPeriod = effectiveRegDates.values.filterNotNull().count { YearMonth.from(it) in monthKeySet }

        val periodConsultations = allVisits.filter {
            it.visitType.equals("CONSULTATION", true) && StatisticsDateUtils.monthOfIST(it.dateGiven) in monthKeySet
        }
        val consultationCount = periodConsultations.size

        val periodIncome = effectiveIncome.filter { tx ->
            val key = StatisticsDateUtils.monthOfIST(FinanceCalculator.resolveReportingDate(tx))
            key != null && key in monthKeySet
        }

        val consultationRevenue = periodIncome
            .filter { it.category.equals("CONSULTATION", true) }
            .sumOf { it.amount.coerceAtLeast(0.0) }

        val periodVaccinations = validVaccinations.filter {
            it.visitType.equals("VACCINATION", true) && StatisticsDateUtils.monthOfIST(it.dateGiven) in monthKeySet
        }
        val vaccinationCount = periodVaccinations.sumOf { v -> v.items.sumOf { it.quantity.coerceAtLeast(0) } }
        val vaccinationRevenue = periodIncome
            .filter { it.category.equals("VACCINATION", true) }
            .sumOf { it.amount.coerceAtLeast(0.0) }

        val vaccinationIncomeRows = periodIncome.filter { it.category.equals("VACCINATION", true) }
        var totalCogs = 0.0
        var pendingCogsCount = 0
        vaccinationIncomeRows.forEach { tx ->
            val snapshot = FinanceCalculator.cogsOf(tx.remarks)
            if (snapshot != null && snapshot >= 0.0) {
                totalCogs += snapshot
            } else {
                pendingCogsCount++
            }
        }
        val isCogsComplete = pendingCogsCount == 0

        val periodExpenses = expenses.filter { exp ->
            val key = StatisticsDateUtils.monthOfIST(exp.expenseDate)
            key != null && key in monthKeySet
        }
        val expenseItemCount = periodExpenses.size
        val totalExpenses = periodExpenses.sumOf { it.amountPaise / 100.0 }

        val totalRevenue = periodIncome.sumOf { it.amount.coerceAtLeast(0.0) }
        var cashTotal = 0.0
        var onlineTotal = 0.0
        periodIncome.forEach { tx ->
            val (cashAmt, onlineAmt) = FinanceCalculator.cashAndOnlineOf(tx)
            cashTotal += cashAmt
            onlineTotal += onlineAmt
        }
        val netProfit = totalRevenue - totalCogs - totalExpenses

        val summary = FullReportSummary(
            totalPatients = totalPatients,
            newPatientsInPeriod = newPatientsInPeriod,
            consultationCount = consultationCount,
            consultationRevenue = consultationRevenue,
            vaccinationCount = vaccinationCount,
            vaccinationRevenue = vaccinationRevenue,
            cogsAmount = totalCogs,
            pendingCogsCount = pendingCogsCount,
            isCogsComplete = isCogsComplete,
            expenseItemCount = expenseItemCount,
            totalExpenses = totalExpenses,
            totalRevenue = totalRevenue,
            cashTotal = cashTotal,
            onlineTotal = onlineTotal,
            netProfit = netProfit
        )

        return FullReportUiState(
            dataPoints = dataPoints,
            summary = summary,
            viewMode = viewMode,
            isLoading = false,
            isDaily = false,
            periodLabel = periodLabel,
            chartMode = chartMode,
            availableFinancialYears = availableYears
        )
    }

    private fun computeDaily(
        patients: List<Patient>,
        validVaccinations: List<Vaccination>,
        allVisits: List<Vaccination>,
        transactions: List<FinanceTransaction>,
        expenses: List<Expense>,
        filterMode: String,
        fyQuarter: Int,
        selectedMonth: Int,
        chartMode: ChartMode,
        viewMode: ReportViewMode,
        availableYears: List<String>
    ): FullReportUiState {
        val effectiveRegDates = StatisticsDateUtils.computeEffectiveRegistrationDates(patients, allVisits)

        val startYearShort = filterMode.substringAfter("FY ").substringBefore("-").toIntOrNull() ?: 0
        val fyStartYear = if (startYearShort > 80) 1900 + startYearShort else 2000 + startYearShort
        val quarterMonths = StatisticsUtils.fyQuarters[fyQuarter - 1].second
        val monthInQuarter = quarterMonths.indexOf(selectedMonth)
        val targetMonth = ((3) + ((fyQuarter - 1) * 3) + monthInQuarter + 12) % 12
        val targetYear = fyStartYear + if (targetMonth >= 3) 0 else 1

        val daysInMonth = YearMonth.of(targetYear, targetMonth + 1).lengthOfMonth()

        data class DayBucket(
            var patients: Int = 0,
            var consults: Int = 0,
            var vaccs: Int = 0,
            var revenue: Double = 0.0,
            var cash: Double = 0.0,
            var online: Double = 0.0,
            var cogs: Double = 0.0,
            var expenses: Double = 0.0
        )
        val dayBuckets = mutableMapOf<Int, DayBucket>()
        for (d in 1..daysInMonth) dayBuckets[d] = DayBucket()

        fun dayOfMonthIST(dateStr: String): Int? {
            val ld = StatisticsDateUtils.parseToISTLocalDate(dateStr) ?: return null
            if (ld.year != targetYear || ld.monthValue - 1 != targetMonth) return null
            return ld.dayOfMonth
        }

        fun isInTargetMonth(dateStr: String): Boolean {
            val ld = StatisticsDateUtils.parseToISTLocalDate(dateStr) ?: return false
            return ld.year == targetYear && ld.monthValue - 1 == targetMonth
        }

        effectiveRegDates.values.filterNotNull().forEach { effDate ->
            if (effDate.year == targetYear && effDate.monthValue - 1 == targetMonth) {
                dayBuckets[effDate.dayOfMonth]?.patients?.plus(1)?.let {
                    dayBuckets[effDate.dayOfMonth] = dayBuckets[effDate.dayOfMonth]!!.copy(patients = it)
                }
            }
        }
        allVisits.filter { it.visitType.equals("CONSULTATION", true) }.forEach { v ->
            dayOfMonthIST(v.dateGiven)?.let {
                dayBuckets[it]?.consults?.plus(1)?.let { n -> dayBuckets[it] = dayBuckets[it]!!.copy(consults = n) }
            }
        }
        validVaccinations.filter { it.visitType.equals("VACCINATION", true) }.forEach { v ->
            dayOfMonthIST(v.dateGiven)?.let {
                val qty = v.items.sumOf { it.quantity.coerceAtLeast(0) }
                dayBuckets[it]?.let { b -> dayBuckets[it] = b.copy(vaccs = b.vaccs + qty) }
            }
        }

        val effectiveIncome = FinanceCalculator.effectiveIncome(transactions.filter { it.type.equals("INCOME", true) })
        effectiveIncome.forEach { tx ->
            dayOfMonthIST(FinanceCalculator.resolveReportingDate(tx))?.let { day ->
                val bucket = dayBuckets[day] ?: return@let
                val amount = tx.amount.coerceAtLeast(0.0)
                val (cashAmt, onlineAmt) = FinanceCalculator.cashAndOnlineOf(tx)
                val snapshot = FinanceCalculator.cogsOf(tx.remarks)
                val cogsAdd = if (snapshot != null && snapshot >= 0.0) snapshot else 0.0
                dayBuckets[day] = bucket.copy(
                    revenue = bucket.revenue + amount,
                    cash = bucket.cash + cashAmt,
                    online = bucket.online + onlineAmt,
                    cogs = bucket.cogs + cogsAdd
                )
            }
        }

        expenses.forEach { exp ->
            dayOfMonthIST(exp.expenseDate)?.let { day ->
                val bucket = dayBuckets[day] ?: return@let
                dayBuckets[day] = bucket.copy(expenses = bucket.expenses + exp.amountPaise / 100.0)
            }
        }

        val fullMonthName = Month.of(targetMonth + 1).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val periodLabel = "$fullMonthName $targetYear"

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

        // Daily Summary calculations
        val totalPatients = patients.size
        val newPatientsInPeriod = effectiveRegDates.values.filterNotNull().count { it.year == targetYear && it.monthValue - 1 == targetMonth }

        val periodConsultations = allVisits.filter {
            it.visitType.equals("CONSULTATION", true) && isInTargetMonth(it.dateGiven)
        }
        val consultationCount = periodConsultations.size

        val periodIncome = effectiveIncome.filter { tx ->
            isInTargetMonth(FinanceCalculator.resolveReportingDate(tx))
        }

        val consultationRevenue = periodIncome
            .filter { it.category.equals("CONSULTATION", true) }
            .sumOf { it.amount.coerceAtLeast(0.0) }

        val periodVaccinations = validVaccinations.filter {
            it.visitType.equals("VACCINATION", true) && isInTargetMonth(it.dateGiven)
        }
        val vaccinationCount = periodVaccinations.sumOf { v -> v.items.sumOf { it.quantity.coerceAtLeast(0) } }
        val vaccinationRevenue = periodIncome
            .filter { it.category.equals("VACCINATION", true) }
            .sumOf { it.amount.coerceAtLeast(0.0) }

        val vaccinationIncomeRows = periodIncome.filter { it.category.equals("VACCINATION", true) }
        var totalCogs = 0.0
        var pendingCogsCount = 0
        vaccinationIncomeRows.forEach { tx ->
            val snapshot = FinanceCalculator.cogsOf(tx.remarks)
            if (snapshot != null && snapshot >= 0.0) {
                totalCogs += snapshot
            } else {
                pendingCogsCount++
            }
        }
        val isCogsComplete = pendingCogsCount == 0

        val periodExpenses = expenses.filter { exp -> isInTargetMonth(exp.expenseDate) }
        val expenseItemCount = periodExpenses.size
        val totalExpenses = periodExpenses.sumOf { it.amountPaise / 100.0 }

        val totalRevenue = periodIncome.sumOf { it.amount.coerceAtLeast(0.0) }
        var cashTotal = 0.0
        var onlineTotal = 0.0
        periodIncome.forEach { tx ->
            val (cashAmt, onlineAmt) = FinanceCalculator.cashAndOnlineOf(tx)
            cashTotal += cashAmt
            onlineTotal += onlineAmt
        }
        val netProfit = totalRevenue - totalCogs - totalExpenses

        val summary = FullReportSummary(
            totalPatients = totalPatients,
            newPatientsInPeriod = newPatientsInPeriod,
            consultationCount = consultationCount,
            consultationRevenue = consultationRevenue,
            vaccinationCount = vaccinationCount,
            vaccinationRevenue = vaccinationRevenue,
            cogsAmount = totalCogs,
            pendingCogsCount = pendingCogsCount,
            isCogsComplete = isCogsComplete,
            expenseItemCount = expenseItemCount,
            totalExpenses = totalExpenses,
            totalRevenue = totalRevenue,
            cashTotal = cashTotal,
            onlineTotal = onlineTotal,
            netProfit = netProfit
        )

        return FullReportUiState(
            dataPoints = dataPoints,
            summary = summary,
            viewMode = viewMode,
            isLoading = false,
            isDaily = true,
            periodLabel = periodLabel,
            chartMode = chartMode,
            availableFinancialYears = availableYears
        )
    }

    private companion object {
        const val TAG = "FullReportViewModel"
    }
}
