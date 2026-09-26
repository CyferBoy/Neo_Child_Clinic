package com.neochildclinic.app

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.neochildclinic.core.session.AuthViewModel
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.features.dashboard.AddStaffScreen
import com.neochildclinic.features.dashboard.EditStaffScreen
import com.neochildclinic.features.dashboard.LoginScreen
import com.neochildclinic.features.dashboard.ManageStaffScreen
import com.neochildclinic.features.dashboard.StaffDetailsScreen
import com.neochildclinic.features.dashboard.DashboardScreen
import com.neochildclinic.features.sync.SyncScreen
import com.neochildclinic.features.audit.FullAuditLogScreen
import com.neochildclinic.features.settings.SettingsScreen
import com.neochildclinic.features.update.AppUpdateScreen
import com.neochildclinic.features.settings.TermsOfServiceScreen
import com.neochildclinic.features.settings.PrivacyPolicyScreen
import com.neochildclinic.features.settings.HelpSupportScreen
import com.neochildclinic.features.settings.SecuritySettingsScreen
import com.neochildclinic.features.settings.BackupSettingsScreen
import com.neochildclinic.features.settings.InventorySettingsScreen
import com.neochildclinic.features.settings.NotificationSettingsScreen
import com.neochildclinic.features.profile.ProfileScreen

@Composable
internal fun AdminGuard(userRole: UserRole?, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (userRole == UserRole.admin) content() else AccessDeniedScreen(onBack)
}

internal fun NavGraphBuilder.dashboardNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    userRole: UserRole?,
    goBack: () -> Unit,
    appUpdateViewModel: com.neochildclinic.features.update.AppUpdateViewModel
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
        val settingsViewModel: com.neochildclinic.features.settings.SettingsViewModel = hiltViewModel()
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
        com.neochildclinic.features.expenses.ExpenseListScreen(
            onBack = goBack,
            onAddExpense = { navController.navigate(Routes.ADD_EXPENSE) },
            onEditExpense = { expenseId -> navController.navigate("edit_expense/$expenseId") }
        )
    }

    composable(Routes.DOCTOR_TIMINGS) {
        com.neochildclinic.features.doctorslots.WeeklyDoctorSlotsScreen(
            onBack = goBack
        )
    }

    composable(Routes.ADD_EXPENSE) {
        com.neochildclinic.features.expenses.AddExpenseScreen(
            expenseId = null,
            onBack = goBack
        )
    }

    composable(
        route = Routes.EDIT_EXPENSE,
        arguments = listOf(navArgument("expenseId") { type = NavType.StringType })
    ) { backStackEntry ->
        val expenseId = backStackEntry.nullableStringArg("expenseId")
        com.neochildclinic.features.expenses.AddExpenseScreen(
            expenseId = expenseId,
            onBack = goBack
        )
    }
}