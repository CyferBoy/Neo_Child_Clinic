package com.neochildclinic.features.patient

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
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConsultationScreen(
    patientId: String,
    onBack: () -> Unit,
    viewModel: AddConsultationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    val today = remember { SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date()) }
    var date by rememberSaveable { mutableStateOf(today) }
    var amount by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var nextFollowUpDate by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "Consultation saved", Toast.LENGTH_SHORT).show()
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
                    title = { Text("Add Consultation") },
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
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DateDropdownPicker(
                    label = "Consultation Date*",
                    currentDate = date,
                    onDateSelected = { date = it }
                )

                StandardTextField(
                    value = amount,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amount = it },
                    label = "Fees (Amount)*",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                StandardTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Clinical Notes"
                )

                DateDropdownPicker(
                    label = "Next Follow-up Date",
                    currentDate = nextFollowUpDate,
                    onDateSelected = { nextFollowUpDate = it }
                )

                Spacer(modifier = Modifier.weight(1f))

                StandardButton(
                    onClick = {
                        if (amount.isBlank()) {
                            Toast.makeText(context, "Please enter amount", Toast.LENGTH_SHORT).show()
                            return@StandardButton
                        }
                        viewModel.saveConsultation(
                            patientId = patientId,
                            date = date,
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            notes = notes,
                            nextFollowUpDate = nextFollowUpDate
                        )
                    },
                    isLoading = uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Consultation")
                }
            }
        }
    }
}
