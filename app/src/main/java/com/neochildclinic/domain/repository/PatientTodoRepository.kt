package com.neochildclinic.domain.repository

interface PatientTodoRepository {
    suspend fun refresh()
}