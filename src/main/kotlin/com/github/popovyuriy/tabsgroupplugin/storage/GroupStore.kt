package com.github.popovyuriy.tabsgroupplugin.storage

/**
 * Every mutation of [TabGroupsState] lives here.
 *
 * Deliberately free of IntelliJ and AWT types so the ordering, pinning and path-rewriting rules
 * can be unit-tested without the platform test fixture. Paths are always in *stored* form; the
 * absolute/relative conversion is [GroupStorageService]'s job.
 *
 * Callers are responsible for synchronization.
 */
class GroupStore(private val state: TabGroupsState) {

    companion object {
        /**
         * Sentinel for "no VCS branch". Contains ':' which Git forbids in ref names, so it can
         * never be confused with a real branch (the previous "default" sentinel collided with a
         * branch actually named `default`).
         */
        const val NO_BRANCH: String = ":none:"

        private val FILE_ORDER: Comparator<String> = compareBy<String>(
            { path ->
                val name = path.substringAfterLast('/')
                if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""
            },
            { path -> path.substringAfterLast('/').lowercase() }
        )
    }

    // ---------------- reads (must never mutate) ----------------

    fun pinnedGroups(): List<GroupState> = state.pinnedGroups.toList()

    fun branchGroups(branch: String): List<GroupState> = branchListOrNull(branch)?.toList() ?: emptyList()

    fun allGroups(branch: String): List<GroupState> = pinnedGroups() + branchGroups(branch)

    fun find(branch: String, groupId: String): GroupState? =
        state.pinnedGroups.firstOrNull { it.id == groupId }
            ?: branchListOrNull(branch)?.firstOrNull { it.id == groupId }

    fun isPinned(groupId: String): Boolean = state.pinnedGroups.any { it.id == groupId }

    // ---------------- group lifecycle ----------------

    /** New groups always start unpinned, in the current branch's list. */
    fun addGroup(branch: String, group: GroupState) {
        group.isPinned = false
        writableBranchList(branch).add(group)
    }

    fun removeGroup(branch: String, groupId: String): Boolean {
        if (state.pinnedGroups.removeAll { it.id == groupId }) return true
        return branchListOrNull(branch)?.removeAll { it.id == groupId } ?: false
    }

    fun rename(branch: String, groupId: String, newName: String): Boolean {
        val group = find(branch, groupId) ?: return false
        if (group.name == newName) return false
        group.name = newName
        return true
    }

    fun setColor(branch: String, groupId: String, colorId: String): Boolean {
        val group = find(branch, groupId) ?: return false
        if (group.colorId == colorId) return false
        group.colorId = colorId
        group.colorRgb = 0
        return true
    }

    // ---------------- pin / unpin ----------------

    fun pin(branch: String, groupId: String): Boolean {
        val list = branchListOrNull(branch) ?: return false
        val index = list.indexOfFirst { it.id == groupId }
        if (index < 0) return false
        val group = list.removeAt(index)
        group.isPinned = true
        // Append. The old code reused the branch index as a pinned index, which put the group
        // in an arbitrary position.
        state.pinnedGroups.add(group)
        return true
    }

    fun unpin(branch: String, groupId: String): Boolean {
        val index = state.pinnedGroups.indexOfFirst { it.id == groupId }
        if (index < 0) return false
        val group = state.pinnedGroups.removeAt(index)
        group.isPinned = false
        writableBranchList(branch).add(group)
        return true
    }

    // ---------------- files ----------------

    fun addFile(branch: String, groupId: String, path: String): Boolean {
        val group = find(branch, groupId) ?: return false
        if (group.filePaths.contains(path)) return false
        group.filePaths.add(path)
        return true
    }

    fun removeFileEverywhere(branch: String, path: String): Boolean {
        var changed = false
        forEachGroup(branch) { if (it.filePaths.remove(path)) changed = true }
        return changed
    }

    fun replacePath(branch: String, oldPath: String, newPath: String): Boolean {
        var changed = false
        forEachGroup(branch) { group ->
            val index = group.filePaths.indexOf(oldPath)
            if (index >= 0) {
                group.filePaths[index] = newPath
                changed = true
            }
        }
        return changed
    }

    /**
     * Re-points every tracked file living under [oldPrefix] at [newPrefix].
     * Handles directory renames and moves, which the old per-file logic missed entirely.
     */
    fun replacePathPrefix(branch: String, oldPrefix: String, newPrefix: String): Boolean {
        var changed = false
        forEachGroup(branch) { group ->
            for (i in group.filePaths.indices) {
                val path = group.filePaths[i]
                if (path == oldPrefix || path.startsWith("$oldPrefix/")) {
                    group.filePaths[i] = newPrefix + path.substring(oldPrefix.length)
                    changed = true
                }
            }
        }
        return changed
    }

    fun removePathPrefix(branch: String, prefix: String): Boolean {
        var changed = false
        forEachGroup(branch) { group ->
            if (group.filePaths.removeAll { it == prefix || it.startsWith("$prefix/") }) changed = true
        }
        return changed
    }

    // ---------------- ordering ----------------

    /**
     * Moves a group by [offset] *within its own section*. Pinned groups are always rendered
     * above branch groups, so the two sections reorder independently.
     */
    fun moveGroup(branch: String, groupId: String, offset: Int): Boolean {
        val list = sectionFor(branch, groupId) ?: return false
        return moveWithin(list, { it.id == groupId }, offset)
    }

    /** Whether [moveGroup] with the same arguments would do anything. Drives menu enablement. */
    fun canMoveGroup(branch: String, groupId: String, offset: Int): Boolean {
        val list = sectionFor(branch, groupId) ?: return false
        val index = list.indexOfFirst { it.id == groupId }
        return index >= 0 && (index + offset) in list.indices
    }

    fun moveFileInGroup(branch: String, groupId: String, path: String, offset: Int): Boolean {
        val group = find(branch, groupId) ?: return false
        return moveWithin(group.filePaths, { it == path }, offset)
    }

    fun sortFiles(branch: String, groupId: String): Boolean {
        val group = find(branch, groupId) ?: return false
        val sorted = group.filePaths.sortedWith(FILE_ORDER)
        if (sorted == group.filePaths) return false
        group.filePaths = sorted.toMutableList()
        return true
    }

    // ---------------- maintenance ----------------

    /**
     * Repairs invariants after load: the pinned flag is derived from list membership, and empty
     * branch buckets (created on *read* by older builds) are dropped.
     */
    fun reconcile() {
        state.pinnedGroups.forEach { it.isPinned = true }
        state.defaultGroups.forEach { it.isPinned = false }
        state.branchGroups.values.forEach { list -> list.forEach { it.isPinned = false } }
        state.branchGroups.entries.removeAll { it.value.isEmpty() }
    }

    // ---------------- internals ----------------

    private fun branchListOrNull(branch: String): MutableList<GroupState>? =
        if (branch == NO_BRANCH) state.defaultGroups else state.branchGroups[branch]

    /** Only for write paths: creating a bucket during a read is what bloated `tabGroups.xml`. */
    private fun writableBranchList(branch: String): MutableList<GroupState> =
        if (branch == NO_BRANCH) state.defaultGroups
        else state.branchGroups.getOrPut(branch) { mutableListOf() }

    private fun sectionFor(branch: String, groupId: String): MutableList<GroupState>? =
        if (isPinned(groupId)) state.pinnedGroups else branchListOrNull(branch)

    private fun forEachGroup(branch: String, action: (GroupState) -> Unit) {
        state.pinnedGroups.forEach(action)
        branchListOrNull(branch)?.forEach(action)
    }

    private fun <T> moveWithin(list: MutableList<T>, match: (T) -> Boolean, offset: Int): Boolean {
        val index = list.indexOfFirst(match)
        if (index < 0) return false
        val target = index + offset
        if (target !in list.indices) return false
        list.add(target, list.removeAt(index))
        return true
    }
}