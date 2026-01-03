package com.github.popovyuriy.tabsgroupplugin.ui

import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.TabGroupService
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

/**
 * LESSON: EditorTabColorProvider
 *
 * This extension point lets us customize the background color
 * of editor tabs. The IDE calls this for each open tab.
 *
 * Return null = default color
 * Return Color = custom background color
 */
class GroupTabColorProvider : EditorTabColorProvider {

    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        val service = TabGroupService.getInstance(project)
        val group = service.findGroupForFile(file)

        return group?.let {
            // Return semi-transparent version of group color
            // (full color would be too intense)
            Color(
                it.color.red,
                it.color.green,
                it.color.blue,
                50  // Alpha: 0=transparent, 255=opaque
            )
        }
    }
}