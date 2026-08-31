package com.github.popovyuriy.tabsgroupplugin.storage

/**
 * The serialized shape of `tabGroups.xml`.
 *
 * Groups live in exactly one list: [pinnedGroups], or the [branchGroups] bucket for the branch
 * they belong to. Projects with no detectable branch use the [GroupStore.NO_BRANCH] key like any
 * other branch.
 */
class TabGroupsState {
    var branchGroups: MutableMap<String, MutableList<GroupState>> = mutableMapOf()
    var pinnedGroups: MutableList<GroupState> = mutableListOf()

    fun copy(): TabGroupsState = TabGroupsState().also { copy ->
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

    /** Paths in *stored* form: relative to the project root where possible. */
    var filePaths: MutableList<String> = mutableListOf()

    fun copy(): GroupState = GroupState().also {
        it.id = id
        it.name = name
        it.colorId = colorId
        it.filePaths = filePaths.toMutableList()
    }
}