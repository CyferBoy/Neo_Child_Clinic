package com.neochildclinic.features.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Vaccine
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.core.designsystem.NeoChildTheme

@Composable
fun FinanceTab(
    vaccinations: List<Vaccination>,
    inventoryItems: List<InventoryItem>,
    onMonthClick: (String) -> Unit = {}
) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }

    val inventory = remember(inventoryItems) {
        inventoryItems.map { item ->
            Vaccine(
                id = item.id,
                type = item.type,
                brandName = item.brandName,
                companyName = item.company,
                stock = item.stock,
                mrp = item.mrp,
                netRate = item.netRate
            )
        }
    }

    val availableYears = remember(vaccinations) {
        StatisticsUtils.getAvailableFinancialYears(vaccinations.map { it.dateGiven })
    }

    val filteredVaccinations = remember(vaccinations, filterMode) {
        vaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode) }
    }

    val financeStats = remember(filteredVaccinations, inventory) {
        FinanceCalculator.calculateFinanceStats(filteredVaccinations, inventory)
    }

    FinanceContent(
        stats = financeStats,
        filterMode = filterMode,
        availableYears = availableYears,
        vaccinations = vaccinations,
        filteredVaccinations = filteredVaccinations,
        inventory = inventory,
        onFilterModeChange = { filterMode = it },
        onMonthClick = onMonthClick
    )
}

@Composable
private fun FinanceContent(
    stats: FinanceStatsData,
    filterMode: String,
    availableYears: List<String>,
    vaccinations: List<Vaccination>,
    filteredVaccinations: List<Vaccination>,
    inventory: List<Vaccine>,
    onFilterModeChange: (String) -> Unit,
    onMonthClick: (String) -> Unit
) {
    val mainOptions = remember(availableYears) { listOf("Overall") + availableYears.map { "FY $it" } }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        FinanceHeader(filterMode = filterMode, mainOptions = mainOptions, onFilterModeChange = onFilterModeChange)

        RevenueOverviewCard(revenue = stats.totalRevenue, filterMode = filterMode)

        Spacer(modifier = Modifier.height(24.dp))

        CashOnlineSummary(cash = stats.cashTotal, online = stats.onlineTotal)

        Spacer(modifier = Modifier.height(24.dp))

        Text("Financial Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        FinanceTable(filteredVaccinations, inventory, onMonthClick)

        Spacer(modifier = Modifier.height(32.dp))

        ChartsSection(filterMode = filterMode, availableYears = availableYears, vaccinations = vaccinations)

        Spacer(modifier = Modifier.height(16.dp))
        ChartLegend()
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun FinanceTabPreview() {
    NeoChildTheme {
        FinanceContent(
            stats = FinanceStatsData(10000.0, 6000.0, 4000.0, 2000.0),
            filterMode = "Overall",
            availableYears = listOf("23-24"),
            vaccinations = emptyList(),
            filteredVaccinations = emptyList(),
            inventory = emptyList(),
            onFilterModeChange = {},
            onMonthClick = {}
        )
    }
}
