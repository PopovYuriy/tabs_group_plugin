package com.github.popovyuriy.tabsgroupplugin.toolWindow

import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.data.ColorPreset
import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.model.TabGroup
import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.TabGroupService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.*

/**
 * Tab Groups panel with modern rounded UI.
 */
class TabGroupsToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val service = TabGroupService.getInstance(project)
    private val contentPanel = JPanel()

    init {
        background = JBColor.PanelBackground

        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.background = JBColor.PanelBackground

        val scrollPane = JBScrollPane(contentPanel).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        add(scrollPane, BorderLayout.CENTER)
        add(createToolbar(), BorderLayout.NORTH)

        service.addChangeListener {
            SwingUtilities.invokeLater { rebuildContent() }
        }

        rebuildContent()
    }

    private fun createToolbar(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)

            add(createToolbarButton(AllIcons.General.Add, "Create new group") {
                val name = JOptionPane.showInputDialog(
                    this@TabGroupsToolWindowPanel,
                    "Enter group name:",
                    "New Group",
                    JOptionPane.PLAIN_MESSAGE
                )
                if (!name.isNullOrBlank()) {
                    service.createGroup(name)
                }
            })

            add(createToolbarButton(AllIcons.Actions.Collapseall, "Collapse all") {
                service.getAllGroups().forEach { service.setGroupExpanded(it.id, false) }
            })

            add(createToolbarButton(AllIcons.Actions.Expandall, "Expand all") {
                service.getAllGroups().forEach { service.setGroupExpanded(it.id, true) }
            })
        }
    }

    private fun createToolbarButton(icon: Icon, tooltip: String, action: () -> Unit): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            preferredSize = Dimension(22, 22)
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { action() }
        }
    }

    private fun rebuildContent() {
        contentPanel.removeAll()

        val groups = service.getAllGroups()

        if (groups.isEmpty()) {
            contentPanel.add(JBLabel("No groups. Right-click a tab to create one.").apply {
                foreground = JBColor.GRAY
                border = JBUI.Borders.empty(10)
                alignmentX = Component.LEFT_ALIGNMENT
            })
        } else {
            for ((index, group) in groups.withIndex()) {
                contentPanel.add(Box.createVerticalStrut(6))
                contentPanel.add(createGroupPanel(group, index, groups.size))
            }
            contentPanel.add(Box.createVerticalStrut(6))
        }

        contentPanel.add(Box.createVerticalGlue())
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun createGroupPanel(group: TabGroup, index: Int, totalGroups: Int): JComponent {
        val isExpanded = service.isGroupExpanded(group.id)
        val subtleColor = getSubtleColor(group.color)

        val groupPanel = RoundedPanel(10, subtleColor).apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(8, 10)
        }

        // Add header
        groupPanel.add(createGroupHeader(group, isExpanded, index, totalGroups))

        // Add files if expanded
        if (isExpanded && group.fileCount() > 0) {
            groupPanel.add(Box.createVerticalStrut(6))
            val files = group.filePaths.toList()
            for ((fileIndex, filePath) in files.withIndex()) {
                groupPanel.add(createFileTab(filePath, group, fileIndex, files.size))
                groupPanel.add(Box.createVerticalStrut(2))
            }
        }

        // Wrap in container
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(0, 6, 0, 6)
            alignmentX = Component.LEFT_ALIGNMENT
            add(groupPanel)
        }
    }

    private fun createGroupHeader(group: TabGroup, isExpanded: Boolean, index: Int, totalGroups: Int): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 22)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        val headerMouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showGroupContextMenu(e, group, index, totalGroups)
                } else {
                    service.toggleGroupExpanded(group.id)
                }
            }
        }

        // Color indicator
        val colorIndicator = RoundedPanel(4, group.color).apply {
            preferredSize = Dimension(4, 16)
            minimumSize = Dimension(4, 16)
            maximumSize = Dimension(4, 16)
            addMouseListener(headerMouseListener)
        }
        panel.add(colorIndicator)

        panel.add(Box.createHorizontalStrut(8))

        // Group name (without file count)
        val nameLabel = JBLabel(group.name).apply {
            font = font.deriveFont(Font.BOLD)
            addMouseListener(headerMouseListener)
        }
        panel.add(nameLabel)

        panel.add(Box.createHorizontalStrut(4))

        // Arrow icon
        val arrowIcon = if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        val arrowLabel = JBLabel(arrowIcon).apply {
            addMouseListener(headerMouseListener)
        }
        panel.add(arrowLabel)

        panel.add(Box.createHorizontalGlue())

        // Pin icon at the end (if pinned)
        if (group.isPinned) {
            val pinLabel = JBLabel(AllIcons.General.Pin_tab).apply {
                toolTipText = "Pinned across branches"
                addMouseListener(headerMouseListener)
            }
            panel.add(pinLabel)
            panel.add(Box.createHorizontalStrut(4))
        }

        panel.addMouseListener(headerMouseListener)

        return panel
    }

    private fun createFileTab(filePath: String, group: TabGroup, fileIndex: Int, totalFiles: Int): JComponent {
        val fileName = filePath.substringAfterLast("/")
        val file = LocalFileSystem.getInstance().findFileByPath(filePath)
        val fileIcon = file?.let {
            FileTypeManager.getInstance().getFileTypeByFileName(fileName).icon
        } ?: AllIcons.FileTypes.Any_type

        val hoverColor = getSubtleColor(group.color, 0.3f)

        val panel = RoundedPanel(6, null).apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(4, 8)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            maximumSize = Dimension(Int.MAX_VALUE, 26)
        }

        // Close button with proper contrast color
        val closeButton = createCloseButton(group.closeButtonColor) {
            val vFile = LocalFileSystem.getInstance().findFileByPath(filePath)
            if (vFile != null) {
                service.removeFileFromGroup(vFile)
            }
        }.apply {
            isVisible = false
        }

        // Hover handler
        fun setHovered(hovered: Boolean) {
            closeButton.isVisible = hovered
            (panel as RoundedPanel).setPanelColor(if (hovered) hoverColor else null)
        }

        val mouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                when {
                    SwingUtilities.isRightMouseButton(e) -> {
                        showFileContextMenu(e, group, filePath, fileIndex, totalFiles)
                    }
                    e.source !is JButton -> {
                        openFile(filePath)
                    }
                }
            }

            override fun mouseEntered(e: MouseEvent) {
                setHovered(true)
            }

            override fun mouseExited(e: MouseEvent) {
                setHovered(false)
            }
        }

        // File icon
        val iconLabel = JBLabel(fileIcon).apply {
            addMouseListener(mouseListener)
        }
        panel.add(iconLabel)

        panel.add(Box.createHorizontalStrut(6))

        // File name
        val fileLabel = JBLabel(fileName).apply {
            toolTipText = filePath
            font = font.deriveFont(12f)
            addMouseListener(mouseListener)
        }
        panel.add(fileLabel)

        panel.add(Box.createHorizontalGlue())

        // Close button
        closeButton.addMouseListener(mouseListener)
        panel.add(closeButton)

        panel.addMouseListener(mouseListener)

        return panel
    }

    private fun createCloseButton(color: Color, action: () -> Unit): JButton {
        return JButton(CloseIcon(color, 12)).apply {
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
    }
    private fun createSmallButton(icon: Icon, tooltip: String, action: () -> Unit): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            preferredSize = Dimension(16, 16)
            minimumSize = Dimension(16, 16)
            maximumSize = Dimension(16, 16)
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { action() }
        }
    }

    private fun showGroupContextMenu(e: MouseEvent, group: TabGroup, index: Int, totalGroups: Int) {
        val popup = JPopupMenu()

        // Move Up
        popup.add(JMenuItem("Move Up", AllIcons.Actions.MoveUp).apply {
            isEnabled = index > 0
            addActionListener {
                service.moveGroupUp(group.id)
            }
        })

        // Move Down
        popup.add(JMenuItem("Move Down", AllIcons.Actions.MoveDown).apply {
            isEnabled = index < totalGroups - 1
            addActionListener {
                service.moveGroupDown(group.id)
            }
        })

        popup.addSeparator()

        // Sort files
        popup.add(JMenuItem("Sort Files", AllIcons.ObjectBrowser.Sorted).apply {
            isEnabled = group.fileCount() > 1
            addActionListener {
                service.sortGroupFiles(group.id)
            }
        })

        popup.addSeparator()

        // Pin/Unpin
        val pinText = if (group.isPinned) "Unpin" else "Pin"
        popup.add(JMenuItem(pinText, AllIcons.General.Pin_tab).apply {
            addActionListener {
                service.toggleGroupPinned(group.id)
            }
        })

        popup.addSeparator()

        // Rename
        popup.add(JMenuItem("Rename", AllIcons.Actions.Edit).apply {
            addActionListener {
                val newName = JOptionPane.showInputDialog(
                    this@TabGroupsToolWindowPanel,
                    "Enter new name:",
                    "Rename Group",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    group.name
                ) as? String
                if (!newName.isNullOrBlank() && newName != group.name) {
                    service.renameGroup(group.id, newName)
                }
            }
        })

        // Change Color - submenu with presets
        val colorMenu = JMenu("Change Color").apply {
            icon = AllIcons.Actions.Colors
        }

        for (preset in ColorPreset.entries) {
            colorMenu.add(createColorMenuItem(preset, group))
        }

        popup.add(colorMenu)

        popup.addSeparator()

        // Delete
        popup.add(JMenuItem("Delete", AllIcons.Actions.GC).apply {
            addActionListener {
                val confirm = JOptionPane.showConfirmDialog(
                    this@TabGroupsToolWindowPanel,
                    "Delete group '${group.name}'?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION
                )
                if (confirm == JOptionPane.YES_OPTION) {
                    service.deleteGroup(group.id)
                }
            }
        })

        popup.show(e.component, e.x, e.y)
    }

    private fun createColorMenuItem(preset: ColorPreset, group: TabGroup): JMenuItem {
        val isSelected = group.color.rgb == preset.mainColor.rgb
        val menuText = if (isSelected) "✓ ${preset.displayName}" else "   ${preset.displayName}"

        return JMenuItem(menuText).apply {
            icon = ColorIcon(preset.mainColor, 12)
            addActionListener {
                service.changeGroupColor(group.id, preset)
            }
        }
    }
    private fun showFileContextMenu(e: MouseEvent, group: TabGroup, filePath: String, fileIndex: Int, totalFiles: Int) {
        val popup = JPopupMenu()

        // Move Up
        popup.add(JMenuItem("Move Up", AllIcons.Actions.MoveUp).apply {
            isEnabled = fileIndex > 0
            addActionListener {
                service.moveFileUp(group.id, filePath)
            }
        })

        // Move Down
        popup.add(JMenuItem("Move Down", AllIcons.Actions.MoveDown).apply {
            isEnabled = fileIndex < totalFiles - 1
            addActionListener {
                service.moveFileDown(group.id, filePath)
            }
        })

        popup.addSeparator()

        // Remove from group
        popup.add(JMenuItem("Remove from Group", AllIcons.Actions.Close).apply {
            addActionListener {
                val vFile = LocalFileSystem.getInstance().findFileByPath(filePath)
                if (vFile != null) {
                    service.removeFileFromGroup(vFile)
                }
            }
        })

        popup.show(e.component, e.x, e.y)
    }

    private fun openFile(filePath: String) {
        val file = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (file != null) {
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }

    private fun getSubtleColor(color: Color, alpha: Float = 0.15f): Color {
        return Color(
            color.red,
            color.green,
            color.blue,
            (255 * alpha).toInt()
        )
    }

    override fun dispose() {
        // Nothing to dispose
    }
}

/**
 * A JPanel with rounded corners and optional background color.
 */
class RoundedPanel(
    private val cornerRadius: Int,
    private var panelColor: Color?
) : JPanel() {

    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        if (panelColor != null) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = panelColor
            g2.fill(RoundRectangle2D.Float(
                0f, 0f,
                width.toFloat(), height.toFloat(),
                cornerRadius.toFloat(), cornerRadius.toFloat()
            ))
            g2.dispose()
        }
        super.paintComponent(g)
    }

    fun setPanelColor(color: Color?) {
        panelColor = color
        repaint()
    }
}

/**
 * A simple colored square icon for menu items.
 */
class ColorIcon(private val color: Color, private val size: Int) : Icon {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Fill
        g2.color = color
        g2.fillRoundRect(x, y, size, size, 3, 3)

        // Border
        g2.color = color.darker()
        g2.drawRoundRect(x, y, size - 1, size - 1, 3, 3)

        g2.dispose()
    }

    override fun getIconWidth(): Int = size
    override fun getIconHeight(): Int = size
}

class CloseIcon(private val color: Color, private val size: Int) : Icon {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        g2.color = color
        g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

        val padding = 3
        g2.drawLine(x + padding, y + padding, x + size - padding, y + size - padding)
        g2.drawLine(x + size - padding, y + padding, x + padding, y + size - padding)

        g2.dispose()
    }

    override fun getIconWidth(): Int = size
    override fun getIconHeight(): Int = size
}