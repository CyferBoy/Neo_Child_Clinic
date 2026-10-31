package com.neochildclinic.feature.vaccination.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.designsystem.*

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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(Spacing.card), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                "Payment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StandardTextField(
                    value = cash,
                    onValueChange = onCashChange,
                    label = "Cash",
                    placeholder = "₹0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                StandardTextField(
                    value = online,
                    onValueChange = onOnlineChange,
                    label = "Online",
                    placeholder = "₹0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total amount",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    com.neochildclinic.core.common.CurrencyUtils.formatInr(total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
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
                    Text("With Fees", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = doctorsAcc,
                        onCheckedChange = onAccToggle
                    )
                    Text("Doctor's Account", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}