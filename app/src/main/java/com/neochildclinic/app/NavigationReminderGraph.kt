package com.neochildclinic.app

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.neochildclinic.features.reminder.DueScreen
import com.neochildclinic.features.reminder.CompletedDismissedScreen

internal fun NavGraphBuilder.reminderGraph(
    navController: NavHostController,
    goBack: () -> Unit
) {
    composable(Routes.DUE) {
        DueScreen(
            onBack = goBack,
            onNavigateToCompleted = {
                navController.navigate("completed_dismissed?tab=0")
            },
            onNavigateToDismissed = {
                navController.navigate("completed_dismissed?tab=1")
            },
            onPatientClick = { patientId ->
                navController.navigate("patient_details/$patientId")
            }
        )
    }

    composable("completed_dismissed?tab={tab}") { backStackEntry ->
        val tab = backStackEntry.arguments?.getString("tab")?.toIntOrNull() ?: 0
        CompletedDismissedScreen(
            onBack = goBack,
            onPatientClick = { patientId ->
                navController.navigate("patient_details/$patientId")
            },
            initialTab = tab
        )
    }

    composable(Routes.PERSONAL_REMINDERS) {
        com.neochildclinic.features.personalreminder.PersonalReminderScreen(
            onBack = goBack,
            onAddReminder = { navController.navigate("add_personal_reminder") },
            onEditReminder = { reminderId ->
                navController.navigate("edit_personal_reminder/$reminderId")
            },
            onPatientClick = { patientId ->
                navController.navigate("patient_details/$patientId")
            }
        )
    }

    composable(
        route = Routes.ADD_PERSONAL_REMINDER,
        arguments = listOf(navArgument("patientId") { type = NavType.StringType; nullable = true })
    ) { backStackEntry ->
        val patientId = backStackEntry.nullableStringArg("patientId")
        com.neochildclinic.features.personalreminder.AddEditPersonalReminderScreen(
            reminderId = null,
            prefillPatientId = patientId,
            onBack = goBack,
            onSaved = goBack
        )
    }

    composable(
        route = Routes.EDIT_PERSONAL_REMINDER,
        arguments = listOf(navArgument("reminderId") { type = NavType.StringType })
    ) { backStackEntry ->
        val reminderId = backStackEntry.stringArg("reminderId")
        com.neochildclinic.features.personalreminder.AddEditPersonalReminderScreen(
            reminderId = reminderId,
            prefillPatientId = null,
            onBack = goBack,
            onSaved = goBack
        )
    }
}