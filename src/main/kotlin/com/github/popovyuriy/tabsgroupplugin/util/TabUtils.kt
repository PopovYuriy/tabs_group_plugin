package com.github.popovyuriy.tabsgroupplugin.util

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project

/**
 * Utility to refresh editor tabs appearance.
 */
object TabUtils {

    /**
     * Forces the IDE to refresh all editor tabs.
     * This causes EditorTabColorProvider to be called again.
     */
    fun refreshAllTabs(project: Project) {
        try {
            val manager = FileEditorManagerEx.getInstanceEx(project)

            // Get all open files and refresh their tabs
            for (file in manager.openFiles) {
                manager.updateFilePresentation(file)
            }
        } catch (e: Exception) {
            println("TabGroups: Tab refresh error: ${e.message}")
        }
    }

    /**
     * Refresh tabs for specific files.
     */
    fun refreshTabs(project: Project, filePaths: Collection<String>) {
        try {
            val manager = FileEditorManagerEx.getInstanceEx(project)

            for (file in manager.openFiles) {
                if (filePaths.contains(file.path)) {
                    manager.updateFilePresentation(file)
                }
            }
        } catch (e: Exception) {
            println("TabGroups: Tab refresh error: ${e.message}")
        }
    }
}