package com.neochildclinic.core.utils

import java.util.*
import java.util.concurrent.TimeUnit

sealed class DateCategory {
    data class Overdue(val days: Int) : DateCategory()
    object Today : DateCategory()
    object Tomorrow : DateCategory()
    data class Future(val dateStr: String) : DateCategory()
}

object DateClassifier {

    /**
     * Helper to get a normalized Calendar at start of day.
     */
    fun getTodayStart(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    /**
     * Classifies a date string into clinic-friendly categories.
     * Uses device local time.
     * Dates before today are strictly classified as Overdue.
     */
    fun classify(dateStr: String, todayStart: Calendar = getTodayStart()): DateCategory {
        val targetDate = PatientUtils.parseDate(dateStr) ?: return DateCategory.Future(dateStr)
        
        val target = Calendar.getInstance().apply {
            time = targetDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMs = target.timeInMillis - todayStart.timeInMillis
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs).toInt()

        return when {
            // A date before today is strictly overdue. There is no grace-period
            // exception in the Due tab.
            diffDays < 0 -> DateCategory.Overdue(-diffDays)
            diffDays == 0 -> DateCategory.Today
            diffDays == 1 -> DateCategory.Tomorrow
            else -> DateCategory.Future(PatientUtils.formatDateForDisplay(dateStr))
        }
    }

    /**
     * Formats the classification for display.
     */
    fun formatDisplay(dateStr: String, todayStart: Calendar = getTodayStart()): String {
        return when (val category = classify(dateStr, todayStart)) {
            is DateCategory.Overdue -> PatientUtils.formatDateForDisplay(dateStr)
            is DateCategory.Today -> "Today"
            is DateCategory.Tomorrow -> "Tomorrow"
            is DateCategory.Future -> category.dateStr
        }
    }

    /**
     * Raw chronological weight (ascending = earliest date first), regardless of
     * overdue status. Used where a list is already filtered/bucketed by category
     * (e.g. the Due tab's category filter) and only needs a tie-breaker within
     * that single bucket - NOT for lists that mix overdue and upcoming items
     * together, which should use [dueDateComparator] instead.
     */
    fun getSortWeight(dateStr: String): Long {
        val targetDate = PatientUtils.parseDate(dateStr) ?: return Long.MAX_VALUE
        return targetDate.time
    }

    /**
     * Unified comparator for sorting dates:
     * 1. Non-overdue (today/tomorrow/future) first, nearest date first.
     * 2. Overdue always last, newest (least overdue) date first.
     *
     * A plain ascending sort by raw timestamp puts overdue items - which are
     * always in the past - at the very TOP, which is the opposite of what the
     * Due widget/list wants. This comparator buckets by overdue status first so
     * overdue items always sort after every upcoming item, regardless of date.
     */
    fun dueDateComparator(todayStart: Calendar = getTodayStart()): Comparator<String> =
        compareBy<String> { classify(it, todayStart) is DateCategory.Overdue }
            .thenComparator { a, b ->
                val timeA = PatientUtils.parseDate(a)?.time ?: Long.MAX_VALUE
                val timeB = PatientUtils.parseDate(b)?.time ?: Long.MAX_VALUE
                val isOverdue = classify(a, todayStart) is DateCategory.Overdue
                // Overdue: newest date first (descending). Everything else: nearest date first (ascending).
                if (isOverdue) timeB.compareTo(timeA) else timeA.compareTo(timeB)
            }
}
