package com.github.popovyuriy.tabsgroupplugin.services

import com.github.popovyuriy.tabsgroupplugin.services.data.ColorPreset
import com.github.popovyuriy.tabsgroupplugin.services.model.TabGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Main service for tab group management.
 * Orchestrates GitBranchService, GroupStorageService, and TabColorService.
 */
@Service(Service.Level.PROJECT)
class TabGroupService(private val project: Project) : Disposable {

    private val gitService = GitBranchService.getInstance(project)
    private val storageService = GroupStorageService.getInstance(project)
    private val colorService = TabColorService.getInstance(project)

    private val listeners = mutableListOf<() -> Unit>()
    private val expandedGroups = mutableSetOf<String>()

    init {
        // Listen for branch changes
        gitService.addBranchChangeListener { oldBranch, newBranch ->
            println("TabGroups: Branch changed from '$oldBranch' to '$newBranch'")
            notifyChanged()
            colorService.refreshAllTabs()
        }
    }

    // ============== Public API - Groups ==============

    fun getAllGroups(): List<TabGroup> {
        return storageService.getGroupsForBranch(gitService.getCurrentBranch())
    }

    fun createGroup(name: String): TabGroup {
        val group = TabGroup(name = name)
        storageService.saveGroup(gitService.getCurrentBranch(), group)
        notifyChanged()
        return group
    }

    fun deleteGroup(groupId: String) {
        storageService.deleteGroup(gitService.getCurrentBranch(), groupId)
        notifyChanged()
        colorService.refreshAllTabs()
    }

    fun findGroupById(id: String): TabGroup? {
        return getAllGroups().find { it.id == id }
    }

    fun findGroupForFile(file: VirtualFile): TabGroup? {
        return getAllGroups().find { it.containsFile(file.path) }
    }

    // ============== Public API - Group Modification ==============

    fun renameGroup(groupId: String, newName: String) {
        storageService.updateGroupName(gitService.getCurrentBranch(), groupId, newName)
        notifyChanged()
    }

    fun changeGroupColor(groupId: String, preset: ColorPreset) {
        storageService.updateGroupColor(gitService.getCurrentBranch(), groupId, preset.mainColor)
        notifyChanged()
        colorService.refreshAllTabs()
    }

    // ============== Public API - Pin/Unpin ==============

    fun toggleGroupPinned(groupId: String) {
        val branch = gitService.getCurrentBranch()
        if (storageService.isGroupPinned(groupId)) {
            storageService.unpinGroup(branch, groupId)
        } else {
            storageService.pinGroup(branch, groupId)
        }
        notifyChanged()
        colorService.refreshAllTabs()
    }

    // ============== Public API - Files ==============

    fun addFileToGroup(file: VirtualFile, group: TabGroup) {
        val branch = gitService.getCurrentBranch()
        // Remove from any existing group first
        storageService.removeFileFromAllGroups(branch, file.path)
        storageService.addFileToGroup(branch, group.id, file.path)
        notifyChanged()
        colorService.refreshAllTabs()
    }

    fun removeFileFromGroup(file: VirtualFile) {
        storageService.removeFileFromAllGroups(gitService.getCurrentBranch(), file.path)
        notifyChanged()
        colorService.refreshAllTabs()
    }

    fun handleFileMoved(oldPath: String, newPath: String) {
        storageService.moveFile(gitService.getCurrentBranch(), oldPath, newPath)
        notifyChanged()
        colorService.refreshAllTabs()
    }

    fun handleFileDeleted(path: String) {
        storageService.removeFileFromAllGroups(gitService.getCurrentBranch(), path)
        notifyChanged()
    }

    // ============== Public API - Ordering ==============

    fun moveGroupUp(groupId: String) {
        storageService.moveGroupUp(gitService.getCurrentBranch(), groupId)
        notifyChanged()
    }

    fun moveGroupDown(groupId: String) {
        storageService.moveGroupDown(gitService.getCurrentBranch(), groupId)
        notifyChanged()
    }

    fun moveFileUp(groupId: String, filePath: String) {
        storageService.moveFileUp(gitService.getCurrentBranch(), groupId, filePath)
        notifyChanged()
    }

    fun moveFileDown(groupId: String, filePath: String) {
        storageService.moveFileDown(gitService.getCurrentBranch(), groupId, filePath)
        notifyChanged()
    }

    fun sortGroupFiles(groupId: String) {
        storageService.sortGroupFiles(gitService.getCurrentBranch(), groupId)
        notifyChanged()
    }

    // ============== Public API - UI State ==============

    fun isGroupExpanded(groupId: String): Boolean = expandedGroups.contains(groupId)

    fun toggleGroupExpanded(groupId: String) {
        if (expandedGroups.contains(groupId)) {
            expandedGroups.remove(groupId)
        } else {
            expandedGroups.add(groupId)
        }
        notifyChanged()
    }

    fun setGroupExpanded(groupId: String, expanded: Boolean) {
        if (expanded) {
            expandedGroups.add(groupId)
        } else {
            expandedGroups.remove(groupId)
        }
        notifyChanged()
    }

    // ============== Change Listeners ==============

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }

    // ============== Lifecycle ==============

    override fun dispose() {
        listeners.clear()
        expandedGroups.clear()
    }

    // ============== Companion ==============

    companion object {
        fun getInstance(project: Project): TabGroupService = project.service()
    }
}