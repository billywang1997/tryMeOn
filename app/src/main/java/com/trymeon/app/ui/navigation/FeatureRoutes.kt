package com.trymeon.app.ui.navigation

/**
 * The app's secondary destinations, named once.
 *
 * They are reached from a list in the ME tab, and were previously addressed by
 * writing the same string in two files. A rename in one of them would have
 * produced a menu entry that navigates nowhere, with nothing to catch it.
 */
object FeatureRoutes {
    const val AUDIT = "audit"
    const val WISHLIST = "wishlist"
    const val STREAK = "streak"
    const val COST = "cost"
    const val CALENDAR = "calendar"
    const val EMERGENCY = "emergency"
    const val RATING = "rating"

    /** Every route above, so a test can check the menu and the graph agree. */
    val all = listOf(AUDIT, WISHLIST, STREAK, COST, CALENDAR, EMERGENCY, RATING)
}
