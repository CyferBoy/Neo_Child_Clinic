package com.neochildclinic.features.vaccination

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.StandardTextField

@Composable
internal fun PaymentSection(
    cash: String,
    online: String,
    total: Double,
    withFees: Boolean,
    doctorsAcc: Boolean,
    onCashChange: (String) -> Unit,
    onOnlineChange: (String) -> Unit,
    onFeesToggle: (Boolean) -> Unit,
    onAccToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StandardTextField(
                    value = cash,
                    onValueChange = onCashChange,
                    label = "Cash Amount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                StandardTextField(
                    value = online,
                    onValueChange = onOnlineChange,
                    label = "Online Amount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Amount", style = MaterialTheme.typography.titleMedium)
                Text(
                    "₹$total",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = withFees,
                        onCheckedChange = onFeesToggle
                    )
                    Text("With Fees")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = doctorsAcc,
                        onCheckedChange = onAccToggle
                    )
                    Text("Doctor's Account")
                }
            }
        }
    }
}