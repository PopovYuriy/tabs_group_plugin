package com.github.popovyuriy.tabsgroupplugin.services.tabGroup.model

import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.data.ColorPreset
import java.awt.Color
import java.util.UUID

/**
 * Represents a group of tabs.
 */
class TabGroup(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New Group",
    var color: Color = ColorPreset.Companion.nextPreset().mainColor,
    var isPinned: Boolean = false
) {
    val filePaths: MutableList<String> = mutableListOf()

    /**
     * Get the close button color based on the group's color.
     */
    val closeButtonColor: Color
        get() = ColorPreset.Companion.getCloseButtonColor(color)

    fun addFile(path: String) {
        if (!filePaths.contains(path)) {
            filePaths.add(path)
        }
    }

    fun removeFile(path: String) {
        filePaths.remove(path)
    }

    fun containsFile(path: String): Boolean = filePaths.contains(path)

    fun fileCount(): Int = filePaths.size
}