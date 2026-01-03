package com.github.popovyuriy.tabsgroupplugin.services.tabGroup.state

class TabGroupServiceState {
    var branchGroups: MutableMap<String, MutableList<GroupState>> = mutableMapOf()
    var pinnedGroups: MutableList<GroupState> = mutableListOf()
}