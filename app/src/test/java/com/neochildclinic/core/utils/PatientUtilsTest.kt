package com.neochildclinic.core.utils

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.*

class PatientUtilsTest {

    @Test
    fun testParseIsoTimestamp() {
        val isoTimestamp = "2026-08-05T23:02:37.424+05:30"
        val date = PatientUtils.parseDate(isoTimestamp)
        assertNotNull("Date should not be null for ISO timestamp", date)
    }

    @Test
    fun testParseIsoTimestampWithMillis() {
        val isoTimestamp = "2026-08-05T23:02:37.424193+05:30"
        val date = PatientUtils.parseDate(isoTimestamp)
        assertNotNull("Date should not be null for ISO timestamp with microseconds", date)
    }
}
