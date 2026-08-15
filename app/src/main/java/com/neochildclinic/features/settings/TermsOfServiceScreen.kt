package com.neochildclinic.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.AppBackground

@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    AppBackground {
        Scaffold(topBar = { SettingsDetailTopBar("Terms of Service", onBack) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Text("Terms of Service", style = MaterialTheme.typography.headlineSmall) }
                item { Text("Last updated: 15 August 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                item { TermsSection("1. Acceptance of Terms", "By accessing or using Vaccine Manager, you agree to these Terms of Service. If you do not agree, do not use the application.") }
                item { TermsSection("2. Intended Use", "Vaccine Manager is intended for authorized clinic and healthcare administrative use, including management of patient vaccination records, vaccine inventory, reminders and related information.") }
                item { TermsSection("3. Authorized Users", "Only users authorized by the clinic or organization may access the application and patient information. Accounts and credentials must not be shared with unauthorized persons.") }
                item { TermsSection("4. User Responsibilities", "Users are responsible for entering accurate information, protecting credentials, maintaining patient confidentiality and using the application only for legitimate and authorized purposes.") }
                item { TermsSection("5. Medical Disclaimer", "Vaccine Manager is a record-management and administrative tool. It does not replace professional medical judgment, clinical guidelines, vaccination recommendations, diagnosis or treatment by a qualified healthcare professional.") }
                item { TermsSection("6. Patient Records", "The clinic and its authorized users remain responsible for the accuracy, completeness, review and appropriate use of patient records entered into the application.") }
                item { TermsSection("7. Availability and Third-Party Services", "Availability may be affected by maintenance, internet connectivity, device conditions, server problems or third-party services. The application may depend on services such as Supabase, Firebase Cloud Messaging and GitHub.") }
                item { TermsSection("8. Updates", "The application may receive bug fixes, security updates, feature updates and mandatory or optional releases. Users may be required to install certain updates to continue using the application.") }
                item { TermsSection("9. Prohibited Use", "Users must not attempt unauthorized access, bypass security controls, access another user's patient information without authorization, interfere with the service, or use the application for unlawful purposes.") }
                item { TermsSection("10. Intellectual Property", "The Vaccine Manager application, its original software, design, branding and original content are protected by applicable intellectual-property laws. No ownership rights are transferred by use of the application.") }
                item { TermsSection("11. Account and Security", "Users must take reasonable steps to protect their account credentials and devices. Suspected unauthorized access should be reported to the clinic administrator or support contact promptly.") }
                item { TermsSection("12. Suspension or Termination", "Access may be suspended or terminated when required for security, misuse, unauthorized access, operational reasons or violation of these terms.") }
                item { TermsSection("13. Changes to These Terms", "These Terms of Service may be updated as the application or its services change. The updated version should be made available through the application.") }
                item { TermsSection("14. Contact", "For questions or support regarding Vaccine Manager, contact neochildclinic.sbg@gmail.com.") }
            }
        }
    }
}

@Composable
private fun TermsSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
