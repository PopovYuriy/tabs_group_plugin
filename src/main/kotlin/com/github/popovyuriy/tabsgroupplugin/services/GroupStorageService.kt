package com.github.popovyuriy.tabsgroupplugin.storage

import com.github.popovyuriy.tabsgroupplugin.model.ColorPreset
import com.github.popovyuriy.tabsgroupplugin.model.TabGroup
import com.github.popovyuriy.tabsgroupplugin.util.ProjectPaths
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Persists tab groups and adapts between the stored representation and [TabGroup].
 *
 * All the interesting logic lives in [GroupStore]; this class only owns the lock, the
 * project-relative path conversion, and format migration.
 */
@State(
    name = "TabGroupStorage",
    storages = [Storage("tabGroups.xml")]
)
@Service(Service.Level.PROJECT)
class GroupStorageService(private val project: Project) : PersistentStateComponent<TabGroupsState> {

    private val lock = Any()
    private var state = TabGroupsState()
    private var store = GroupStore(state)

    // ============== PersistentStateComponent ==============

    /** Returns a deep copy: the platform may serialize off the thread that is mutating us. */
    override fun getState(): TabGroupsState = synchronized(lock) { state.copy() }

    override fun loadState(loadedState: TabGroupsState) {
        synchronized(lock) {
            state = loadedState
            store = GroupStore(loadedState)
            migrate(loadedState)
            store.reconcile()
        }
    }

    override fun noStateLoaded() {
        synchronized(lock) { state.version = CURRENT_VERSION }
    }

    // ============== Reads ==============

    fun getPinnedGroups(): List<TabGroup> =
        synchronized(lock) { store.pinnedGroups().map { it.toTabGroup() } }

    fun getBranchGroups(branch: String): List<TabGroup> =
        synchronized(lock) { store.branchGroups(branch).map { it.toTabGroup() } }

    fun getAllGroups(branch: String): List<TabGroup> =
        synchronized(lock) { store.allGroups(branch).map { it.toTabGroup() } }

    fun isGroupPinned(groupId: String): Boolean = synchronized(lock) { store.isPinned(groupId) }

    fun canMoveGroup(branch: String, groupId: String, offset: Int): Boolean =
        synchronized(lock) { store.canMoveGroup(branch, groupId, offset) }

    // ============== Writes (each returns whether anything changed) ==============

    fun addGroup(branch: String, group: TabGroup) = synchronized(lock) {
        store.addGroup(branch, group.toGroupState())
    }

    fun deleteGroup(branch: String, groupId: String): Boolean =
        synchronized(lock) { store.removeGroup(branch, groupId) }

    fun renameGroup(branch: String, groupId: String, newName: String): Boolean =
        synchronized(lock) { store.rename(branch, groupId, newName) }

    fun setGroupColor(branch: String, groupId: String, preset: ColorPreset): Boolean =
        synchronized(lock) { store.setColor(branch, groupId, preset.name) }

    fun pinGroup(branch: String, groupId: String): Boolean =
        synchronized(lock) { store.pin(branch, groupId) }

    fun unpinGroup(branch: String, groupId: String): Boolean =
        synchronized(lock) { store.unpin(branch, groupId) }

    fun addFileToGroup(branch: String, groupId: String, absolutePath: String): Boolean =
        synchronized(lock) { store.addFile(branch, groupId, stored(absolutePath)) }

    fun removeFileFromAllGroups(branch: String, absolutePath: String): Boolean =
        synchronized(lock) { store.removeFileEverywhere(branch, stored(absolutePath)) }

    fun movePath(branch: String, oldAbsolute: String, newAbsolute: String): Boolean =
        synchronized(lock) { store.replacePath(branch, stored(oldAbsolute), stored(newAbsolute)) }

    fun moveDirectory(branch: String, oldAbsolute: String, newAbsolute: String): Boolean =
        synchronized(lock) { store.replacePathPrefix(branch, stored(oldAbsolute), stored(newAbsolute)) }

    fun removeDirectory(branch: String, absolutePath: String): Boolean =
        synchronized(lock) { store.removePathPrefix(branch, stored(absolutePath)) }

    fun moveGroup(branch: String, groupId: String, offset: Int): Boolean =
        synchronized(lock) { store.moveGroup(branch, groupId, offset) }

    fun moveFileInGroup(branch: String, groupId: String, absolutePath: String, offset: Int): Boolean =
        synchronized(lock) { store.moveFileInGroup(branch, groupId, stored(absolutePath), offset) }

    fun sortGroupFiles(branch: String, groupId: String): Boolean =
        synchronized(lock) { store.sortFiles(branch, groupId) }

    // ============== Conversion ==============

    private fun stored(absolutePath: String) = ProjectPaths.toStored(project, absolutePath)

    private fun GroupState.toTabGroup() = TabGroup(
        id = id,
        name = name,
        color = ColorPreset.byId(colorId) ?: ColorPreset.byLegacyRgb(colorRgb) ?: ColorPreset.DEFAULT,
        isPinned = isPinned,
        filePaths = filePaths.map { ProjectPaths.toAbsolute(project, it) }
    )

    private fun TabGroup.toGroupState() = GroupState().also { group ->
        group.id = id
        group.name = name
        group.colorId = color.name
        group.isPinned = isPinned
        group.filePaths = filePaths.mapTo(mutableListOf()) { stored(it) }
    }

    // ============== Migration ==============

    private fun migrate(loaded: TabGroupsState) {
        if (loaded.version >= CURRENT_VERSION) return

        val everyGroup = loaded.pinnedGroups.asSequence() +
                loaded.defaultGroups.asSequence() +
                loaded.branchGroups.values.asSequence().flatMap { it.asSequence() }

        for (group in everyGroup) {
            // v1: raw RGB int -> preset name.
            if (group.colorId.isBlank()) {
                group.colorId = (ColorPreset.byLegacyRgb(group.colorRgb) ?: ColorPreset.DEFAULT).name
                group.colorRgb = 0
            }
            // v1: absolute paths -> project-relative where possible.
            for (i in group.filePaths.indices) {
                group.filePaths[i] = stored(group.filePaths[i])
            }
        }
        loaded.version = CURRENT_VERSION
    }

    companion object {
        const val CURRENT_VERSION: Int = 1

        fun getInstance(project: Project): GroupStorageService = project.service()
    }
}