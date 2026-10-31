package com.neochildclinic.feature.inventory.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBatchScreen(
    vaccineId: String,
    brandName: String,
    batchId: String? = null,
    onBack: () -> Unit = {},
    viewModel: AddBatchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var batchNumber by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var expiryDate by rememberSaveable { mutableStateOf("") }
    var mrp by rememberSaveable { mutableStateOf("") }
    var netRate by rememberSaveable { mutableStateOf("") }
    var manufacturer by rememberSaveable { mutableStateOf("") }
    var customSellingPrice by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(batchId, vaccineId) {
        if (batchId != null) {
            viewModel.loadBatch(batchId)
        } else {
            viewModel.loadDefaults(vaccineId)
        }
    }

    LaunchedEffect(uiState.batch, uiState.defaultMrp, uiState.defaultNetRate, uiState.defaultManufacturer) {
        uiState.batch?.let { b ->
            batchNumber = b.batchNumber
            quantity = b.remainingQuantity.toString()
            expiryDate = b.expiryDate
            mrp = b.sellingPrice.toString()
            netRate = b.purchaseCost.toString()
            manufacturer = b.manufacturer
        } ?: run {
            uiState.defaultMrp?.let { mrp = it.toString() }
            uiState.defaultNetRate?.let { netRate = it.toString() }
            uiState.defaultManufacturer?.let { manufacturer = it }
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "Batch saved", Toast.LENGTH_SHORT).show()
            viewModel.resetState()
            onBack()
        }
    }

    ShowSnackbar(uiState.error, snackbarHostState) { viewModel.resetState() }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                BackTopAppBar(
                    title = { Text(if (batchId != null) "Edit Batch" else "Add Batch") },
                    onBack = onBack
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = if (batchId != null) "Batch $batchNumber · $brandName" else "Adding a batch for $brandName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFE0EBE2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.card),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4F6F5A),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Vaccines,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = brandName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF244032)
                            )
                            if (manufacturer.isNotBlank()) {
                                Text(
                                    text = manufacturer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF244032).copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.Bottom
                ) {
                    StandardTextField(
                        value = batchNumber,
                        onValueChange = { batchNumber = it },
                        label = "Batch number *",
                        placeholder = "e.g. PXM24B",
                        modifier = Modifier.weight(1f)
                    )

                    DateDropdownPicker(
                        label = "Expiry date *",
                        currentDate = expiryDate,
                        onDateSelected = { expiryDate = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    StandardTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = "Quantity *",
                        placeholder = "0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    StandardTextField(
                        value = mrp,
                        onValueChange = { mrp = it },
                        label = "MRP *",
                        placeholder = "₹0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    StandardTextField(
                        value = netRate,
                        onValueChange = { netRate = it },
                        label = "Net rate *",
                        placeholder = "₹0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                StandardTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = "Batch manufacturer (if different)",
                    placeholder = "Optional"
                )

                Surface(
                    color = Color(0xFFE4EDFD),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color(0xFF0B357E),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Batch passport",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0B357E)
                            )
                            Text(
                                text = "Number, expiry and rates follow each dose.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF0B357E).copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selling price",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Optional",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                StandardTextField(
                    value = customSellingPrice,
                    onValueChange = { customSellingPrice = it },
                    label = "Selling price per dose",
                    placeholder = "Defaults to MRP",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Text(
                    text = "Used for receipts and finance reports.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                StandardButton(
                    onClick = {
                        if (batchNumber.isBlank() || quantity.isBlank() || expiryDate.isBlank() || mrp.isBlank() || netRate.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Please fill all required fields")
                            }
                            return@StandardButton
                        }
                        val effectiveMrp = customSellingPrice.toDoubleOrNull() ?: mrp.toDoubleOrNull() ?: 0.0
                        viewModel.saveBatch(
                            batchId = batchId,
                            vaccineId = vaccineId,
                            batchNumber = batchNumber,
                            quantity = quantity,
                            expiryDate = expiryDate,
                            mrp = effectiveMrp,
                            netRate = netRate.toDoubleOrNull() ?: 0.0,
                            manufacturer = if (manufacturer.isBlank()) brandName else manufacturer
                        )
                    },
                    isLoading = uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(if (batchId != null) "Update Batch" else "Save Batch")
                    }
                }
            }
        }
    }
}
