package com.github.popovyuriy.tabsgroupplugin.ui

import com.github.popovyuriy.tabsgroupplugin.services.TabColorService
import com.github.popovyuriy.tabsgroupplugin.services.TabGroupService
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

/**
 * Colors editor tabs by group.
 *
 * Called on the EDT for every tab on every repaint, so it does a single hash lookup and nothing
 * more.
 */
class GroupTabColorProvider : EditorTabColorProvider {

    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        if (project.isDisposed) return null
        val preset = TabGroupService.getInstance(project).presetForPath(file.path) ?: return null
        return TabColorService.getInstance(project).tabBackground(preset)
    }
}