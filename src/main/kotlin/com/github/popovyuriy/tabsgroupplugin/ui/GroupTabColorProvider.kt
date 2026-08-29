package com.github.popovyuriy.tabsgroupplugin.ui

import com.github.popovyuriy.tabsgroupplugin.services.TabColorService
import com.github.popovyuriy.tabsgroupplugin.services.TabGroupService
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

/**
 * Colors editor tabs based on their group color.
 */
class GroupTabColorProvider : EditorTabColorProvider {

    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        val groupService = TabGroupService.getInstance(project)
        val colorService = TabColorService.getInstance(project)

        val group = groupService.findGroupForFile(file) ?: return null

        return colorService.getTabColor(group.color)
    }
}