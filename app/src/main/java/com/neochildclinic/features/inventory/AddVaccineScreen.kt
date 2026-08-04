package com.neochildclinic.features.inventory

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.neochildclinic.core.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVaccineScreen(
    vaccineId: String? = null,
    onBack: () -> Unit = {},
    viewModel: AddVaccineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var brandName by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var companyName by rememberSaveable { mutableStateOf("") }
    var mrp by rememberSaveable { mutableStateOf("") }
    var netRate by rememberSaveable { mutableStateOf("") }

    var typeExpanded by remember { mutableStateOf(false) }
    var brandExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(vaccineId) {
        if (vaccineId != null) {
            viewModel.loadVaccine(vaccineId)
        }
    }

    LaunchedEffect(uiState.vaccine) {
        uiState.vaccine?.let {
            brandName = it.brandName
            type = it.type
            companyName = it.companyName
            mrp = if (it.mrp > 0) it.mrp.toString() else ""
            netRate = if (it.netRate > 0) it.netRate.toString() else ""
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "Vaccine definition saved", Toast.LENGTH_SHORT).show()
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
                    title = { Text(if (vaccineId != null) "Edit Vaccine" else "Add Vaccine") },
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
                StandardAutoCompleteField(
                    value = type,
                    onValueChange = { 
                        type = it
                        typeExpanded = true 
                    },
                    label = "Vaccine Type*",
                    placeholder = "e.g. DTaP, BCG, Polio",
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                    dropdownContent = {
                        val filteredTypes = uiState.allTypes.filter { it.contains(type, ignoreCase = true) }
                        filteredTypes.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = {
                                    type = suggestion
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                )

                StandardAutoCompleteField(
                    value = brandName,
                    onValueChange = { 
                        brandName = it
                        brandExpanded = true
                    },
                    label = "Brand Name*",
                    placeholder = "e.g. Pentaxim",
                    expanded = brandExpanded,
                    onExpandedChange = { brandExpanded = it },
                    dropdownContent = {
                        val typeBrands = uiState.brandSuggestions[type] ?: emptyList()
                        val filteredBrands = typeBrands.filter { it.contains(brandName, ignoreCase = true) }
                        filteredBrands.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = {
                                    brandName = suggestion
                                    brandExpanded = false
                                }
                            )
                        }
                    }
                )

                StandardTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = "Manufacturer*",
                    placeholder = "e.g. Sanofi"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StandardTextField(
                        value = mrp,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) mrp = it },
                        label = "MRP (Standard)",
                        placeholder = "0.00",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    StandardTextField(
                        value = netRate,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) netRate = it },
                        label = "Net Rate (Standard)",
                        placeholder = "0.00",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                StandardButton(
                    onClick = {
                        if (brandName.isBlank() || type.isBlank() || companyName.isBlank()) {
                            Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                            return@StandardButton
                        }
                        viewModel.saveVaccine(
                            vaccineId, 
                            brandName, 
                            type, 
                            companyName,
                            mrp.toDoubleOrNull() ?: 0.0,
                            netRate.toDoubleOrNull() ?: 0.0
                        )
                    },
                    isLoading = uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (vaccineId != null) "Update Vaccine" else "Create Vaccine")
                }
            }
        }
    }
}
