package com.github.popovyuriy.tabsgroupplugin.startup

import com.github.popovyuriy.tabsgroupplugin.listeners.FileChangeListener
import com.github.popovyuriy.tabsgroupplugin.services.GitBranchService
import com.github.popovyuriy.tabsgroupplugin.services.TabGroupService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager

class TabGroupsStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val groupService = TabGroupService.getInstance(project)

        // Parented to the service so the connection is released with the project. The previous
        // parameterless connect() leaked the subscription for the lifetime of the IDE.
        project.messageBus.connect(groupService)
            .subscribe(VirtualFileManager.VFS_CHANGES, FileChangeListener(project))

        // We are on a background dispatcher here, so the initial HEAD read is free.
        GitBranchService.getInstance(project).refreshNow()
        groupService.refresh()
    }
}