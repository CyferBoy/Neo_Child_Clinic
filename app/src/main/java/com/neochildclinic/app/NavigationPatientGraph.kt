package com.neochildclinic.app

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.neochildclinic.features.patient.AddPatientScreen
import com.neochildclinic.features.patient.AddConsultationScreen
import com.neochildclinic.features.patient.PatientDetailsScreen
import com.neochildclinic.features.patient.PatientListScreen
import com.neochildclinic.features.search.SearchScreen
import com.neochildclinic.features.vaccination.AddVaccinationScreen

internal fun NavGraphBuilder.patientGraph(
    navController: NavHostController,
    goBack: () -> Unit
) {
    composable(Routes.SEARCH) {
        SearchScreen(
            onBack = goBack,
            onPatientClick = { patientId ->
                navController.navigate("patient_details/$patientId")
            }
        )
    }

    composable(Routes.ADD_PATIENT) {
        AddPatientScreen(
            onBack = goBack,
            onNavigateToDetails = { patientId ->
                navController.navigate("patient_details/$patientId") {
                    popUpTo(Routes.ADD_PATIENT) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }

    composable(Routes.PATIENT_LIST) {
        PatientListScreen(
            onBack = goBack,
            onAddPatient = { navController.navigate(Routes.ADD_PATIENT) },
            onPatientClick = { patientId ->
                navController.navigate("patient_details/$patientId")
            },
            onEditPatient = { patientId ->
                navController.navigate("edit_patient/$patientId")
            }
        )
    }

    composable(
        route = Routes.PATIENT_DETAILS,
        arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val patientId = backStackEntry.stringArg("patientId")
        PatientDetailsScreen(
            patientId = patientId,
            onBack = goBack,
            onAddVaccine = { id ->
                navController.navigate("add_vaccine/$id")
            },
            onAddConsultation = { id ->
                navController.navigate("add_consultation/$id")
            },
            onEditVaccination = { id ->
                navController.navigate("edit_vaccination/$id")
            },
            onEditConsultation = { id ->
                navController.navigate("edit_consultation/$id")
            },
            onEditPatient = { id ->
                navController.navigate("edit_patient/$id")
            }
        )
    }

    composable(
        route = Routes.EDIT_PATIENT,
        arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val patientId = backStackEntry.nullableStringArg("patientId")
        AddPatientScreen(
            patientId = patientId,
            onBack = goBack,
            onNavigateToDetails = { id ->
                navController.navigate("patient_details/$id") {
                    popUpTo(Routes.EDIT_PATIENT) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }

    composable(
        route = Routes.EDIT_VACCINATION,
        arguments = listOf(navArgument("vaccinationId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val vaccinationId = backStackEntry.nullableStringArg("vaccinationId")
        AddVaccinationScreen(
            vaccinationId = vaccinationId,
            onBack = goBack
        )
    }

    composable(
        route = Routes.ADD_CONSULTATION,
        arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val patientId = backStackEntry.stringArg("patientId")
        AddConsultationScreen(
            patientId = patientId,
            onBack = goBack
        )
    }

    composable(
        route = Routes.EDIT_CONSULTATION,
        arguments = listOf(navArgument("consultationId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val consultationId = backStackEntry.stringArg("consultationId")
        AddConsultationScreen(
            patientId = "",
            consultationId = consultationId,
            onBack = goBack
        )
    }

    composable(
        route = Routes.ADD_VACCINE_FOR_PATIENT,
        arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val patientId = backStackEntry.stringArg("patientId")
        AddVaccinationScreen(
            patientId = patientId,
            onBack = goBack,
        )
    }

    composable(
        route = Routes.TODAY_PATIENTS,
        arguments = listOf(
            navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("highlightId") { type = NavType.StringType; nullable = true; defaultValue = null }
        )
    ) { backStackEntry ->
        val dashboardViewModel: com.neochildclinic.features.dashboard.DashboardViewModel = hiltViewModel()
        com.neochildclinic.features.dashboard.TodayPatientsScreen(
            viewModel = dashboardViewModel,
            initialTab = backStackEntry.nullableStringArg("tab"),
            highlightId = backStackEntry.nullableStringArg("highlightId"),
            onBack = goBack
        )
    }
}