package com.github.popovyuriy.tabsgroupplugin.services.tabGroup.data

import java.awt.Color

/**
 * Predefined color presets with main color and contrasting close button color.
 */
enum class ColorPreset(
    val displayName: String,
    val mainColor: Color,
    val closeButtonColor: Color
) {
    BLUE(
        "Blue",
        Color(0x4A90D9),
        Color(0xFFFFFF)
    ),
    GREEN(
        "Green",
        Color(0x50A14F),
        Color(0xFFFFFF)
    ),
    ORANGE(
        "Orange",
        Color(0xD98C3F),
        Color(0x2B2B2B)
    ),
    PURPLE(
        "Purple",
        Color(0x9C6BBF),
        Color(0xFFFFFF)
    ),
    RED(
        "Red",
        Color(0xD75F5F),
        Color(0xFFFFFF)
    ),
    CYAN(
        "Cyan",
        Color(0x4DB6AC),
        Color(0x2B2B2B)
    );

    companion object {
        private var nextIndex = 0

        /**
         * Get next color preset in rotation.
         */
        fun nextPreset(): ColorPreset {
            val preset = entries[nextIndex % entries.size]
            nextIndex++
            return preset
        }

        /**
         * Find preset by main color RGB value.
         */
        fun findByColor(colorRgb: Int): ColorPreset? {
            return entries.find { it.mainColor.rgb == colorRgb }
        }

        /**
         * Get close button color for a given main color.
         * Falls back to white if no preset found.
         */
        fun getCloseButtonColor(mainColor: Color): Color {
            return findByColor(mainColor.rgb)?.closeButtonColor ?: Color.WHITE
        }
    }
}