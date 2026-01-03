package com.github.popovyuriy.tabsgroupplugin.startup

import com.github.popovyuriy.tabsgroupplugin.listeners.BranchChangePoller
import com.github.popovyuriy.tabsgroupplugin.listeners.FileChangeListener
import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.TabGroupService
import com.github.popovyuriy.tabsgroupplugin.util.TabUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.delay

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        println("TabGroups: Plugin initialized for project: ${project.name}")

        // Initialize the service (loads persisted state)
        val service = TabGroupService.getInstance(project)

        // Register file system listener (for move/rename/delete)
        project.messageBus
            .connect()
            .subscribe(
                VirtualFileManager.VFS_CHANGES,
                FileChangeListener(project)
            )
        println("TabGroups: File change listener registered")

        // Start branch change poller
        val poller = BranchChangePoller(project, service)
        poller.start()
        Disposer.register(service, poller)

        // Wait a bit for IDE to fully initialize tabs, then refresh colors
        delay(1000)
        TabUtils.refreshAllTabs(project)
        println("TabGroups: Tab colors refreshed")
    }
}