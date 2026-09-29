package com.neochildclinic.app

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.neochildclinic.core.security.AuthViewModel
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.feature.staff.presentation.AddStaffScreen
import com.neochildclinic.feature.staff.presentation.EditStaffScreen
import com.neochildclinic.feature.auth.presentation.LoginScreen
import com.neochildclinic.feature.staff.presentation.ManageStaffScreen
import com.neochildclinic.feature.staff.presentation.StaffDetailsScreen
import com.neochildclinic.feature.dashboard.presentation.DashboardScreen
import com.neochildclinic.feature.sync.presentation.SyncScreen
import com.neochildclinic.feature.audit.presentation.FullAuditLogScreen
import com.neochildclinic.feature.settings.presentation.SettingsScreen
import com.neochildclinic.feature.update.presentation.AppUpdateScreen
import com.neochildclinic.feature.settings.presentation.TermsOfServiceScreen
import com.neochildclinic.feature.settings.presentation.PrivacyPolicyScreen
import com.neochildclinic.feature.settings.presentation.HelpSupportScreen
import com.neochildclinic.feature.settings.presentation.SecuritySettingsScreen
import com.neochildclinic.feature.settings.presentation.BackupSettingsScreen
import com.neochildclinic.feature.settings.presentation.InventorySettingsScreen
import com.neochildclinic.feature.settings.presentation.NotificationSettingsScreen
import com.neochildclinic.feature.profile.presentation.ProfileScreen

@Composable
internal fun AdminGuard(userRole: UserRole?, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (userRole == UserRole.admin) content() else AccessDeniedScreen(onBack)
}

internal fun NavGraphBuilder.dashboardNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    userRole: UserRole?,
    goBack: () -> Unit,
    appUpdateViewModel: com.neochildclinic.feature.update.presentation.AppUpdateViewModel
) {
    composable(Routes.LOGIN) {
        LoginScreen(
            onLoginSuccess = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            },
            viewModel = authViewModel
        )
    }

    composable(Routes.DASHBOARD) {
        DashboardScreen(
            authViewModel = authViewModel,
            onAddPatient = { navController.navigate(Routes.ADD_PATIENT) },
            onPatientList = { navController.navigate(Routes.PATIENT_LIST) },
            onAddVaccine = { navController.navigate(Routes.VACCINE_INVENTORY) },
            onStatistics = { navController.navigate(Routes.STATISTICS) },
            onBorrowed = { navController.navigate(Routes.BORROWED) },
            onDue = { navController.navigate(Routes.DUE) },
            onWaste = { navController.navigate(Routes.WASTE) },
            onTodayPatients = { navController.navigate("today_patients") },
            onPersonalReminders = { navController.navigate(Routes.PERSONAL_REMINDERS) },
            onExpenses = { navController.navigate(Routes.EXPENSES) },
            onDoctorTimings = { navController.navigate(Routes.DOCTOR_TIMINGS) },
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
        val settingsViewModel: com.neochildclinic.feature.settings.presentation.SettingsViewModel = hiltViewModel()
        SettingsScreen(
            viewModel = settingsViewModel,
            onBack = goBack,
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
        NotificationSettingsScreen(onBack = goBack)
    }

    composable(Routes.INVENTORY_SETTINGS) {
        InventorySettingsScreen(onBack = goBack)
    }

    composable(Routes.BACKUP_SETTINGS) {
        BackupSettingsScreen(onBack = goBack)
    }

    composable(Routes.SECURITY_SETTINGS) {
        SecuritySettingsScreen(onBack = goBack)
    }

    composable(Routes.HELP_SUPPORT) {
        HelpSupportScreen(onBack = goBack)
    }

    composable(Routes.PRIVACY_POLICY) {
        PrivacyPolicyScreen(onBack = goBack)
    }

    composable(Routes.TERMS_OF_SERVICE) {
        TermsOfServiceScreen(onBack = goBack)
    }

    composable(Routes.APP_UPDATE) {
        AppUpdateScreen(onBack = goBack, viewModel = appUpdateViewModel)
    }

    composable(Routes.PROFILE) {
        ProfileScreen(
            onBack = goBack,
            onLogout = {
                authViewModel.logout()
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }

    composable(Routes.SYNC) {
        SyncScreen(
            onBack = goBack,
            isAdmin = userRole == UserRole.admin
        )
    }

    composable(Routes.AUDIT_LOGS) {
        FullAuditLogScreen(onBack = goBack)
    }

    composable(Routes.MANAGE_STAFF) {
        AdminGuard(userRole, goBack) {
            ManageStaffScreen(
                onBack = goBack,
                onAddStaff = { navController.navigate(Routes.ADD_STAFF) },
                onStaffClick = { staffId ->
                    navController.navigate("staff_details/$staffId")
                }
            )
        }
    }

    composable(
        route = Routes.STAFF_DETAILS,
        arguments = listOf(navArgument("staffId") { type = NavType.StringType })
    ) { backStackEntry ->
        val staffId = backStackEntry.stringArg("staffId")
        AdminGuard(userRole, goBack) {
            StaffDetailsScreen(
                staffId = staffId,
                onBack = goBack,
                onEdit = { id -> navController.navigate("edit_staff/$id") }
            )
        }
    }

    composable(Routes.ADD_STAFF) {
        AdminGuard(userRole, goBack) {
            AddStaffScreen(onBack = goBack)
        }
    }

    composable(
        route = Routes.EDIT_STAFF,
        arguments = listOf(navArgument("staffId") { type = NavType.StringType })
    ) { backStackEntry ->
        val staffId = backStackEntry.stringArg("staffId")
        AdminGuard(userRole, goBack) {
            EditStaffScreen(
                staffId = staffId,
                onBack = goBack
            )
        }
    }

    composable(Routes.EXPENSES) {
        com.neochildclinic.feature.finance.presentation.ExpenseListScreen(
            onBack = goBack,
            onAddExpense = { navController.navigate(Routes.ADD_EXPENSE) },
            onEditExpense = { expenseId -> navController.navigate("edit_expense/$expenseId") }
        )
    }

    composable(Routes.DOCTOR_TIMINGS) {
        com.neochildclinic.feature.doctor.presentation.WeeklyDoctorSlotsScreen(
            onBack = goBack
        )
    }

    composable(Routes.ADD_EXPENSE) {
        com.neochildclinic.feature.finance.presentation.AddExpenseScreen(
            expenseId = null,
            onBack = goBack
        )
    }

    composable(
        route = Routes.EDIT_EXPENSE,
        arguments = listOf(navArgument("expenseId") { type = NavType.StringType })
    ) { backStackEntry ->
        val expenseId = backStackEntry.nullableStringArg("expenseId")
        com.neochildclinic.feature.finance.presentation.AddExpenseScreen(
            expenseId = expenseId,
            onBack = goBack
        )
    }
}