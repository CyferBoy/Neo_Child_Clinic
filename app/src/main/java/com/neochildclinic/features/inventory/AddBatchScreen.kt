package com.neochildclinic.features.inventory

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField

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

    LaunchedEffect(uiState.batch, uiState.defaultBatch) {
        uiState.batch?.let { b ->
            batchNumber = b.batchNumber
            quantity = b.remainingQuantity.toString()
            expiryDate = b.expiryDate
            mrp = b.sellingPrice.toString()
            netRate = b.purchaseCost.toString()
            manufacturer = b.manufacturer
        } ?: uiState.defaultBatch?.let { d ->
            mrp = d.sellingPrice.toString()
            netRate = d.purchaseCost.toString()
            manufacturer = d.manufacturer
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "Batch saved", Toast.LENGTH_SHORT).show()
            viewModel.resetState()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.resetState()
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (batchId != null) "Edit Batch" else "Add Batch")
                            Text(brandName, style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Adding batch for $brandName", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                StandardTextField(
                    value = batchNumber,
                    onValueChange = { batchNumber = it },
                    label = "Batch Number*"
                )

                StandardTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = "Quantity*",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                DateDropdownPicker(
                    label = "Expiry Date*",
                    currentDate = expiryDate,
                    onDateSelected = { expiryDate = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(
                        value = mrp,
                        onValueChange = { mrp = it },
                        label = "MRP*",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    StandardTextField(
                        value = netRate,
                        onValueChange = { netRate = it },
                        label = "Net Rate*",
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

                Spacer(modifier = Modifier.height(24.dp))

                StandardButton(
                    onClick = {
                        if (batchNumber.isBlank() || quantity.isBlank() || expiryDate.isBlank() || mrp.isBlank() || netRate.isBlank()) {
                            Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                            return@StandardButton
                        }
                        viewModel.saveBatch(
                            batchId = batchId,
                            vaccineId = vaccineId,
                            batchNumber = batchNumber,
                            quantity = quantity.toIntOrNull() ?: 0,
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
