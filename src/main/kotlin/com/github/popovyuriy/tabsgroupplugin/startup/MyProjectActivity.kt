package com.github.popovyuriy.tabsgroupplugin.startup

import com.github.popovyuriy.tabsgroupplugin.listeners.FileChangeListener
import com.github.popovyuriy.tabsgroupplugin.services.GitBranchService
import com.github.popovyuriy.tabsgroupplugin.services.TabColorService
import com.github.popovyuriy.tabsgroupplugin.services.TabGroupService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.delay

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        println("TabGroups: Plugin initialized for project: ${project.name}")

        // Initialize services (this also starts branch polling)
        GitBranchService.getInstance(project)
        TabGroupService.getInstance(project)
        val colorService = TabColorService.getInstance(project)

        // Register file system listener
        project.messageBus
            .connect()
            .subscribe(
                VirtualFileManager.VFS_CHANGES,
                FileChangeListener(project)
            )
        println("TabGroups: File change listener registered")

        // Wait for IDE to initialize, then refresh tab colors
        delay(1000)
        colorService.refreshAllTabs()
        println("TabGroups: Initial tab colors refreshed")
    }
}