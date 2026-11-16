package com.neochildclinic.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `toList returns null for null input`() {
        assertNull(converters.toList(null))
    }

    @Test
    fun `toList returns null for literal string null`() {
        assertNull(converters.toList("null"))
        assertNull(converters.toList("  null  "))
    }

    @Test
    fun `toList returns empty list for blank input`() {
        assertEquals(emptyList<String>(), converters.toList(""))
        assertEquals(emptyList<String>(), converters.toList("   "))
    }

    @Test
    fun `toList decodes valid json array`() {
        val json = """["BCG", "OPV", "HepB"]"""
        assertEquals(listOf("BCG", "OPV", "HepB"), converters.toList(json))
    }

    @Test
    fun `toList decodes comma-separated string fallback`() {
        val csv = "BCG, OPV, HepB"
        assertEquals(listOf("BCG", "OPV", "HepB"), converters.toList(csv))
    }

    @Test
    fun `fromList encodes list to json`() {
        val list = listOf("BCG", "OPV")
        val json = converters.fromList(list)
        assertEquals("""["BCG","OPV"]""", json)
    }

    @Test
    fun `fromList returns null for null input`() {
        assertNull(converters.fromList(null))
    }
}
