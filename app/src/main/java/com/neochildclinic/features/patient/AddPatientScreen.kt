package com.neochildclinic.features.patient

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.core.utils.PatientUtils.calculateDetailedAge
import com.neochildclinic.core.utils.PatientUtils.formatDateForDisplay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddPatientScreen(
    patientId: String? = null,
    onBack: () -> Unit = {},
    onNavigateToDetails: (String) -> Unit = {},
    viewModel: PatientViewModel = hiltViewModel(),
) {
    // Form State
    var clinicId by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var alternatePhone by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("Male") }
    var address by rememberSaveable { mutableStateOf("") }
    
    // For age selection
    var ageValue by rememberSaveable { mutableStateOf("0") }
    var ageUnit by rememberSaveable { mutableStateOf("Years") }
    
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    var registrationDate by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current

    // Load data if in edit mode
    LaunchedEffect(patientId) {
        if (patientId != null) {
            isEditMode = true
            isLoading = true
            val patient = viewModel.getPatientById(patientId)
            if (patient != null) {
                clinicId = if (patient.patientClinicId.startsWith("TEMP-")) "" else patient.patientClinicId
                name = patient.name
                phone = patient.phone
                alternatePhone = patient.alternatePhone
                dob = formatDateForDisplay(patient.dob)
                gender = patient.gender
                address = patient.address
                registrationDate = patient.registrationDate
                
                val detailedAge = calculateDetailedAge(patient.dob)
                ageValue = detailedAge.first.toString()
                ageUnit = detailedAge.second
            }
            isLoading = false
        }
    }

    AddPatientContent(
        isEditMode = isEditMode,
        onBack = onBack,
        clinicId = clinicId,
        onClinicIdChange = { clinicId = it },
        name = name,
        onNameChange = { name = it },
        phone = phone,
        onPhoneChange = { phone = it },
        alternatePhone = alternatePhone,
        onAlternatePhoneChange = { alternatePhone = it },
        dob = dob,
        onDobChange = { selectedDob ->
            dob = selectedDob
            val detailedAge = calculateDetailedAge(selectedDob)
            ageValue = detailedAge.first.toString()
            ageUnit = detailedAge.second
        },
        ageValue = ageValue,
        onAgeValueChange = { newVal ->
            ageValue = newVal
            val cal = Calendar.getInstance()
            val value = newVal.toIntOrNull() ?: 0
            when (ageUnit) {
                "Years" -> cal.add(Calendar.YEAR, -value)
                "Months" -> cal.add(Calendar.MONTH, -value)
                "Weeks" -> cal.add(Calendar.WEEK_OF_YEAR, -value)
            }
            dob = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(cal.time)
        },
        ageUnit = ageUnit,
        onAgeUnitChange = { newUnit ->
            ageUnit = newUnit
            val cal = Calendar.getInstance()
            val value = ageValue.toIntOrNull() ?: 0
            when (newUnit) {
                "Years" -> cal.add(Calendar.YEAR, -value)
                "Months" -> cal.add(Calendar.MONTH, -value)
                "Weeks" -> cal.add(Calendar.WEEK_OF_YEAR, -value)
            }
            dob = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(cal.time)
        },
        gender = gender,
        onGenderChange = { gender = it },
        address = address,
        onAddressChange = { address = it },
        isLoading = isLoading,
        onSave = {
            if (name.isBlank() || dob.isBlank()) {
                Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            } else {
                isLoading = true
                val patient = Patient(
                    id = patientId ?: UUID.randomUUID().toString(),
                    patientClinicId = clinicId,
                    name = name,
                    phone = phone,
                    alternatePhone = alternatePhone,
                    dob = dob,
                    gender = gender,
                    address = address,
                    registrationDate = if (isEditMode) registrationDate else SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
                )

                viewModel.savePatient(patient) {
                    isLoading = false
                    Toast.makeText(context, if (isEditMode) "Patient Updated" else "Patient Added", Toast.LENGTH_SHORT).show()
                    if (isEditMode) onBack() else onNavigateToDetails(patient.id)
                }
            }
        }
    )
}
