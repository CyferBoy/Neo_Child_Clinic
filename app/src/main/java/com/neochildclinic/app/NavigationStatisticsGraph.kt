package com.neochildclinic.app

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.features.statistics.MonthlyFinanceDetailsScreen
import com.neochildclinic.features.statistics.MilestonePatientsScreen
import com.neochildclinic.features.statistics.VaccineDetailScreen
import com.neochildclinic.features.statistics.FullReportScreen
import com.neochildclinic.features.statistics.StatisticsScreen

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
            onVaccineTypeClick = { type, brandName ->
                val encodedType = java.net.URLEncoder.encode(type, "UTF-8")
                val encodedBrand = java.net.URLEncoder.encode(brandName, "UTF-8")
                navController.navigate("vaccine_detail/$encodedType/$encodedBrand")
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
            navArgument("brandName") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val type = java.net.URLDecoder.decode(backStackEntry.stringArg("type"), "UTF-8")
        val brandName = java.net.URLDecoder.decode(backStackEntry.stringArg("brandName"), "UTF-8")
        VaccineDetailScreen(
            type = type,
            brandName = brandName,
            onBack = goBack,
            onPatientClick = { patientId ->
                navController.navigate("patient_details/$patientId")
            }
        )
    }
}