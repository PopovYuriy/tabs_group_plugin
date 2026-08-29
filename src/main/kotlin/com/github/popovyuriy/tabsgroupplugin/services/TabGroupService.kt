package com.github.popovyuriy.tabsgroupplugin.services

import com.github.popovyuriy.tabsgroupplugin.model.ColorPreset
import com.github.popovyuriy.tabsgroupplugin.model.TabGroup
import com.github.popovyuriy.tabsgroupplugin.storage.GroupStorageService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The façade the rest of the plugin talks to.
 *
 * Besides orchestrating the other services it maintains a path -> color index. Tab color lookup
 * happens on the EDT for every tab on every repaint; without the index each lookup rebuilt every
 * group and linearly scanned every path.
 */
@Service(Service.Level.PROJECT)
class TabGroupService(private val project: Project) : Disposable {

    private val storage: GroupStorageService get() = GroupStorageService.getInstance(project)
    // Held directly rather than looked up lazily: dispose() needs it while the project is
    // already being torn down, when service lookup can fail.
    private val branchService: GitBranchService = GitBranchService.getInstance(project)
    private val colorService: TabColorService get() = TabColorService.getInstance(project)

    private val listeners = CopyOnWriteArrayList<Runnable>()
    private val expandedGroups: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    private val branchListener = GitBranchService.BranchChangeListener { _, _ -> refresh() }

    @Volatile
    private var pathIndex: Map<String, ColorPreset> = emptyMap()

    @Volatile
    private var trackedNames: Set<String> = emptySet()

    init {
        branchService.addBranchChangeListener(branchListener)
        rebuildIndex()
    }

    val currentBranch: String get() = branchService.getCurrentBranch()

    // ============== Reads ==============

    fun getAllGroups(): List<TabGroup> = storage.getAllGroups(currentBranch)

    /** Pinned groups, rendered above the branch section. */
    fun getPinnedGroups(): List<TabGroup> = storage.getPinnedGroups()

    /** Groups belonging to the current branch only. */
    fun getBranchGroups(): List<TabGroup> = storage.getBranchGroups(currentBranch)

    fun findGroupById(groupId: String): TabGroup? = getAllGroups().firstOrNull { it.id == groupId }

    fun findGroupForFile(file: VirtualFile): TabGroup? = findGroupForPath(file.path)

    fun findGroupForPath(path: String): TabGroup? =
        if (pathIndex.containsKey(path)) getAllGroups().firstOrNull { it.containsFile(path) } else null

    /** Fast path used by the tab color provider. */
    fun presetForPath(path: String): ColorPreset? = pathIndex[path]

    /**
     * Whether any tracked path contains a segment with this name. Lets the VFS listener ignore
     * the thousands of irrelevant create events a branch checkout or a build produces.
     */
    fun isTrackedName(name: String): Boolean = trackedNames.contains(name)

    fun canMoveGroup(groupId: String, offset: Int): Boolean =
        storage.canMoveGroup(currentBranch, groupId, offset)

    // ============== Groups ==============

    fun createGroup(name: String): TabGroup {
        val existing = getAllGroups()
        val group = TabGroup(name = name.trim().ifEmpty { "New Group" }, color = nextPreset(existing))
        storage.addGroup(currentBranch, group)
        refresh()
        return group
    }

    fun deleteGroup(groupId: String) {
        expandedGroups.remove(groupId)
        if (storage.deleteGroup(currentBranch, groupId)) refresh()
    }

    fun renameGroup(groupId: String, newName: String) {
        if (storage.renameGroup(currentBranch, groupId, newName.trim())) refresh()
    }

    fun changeGroupColor(groupId: String, preset: ColorPreset) {
        if (storage.setGroupColor(currentBranch, groupId, preset)) refresh()
    }

    fun toggleGroupPinned(groupId: String) {
        val branch = currentBranch
        val changed = if (storage.isGroupPinned(groupId)) {
            storage.unpinGroup(branch, groupId)
        } else {
            storage.pinGroup(branch, groupId)
        }
        if (changed) refresh()
    }

    // ============== Files ==============

    fun addFileToGroup(file: VirtualFile, group: TabGroup) = addFilesToGroup(listOf(file), group)

    fun addFilesToGroup(files: List<VirtualFile>, group: TabGroup) {
        val branch = currentBranch
        var changed = false
        for (file in files) {
            if (file.isDirectory) continue
            // A file belongs to at most one group.
            changed = storage.removeFileFromAllGroups(branch, file.path) or changed
            changed = storage.addFileToGroup(branch, group.id, file.path) or changed
        }
        if (changed) refresh()
    }

    fun removeFileFromGroup(file: VirtualFile) = removeFilesFromGroups(listOf(file))

    fun removeFilesFromGroups(files: List<VirtualFile>) {
        val branch = currentBranch
        var changed = false
        for (file in files) {
            changed = storage.removeFileFromAllGroups(branch, file.path) or changed
        }
        if (changed) refresh()
    }

    fun removeFilePathFromGroups(path: String) {
        if (storage.removeFileFromAllGroups(currentBranch, path)) refresh()
    }

    fun handlePathMoved(oldPath: String, newPath: String, isDirectory: Boolean) {
        val branch = currentBranch
        val changed = if (isDirectory) {
            storage.moveDirectory(branch, oldPath, newPath)
        } else {
            storage.movePath(branch, oldPath, newPath)
        }
        if (changed) refresh()
    }

    fun handlePathRemoved(path: String, isDirectory: Boolean) {
        val branch = currentBranch
        val changed = if (isDirectory) {
            storage.removeDirectory(branch, path)
        } else {
            storage.removeFileFromAllGroups(branch, path)
        }
        if (changed) refresh()
    }

    // ============== Ordering ==============

    fun moveGroupUp(groupId: String) = moveGroup(groupId, -1)

    fun moveGroupDown(groupId: String) = moveGroup(groupId, 1)

    private fun moveGroup(groupId: String, offset: Int) {
        if (storage.moveGroup(currentBranch, groupId, offset)) refresh()
    }

    fun moveFileUp(groupId: String, filePath: String) = moveFile(groupId, filePath, -1)

    fun moveFileDown(groupId: String, filePath: String) = moveFile(groupId, filePath, 1)

    private fun moveFile(groupId: String, filePath: String, offset: Int) {
        if (storage.moveFileInGroup(currentBranch, groupId, filePath, offset)) refresh()
    }

    fun sortGroupFiles(groupId: String) {
        if (storage.sortGroupFiles(currentBranch, groupId)) refresh()
    }

    // ============== UI state ==============

    fun isGroupExpanded(groupId: String): Boolean = expandedGroups.contains(groupId)

    fun toggleGroupExpanded(groupId: String) = setGroupExpanded(groupId, !isGroupExpanded(groupId))

    fun setGroupExpanded(groupId: String, expanded: Boolean) {
        val changed = if (expanded) expandedGroups.add(groupId) else expandedGroups.remove(groupId)
        if (changed) notifyListeners()
    }

    // ============== Listeners ==============

    /** Keep a reference to [listener]: removal is by identity. */
    fun addChangeListener(listener: Runnable) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    /** Rebuilds the lookup index, repaints tabs and notifies the UI. */
    fun refresh() {
        rebuildIndex()
        colorService.refreshAllTabs()
        notifyListeners()
    }

    private fun notifyListeners() {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            fireChanged()
        } else {
            application.invokeLater({ fireChanged() }, ModalityState.any())
        }
    }

    private fun fireChanged() {
        if (project.isDisposed) return
        for (listener in listeners) {
            try {
                listener.run()
            } catch (e: Exception) {
                thisLogger().error("Tab group change listener failed", e)
            }
        }
    }

    private fun rebuildIndex() {
        val index = HashMap<String, ColorPreset>()
        val names = HashSet<String>()
        for (group in getAllGroups()) {
            for (path in group.filePaths) {
                index[path] = group.color
                path.split('/').forEach { segment -> if (segment.isNotEmpty()) names.add(segment) }
            }
        }
        pathIndex = index
        trackedNames = names
    }

    /** Picks the least-used preset, so colors stay varied and are reproducible across restarts. */
    private fun nextPreset(existing: List<TabGroup>): ColorPreset {
        val used = existing.groupingBy { it.color }.eachCount()
        return ColorPreset.entries.minByOrNull { used[it] ?: 0 } ?: ColorPreset.DEFAULT
    }

    // ============== Lifecycle ==============

    override fun dispose() {
        branchService.removeBranchChangeListener(branchListener)
        listeners.clear()
        expandedGroups.clear()
        pathIndex = emptyMap()
        trackedNames = emptySet()
    }

    companion object {
        fun getInstance(project: Project): TabGroupService = project.service()
    }
}