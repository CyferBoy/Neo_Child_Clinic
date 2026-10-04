package com.neochildclinic.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Regression guard for batch navigation breaking on real brand names.
 *
 * `brandName` is free text typed by the user in AddVaccineScreen, so it routinely contains
 * spaces ("Serum Institute") and sometimes reserved URI characters. NavigationInventoryGraph
 * used to interpolate it straight into the route, producing a URI that Navigation Compose
 * cannot match - tapping Edit/Add Batch simply did nothing. The statistics graph already
 * encoded on navigate and decoded on read; the inventory graph did neither.
 *
 * [navDecodes] mimics what Navigation actually does with a matched argument: it percent-decodes
 * only, and leaves '+' alone (java.net.URLDecoder would wrongly turn '+' into a space, which is
 * exactly why the pairing must be URLEncoder -> Navigation -> URLDecoder).
 */
class BatchRouteTest {

    /** Brand names that break naive interpolation. */
    private val brands = listOf(
        "Serum Institute of India",
        "Panacea",
        "GlaxoSmithKline",
        "Pfizer & Co",
        "Sanofi/Aventis",
        "Dr. Reddy's",
        "100% Pure",
        "Brand #1",
        "Already+Encoded",
        ""
    )

    @Test
    fun `add_batch route keeps brand name intact through navigation`() {
        brands.forEach { brand ->
            val route = addBatchRoute("vaccine-1", brand)
            assertNoRawSpace(route, "add_batch", brand)

            // brandName is a PATH segment: Navigation matches it raw and hands the still-encoded
            // value to the destination, so the screen must decode it itself.
            val segment = route.split("/").last()
            assertEquals(brand, dec(segment))
        }
    }

    @Test
    fun `edit_batch route keeps brand name intact through navigation`() {
        brands.forEach { brand ->
            val route = editBatchRoute("batch-1", "vaccine-1", brand)
            assertNoRawSpace(route, "edit_batch", brand)

            // brandName is a QUERY parameter: Navigation percent-decodes it while matching, so the
            // destination must use the value as-is. Decoding again would corrupt a literal '+'.
            assertEquals(brand, navDecodes(queryParam(route, "brandName")))
        }
    }

    @Test
    fun `edit_batch route cannot be broken by reserved characters in the brand`() {
        // A brand containing '&' must not be able to forge an extra query parameter.
        val route = editBatchRoute("batch-1", "vaccine-1", "Pfizer & Co")
        assertEquals(
            listOf("vaccineId", "brandName"),
            route.substringAfter("?").split("&").map { it.substringBefore("=") }
        )
    }

    /** Navigation decodes %XX escapes but leaves '+' as a literal plus. */
    private fun navDecodes(raw: String): String =
        java.util.regex.Pattern.compile("%([0-9A-Fa-f]{2})")
            .matcher(raw)
            .let { m ->
                val sb = StringBuilder()
                var last = 0
                while (m.find()) {
                    sb.append(raw, last, m.start())
                    sb.append(m.group(1).toInt(16).toChar())
                    last = m.end()
                }
                sb.append(raw.substring(last)).toString()
            }

    private fun queryParam(route: String, name: String): String =
        route.substringAfter("?")
            .split("&")
            .first { it.substringBefore("=") == name }
            .substringAfter("=")

    private fun assertNoRawSpace(route: String, feature: String, brand: String) {
        assertFalse(
            "raw space in $feature route breaks Navigation matching (brand=\"$brand\"): $route",
            route.contains(' ')
        )
    }
}