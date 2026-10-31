package com.neochildclinic.feature.inventory.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.neochildclinic.core.ui.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVaccineScreen(
    vaccineId: String? = null,
    onBack: () -> Unit = {},
    viewModel: AddVaccineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    ShowSnackbar(uiState.error, snackbarHostState) { viewModel.resetState() }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                BackTopAppBar(
                    title = { Text(if (vaccineId != null) "Edit Vaccine" else "Add Vaccine") },
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
                    text = if (vaccineId != null) "Update this vaccine definition" else "Create a vaccine type before adding stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Vaccine identity",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.xs)
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

                StandardAutoCompleteField(
                    value = type,
                    onValueChange = { 
                        type = it
                        typeExpanded = true 
                    },
                    label = "Vaccine Type*",
                    placeholder = "e.g. DTaP-IPV-Hib",
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

                StandardTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = "Manufacturer*",
                    placeholder = "e.g. Sanofi Pasteur"
                )

                Text(
                    text = "Standard pricing",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.xs)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    StandardTextField(
                        value = mrp,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) mrp = it },
                        label = "MRP",
                        placeholder = "₹0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    StandardTextField(
                        value = netRate,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) netRate = it },
                        label = "Net rate",
                        placeholder = "₹0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Standard rates are defaults",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Batches can override these rates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                StandardButton(
                    onClick = {
                        if (brandName.isBlank() || type.isBlank() || companyName.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Please fill all required fields")
                            }
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
                        Text(if (vaccineId != null) "Update Vaccine" else "Create Vaccine")
                    }
                }
            }
        }
    }
}
