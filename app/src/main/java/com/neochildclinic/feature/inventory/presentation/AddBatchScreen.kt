package com.neochildclinic.feature.inventory.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                    title = {
                        Column {
                            Text(if (batchId != null) "Edit Batch" else "Add Batch")
                            Text(brandName, style = MaterialTheme.typography.labelMedium)
                        }
                    },
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
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.card),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            text = "Batch details for $brandName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        StandardTextField(
                            value = batchNumber,
                            onValueChange = { batchNumber = it },
                            label = "Batch Number *"
                        )

                        StandardTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = "Quantity *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        DateDropdownPicker(
                            label = "Expiry Date *",
                            currentDate = expiryDate,
                            onDateSelected = { expiryDate = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            StandardTextField(
                                value = mrp,
                                onValueChange = { mrp = it },
                                label = "MRP *",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            StandardTextField(
                                value = netRate,
                                onValueChange = { netRate = it },
                                label = "Net Rate *",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        StandardTextField(
                            value = manufacturer,
                            onValueChange = { manufacturer = it },
                            label = "Batch Manufacturer (if different)",
                            placeholder = "e.g. Sanofi"
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.card),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            "Saved offline first — Syncs automatically with clinic cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                StandardButton(
                    onClick = {
                        if (batchNumber.isBlank() || quantity.isBlank() || expiryDate.isBlank() || mrp.isBlank() || netRate.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Please fill all required fields")
                            }
                            return@StandardButton
                        }
                        viewModel.saveBatch(
                            batchId = batchId,
                            vaccineId = vaccineId,
                            batchNumber = batchNumber,
                            quantity = quantity,
                            expiryDate = expiryDate,
                            mrp = mrp.toDoubleOrNull() ?: 0.0,
                            netRate = netRate.toDoubleOrNull() ?: 0.0,
                            manufacturer = if (manufacturer.isBlank()) brandName else manufacturer
                        )
                    },
                    isLoading = uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (batchId != null) "Update Batch" else "Save Batch")
                }
            }
        }
    }
}
