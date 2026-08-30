package com.trymeon.app.ui.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ME tab's menu and the navigation graph have to agree.
 *
 * They are separate files that used to repeat the same string literals, so a
 * rename in one would have produced a menu entry leading nowhere — the sort of
 * break that shows up as a dead tap rather than a failure. Reading the sources
 * is crude, but it checks the thing that actually breaks.
 */
class FeatureRoutesTest {

    private val nav = File("src/main/java/com/trymeon/app/ui/navigation/AppNavigation.kt").readText()
    private val profile = File("src/main/java/com/trymeon/app/ui/screens/profile/ProfileScreen.kt").readText()

    private fun constName(route: String): String = FeatureRoutes::class.java.declaredFields
        .first { it.isAccessible = true; it.get(FeatureRoutes) == route }.name

    @Test
    fun `every route in the menu is registered in the graph`() {
        FeatureRoutes.all.forEach { route ->
            assertTrue(
                "$route is offered but the graph does not register it",
                nav.contains("composable(FeatureRoutes.${constName(route)})")
            )
        }
    }

    @Test
    fun `the menu offers every secondary destination`() {
        FeatureRoutes.all.forEach { route ->
            assertTrue(
                "$route has no entry in the ME tab",
                profile.contains("FeatureRoutes.${constName(route)}")
            )
        }
        assertEquals(7, FeatureRoutes.all.size)
    }
}
