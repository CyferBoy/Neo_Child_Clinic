package com.neochildclinic.finalfix3

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the final architecture residuals: the refresh pipeline and shared UI live in
 * core, feature code never imports app, and the two platform helpers are reached through
 * domain contracts. Source-level checks keep this independent of Gradle/Hilt wiring.
 */
class ArchitectureResidualTest {

    @Test
    fun `refresh use case lives in core sync only`() {
        assertTrue(File(mainRoot(), "core/sync/RefreshDataUseCase.kt").isFile)
        assertFalse(File(mainRoot(), "app/sync/RefreshDataUseCase.kt").exists())
    }

    @Test
    fun `no feature source imports app`() {
        val violations = mutableListOf<String>()
        File(mainRoot(), "feature").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (line.trim().startsWith("import com.neochildclinic.app.")) {
                            violations += "${file.name}:${index + 1}"
                        }
                    }
                }
            }
        assertTrue("feature -> app imports remain: $violations", violations.isEmpty())
    }

    @Test
    fun `dashboard gets the drawer from core ui`() {
        val dashboard = File(mainRoot(), "feature/dashboard/presentation/DashboardScreen.kt").readText()
        assertTrue(dashboard.contains("import com.neochildclinic.core.ui.AppDrawer"))
        assertTrue(File(mainRoot(), "core/ui/AppDrawer.kt").isFile)
    }

    @Test
    fun `widget uses a UI model and no app activity or room entity`() {
        val widget = File(mainRoot(), "feature/widget/presentation/VaccineWidget.kt").readText()
        assertFalse(widget.contains("import com.neochildclinic.app.MainActivity"))
        assertFalse(widget.contains("import com.neochildclinic.data.local.entity"))
        assertTrue(widget.contains("ComponentName(context, \"com.neochildclinic.app.MainActivity\")"))
        assertTrue(widget.contains("WidgetDueItem"))
    }

    @Test
    fun `update and backup helpers are injected via domain contracts`() {
        val repository = File(mainRoot(), "domain/repository/AppUpdateManager.kt").readText()
        val scheduler = File(mainRoot(), "domain/repository/BackupAutoScheduler.kt").readText()
        assertTrue(repository.contains("interface AppUpdateManager"))
        assertTrue(scheduler.contains("interface BackupAutoScheduler"))

        val module = File(mainRoot(), "di/RepositoryModule.kt").readText()
        assertTrue(module.contains("AppUpdateManager<com.neochildclinic.feature.update.data.AppUpdateInfo>"))
        assertTrue(module.contains("): BackupAutoScheduler"))
    }

    private fun mainRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(dir, "app/src/main/java/com/neochildclinic")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile ?: return dir
        }
        error("Could not locate app/src/main/java/com/neochildclinic")
    }
}
