package com.github.popovyuriy.tabsgroupplugin.model

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Predefined group accent colors.
 *
 * Each preset carries a light and a dark variant, so groups stay readable in every theme.
 * Presets are persisted by [name]; [legacyRgb] only exists so that groups created by
 * plugin versions before 1.1.0 (which stored a raw RGB int) can be migrated.
 */
enum class ColorPreset(
    val displayName: String,
    private val lightRgb: Int,
    private val darkRgb: Int,
    private val legacyRgb: Int
) {
    BLUE("Blue", 0x3574F0, 0x548AF7, 0x4A90D9),
    GREEN("Green", 0x369650, 0x5FAD65, 0x50A14F),
    ORANGE("Orange", 0xC96A21, 0xE08855, 0xD98C3F),
    PURPLE("Purple", 0x834DF0, 0xA379F0, 0x9C6BBF),
    RED("Red", 0xDB3B4B, 0xE55765, 0xD75F5F),
    CYAN("Cyan", 0x0E8FA8, 0x35BBD0, 0x4DB6AC);

    /** Theme-aware accent color. Resolves light/dark at paint time. */
    val color: JBColor by lazy { JBColor(Color(lightRgb), Color(darkRgb)) }

    companion object {
        val DEFAULT: ColorPreset = BLUE

        fun byId(id: String?): ColorPreset? =
            if (id.isNullOrBlank()) null else entries.firstOrNull { it.name.equals(id, ignoreCase = true) }

        /** Maps a pre-1.1.0 stored RGB value back onto a preset. */
        fun byLegacyRgb(rgb: Int): ColorPreset? {
            val masked = rgb and 0xFFFFFF
            if (masked == 0) return null
            return entries.firstOrNull { it.legacyRgb == masked || it.lightRgb == masked || it.darkRgb == masked }
        }
    }
}