package com.github.popovyuriy.tabsgroupplugin.actions

import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.TabGroupService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import javax.swing.JOptionPane

/**
 * LESSON: Actions
 *
 * This action appears when right-clicking on an editor tab.
 * It shows a popup with available groups to add the file to.
 */
class AddToGroupAction : AnAction("Add to Group...") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val service = TabGroupService.getInstance(project)
        val groups = service.getAllGroups()

        // Build list of options
        val options = mutableListOf<GroupOption>()

        // Add existing groups
        for (group in groups) {
            val alreadyInGroup = group.containsFile(file.path)
            val prefix = if (alreadyInGroup) "✓ " else ""
            options.add(GroupOption(
                displayName = "$prefix${group.name} (${group.fileCount()} files)",
                groupId = group.id,
                isNew = false
            ))
        }

        // Add "New Group" option
        options.add(GroupOption(
            displayName = "+ Create New Group...",
            groupId = null,
            isNew = true
        ))

        // Add "Remove from Group" if file is in a group
        val currentGroup = service.findGroupForFile(file)
        if (currentGroup != null) {
            options.add(GroupOption(
                displayName = "✕ Remove from '${currentGroup.name}'",
                groupId = currentGroup.id,
                isRemove = true
            ))
        }

        // Show popup
        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<GroupOption>("Add to Group", options) {

                override fun getTextFor(value: GroupOption): String = value.displayName

                override fun onChosen(selectedValue: GroupOption, finalChoice: Boolean): PopupStep<*>? {
                    if (!finalChoice) return null

                    // Must run later because popup is still closing
                    javax.swing.SwingUtilities.invokeLater {
                        when {
                            selectedValue.isNew -> {
                                // Create new group
                                val name = JOptionPane.showInputDialog(
                                    null,
                                    "Enter group name:",
                                    "New Group",
                                    JOptionPane.PLAIN_MESSAGE
                                )
                                if (!name.isNullOrBlank()) {
                                    val newGroup = service.createGroup(name)
                                    service.addFileToGroup(file, newGroup)
                                }
                            }
                            selectedValue.isRemove -> {
                                // Remove from group
                                service.removeFileFromGroup(file)
                            }
                            else -> {
                                // Add to existing group
                                val group = service.findGroupById(selectedValue.groupId!!)
                                if (group != null) {
                                    service.addFileToGroup(file, group)
                                }
                            }
                        }
                    }

                    return FINAL_CHOICE
                }
            }
        )

        popup.showInBestPositionFor(e.dataContext)
    }

    override fun update(e: AnActionEvent) {
        // Only show when right-clicking on a file/tab
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * Helper class to represent options in the popup
     */
    private data class GroupOption(
        val displayName: String,
        val groupId: String?,
        val isNew: Boolean = false,
        val isRemove: Boolean = false
    )
}