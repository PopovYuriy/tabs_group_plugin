package com.github.popovyuriy.tabsgroupplugin.model

import java.util.UUID

/**
 * An immutable snapshot of a tab group.
 *
 * Instances handed out by the services are copies of the persisted state, so mutating one
 * would silently do nothing. Making the type immutable turns that class of bug into a
 * compile error: all changes go through
 * [com.github.popovyuriy.tabsgroupplugin.services.TabGroupService].
 *
 * [filePaths] are always absolute; the storage layer handles relative-path conversion.
 */
data class TabGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New Group",
    val color: ColorPreset = ColorPreset.DEFAULT,
    val isPinned: Boolean = false,
    val filePaths: List<String> = emptyList()
) {
    val fileCount: Int get() = filePaths.size

    fun containsFile(path: String): Boolean = filePaths.contains(path)
}