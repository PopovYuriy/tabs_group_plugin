package com.github.popovyuriy.tabsgroupplugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.io.File
import java.util.Timer
import java.util.TimerTask
import javax.swing.SwingUtilities

/**
 * Service for Git branch detection and monitoring.
 */
@Service(Service.Level.PROJECT)
class GitBranchService(private val project: Project) : Disposable {

    private val listeners = mutableListOf<BranchChangeListener>()
    private var currentBranch: String = "default"
    private var timer: Timer? = null

    init {
        currentBranch = detectBranch()
        startPolling()
    }

    // ============== Public API ==============

    fun getCurrentBranch(): String = currentBranch

    fun addBranchChangeListener(listener: BranchChangeListener) {
        listeners.add(listener)
    }

    fun removeBranchChangeListener(listener: BranchChangeListener) {
        listeners.remove(listener)
    }

    // ============== Branch Detection ==============

    private fun detectBranch(): String {
        // Method 1: Try Git4Idea API
        val git4IdeaBranch = detectViaGit4Idea()
        if (!git4IdeaBranch.isNullOrBlank()) {
            return git4IdeaBranch
        }

        // Method 2: Read .git/HEAD file
        val headBranch = detectViaGitHead()
        if (!headBranch.isNullOrBlank()) {
            return headBranch
        }

        return "default"
    }

    private fun detectViaGit4Idea(): String? {
        try {
            val managerClass = Class.forName("git4idea.repo.GitRepositoryManager")
            val getInstanceMethod = managerClass.getMethod("getInstance", Project::class.java)
            val manager = getInstanceMethod.invoke(null, project) ?: return null

            val getReposMethod = managerClass.getMethod("getRepositories")
            val repositories = getReposMethod.invoke(manager) as? List<*> ?: return null
            val repository = repositories.firstOrNull() ?: return null

            // Try getCurrentBranchName()
            try {
                val method = repository.javaClass.getMethod("getCurrentBranchName")
                val result = method.invoke(repository) as? String
                if (!result.isNullOrBlank()) return result
            } catch (_: Exception) {}

            // Try getCurrentBranch().getName()
            try {
                val getBranchMethod = repository.javaClass.getMethod("getCurrentBranch")
                val branch = getBranchMethod.invoke(repository) ?: return null
                val getNameMethod = branch.javaClass.getMethod("getName")
                return getNameMethod.invoke(branch) as? String
            } catch (_: Exception) {}

            return null
        } catch (_: Exception) {
            return null
        }
    }

    private fun detectViaGitHead(): String? {
        try {
            val basePath = project.basePath ?: return null
            val headFile = File(basePath, ".git/HEAD")

            val content = when {
                headFile.exists() -> headFile.readText().trim()
                File(basePath, ".git").isFile -> {
                    // Worktree or submodule
                    val gitDir = File(basePath, ".git").readText().trim().removePrefix("gitdir: ")
                    File(gitDir, "HEAD").takeIf { it.exists() }?.readText()?.trim()
                }
                else -> null
            } ?: return null

            // Parse "ref: refs/heads/branch-name"
            if (content.startsWith("ref: refs/heads/")) {
                return content.removePrefix("ref: refs/heads/")
            }

            // Detached HEAD - return short hash
            if (content.length >= 7) {
                return content.substring(0, 7)
            }

            return null
        } catch (_: Exception) {
            return null
        }
    }

    // ============== Polling ==============

    private fun startPolling() {
        timer = Timer("TabGroups-BranchPoller", true)
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkBranchChange()
            }
        }, 1000, 2000)
    }

    private fun checkBranchChange() {
        try {
            val newBranch = detectBranch()
            if (newBranch != currentBranch) {
                val oldBranch = currentBranch
                currentBranch = newBranch
                SwingUtilities.invokeLater {
                    notifyBranchChanged(oldBranch, newBranch)
                }
            }
        } catch (_: Exception) {}
    }

    private fun notifyBranchChanged(oldBranch: String, newBranch: String) {
        listeners.forEach { it.onBranchChanged(oldBranch, newBranch) }
    }

    // ============== Lifecycle ==============

    override fun dispose() {
        timer?.cancel()
        timer = null
        listeners.clear()
    }

    // ============== Companion ==============

    companion object {
        fun getInstance(project: Project): GitBranchService = project.service()
    }

    // ============== Listener Interface ==============

    fun interface BranchChangeListener {
        fun onBranchChanged(oldBranch: String, newBranch: String)
    }
}