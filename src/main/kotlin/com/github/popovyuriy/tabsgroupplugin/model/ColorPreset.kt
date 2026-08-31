package com.github.popovyuriy.tabsgroupplugin.model

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Predefined group accent colors.
 *
 * Each preset carries a light and a dark variant, so groups stay readable in every theme.
 * Presets are persisted by [name].
 */
enum class ColorPreset(
    val displayName: String,
    private val lightRgb: Int,
    private val darkRgb: Int
) {
    BLUE("Blue", 0x3574F0, 0x548AF7),
    GREEN("Green", 0x369650, 0x5FAD65),
    ORANGE("Orange", 0xC96A21, 0xE08855),
    PURPLE("Purple", 0x834DF0, 0xA379F0),
    RED("Red", 0xDB3B4B, 0xE55765),
    CYAN("Cyan", 0x0E8FA8, 0x35BBD0);

    /** Theme-aware accent color. Resolves light/dark at paint time. */
    val color: JBColor by lazy { JBColor(Color(lightRgb), Color(darkRgb)) }

    companion object {
        val DEFAULT: ColorPreset = BLUE

        fun byId(id: String?): ColorPreset? =
            if (id.isNullOrBlank()) null else entries.firstOrNull { it.name.equals(id, ignoreCase = true) }
    }
}