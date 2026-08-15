package com.neochildclinic.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.AppBackground

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    AppBackground {
        Scaffold(topBar = { SettingsDetailTopBar("Privacy Policy", onBack) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Text("Privacy Policy", style = MaterialTheme.typography.headlineSmall) }
                item { Text("Last updated: 15 August 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                item {
                    PolicySection("1. Introduction",
                        "Vaccine Manager is a clinic management application designed to help authorized users manage patient and vaccination records, vaccine inventory, reminders, synchronization and related administrative tasks.")
                }
                item {
                    PolicySection("2. Information We Handle",
                        "Depending on the features used by your clinic, the application may handle patient details, vaccination records, vaccine and batch information, due dates, staff account information, device notification tokens, app version information and synchronization information.")
                }
                item {
                    PolicySection("3. How Information Is Used",
                        "Information is used to provide the application's clinical and administrative functions, maintain vaccination schedules and records, manage inventory, synchronize authorized data, deliver relevant notifications, maintain application security, troubleshoot problems and provide application updates.")
                }
                item {
                    PolicySection("4. Backend and Cloud Services",
                        "The application uses Supabase services for its backend and data synchronization. Access to backend data is intended to be controlled through authentication and database authorization policies.")
                }
                item {
                    PolicySection("5. Push Notifications",
                        "Firebase Cloud Messaging (FCM) may be used to deliver application notifications, including vaccination reminders, inventory or synchronization alerts, and application update notifications. A device notification token may be stored for this purpose.")
                }
                item {
                    PolicySection("6. Application Updates",
                        "Application updates may be distributed through GitHub Releases. The application may check for newer releases and may download a release APK when the user chooses to update. Mandatory updates may be required for continued use.")
                }
                item {
                    PolicySection("7. Data Security",
                        "Reasonable technical and organizational measures should be used to protect information, including authenticated access, encrypted network communication and server-side protection of sensitive credentials. No method of electronic storage or transmission can be guaranteed to be completely secure.")
                }
                item {
                    PolicySection("8. Authorized Access",
                        "Patient information should only be accessed by users who are authorized by the clinic or organization. Users are responsible for maintaining the confidentiality of credentials and for using patient information only for authorized purposes.")
                }
                item {
                    PolicySection("9. Data Retention and Deletion",
                        "Patient and account records may be retained according to the clinic's operational, legal and record-retention requirements. Requests concerning correction or deletion should be handled through the clinic or system administrator in accordance with applicable requirements.")
                }
                item {
                    PolicySection("10. Third-Party Services",
                        "The application may use third-party infrastructure or services including Supabase, Firebase Cloud Messaging and GitHub for backend services, notifications and application distribution. Their respective services may process technical information as required to provide those services.")
                }
                item {
                    PolicySection("11. Children's Information",
                        "Vaccination records may relate to children. The application is intended for authorized clinic or healthcare personnel and is not intended to be used directly by children.")
                }
                item {
                    PolicySection("12. Changes to This Policy",
                        "This Privacy Policy may be updated when the application, services or applicable requirements change. The updated version should be made available through the application.")
                }
                item {
                    PolicySection("13. Contact",
                        "For questions or support regarding Vaccine Manager, contact neochildclinic.sbg@gmail.com.")
                }
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
