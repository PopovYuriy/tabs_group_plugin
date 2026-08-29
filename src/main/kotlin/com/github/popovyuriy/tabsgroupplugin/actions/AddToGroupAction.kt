package com.github.popovyuriy.tabsgroupplugin.actions

import com.github.popovyuriy.tabsgroupplugin.model.TabGroup
import com.github.popovyuriy.tabsgroupplugin.services.TabGroupService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile

/**
 * Shown in the editor tab and project view popups. Supports multi-selection in the project view.
 */
class AddToGroupAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && selectedFiles(e).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = selectedFiles(e)
        if (files.isEmpty()) return

        val service = TabGroupService.getInstance(project)
        val options = buildOptions(service, files)

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<GroupOption>(popupTitle(files), options) {

                override fun getTextFor(value: GroupOption): String = value.displayName

                override fun onChosen(selectedValue: GroupOption, finalChoice: Boolean): PopupStep<*>? {
                    if (!finalChoice) return null
                    // The popup is still closing; run once it is gone.
                    ApplicationManager.getApplication().invokeLater(
                        { apply(project, service, selectedValue, files) },
                        ModalityState.any()
                    )
                    return FINAL_CHOICE
                }
            }
        )
        popup.showInBestPositionFor(e.dataContext)
    }

    private fun apply(
        project: Project,
        service: TabGroupService,
        option: GroupOption,
        files: List<VirtualFile>
    ) {
        if (project.isDisposed) return
        when {
            option.isNew -> {
                val name = Messages.showInputDialog(
                    project,
                    "Enter group name:",
                    "New Group",
                    null,
                    "",
                    null
                )
                if (!name.isNullOrBlank()) {
                    service.addFilesToGroup(files, service.createGroup(name))
                }
            }

            option.isRemove -> service.removeFilesFromGroups(files)

            else -> {
                val group = option.groupId?.let { service.findGroupById(it) } ?: return
                service.addFilesToGroup(files, group)
            }
        }
    }

    private fun buildOptions(service: TabGroupService, files: List<VirtualFile>): List<GroupOption> {
        val options = mutableListOf<GroupOption>()
        val paths = files.map { it.path }

        for (group in service.getAllGroups()) {
            val contained = paths.count { group.containsFile(it) }
            options.add(
                GroupOption(
                    displayName = buildString {
                        if (contained == paths.size) append("\u2713 ")
                        append(group.name)
                        append(" (").append(group.fileCount).append(" files)")
                    },
                    groupId = group.id
                )
            )
        }

        options.add(GroupOption(displayName = "+ Create New Group\u2026", groupId = null, isNew = true))

        val grouped = files.filter { service.findGroupForPath(it.path) != null }
        if (grouped.isNotEmpty()) {
            val label = if (grouped.size == 1) {
                val group: TabGroup? = service.findGroupForPath(grouped.first().path)
                "\u2715 Remove from '${group?.name.orEmpty()}'"
            } else {
                "\u2715 Remove ${grouped.size} files from their groups"
            }
            options.add(GroupOption(displayName = label, groupId = null, isRemove = true))
        }
        return options
    }

    private fun popupTitle(files: List<VirtualFile>): String =
        if (files.size == 1) "Add to Group" else "Add ${files.size} Files to Group"

    private fun selectedFiles(e: AnActionEvent): List<VirtualFile> {
        val array = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val files = array?.toList() ?: listOfNotNull(e.getData(CommonDataKeys.VIRTUAL_FILE))
        return files.filter { it.isValid && !it.isDirectory }
    }

    private data class GroupOption(
        val displayName: String,
        val groupId: String?,
        val isNew: Boolean = false,
        val isRemove: Boolean = false
    )
}