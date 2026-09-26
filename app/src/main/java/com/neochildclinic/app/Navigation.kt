package com.neochildclinic.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.*
import com.neochildclinic.core.session.AuthViewModel
import com.neochildclinic.domain.model.UserRole

/** Reads a required nav argument, "" when absent. */
internal fun androidx.navigation.NavBackStackEntry.stringArg(key: String, default: String = ""): String =
    arguments?.getString(key) ?: default

/** Reads a nullable nav argument. */
internal fun androidx.navigation.NavBackStackEntry.nullableStringArg(key: String): String? =
    arguments?.getString(key)

@Composable
fun AppNavigation(
    navController: androidx.navigation.NavHostController = rememberNavController(),
    appUpdateViewModel: com.neochildclinic.features.update.AppUpdateViewModel = hiltViewModel()
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authProfile by authViewModel.profile.collectAsState()
    val isProfileLoading by authViewModel.isProfileLoading.collectAsState()
    // While the authoritative profile is still resolving, don't treat the user as a
    // nurse for route-guarding purposes (see DashboardScreen for the matching gate on
    // the dashboard/drawer itself). Falling through to UserRole.nurse here too early
    // was part of the same intermittent-nurse-view bug.
    val userRole = authProfile?.role ?: if (isProfileLoading) null else UserRole.nurse
    val goBack: () -> Unit = { navController.popBackStack() }

    // The Supabase SDK resolves any session saved to disk asynchronously. Reading
    // currentUser synchronously here would race that resolution and randomly send
    // an already-logged-in user back to the Login screen (or vice versa). Wait for
    // a definitive answer first.
    var resolvedStartDest by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val status = authViewModel.awaitResolvedSessionStatus()
        resolvedStartDest = when {
            status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated -> Routes.DASHBOARD
            status != null -> Routes.LOGIN
            // status == null means awaitResolvedSessionStatus() timed out (see its doc) -
            // almost always no network at launch while a stored session still needs a
            // refresh it can't complete. Fall back to whatever's already cached in memory
            // rather than sitting on the loading screen below forever - a previously
            // logged-in user keeps full offline access to their local data.
            authViewModel.currentUser != null -> Routes.DASHBOARD
            else -> Routes.LOGIN
        }
    }

    val startDest = resolvedStartDest
    if (startDest == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = com.neochildclinic.R.drawable.app_logo),
                contentDescription = "Clinic Logo",
                modifier = Modifier.size(140.dp)
            )
        }
        return
    }

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
        dashboardNavGraph(navController, authViewModel, userRole, goBack, appUpdateViewModel)
        patientGraph(navController, goBack)
        inventoryGraph(navController, goBack)
        statisticsGraph(navController, userRole, goBack)
        reminderGraph(navController, goBack)
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