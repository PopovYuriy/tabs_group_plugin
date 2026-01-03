package com.github.popovyuriy.tabsgroupplugin.services.tabGroup.state

import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.model.TabGroup
import java.awt.Color

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