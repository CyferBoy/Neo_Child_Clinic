package com.neochildclinic.domain.repository

interface PersonalReminderRepository {
    suspend fun refresh()
}