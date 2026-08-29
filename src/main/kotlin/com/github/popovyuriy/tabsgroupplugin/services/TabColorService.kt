package com.github.popovyuriy.tabsgroupplugin.services

import com.github.popovyuriy.tabsgroupplugin.model.ColorPreset
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import java.awt.Color

/**
 * Derives the tinted colors used for editor tabs and tool window rows.
 *
 * Colors are *blended* against the surface they will be painted on rather than returned with an
 * alpha channel. Translucent tab colors composite differently across IDE versions and themes;
 * blending ourselves gives the same result everywhere.
 */
@Service(Service.Level.PROJECT)
class TabColorService(private val project: Project) {

    /** Tint for an editor tab. */
    fun tabBackground(preset: ColorPreset): Color = blend(preset.color, editorBackground(), TAB_TINT)

    /** Tint for a group container in the tool window. */
    fun groupPanelBackground(preset: ColorPreset): Color = blend(preset.color, panelBackground(), PANEL_TINT)

    /** Tint for a hovered file row in the tool window. */
    fun hoverBackground(preset: ColorPreset): Color = blend(preset.color, panelBackground(), HOVER_TINT)

    /** Repaints open tabs so color changes show up immediately. Safe to call from any thread. */
    fun refreshAllTabs() {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            doRefresh()
        } else {
            application.invokeLater({ doRefresh() }, ModalityState.any())
        }
    }

    private fun doRefresh() {
        if (project.isDisposed) return
        try {
            val manager = FileEditorManagerEx.getInstanceEx(project)
            for (file in manager.openFiles) {
                manager.updateFilePresentation(file)
            }
        } catch (e: Exception) {
            thisLogger().warn("Could not refresh tab presentation", e)
        }
    }

    private fun editorBackground(): Color =
        EditorColorsManager.getInstance().globalScheme.defaultBackground

    private fun panelBackground(): Color = UIUtil.getPanelBackground()

    private fun blend(accent: Color, background: Color, weight: Float): Color {
        // Read the components through the Color API so JBColor resolves the active theme first.
        val r = accent.red * weight + background.red * (1 - weight)
        val g = accent.green * weight + background.green * (1 - weight)
        val b = accent.blue * weight + background.blue * (1 - weight)
        return Color(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }

    companion object {
        private const val TAB_TINT = 0.18f
        private const val PANEL_TINT = 0.16f
        private const val HOVER_TINT = 0.30f

        fun getInstance(project: Project): TabColorService = project.service()
    }
}