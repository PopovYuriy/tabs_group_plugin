package com.github.popovyuriy.tabsgroupplugin.services

import com.github.popovyuriy.tabsgroupplugin.services.model.TabGroup
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import java.awt.Color

/**
 * Service for persisting and managing tab group data.
 */
@State(
    name = "TabGroupStorage",
    storages = [Storage("tabGroups.xml")]
)
@Service(Service.Level.PROJECT)
class GroupStorageService(private val project: Project) : PersistentStateComponent<GroupStorageService.State> {

    private var state = State()

    // ============== State Classes ==============

    class State {
        var branchGroups: MutableMap<String, MutableList<GroupState>> = mutableMapOf()
        var defaultGroups: MutableList<GroupState> = mutableListOf()
        var pinnedGroups: MutableList<GroupState> = mutableListOf()
    }

    class GroupState {
        var id: String = ""
        var name: String = ""
        var colorRgb: Int = Color.BLUE.rgb
        var filePaths: MutableList<String> = mutableListOf()
        var isPinned: Boolean = false

        constructor()

        constructor(group: TabGroup) {
            this.id = group.id
            this.name = group.name
            this.colorRgb = group.color.rgb
            this.filePaths = group.filePaths.toMutableList()
            this.isPinned = group.isPinned
        }

        fun toTabGroup(): TabGroup {
            return TabGroup(
                id = id,
                name = name,
                color = Color(colorRgb),
                isPinned = isPinned
            ).also { group ->
                filePaths.forEach { group.addFile(it) }
            }
        }
    }

    // ============== PersistentStateComponent ==============

    override fun getState(): State = state

    override fun loadState(loadedState: State) {
        state = loadedState
    }

    // ============== Group Retrieval ==============

    fun getGroupsForBranch(branch: String): List<TabGroup> {
        val branchGroups = if (branch == "default") {
            state.defaultGroups
        } else {
            state.branchGroups.getOrPut(branch) { mutableListOf() }
        }

        // Pinned groups + branch groups
        val allGroups = mutableListOf<TabGroup>()
        allGroups.addAll(state.pinnedGroups.map { it.toTabGroup() })
        allGroups.addAll(branchGroups.map { it.toTabGroup() })

        return allGroups
    }

    fun getPinnedGroups(): List<TabGroup> {
        return state.pinnedGroups.map { it.toTabGroup() }
    }

    // ============== Group Modification ==============

    fun saveGroup(branch: String, group: TabGroup) {
        if (group.isPinned) {
            val existing = state.pinnedGroups.indexOfFirst { it.id == group.id }
            if (existing >= 0) {
                state.pinnedGroups[existing] = GroupState(group)
            } else {
                state.pinnedGroups.add(GroupState(group))
            }
        } else {
            val branchGroups = getBranchGroupStates(branch)
            val existing = branchGroups.indexOfFirst { it.id == group.id }
            if (existing >= 0) {
                branchGroups[existing] = GroupState(group)
            } else {
                branchGroups.add(GroupState(group))
            }
        }
    }

    fun deleteGroup(branch: String, groupId: String) {
        // Try pinned first
        if (state.pinnedGroups.removeIf { it.id == groupId }) {
            return
        }

        // Then branch groups
        getBranchGroupStates(branch).removeIf { it.id == groupId }
    }

    fun updateGroupName(branch: String, groupId: String, newName: String) {
        findGroupState(branch, groupId)?.name = newName
    }

    fun updateGroupColor(branch: String, groupId: String, newColor: Color) {
        findGroupState(branch, groupId)?.colorRgb = newColor.rgb
    }

    // ============== Pin/Unpin ==============

    fun pinGroup(branch: String, groupId: String) {
        val branchGroups = getBranchGroupStates(branch)
        val index = branchGroups.indexOfFirst { it.id == groupId }
        if (index >= 0) {
            val groupState = branchGroups.removeAt(index)
            groupState.isPinned = true
            val insertIndex = minOf(index, state.pinnedGroups.size)
            state.pinnedGroups.add(insertIndex, groupState)
        }
    }

    fun unpinGroup(branch: String, groupId: String) {
        val index = state.pinnedGroups.indexOfFirst { it.id == groupId }
        if (index >= 0) {
            val groupState = state.pinnedGroups.removeAt(index)
            groupState.isPinned = false
            val branchGroups = getBranchGroupStates(branch)
            val insertIndex = minOf(index, branchGroups.size)
            branchGroups.add(insertIndex, groupState)
        }
    }

    fun isGroupPinned(groupId: String): Boolean {
        return state.pinnedGroups.any { it.id == groupId }
    }

    // ============== File Operations ==============

    fun addFileToGroup(branch: String, groupId: String, filePath: String) {
        val groupState = findGroupState(branch, groupId) ?: return
        if (!groupState.filePaths.contains(filePath)) {
            groupState.filePaths.add(filePath)
        }
    }

    fun removeFileFromGroup(branch: String, groupId: String, filePath: String) {
        findGroupState(branch, groupId)?.filePaths?.remove(filePath)
    }

    fun removeFileFromAllGroups(branch: String, filePath: String) {
        state.pinnedGroups.forEach { it.filePaths.remove(filePath) }
        getBranchGroupStates(branch).forEach { it.filePaths.remove(filePath) }
    }

    fun moveFile(branch: String, oldPath: String, newPath: String) {
        state.pinnedGroups.forEach { group ->
            val index = group.filePaths.indexOf(oldPath)
            if (index >= 0) {
                group.filePaths[index] = newPath
            }
        }
        getBranchGroupStates(branch).forEach { group ->
            val index = group.filePaths.indexOf(oldPath)
            if (index >= 0) {
                group.filePaths[index] = newPath
            }
        }
    }

    // ============== Ordering ==============

    fun moveGroupUp(branch: String, groupId: String) {
        if (moveInList(state.pinnedGroups, groupId, -1)) return
        moveInList(getBranchGroupStates(branch), groupId, -1)
    }

    fun moveGroupDown(branch: String, groupId: String) {
        if (moveInList(state.pinnedGroups, groupId, 1)) return
        moveInList(getBranchGroupStates(branch), groupId, 1)
    }

    fun moveFileUp(branch: String, groupId: String, filePath: String) {
        val groupState = findGroupState(branch, groupId) ?: return
        moveFileInList(groupState.filePaths, filePath, -1)
    }

    fun moveFileDown(branch: String, groupId: String, filePath: String) {
        val groupState = findGroupState(branch, groupId) ?: return
        moveFileInList(groupState.filePaths, filePath, 1)
    }

    fun sortGroupFiles(branch: String, groupId: String) {
        val groupState = findGroupState(branch, groupId) ?: return
        groupState.filePaths = groupState.filePaths.sortedWith(compareBy(
            { path ->
                val name = path.substringAfterLast("/")
                if (name.contains(".")) name.substringAfterLast(".").lowercase() else ""
            },
            { path -> path.substringAfterLast("/").lowercase() }
        )).toMutableList()
    }

    // ============== Private Helpers ==============

    private fun getBranchGroupStates(branch: String): MutableList<GroupState> {
        return if (branch == "default") {
            state.defaultGroups
        } else {
            state.branchGroups.getOrPut(branch) { mutableListOf() }
        }
    }

    private fun findGroupState(branch: String, groupId: String): GroupState? {
        return state.pinnedGroups.find { it.id == groupId }
            ?: getBranchGroupStates(branch).find { it.id == groupId }
    }

    private fun moveInList(list: MutableList<GroupState>, groupId: String, direction: Int): Boolean {
        val index = list.indexOfFirst { it.id == groupId }
        if (index < 0) return false

        val newIndex = index + direction
        if (newIndex < 0 || newIndex >= list.size) return false

        val item = list.removeAt(index)
        list.add(newIndex, item)
        return true
    }

    private fun moveFileInList(list: MutableList<String>, filePath: String, direction: Int) {
        val index = list.indexOf(filePath)
        if (index < 0) return

        val newIndex = index + direction
        if (newIndex < 0 || newIndex >= list.size) return

        list.removeAt(index)
        list.add(newIndex, filePath)
    }

    // ============== Companion ==============

    companion object {
        fun getInstance(project: Project): GroupStorageService = project.service()
    }
}