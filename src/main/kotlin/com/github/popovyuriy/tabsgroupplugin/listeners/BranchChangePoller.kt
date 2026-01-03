package com.github.popovyuriy.tabsgroupplugin.listeners

import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.TabGroupService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.util.Timer
import java.util.TimerTask
import javax.swing.SwingUtilities

/**
 * Polls for Git branch changes every few seconds.
 */
class BranchChangePoller(
    private val project: Project,
    private val service: TabGroupService
) : Disposable {

    private var timer: Timer? = null
    private var lastBranch: String? = null

    fun start() {
        // Get initial branch
        lastBranch = service.getCurrentBranchPublic()

        timer = Timer("TabGroups-BranchPoller", true)
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkBranchChange()
            }
        }, 1000, 2000)  // Check every 2 seconds

        println("TabGroups: Branch poller started, current branch: $lastBranch")
    }

    private fun checkBranchChange() {
        try {
            val currentBranch = service.getCurrentBranchPublic()
            if (lastBranch != null && lastBranch != currentBranch) {
                println("TabGroups: Branch changed from '$lastBranch' to '$currentBranch'")
                lastBranch = currentBranch
                SwingUtilities.invokeLater {
                    service.onBranchChanged()
                }
            } else if (lastBranch == null) {
                lastBranch = currentBranch
            }
        } catch (e: Exception) {
            println("TabGroups: Branch check error: ${e.message}")
        }
    }

    override fun dispose() {
        timer?.cancel()
        timer = null
        println("TabGroups: Branch poller stopped")
    }
}