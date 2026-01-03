package com.github.popovyuriy.tabsgroupplugin.listeners

import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.TabGroupService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Enforces single tab slot for ALL grouped files.
 * When any grouped file opens, close ALL other grouped files.
 */
class GroupedFileEditorListener(private val project: Project) : FileEditorManagerListener {

    private var isProcessing = false

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (isProcessing) return

        val service = TabGroupService.getInstance(project)

        // Check if opened file belongs to ANY group
        val group = service.findGroupForFile(file) ?: return

        // Find ALL other grouped files that are open (from ANY group)
        val allGroupedPaths = service.getAllGroups()
            .flatMap { it.filePaths }
            .toSet()

        val otherGroupedFiles = source.openFiles.filter { openFile ->
            openFile != file && allGroupedPaths.contains(openFile.path)
        }

        if (otherGroupedFiles.isEmpty()) return

        // Close all other grouped files
        isProcessing = true
        ApplicationManager.getApplication().invokeLater {
            try {
                for (otherFile in otherGroupedFiles) {
                    source.closeFile(otherFile)
                }
            } finally {
                isProcessing = false
            }
        }

        println("TabGroups: Closed ${otherGroupedFiles.size} grouped files, opened ${file.name} from '${group.name}'")
    }
}