package com.neochildclinic.domain.repository

import com.neochildclinic.data.repository.SyncRepositoryImpl

enum class SyncState { IDLE, SYNCING, ERROR }

typealias SyncRepository = SyncRepositoryImpl
