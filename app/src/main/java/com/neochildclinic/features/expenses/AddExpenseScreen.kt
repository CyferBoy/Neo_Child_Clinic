package com.neochildclinic.features.expenses

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    expenseId: String? = null,
    onBack: () -> Unit = {},
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(expenseId) {
        if (!expenseId.isNullOrBlank()) viewModel.loadForEdit(expenseId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, if (uiState.isEditMode) "Expense updated" else "Expense added", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                val fileName = it.lastPathSegment ?: "receipt_${System.currentTimeMillis()}"
                viewModel.uploadAttachment(fileName, bytes)
            }
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (uiState.isEditMode) "Edit Expense" else "Add Expense") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DateDropdownPicker(
                    label = "Expense Date*",
                    currentDate = uiState.expenseDate,
                    onDateSelected = viewModel::onDateChange,
                    modifier = Modifier.fillMaxWidth()
                )

                CategoryDropdown(
                    selected = uiState.category,
                    onSelected = viewModel::onCategoryChange
                )

                StandardTextField(
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChange,
                    label = "Title*"
                )

                StandardTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = "Description"
                )

                StandardTextField(
                    value = uiState.amount,
                    onValueChange = viewModel::onAmountChange,
                    label = "Amount (₹)*",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                PaymentMethodDropdown(
                    selected = uiState.paymentMethod,
                    onSelected = viewModel::onPaymentMethodChange
                )

                StandardTextField(
                    value = uiState.referenceNumber,
                    onValueChange = viewModel::onReferenceNumberChange,
                    label = "Reference Number",
                    placeholder = "e.g. Cheque/UTR number (optional)"
                )

                AttachmentSection(
                    attachmentPath = uiState.attachmentPath,
                    isUploading = uiState.isUploadingAttachment,
                    onAttach = { launcher.launch("*/*") },
                    onRemove = viewModel::removeAttachment
                )

                Spacer(Modifier.height(8.dp))

                StandardButton(
                    onClick = { viewModel.submit() },
                    isLoading = uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isEditMode) "Update Expense" else "Save Expense")
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: ExpenseCategory?,
    onSelected: (ExpenseCategory) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Category*") },
            placeholder = { Text("Select a category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExpenseCategory.entries.forEach { category ->
                DropdownMenuItem(text = { Text(category.label) }, onClick = {
                    onSelected(category)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodDropdown(
    selected: ExpensePaymentMethod?,
    onSelected: (ExpensePaymentMethod) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Payment Method*") },
            placeholder = { Text("Select a payment method") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExpensePaymentMethod.entries.forEach { method ->
                DropdownMenuItem(text = { Text(method.label) }, onClick = {
                    onSelected(method)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun AttachmentSection(
    attachmentPath: String?,
    isUploading: Boolean,
    onAttach: () -> Unit,
    onRemove: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isUploading -> "Uploading receipt..."
                        attachmentPath != null -> "Receipt attached"
                        else -> "No receipt attached"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else if (attachmentPath != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                }
            } else {
                TextButton(onClick = onAttach) {
                    Text("Attach Receipt")
                }
            }
        }
    }
}
