package com.craftengine.diamondcraft

/** One entitlement decision shared by every UI and export entry point. */
internal object ProAccess {
    fun allowed(playPurchaseConfirmed: Boolean): Boolean = playPurchaseConfirmed

    fun maxWidth(isPro: Boolean): Int = if (isPro) 200 else 100
    fun maxColors(isPro: Boolean): Int = if (isPro) 120 else 60
}
