package com.example.connectx.model

/**
 * User-configurable game settings, persisted across app launches.
 *
 * Icon values are indices into the disc icon options; a value of -1 means
 * "use the default" (for icon2 the default depends on the mode).
 * Color values are indices into the 24-entry palette; -1 means "use the
 * default".
 */
data class SetupSettings(
    val width: Int,
    val height: Int,
    val connect: Int,
    val vsAI: Boolean,
    val aiLevel: Int,
    val icon1: Int = -1,
    val icon2: Int = -1,
    val color1: Int = -1,
    val color2: Int = -1
) {
    companion object {
        val DEFAULT = SetupSettings(width = 7, height = 6, connect = 4, vsAI = false, aiLevel = 1)

        /** Default icon for the second player, depending on the mode. */
        fun defaultIcon2(vsAI: Boolean): Int = if (vsAI) 2 else 1 // A / star down

        /** Resolves the effective second-player icon (mode default when unset). */
        fun resolveIcon2(icon2: Int, vsAI: Boolean): Int = if (icon2 < 0) defaultIcon2(vsAI) else icon2
    }
}
