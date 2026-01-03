package com.github.popovyuriy.tabsgroupplugin.services.tabGroup

import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.data.ColorPreset
import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.state.GroupState
import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.model.TabGroup
import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.state.TabGroupServiceState
import com.github.popovyuriy.tabsgroupplugin.util.GitUtils
import com.github.popovyuriy.tabsgroupplugin.util.TabUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.SwingUtilities

@State(
    name = "TabGroupService",
    storages = [Storage("tabGroups.xml")]
)
@Service(Service.Level.PROJECT)
class TabGroupService(private val project: Project) : PersistentStateComponent<TabGroupServiceState>, Disposable {

    // ============== In-Memory Storage ==============

    private var state = TabGroupServiceState()
    private val listeners = mutableListOf<() -> Unit>()
    private var lastKnownBranch: String? = null

    // ============== PersistentStateComponent ==============

    override fun getState(): TabGroupServiceState = state

    override fun loadState(loadedState: TabGroupServiceState) {
        state = loadedState
        notifyGroupsChanged()

        // Refresh tabs after state is loaded
        SwingUtilities.invokeLater {
            TabUtils.refreshAllTabs(project)
        }
    }

    // ============== UI State ==============

    private val expandedGroups = mutableSetOf<String>()

    fun isGroupExpanded(groupId: String): Boolean = expandedGroups.contains(groupId)

    fun toggleGroupExpanded(groupId: String) {
        if (expandedGroups.contains(groupId)) {
            expandedGroups.remove(groupId)
        } else {
            expandedGroups.add(groupId)
        }
        notifyGroupsChanged()
    }

    fun setGroupExpanded(groupId: String, expanded: Boolean) {
        if (expanded) {
            expandedGroups.add(groupId)
        } else {
            expandedGroups.remove(groupId)
        }
        notifyGroupsChanged()
    }

    /**
     * Public method to get current branch (for poller).
     */
    fun getCurrentBranchPublic(): String = getCurrentBranch()

    // ============== Branch Change Handling ==============

    fun onBranchChanged() {
        val newBranch = getCurrentBranch()
        println("TabGroups: Branch changed to '$newBranch', refreshing UI...")
        lastKnownBranch = newBranch
        notifyGroupsChanged()
        TabUtils.refreshAllTabs(project)
    }

    // ============== Branch Detection ==============

    /**
     * Get current Git branch name.
     * Uses pure string-based reflection - no Git4Idea imports!
     */
    private fun getCurrentBranch(): String {
        return GitUtils.getCurrentBranchName(project)
    }
    /**
     * Get groups for the current branch.
     */
    private fun getCurrentBranchGroups(): MutableList<TabGroup> {
        val branch = getCurrentBranch()

        println("TabGroups: Getting groups for branch '$branch'")

        // Get branch-specific groups
        val branchGroupStates = state.branchGroups.getOrPut(branch) { mutableListOf() }

        // Combine: pinned groups first, then branch groups
        val allGroups = mutableListOf<TabGroup>()

        // Add pinned groups (visible on all branches)
        allGroups.addAll(state.pinnedGroups.map { it.toTabGroup() })

        // Add branch-specific groups
        allGroups.addAll(branchGroupStates.map { it.toTabGroup() })

        println("TabGroups: Found ${state.pinnedGroups.size} pinned + ${branchGroupStates.size} branch groups")

        return allGroups
    }

    /**
     * Save groups for the current branch.
     */
    private fun saveCurrentBranchGroups(groups: List<TabGroup>) {
        val branch = getCurrentBranch()

        // Separate pinned and non-pinned groups
        val pinnedGroups = groups.filter { it.isPinned }
        val branchGroups = groups.filter { !it.isPinned }

        // Save pinned groups
        state.pinnedGroups = pinnedGroups.map { GroupState(it) }.toMutableList()

        // Save branch groups
        val groupStates = branchGroups.map { GroupState(it) }.toMutableList()
        state.branchGroups[branch] = groupStates

        println("TabGroups: Saved ${pinnedGroups.size} pinned + ${branchGroups.size} groups for branch '$branch'")
    }

    // ============== Pinned Groups ==============

    fun toggleGroupPinned(groupId: String) {
        val branch = getCurrentBranch()
        val branchGroupStates = state.branchGroups.getOrPut(branch) { mutableListOf() }

        // Check if currently pinned
        val pinnedIndex = state.pinnedGroups.indexOfFirst { it.id == groupId }

        if (pinnedIndex >= 0) {
            // Unpin: Move from pinned to current branch (keep position)
            val groupState = state.pinnedGroups.removeAt(pinnedIndex)
            groupState.isPinned = false

            // Insert at same visual position
            val insertIndex = minOf(pinnedIndex, branchGroupStates.size)
            branchGroupStates.add(insertIndex, groupState)
        } else {
            // Pin: Move from current branch to pinned (keep position)
            val branchIndex = branchGroupStates.indexOfFirst { it.id == groupId }
            if (branchIndex >= 0) {
                val groupState = branchGroupStates.removeAt(branchIndex)
                groupState.isPinned = true

                // Insert at same visual position in pinned list
                val insertIndex = minOf(branchIndex, state.pinnedGroups.size)
                state.pinnedGroups.add(insertIndex, groupState)
            }
        }

        notifyGroupsChanged()
        TabUtils.refreshAllTabs(project)
    }

// ============== Reorder Groups ==============

    fun moveGroupUp(groupId: String) {
        val moved = moveInList(state.pinnedGroups, groupId, -1)
        if (!moved) {
            val branch = getCurrentBranch()
            val branchGroups = state.branchGroups.getOrPut(branch) { mutableListOf() }
            moveInList(branchGroups, groupId, -1)
        }
        notifyGroupsChanged()
    }

    fun moveGroupDown(groupId: String) {
        val moved = moveInList(state.pinnedGroups, groupId, 1)
        if (!moved) {
            val branch = getCurrentBranch()
            val branchGroups = state.branchGroups.getOrPut(branch) { mutableListOf() }
            moveInList(branchGroups, groupId, 1)
        }
        notifyGroupsChanged()
    }

    private fun moveInList(list: MutableList<GroupState>, groupId: String, direction: Int): Boolean {
        val index = list.indexOfFirst { it.id == groupId }
        if (index < 0) return false

        val newIndex = index + direction
        if (newIndex < 0 || newIndex >= list.size) return false

        val group = list.removeAt(index)
        list.add(newIndex, group)
        return true
    }

    // ============== Group Operations ==============

    fun getAllGroups(): List<TabGroup> {
        val groups = getCurrentBranchGroups().toList()
        val branch = getCurrentBranch()
        println("TabGroups: getAllGroups() called - branch='$branch', returning ${groups.size} groups: ${groups.map { it.name }}")
        return groups
    }

    fun createGroup(name: String): TabGroup {
        val groups = getCurrentBranchGroups()
        val group = TabGroup(name = name)
        groups.add(group)
        saveCurrentBranchGroups(groups)
        notifyGroupsChanged()
        return group
    }

    fun findGroupById(id: String): TabGroup? {
        return getCurrentBranchGroups().find { it.id == id }
    }

    fun findGroupForFile(file: VirtualFile): TabGroup? {
        return getCurrentBranchGroups().find { it.containsFile(file.path) }
    }

    // ============== File Operations ==============

    fun addFileToGroup(file: VirtualFile, group: TabGroup) {
        group.addFile(file.path)

        val groups = getCurrentBranchGroups()
        saveCurrentBranchGroups(groups)
        notifyGroupsChanged()

        // Refresh tab color
        TabUtils.refreshAllTabs(project)
    }

    fun removeFileFromGroup(file: VirtualFile) {
        val groups = getCurrentBranchGroups()
        groups.forEach { it.removeFile(file.path) }
        saveCurrentBranchGroups(groups)
        notifyGroupsChanged()

        // Refresh tab color
        TabUtils.refreshAllTabs(project)
    }

    // ============== Group Modification ==============

    fun renameGroup(groupId: String, newName: String) {
        val group = findGroupById(groupId)
        group?.name = newName
        notifyGroupsChanged()
    }

    fun changeGroupColor(groupId: String, preset: ColorPreset) {
        // Check pinned groups first
        val pinnedGroup = state.pinnedGroups.find { it.id == groupId }
        if (pinnedGroup != null) {
            pinnedGroup.colorRgb = preset.mainColor.rgb
            notifyGroupsChanged()
            TabUtils.refreshAllTabs(project)
            return
        }

        // Check branch groups
        val branch = getCurrentBranch()
        val groupStates = state.branchGroups.getOrPut(branch) { mutableListOf() }

        groupStates.find { it.id == groupId }?.colorRgb = preset.mainColor.rgb
        notifyGroupsChanged()
        TabUtils.refreshAllTabs(project)
    }

    fun deleteGroup(groupId: String) {
        // Check if it's a pinned group
        if (state.pinnedGroups.any { it.id == groupId }) {
            state.pinnedGroups.removeIf { it.id == groupId }
        } else {
            // Remove from branch groups
            val groups = getCurrentBranchGroups().filter { !it.isPinned }.toMutableList()
            groups.removeIf { it.id == groupId }

            val branch = getCurrentBranch()
            val groupStates = groups.map { GroupState(it) }.toMutableList()
            state.branchGroups[branch] = groupStates
        }

        notifyGroupsChanged()
        TabUtils.refreshAllTabs(project)
    }

    // ============== File Move Handling ==============

    fun handleFileMoved(oldPath: String, newPath: String) {
        val groups = getCurrentBranchGroups()
        groups.forEach { group ->
            if (group.containsFile(oldPath)) {
                group.removeFile(oldPath)
                group.addFile(newPath)
            }
        }
        saveCurrentBranchGroups(groups)
        notifyGroupsChanged()
    }

    fun handleFileDeleted(path: String) {
        val groups = getCurrentBranchGroups()
        groups.forEach { it.removeFile(path) }
        saveCurrentBranchGroups(groups)
        notifyGroupsChanged()
    }

    // ============== Change Notification ==============

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        notifyGroupsChanged()
    }

    private fun notifyGroupsChanged() {
        listeners.forEach { it.invoke() }
    }

    // ============== File Ordering ==============

    fun sortGroupFiles(groupId: String) {
        // Check pinned groups first
        val pinnedGroup = state.pinnedGroups.find { it.id == groupId }
        if (pinnedGroup != null) {
            pinnedGroup.filePaths = sortFilesByExtensionThenName(pinnedGroup.filePaths)
            notifyGroupsChanged()
            return
        }

        // Check branch groups
        val branch = getCurrentBranch()
        val groupStates = state.branchGroups.getOrPut(branch) { mutableListOf() }

        val group = groupStates.find { it.id == groupId }
        if (group != null) {
            group.filePaths = sortFilesByExtensionThenName(group.filePaths)
            notifyGroupsChanged()
        }
    }

    fun moveFileUp(groupId: String, filePath: String) {
        moveFileInGroup(groupId, filePath, -1)
    }

    fun moveFileDown(groupId: String, filePath: String) {
        moveFileInGroup(groupId, filePath, 1)
    }

    private fun moveFileInGroup(groupId: String, filePath: String, direction: Int) {
        // Check pinned groups first
        val pinnedGroup = state.pinnedGroups.find { it.id == groupId }
        if (pinnedGroup != null) {
            moveInFileList(pinnedGroup.filePaths, filePath, direction)
            notifyGroupsChanged()
            return
        }

        // Check branch groups
        val branch = getCurrentBranch()
        val groupStates = state.branchGroups.getOrPut(branch) { mutableListOf() }

        val group = groupStates.find { it.id == groupId }
        if (group != null) {
            moveInFileList(group.filePaths, filePath, direction)
            notifyGroupsChanged()
        }
    }

    private fun moveInFileList(list: MutableList<String>, filePath: String, direction: Int) {
        val index = list.indexOf(filePath)
        if (index < 0) return

        val newIndex = index + direction
        if (newIndex < 0 || newIndex >= list.size) return

        list.removeAt(index)
        list.add(newIndex, filePath)
    }

    /**
     * Sort files by extension first, then by name within each extension.
     * Example: App.kt, Main.kt, Utils.kt, Config.xml, Data.xml
     */
    private fun sortFilesByExtensionThenName(files: MutableList<String>): MutableList<String> {
        return files.sortedWith(compareBy(
            {
                val name = it.substringAfterLast("/")
                if (name.contains(".")) name.substringAfterLast(".").lowercase() else ""
            },
            {
                it.substringAfterLast("/").lowercase()
            }
        )).toMutableList()
    }

    override fun dispose() {
        listeners.clear()
    }

    // ============== Companion ==============

    companion object {
        fun getInstance(project: Project): TabGroupService {
            return project.service()
        }
    }
}