package com.neochildclinic.domain.model

data class ClinicStats(
    val todayVaccinations: Int = 0,
    val todayRevenue: Double = 0.0,
    val todayCash: Double = 0.0,
    val todayOnline: Double = 0.0,
    val monthlyVaccinations: Int = 0,
    val monthlyRevenue: Double = 0.0,
    val dueToday: Int = 0,
    val overdue: Int = 0,
    val lowStockCount: Int = 0,
    val topVaccines: List<Pair<String, Int>> = emptyList()
)
