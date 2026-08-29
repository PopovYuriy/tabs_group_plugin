package com.github.popovyuriy.tabsgroupplugin.toolWindow

import com.github.popovyuriy.tabsgroupplugin.model.ColorPreset
import com.github.popovyuriy.tabsgroupplugin.model.TabGroup
import com.github.popovyuriy.tabsgroupplugin.services.TabColorService
import com.github.popovyuriy.tabsgroupplugin.services.TabGroupService
import com.github.popovyuriy.tabsgroupplugin.storage.GroupStore
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/**
 * Tab Groups panel.
 *
 * Pinned groups are rendered above branch groups and the two sections reorder independently,
 * which is what the underlying storage actually supports. Rebuilds are coalesced onto a single
 * EDT pass and restore the scroll position, so a branch poll no longer jumps the view.
 */
class TabGroupsToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val service = TabGroupService.getInstance(project)
    private val colorService = TabColorService.getInstance(project)

    private val contentPanel = JPanel()
    private val scrollPane: JBScrollPane
    private val branchLabel = JBLabel()

    // Held as a field: removeChangeListener compares by identity.
    private val changeListener = Runnable { scheduleRebuild() }
    private var rebuildScheduled = false

    init {
        background = JBColor.PanelBackground

        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.background = JBColor.PanelBackground

        scrollPane = JBScrollPane(contentPanel).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        add(createToolbar(), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        service.addChangeListener(changeListener)
        rebuildContent()
    }

    // ============== Toolbar ==============

    private fun createToolbar(): JComponent {
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            isOpaque = false
        }

        buttons.add(toolbarButton(AllIcons.General.Add, "Create new group") {
            val name = Messages.showInputDialog(project, "Enter group name:", "New Group", null, "", null)
            if (!name.isNullOrBlank()) service.createGroup(name)
        })

        buttons.add(toolbarButton(AllIcons.Actions.Collapseall, "Collapse all") {
            service.getAllGroups().forEach { service.setGroupExpanded(it.id, false) }
        })

        buttons.add(toolbarButton(AllIcons.Actions.Expandall, "Expand all") {
            service.getAllGroups().forEach { service.setGroupExpanded(it.id, true) }
        })

        branchLabel.apply {
            foreground = JBColor.GRAY
            border = JBUI.Borders.empty(0, 6)
            // A JLabel clips its own text with an ellipsis once it is narrower than its preferred
            // size, but only if the layout actually gives it less. FlowLayout always honours the
            // preferred width, so a long branch name simply ran off the edge of the tool window.
            // In BorderLayout.CENTER the label gets whatever is left over instead, and a zero
            // minimum stops it from forcing the panel wider.
            minimumSize = Dimension(0, 0)
        }

        return JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
            add(buttons, BorderLayout.WEST)
            add(branchLabel, BorderLayout.CENTER)
        }
    }

    private fun toolbarButton(icon: Icon, tooltip: String, action: () -> Unit): JButton =
        JButton(icon).apply {
            toolTipText = tooltip
            preferredSize = Dimension(22, 22)
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { action() }
        }

    // ============== Rebuilding ==============

    /** Coalesces bursts of changes into one repaint. Always runs on the EDT. */
    private fun scheduleRebuild() {
        if (rebuildScheduled) return
        rebuildScheduled = true
        ApplicationManager.getApplication().invokeLater({
            rebuildScheduled = false
            if (!project.isDisposed) rebuildContent()
        }, ModalityState.any())
    }

    private fun rebuildContent() {
        val scrollPosition = scrollPane.verticalScrollBar.value

        contentPanel.removeAll()
        updateBranchLabel()

        val pinned = service.getPinnedGroups()
        val branchGroups = service.getBranchGroups()

        if (pinned.isEmpty() && branchGroups.isEmpty()) {
            contentPanel.add(JBLabel("No groups yet. Right-click a tab to create one.").apply {
                foreground = JBColor.GRAY
                border = JBUI.Borders.empty(10)
                alignmentX = Component.LEFT_ALIGNMENT
            })
        } else {
            (pinned + branchGroups).forEach { group ->
                contentPanel.add(Box.createVerticalStrut(6))
                contentPanel.add(createGroupPanel(group))
            }
            contentPanel.add(Box.createVerticalStrut(6))
        }

        contentPanel.add(Box.createVerticalGlue())
        contentPanel.revalidate()
        contentPanel.repaint()

        // Restore after layout has settled, otherwise the maximum is still stale.
        SwingUtilities.invokeLater { scrollPane.verticalScrollBar.value = scrollPosition }
    }

    /**
     * The label truncates itself when the tool window is narrow, so the full name always goes into
     * the tooltip.
     */
    private fun updateBranchLabel() {
        val branch = service.currentBranch
        if (branch == GroupStore.NO_BRANCH) {
            branchLabel.text = "no branch"
            branchLabel.toolTipText = "No Git branch detected \u2014 groups are shared across the project"
        } else {
            branchLabel.text = branch
            branchLabel.toolTipText = "Current branch: $branch"
        }
    }

    // ============== Group rendering ==============

    private fun createGroupPanel(group: TabGroup): JComponent {
        val isExpanded = service.isGroupExpanded(group.id)

        val groupPanel = RoundedPanel(10, colorService.groupPanelBackground(group.color)).apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(8, 10)
        }

        groupPanel.add(createGroupHeader(group, isExpanded))

        if (isExpanded && group.fileCount > 0) {
            groupPanel.add(Box.createVerticalStrut(6))
            group.filePaths.forEachIndexed { index, filePath ->
                groupPanel.add(createFileRow(filePath, group, index, group.fileCount))
                groupPanel.add(Box.createVerticalStrut(2))
            }
        }

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(0, 6)
            alignmentX = Component.LEFT_ALIGNMENT
            add(groupPanel)
        }
    }

    private fun createGroupHeader(group: TabGroup, isExpanded: Boolean): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 22)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        val mouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showGroupContextMenu(e, group)
                } else {
                    service.toggleGroupExpanded(group.id)
                }
            }
        }

        panel.add(RoundedPanel(4, group.color.color).apply {
            preferredSize = Dimension(4, 16)
            minimumSize = Dimension(4, 16)
            maximumSize = Dimension(4, 16)
            addMouseListener(mouseListener)
        })
        panel.add(Box.createHorizontalStrut(8))

        panel.add(JBLabel(group.name).apply {
            font = font.deriveFont(Font.BOLD)
            toolTipText = "${group.fileCount} file(s)"
            addMouseListener(mouseListener)
        })
        panel.add(Box.createHorizontalStrut(4))

        val arrow = if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        panel.add(JBLabel(arrow).apply { addMouseListener(mouseListener) })

        panel.add(Box.createHorizontalGlue())

        if (group.isPinned) {
            panel.add(JBLabel(AllIcons.General.Pin_tab).apply {
                toolTipText = "Pinned across branches"
                addMouseListener(mouseListener)
            })
            panel.add(Box.createHorizontalStrut(4))
        }

        panel.addMouseListener(mouseListener)
        return panel
    }

    private fun createFileRow(filePath: String, group: TabGroup, index: Int, totalFiles: Int): JComponent {
        val fileName = filePath.substringAfterLast('/')
        val exists = LocalFileSystem.getInstance().findFileByPathIfCached(filePath) != null
        val icon = FileTypeManager.getInstance().getFileTypeByFileName(fileName).icon
            ?: AllIcons.FileTypes.Any_type
        val hoverColor = colorService.hoverBackground(group.color)

        val row = RoundedPanel(6, null).apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(4, 8)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            maximumSize = Dimension(Int.MAX_VALUE, 26)
        }

        val closeButton = createCloseButton { removeFromGroup(filePath) }.apply { isVisible = false }

        val mouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                when {
                    SwingUtilities.isRightMouseButton(e) ->
                        showFileContextMenu(e, group, filePath, index, totalFiles)

                    e.source !is JButton -> openFile(filePath)
                }
            }

            override fun mouseEntered(e: MouseEvent) {
                closeButton.isVisible = true
                row.setPanelColor(hoverColor)
            }

            override fun mouseExited(e: MouseEvent) {
                if (row.mousePosition != null) return
                closeButton.isVisible = false
                row.setPanelColor(null)
            }
        }

        row.add(JBLabel(icon).apply { addMouseListener(mouseListener) })
        row.add(Box.createHorizontalStrut(6))
        row.add(JBLabel(fileName).apply {
            toolTipText = if (exists) filePath else "$filePath (not found)"
            font = font.deriveFont(12f)
            if (!exists) foreground = JBColor.GRAY
            addMouseListener(mouseListener)
        })
        row.add(Box.createHorizontalGlue())

        closeButton.addMouseListener(mouseListener)
        row.add(closeButton)
        row.addMouseListener(mouseListener)

        return row
    }

    private fun createCloseButton(action: () -> Unit): JButton =
        JButton(CloseIcon(JBColor.foreground(), 12)).apply {
            toolTipText = "Remove from group"
            preferredSize = Dimension(16, 16)
            minimumSize = Dimension(16, 16)
            maximumSize = Dimension(16, 16)
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { action() }
        }

    // ============== Context menus ==============

    private fun showGroupContextMenu(e: MouseEvent, group: TabGroup) {
        val popup = JPopupMenu()

        popup.add(JMenuItem("Move Up", AllIcons.Actions.MoveUp).apply {
            // Enablement now comes from the storage layer, which knows that pinned and branch
            // groups are separate lists.
            isEnabled = service.canMoveGroup(group.id, -1)
            addActionListener { service.moveGroupUp(group.id) }
        })

        popup.add(JMenuItem("Move Down", AllIcons.Actions.MoveDown).apply {
            isEnabled = service.canMoveGroup(group.id, 1)
            addActionListener { service.moveGroupDown(group.id) }
        })

        popup.addSeparator()

        popup.add(JMenuItem("Sort Files", AllIcons.ObjectBrowser.Sorted).apply {
            isEnabled = group.fileCount > 1
            addActionListener { service.sortGroupFiles(group.id) }
        })

        popup.addSeparator()

        popup.add(JMenuItem(if (group.isPinned) "Unpin" else "Pin", AllIcons.General.Pin_tab).apply {
            addActionListener { service.toggleGroupPinned(group.id) }
        })

        popup.addSeparator()

        popup.add(JMenuItem("Rename", AllIcons.Actions.Edit).apply {
            addActionListener {
                val newName = Messages.showInputDialog(
                    project, "Enter new name:", "Rename Group", null, group.name, null
                )
                if (!newName.isNullOrBlank() && newName != group.name) {
                    service.renameGroup(group.id, newName)
                }
            }
        })

        val colorMenu = JMenu("Change Color").apply { icon = AllIcons.Actions.Colors }
        ColorPreset.entries.forEach { colorMenu.add(createColorMenuItem(it, group)) }
        popup.add(colorMenu)

        popup.addSeparator()

        popup.add(JMenuItem("Delete", AllIcons.General.Remove).apply {
            addActionListener {
                val confirmed = Messages.showYesNoDialog(
                    project,
                    "Delete group '${group.name}'?",
                    "Confirm Delete",
                    Messages.getQuestionIcon()
                ) == Messages.YES
                if (confirmed) service.deleteGroup(group.id)
            }
        })

        popup.show(e.component, e.x, e.y)
    }

    private fun createColorMenuItem(preset: ColorPreset, group: TabGroup): JMenuItem {
        val selected = group.color == preset
        return JMenuItem(if (selected) "\u2713 ${preset.displayName}" else "   ${preset.displayName}").apply {
            icon = ColorIcon(preset.color, 12)
            addActionListener { service.changeGroupColor(group.id, preset) }
        }
    }

    private fun showFileContextMenu(
        e: MouseEvent,
        group: TabGroup,
        filePath: String,
        index: Int,
        totalFiles: Int
    ) {
        val popup = JPopupMenu()

        popup.add(JMenuItem("Move Up", AllIcons.Actions.MoveUp).apply {
            isEnabled = index > 0
            addActionListener { service.moveFileUp(group.id, filePath) }
        })

        popup.add(JMenuItem("Move Down", AllIcons.Actions.MoveDown).apply {
            isEnabled = index < totalFiles - 1
            addActionListener { service.moveFileDown(group.id, filePath) }
        })

        popup.addSeparator()

        popup.add(JMenuItem("Remove from Group", AllIcons.Actions.Close).apply {
            addActionListener { removeFromGroup(filePath) }
        })

        popup.show(e.component, e.x, e.y)
    }

    // ============== Actions ==============

    private fun removeFromGroup(filePath: String) {
        // Works even when the file no longer exists on disk.
        service.removeFilePathFromGroups(filePath)
    }

    private fun openFile(filePath: String) {
        val file = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (file == null) {
            Messages.showWarningDialog(project, "File no longer exists:\n$filePath", "Cannot Open File")
            return
        }
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    override fun dispose() {
        service.removeChangeListener(changeListener)
    }
}