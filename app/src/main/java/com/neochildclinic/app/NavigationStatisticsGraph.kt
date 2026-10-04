package com.neochildclinic.app

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.feature.statistics.presentation.MonthlyFinanceDetailsScreen
import com.neochildclinic.feature.statistics.presentation.MilestonePatientsScreen
import com.neochildclinic.feature.statistics.presentation.VaccineDetailScreen
import com.neochildclinic.feature.statistics.presentation.FullReportScreen
import com.neochildclinic.feature.statistics.presentation.StatisticsScreen

internal fun NavGraphBuilder.statisticsGraph(
    navController: NavHostController,
    userRole: UserRole?,
    goBack: () -> Unit
) {
    composable(Routes.STATISTICS) {
        val hasStatisticsAccess = userRole == UserRole.admin || userRole == UserRole.doctor
        StatisticsScreen(
            hasAccess = hasStatisticsAccess,
            onBack = goBack,
            onMonthClick = { monthKey ->
                navController.navigate("monthly_finance_details/$monthKey")
            },
            onMilestoneClick = { milestoneKey ->
                navController.navigate("milestone_patients/$milestoneKey")
            },
            onFullReportClick = {
                navController.navigate(Routes.FULL_REPORT)
            },
            onVaccineTypeClick = { type, vaccineId ->
                navController.navigate(vaccineDetailRoute(type, vaccineId))
            }
        )
    }

    composable(
        route = Routes.MONTHLY_FINANCE_DETAILS,
        arguments = listOf(navArgument("monthKey") { type = NavType.StringType }),
    ) { backStackEntry ->
        val monthKey = backStackEntry.stringArg("monthKey")
        MonthlyFinanceDetailsScreen(
            monthKey = monthKey,
            onBack = goBack
        )
    }

    composable(
        route = Routes.MILESTONE_PATIENTS,
        arguments = listOf(navArgument("milestoneKey") { type = NavType.StringType }),
    ) { backStackEntry ->
        val milestoneKey = backStackEntry.stringArg("milestoneKey")
        MilestonePatientsScreen(
            milestoneKey = milestoneKey,
            onBack = goBack,
            onPatientClick = { patientId ->
                navController.navigate("patient_details/$patientId")
            }
        )
    }

    composable(Routes.FULL_REPORT) {
        FullReportScreen(onBack = goBack)
    }

    composable(
        route = Routes.VACCINE_DETAIL,
        arguments = listOf(
            navArgument("type") { type = NavType.StringType },
            // Optional: absent when the user tapped a vaccine type rather than a brand.
            navArgument("vaccineId") {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { backStackEntry ->
        // `type` is a path segment (Navigation does not decode it); `vaccineId` is a query
        // param (Navigation already decoded it) - decoding either twice would corrupt values.
        val type = dec(backStackEntry.stringArg("type"))
        val vaccineId = backStackEntry.stringArg("vaccineId").orEmpty()
        VaccineDetailScreen(
            type = type,
            vaccineId = vaccineId.ifBlank { null },
            onBack = goBack,
            onPatientClick = { patientId ->
                navController.navigate("patient_details/$patientId")
            }
        )
    }
}