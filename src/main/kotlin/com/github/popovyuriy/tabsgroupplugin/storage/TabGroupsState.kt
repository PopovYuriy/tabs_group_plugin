package com.github.popovyuriy.tabsgroupplugin.storage

/**
 * The serialized shape of `tabGroups.xml`.
 *
 * Field names must stay stable: they are the on-disk format. New fields need a bump of
 * [GroupStorageService.CURRENT_VERSION] plus a migration step.
 */
class TabGroupsState {
    var version: Int = 0
    var branchGroups: MutableMap<String, MutableList<GroupState>> = mutableMapOf()

    /** Groups for projects with no detectable branch. Kept under the old name for compatibility. */
    var defaultGroups: MutableList<GroupState> = mutableListOf()
    var pinnedGroups: MutableList<GroupState> = mutableListOf()

    fun copy(): TabGroupsState = TabGroupsState().also { copy ->
        copy.version = version
        copy.defaultGroups = defaultGroups.mapTo(mutableListOf()) { it.copy() }
        copy.pinnedGroups = pinnedGroups.mapTo(mutableListOf()) { it.copy() }
        copy.branchGroups = branchGroups.mapValuesTo(mutableMapOf<String, MutableList<GroupState>>()) {
                (_, groups) -> groups.mapTo(mutableListOf()) { group -> group.copy() }
        }
    }
}

class GroupState {
    var id: String = ""
    var name: String = ""

    /** Name of a [com.github.popovyuriy.tabsgroupplugin.model.ColorPreset]. */
    var colorId: String = ""

    /** Legacy pre-1.1.0 field. Read during migration only, never written for new groups. */
    var colorRgb: Int = 0

    /** Paths in *stored* form: relative to the project root where possible. */
    var filePaths: MutableList<String> = mutableListOf()

    /**
     * Mirrors membership of [TabGroupsState.pinnedGroups] so the flag survives a round-trip.
     * It is never used to decide *where* a group lives; the owning list is the single source
     * of truth and [GroupStore.reconcile] repairs the flag on load.
     */
    var isPinned: Boolean = false

    fun copy(): GroupState = GroupState().also {
        it.id = id
        it.name = name
        it.colorId = colorId
        it.colorRgb = colorRgb
        it.filePaths = filePaths.toMutableList()
        it.isPinned = isPinned
    }
}