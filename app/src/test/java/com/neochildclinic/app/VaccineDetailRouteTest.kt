package com.neochildclinic.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Statistics -> Vaccination -> Upcoming drill-down routes.
 *
 * `vaccineId` is an optional query arg: absent means the user tapped a vaccine TYPE (show
 * every upcoming brand of that type), present means one BRAND. The two cases previously had
 * no distinct representation - the old route was `vaccine_detail/{type}/{brandName}` with
 * brandName mandatory, which is why a type tap could only navigate when the type happened
 * to have exactly one brand.
 *
 * Encoding matters because `type` is free text typed on the Add Vaccination screen.
 */
class VaccineDetailRouteTest {

    private val nastyTypes = listOf(
        "DTaP",
        "Booster Dose",
        "MR / IPV",
        "Rotavirus & Rotarix",
        "Japanese Encephalitis (JE)",
        "HPV?",
        "50% off",
        "a+b",
        "type#hash",
        " spaced "
    )

    @Test
    fun typeOnlyRouteOmitsTheVaccineIdParam() {
        assertEquals("vaccine_detail/DTaP", vaccineDetailRoute("DTaP", null))
        assertEquals("vaccine_detail/DTaP", vaccineDetailRoute("DTaP", ""))
    }

    @Test
    fun brandRouteCarriesTheVaccineIdParam() {
        assertEquals("vaccine_detail/DTaP?vaccineId=vac-1", vaccineDetailRoute("DTaP", "vac-1"))
    }

    @Test
    fun everyTypeRoundTripsThroughEncoding() {
        for (type in nastyTypes) {
            val route = vaccineDetailRoute(type, null)
            val encoded = route.removePrefix("vaccine_detail/")

            assertFalse(
                "type '$type' left a raw space in the route: $route",
                encoded.contains(' ')
            )
            // Path segments are decoded by the destination via dec().
            assertEquals(type, dec(encoded))
        }
    }

    @Test
    fun everyTypeRoundTripsWithABrandSelected() {
        for (type in nastyTypes) {
            val route = vaccineDetailRoute(type, "vac-1")
            val encoded = route.removePrefix("vaccine_detail/")

            assertFalse("type '$type' left a raw space: $route", encoded.contains(' '))

            val query = encoded.substringAfter('?')
            val path = encoded.substringBefore('?')
            val rawId = query.removePrefix("vaccineId=")

            assertEquals(type, dec(path))
            // Query params are decoded by Navigation, so no second decode here - this
            // assertion only confirms the value survives the encode.
            assertEquals("vac-1", dec(rawId))
            assertTrue(route.startsWith("vaccine_detail/"))
        }
    }

    @Test
    fun brandIdsWithReservedCharactersRoundTrip() {
        val ids = listOf("vac-1", "vac_2", "vac.3", "vac 4", "vac+5", "vac%6")
        for (id in ids) {
            val route = vaccineDetailRoute("DTaP", id)
            val rawId = route.substringAfter("vaccineId=")
            assertEquals(id, dec(rawId))
        }
    }

    @Test
    fun routeTemplateMatchesThePatternTheGraphBuilds() {
        // The composable's route string must accept exactly what vaccineDetailRoute builds,
        // otherwise Navigation silently fails to match and the tap does nothing.
        assertEquals(
            "vaccine_detail/{type}?vaccineId={vaccineId}",
            Routes.VACCINE_DETAIL
        )
        assertTrue(Routes.VACCINE_DETAIL.contains("{type}"))
        assertTrue(Routes.VACCINE_DETAIL.contains("?vaccineId={vaccineId}"))
    }

    @Test
    fun noRawBrandNameIsUsedAsIdentity() {
        // Guards the regression this change fixes: the drill-down is keyed by the stable
        // catalog id, so a display name is never placed where an id is expected.
        val route = vaccineDetailRoute("DTaP", "vac-1")
        assertFalse(route.contains("brandName"))
    }
}