package com.github.popovyuriy.tabsgroupplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import java.awt.Color

/**
 * Service for managing tab colors.
 */
@Service(Service.Level.PROJECT)
class TabColorService(private val project: Project) {

    // ============== Tab Color Constants ==============

    companion object {
        /** Alpha for tab background in IDE tabs panel (very subtle) */
        const val TAB_BACKGROUND_ALPHA = 25  // Was 40, now more subtle

        /** Alpha for group panel background in Tool Window */
        const val GROUP_PANEL_ALPHA = 0.15f

        /** Alpha for hover effect in Tool Window */
        const val HOVER_ALPHA = 0.3f

        fun getInstance(project: Project): TabColorService = project.service()
    }

    // ============== Color Utilities ==============

    /**
     * Get subtle tab color for IDE editor tabs.
     */
    fun getTabColor(baseColor: Color): Color {
        return Color(
            baseColor.red,
            baseColor.green,
            baseColor.blue,
            TAB_BACKGROUND_ALPHA
        )
    }

    /**
     * Get subtle color for Tool Window group panels.
     */
    fun getGroupPanelColor(baseColor: Color): Color {
        return Color(
            baseColor.red,
            baseColor.green,
            baseColor.blue,
            (255 * GROUP_PANEL_ALPHA).toInt()
        )
    }

    /**
     * Get hover color for Tool Window items.
     */
    fun getHoverColor(baseColor: Color): Color {
        return Color(
            baseColor.red,
            baseColor.green,
            baseColor.blue,
            (255 * HOVER_ALPHA).toInt()
        )
    }

    // ============== Tab Refresh ==============

    /**
     * Refresh all editor tabs to update colors.
     */
    fun refreshAllTabs() {
        try {
            val manager = FileEditorManagerEx.getInstanceEx(project)
            for (file in manager.openFiles) {
                manager.updateFilePresentation(file)
            }
        } catch (e: Exception) {
            println("TabGroups: Tab refresh error: ${e.message}")
        }
    }
}