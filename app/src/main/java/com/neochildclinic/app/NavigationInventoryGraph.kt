package com.neochildclinic.app

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.neochildclinic.features.inventory.AddVaccineScreen
import com.neochildclinic.features.inventory.AddBatchScreen
import com.neochildclinic.features.inventory.AddStockScreen
import com.neochildclinic.features.inventory.StockHistoryScreen
import com.neochildclinic.features.inventory.BorrowedScreen
import com.neochildclinic.features.inventory.VaccineInventoryScreen
import com.neochildclinic.features.inventory.WasteScreen

internal fun NavGraphBuilder.inventoryGraph(
    navController: NavHostController,
    goBack: () -> Unit
) {
    composable(Routes.VACCINE_INVENTORY) {
        VaccineInventoryScreen(
            onBack = goBack,
            onAddVaccine = { navController.navigate(Routes.ADD_VACCINE_DEFINITION) },
            onEditVaccine = { id ->
                navController.navigate("edit_vaccine_definition/$id")
            },
            onAddBatch = { vaccineId, brandName ->
                navController.navigate("add_batch/$vaccineId/$brandName")
            },
            onEditBatch = { batchId, vaccineId, brandName ->
                navController.navigate("edit_batch/$batchId?vaccineId=$vaccineId&brandName=$brandName")
            },
            onAddStock = { navController.navigate(Routes.ADD_VACCINE_STOCK) },
            onStockHistory = { navController.navigate(Routes.STOCK_HISTORY) }
        )
    }

    composable(Routes.ADD_VACCINE_STOCK) {
        AddStockScreen(onBack = goBack)
    }

    composable(Routes.STOCK_HISTORY) {
        StockHistoryScreen(onBack = goBack)
    }

    composable(Routes.ADD_VACCINE_DEFINITION) {
        AddVaccineScreen(onBack = goBack)
    }

    composable(
        route = Routes.EDIT_VACCINE_DEFINITION,
        arguments = listOf(navArgument("vaccineId") { type = NavType.StringType })
    ) { backStackEntry ->
        val vaccineId = backStackEntry.nullableStringArg("vaccineId")
        AddVaccineScreen(
            vaccineId = vaccineId,
            onBack = goBack
        )
    }

    composable(
        route = Routes.ADD_BATCH,
        arguments = listOf(
            navArgument("vaccineId") { type = NavType.StringType },
            navArgument("brandName") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val vaccineId = backStackEntry.stringArg("vaccineId")
        val brandName = backStackEntry.stringArg("brandName")
        AddBatchScreen(
            vaccineId = vaccineId,
            brandName = brandName,
            onBack = goBack
        )
    }

    composable(
        route = Routes.EDIT_BATCH,
        arguments = listOf(
            navArgument("batchId") { type = NavType.StringType },
            navArgument("vaccineId") { type = NavType.StringType; nullable = true },
            navArgument("brandName") { type = NavType.StringType; nullable = true }
        )
    ) { backStackEntry ->
        val batchId = backStackEntry.nullableStringArg("batchId")
        val vaccineId = backStackEntry.stringArg("vaccineId")
        val brandName = backStackEntry.stringArg("brandName")
        AddBatchScreen(
            batchId = batchId,
            vaccineId = vaccineId,
            brandName = brandName,
            onBack = goBack
        )
    }

    composable(Routes.BORROWED) {
        BorrowedScreen(onBack = goBack)
    }

    composable(Routes.WASTE) {
        WasteScreen(onBack = goBack)
    }
}