package com.neochildclinic.app

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.features.dashboard.AuthViewModel
import com.neochildclinic.features.dashboard.DashboardViewModel
import com.neochildclinic.features.dashboard.LoginScreen
import com.neochildclinic.features.dashboard.ManageStaffScreen
import com.neochildclinic.features.dashboard.StaffDetailsScreen
import com.neochildclinic.features.dashboard.AddStaffScreen
import com.neochildclinic.features.dashboard.EditStaffScreen
import com.neochildclinic.features.dashboard.DashboardScreen
import com.neochildclinic.features.patient.AddPatientScreen
import com.neochildclinic.features.patient.AddConsultationScreen
import com.neochildclinic.features.patient.PatientDetailsScreen
import com.neochildclinic.features.patient.PatientListScreen
import com.neochildclinic.features.sync.SyncScreen
import com.neochildclinic.features.audit.FullAuditLogScreen
import com.neochildclinic.features.settings.SettingsScreen
import com.neochildclinic.features.settings.AppUpdateScreen
import com.neochildclinic.features.settings.TermsOfServiceScreen
import com.neochildclinic.features.settings.PrivacyPolicyScreen
import com.neochildclinic.features.settings.HelpSupportScreen
import com.neochildclinic.features.settings.SecuritySettingsScreen
import com.neochildclinic.features.settings.BackupSettingsScreen
import com.neochildclinic.features.settings.InventorySettingsScreen
import com.neochildclinic.features.settings.NotificationSettingsScreen
import com.neochildclinic.features.profile.ProfileScreen
import com.neochildclinic.features.reminder.DueScreen
import com.neochildclinic.features.statistics.MonthlyFinanceDetailsScreen
import com.neochildclinic.features.statistics.StatisticsScreen
import com.neochildclinic.features.inventory.AddVaccineScreen
import com.neochildclinic.features.inventory.AddBatchScreen
import com.neochildclinic.features.search.SearchScreen
import com.neochildclinic.features.vaccination.AddVaccinationScreen
import com.neochildclinic.features.inventory.BorrowedScreen
import com.neochildclinic.features.inventory.VaccineInventoryScreen
import com.neochildclinic.features.inventory.WasteScreen
import com.neochildclinic.features.reminder.CompletedDismissedScreen

@Composable
fun AppNavigation(
    navController: androidx.navigation.NavHostController = rememberNavController()
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val dashboardUiState by dashboardViewModel.uiState.collectAsState()
    val authProfile by authViewModel.profile.collectAsState()
    val userRole = authProfile?.role ?: UserRole.nurse
    
    val startDest = if (authViewModel.currentUser != null) Routes.DASHBOARD else Routes.LOGIN

    androidx.compose.runtime.LaunchedEffect(authProfile) {
        if (authProfile == null && authViewModel.currentUser == null) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDest,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onAddPatient = { navController.navigate(Routes.ADD_PATIENT) },
                onPatientList = { navController.navigate(Routes.PATIENT_LIST) },
                onAddVaccine = { navController.navigate(Routes.VACCINE_INVENTORY) },
                onStatistics = { navController.navigate(Routes.STATISTICS) },
                onBorrowed = { navController.navigate(Routes.BORROWED) },
                onDue = { navController.navigate(Routes.DUE) },
                onWaste = { navController.navigate(Routes.WASTE) },
                onManageStaff = { navController.navigate(Routes.MANAGE_STAFF) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onSync = { navController.navigate(Routes.SYNC) },
                onAuditLogs = { navController.navigate(Routes.AUDIT_LOGS) },
                onProfile = { navController.navigate(Routes.PROFILE) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNotifications = { navController.navigate(Routes.NOTIFICATION_SETTINGS) },
                onInventory = { navController.navigate(Routes.INVENTORY_SETTINGS) },
                onBackup = { navController.navigate(Routes.BACKUP_SETTINGS) },
                onSecurity = { navController.navigate(Routes.SECURITY_SETTINGS) },
                onHelpSupport = { navController.navigate(Routes.HELP_SUPPORT) },
                onPrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) },
                onTermsOfService = { navController.navigate(Routes.TERMS_OF_SERVICE) },
                onCheckForUpdates = { navController.navigate(Routes.APP_UPDATE) }
            )
        }

        composable(Routes.NOTIFICATION_SETTINGS) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.INVENTORY_SETTINGS) {
            InventorySettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BACKUP_SETTINGS) {
            BackupSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SECURITY_SETTINGS) {
            SecuritySettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HELP_SUPPORT) {
            HelpSupportScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TERMS_OF_SERVICE) {
            TermsOfServiceScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.APP_UPDATE) {
            AppUpdateScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SYNC) {
            SyncScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.AUDIT_LOGS) {
            FullAuditLogScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MANAGE_STAFF) {
            if (userRole == UserRole.admin) {
                ManageStaffScreen(
                    onBack = { navController.popBackStack() },
                    onAddStaff = { navController.navigate(Routes.ADD_STAFF) },
                    onStaffClick = { staffId ->
                        navController.navigate("staff_details/$staffId")
                    }
                )
            } else {
                AccessDeniedScreen { navController.popBackStack() }
            }
        }

        composable(
            route = Routes.STAFF_DETAILS,
            arguments = listOf(navArgument("staffId") { type = NavType.StringType })
        ) { backStackEntry ->
            val staffId = backStackEntry.arguments?.getString("staffId") ?: ""
            if (userRole == UserRole.admin) {
                StaffDetailsScreen(
                    staffId = staffId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("edit_staff/$id") }
                )
            } else {
                AccessDeniedScreen { navController.popBackStack() }
            }
        }

        composable(Routes.ADD_STAFF) {
            if (userRole == UserRole.admin) {
                AddStaffScreen(onBack = { navController.popBackStack() })
            } else {
                AccessDeniedScreen { navController.popBackStack() }
            }
        }

        composable(
            route = Routes.EDIT_STAFF,
            arguments = listOf(navArgument("staffId") { type = NavType.StringType })
        ) { backStackEntry ->
            val staffId = backStackEntry.arguments?.getString("staffId") ?: ""
            if (userRole == UserRole.admin) {
                EditStaffScreen(
                    staffId = staffId,
                    onBack = { navController.popBackStack() }
                )
            } else {
                AccessDeniedScreen { navController.popBackStack() }
            }
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onPatientClick = { patientId ->
                    navController.navigate("patient_details/$patientId")
                }
            )
        }

        composable(Routes.ADD_PATIENT) {
            AddPatientScreen(
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
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
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            PatientDetailsScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() },
                onAddVaccine = { id ->
                    navController.navigate("add_vaccine/$id")
                },
                onAddConsultation = { id ->
                    navController.navigate("add_consultation/$id")
                },
                onEditVaccination = { id ->
                    navController.navigate("edit_vaccination/$id")
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
            val patientId = backStackEntry.arguments?.getString("patientId")
            AddPatientScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() },
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
            val vaccinationId = backStackEntry.arguments?.getString("vaccinationId")
            AddVaccinationScreen(
                vaccinationId = vaccinationId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.VACCINE_INVENTORY) {
            VaccineInventoryScreen(
                onBack = { navController.popBackStack() },
                onAddVaccine = { navController.navigate(Routes.ADD_VACCINE_DEFINITION) },
                onEditVaccine = { id ->
                    navController.navigate("edit_vaccine_definition/$id")
                },
                onAddBatch = { vaccineId, brandName ->
                    navController.navigate("add_batch/$vaccineId/$brandName")
                },
                onEditBatch = { batchId, vaccineId, brandName ->
                    navController.navigate("edit_batch/$batchId?vaccineId=$vaccineId&brandName=$brandName")
                }
            )
        }

        composable(Routes.STATISTICS) {
            StatisticsScreen(
                onBack = { navController.popBackStack() },
                onMonthClick = { monthKey ->
                    navController.navigate("monthly_finance_details/$monthKey")
                }
            )
        }

        composable(
            route = Routes.MONTHLY_FINANCE_DETAILS,
            arguments = listOf(navArgument("monthKey") { type = NavType.StringType }),
        ) { backStackEntry ->
            val monthKey = backStackEntry.arguments?.getString("monthKey") ?: ""
            MonthlyFinanceDetailsScreen(
                monthKey = monthKey,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADD_VACCINE_DEFINITION) {
            AddVaccineScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.EDIT_VACCINE_DEFINITION,
            arguments = listOf(navArgument("vaccineId") { type = NavType.StringType })
        ) { backStackEntry ->
            val vaccineId = backStackEntry.arguments?.getString("vaccineId")
            AddVaccineScreen(
                vaccineId = vaccineId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ADD_BATCH,
            arguments = listOf(
                navArgument("vaccineId") { type = NavType.StringType },
                navArgument("brandName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val vaccineId = backStackEntry.arguments?.getString("vaccineId") ?: ""
            val brandName = backStackEntry.arguments?.getString("brandName") ?: ""
            AddBatchScreen(
                vaccineId = vaccineId,
                brandName = brandName,
                onBack = { navController.popBackStack() }
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
            val batchId = backStackEntry.arguments?.getString("batchId")
            val vaccineId = backStackEntry.arguments?.getString("vaccineId") ?: ""
            val brandName = backStackEntry.arguments?.getString("brandName") ?: ""
            AddBatchScreen(
                batchId = batchId,
                vaccineId = vaccineId,
                brandName = brandName,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BORROWED) {
            BorrowedScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DUE) {
            DueScreen(
                onBack = { navController.popBackStack() },
                onNavigateToCompletedDismissed = {
                    navController.navigate(Routes.COMPLETED_DISMISSED)
                },
                onPatientClick = { patientId ->
                    navController.navigate("patient_details/$patientId")
                }
            )
        }

        composable(Routes.COMPLETED_DISMISSED) {
            CompletedDismissedScreen(
                onBack = { navController.popBackStack() },
                onPatientClick = { patientId ->
                    navController.navigate("patient_details/$patientId")
                }
            )
        }

        composable(Routes.WASTE) {
            WasteScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "add_vaccine_with_details/{patientId}/{vaccineName}",
            arguments = listOf(
                navArgument("patientId") { type = NavType.StringType },
                navArgument("vaccineName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            val vaccineName = backStackEntry.arguments?.getString("vaccineName") ?: ""
            AddVaccinationScreen(
                patientId = patientId,
                initialVaccineName = vaccineName,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ADD_CONSULTATION,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            AddConsultationScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADD_VACCINE) {
            AddVaccinationScreen(
                patientId = "",
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ADD_VACCINE_FOR_PATIENT,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            AddVaccinationScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
fun AccessDeniedScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Access Denied", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
            androidx.compose.material3.Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text("Go Back")
            }
        }
    }
}
