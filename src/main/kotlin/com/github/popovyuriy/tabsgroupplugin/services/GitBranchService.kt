package com.github.popovyuriy.tabsgroupplugin.services

import com.github.popovyuriy.tabsgroupplugin.storage.GroupStore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Detects the current Git branch by reading `.git/HEAD` directly.
 *
 * Reading the file rather than calling into git4idea is deliberate: git4idea is a separate
 * bundled plugin, so without a `<depends>` entry its classes are not even visible to our
 * classloader (the old reflection-based lookup could never succeed). Declaring that dependency
 * would in turn make the plugin unloadable in an IDE where the user disabled Git support.
 * Parsing HEAD keeps the plugin dependency-free and therefore installable in every JetBrains IDE.
 *
 * Polling runs on the shared application scheduler and is gated on HEAD's modification stamp,
 * so the steady-state cost is one `File.lastModified()` per interval.
 */
@Service(Service.Level.PROJECT)
class GitBranchService(private val project: Project) : Disposable {

    private val listeners = CopyOnWriteArrayList<BranchChangeListener>()

    @Volatile
    private var currentBranch: String = GroupStore.NO_BRANCH

    @Volatile
    private var gitDir: File? = null

    @Volatile
    private var headStamp: Long = -1L

    private var pollTask: ScheduledFuture<*>? = null

    init {
        pollTask = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(Runnable { poll() }, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    // ============== Public API ==============

    fun getCurrentBranch(): String = currentBranch

    /** Forces a synchronous re-read. Must not be called on the EDT. */
    fun refreshNow() = poll()

    fun addBranchChangeListener(listener: BranchChangeListener) {
        listeners.add(listener)
    }

    fun removeBranchChangeListener(listener: BranchChangeListener) {
        listeners.remove(listener)
    }

    // ============== Detection ==============

    private fun poll() {
        if (project.isDisposed) return
        try {
            val dir = resolveGitDir() ?: run {
                updateBranch(GroupStore.NO_BRANCH)
                return
            }
            val head = File(dir, "HEAD")
            val stamp = head.lastModified()
            if (stamp == headStamp && currentBranch != GroupStore.NO_BRANCH) return
            headStamp = stamp
            updateBranch(parseHead(head) ?: GroupStore.NO_BRANCH)
        } catch (e: Exception) {
            thisLogger().debug("Branch detection failed", e)
        }
    }

    private fun resolveGitDir(): File? {
        gitDir?.let { if (it.isDirectory) return it }

        val basePath = project.basePath ?: return null
        val dotGit = File(basePath, ".git")
        val resolved = when {
            dotGit.isDirectory -> dotGit
            // Worktree or submodule: .git is a file containing "gitdir: <path>".
            dotGit.isFile -> {
                val target = dotGit.readText().trim().removePrefix("gitdir:").trim()
                val file = File(target).let { if (it.isAbsolute) it else File(basePath, target) }
                file.takeIf { it.isDirectory }
            }
            else -> null
        }
        gitDir = resolved
        return resolved
    }

    private fun parseHead(head: File): String? {
        if (!head.isFile) return null
        val content = head.readText().trim()
        if (content.startsWith(SYMBOLIC_REF_PREFIX)) {
            return content.removePrefix(SYMBOLIC_REF_PREFIX).takeIf { it.isNotBlank() }
        }
        // Detached HEAD: use a short hash so groups are at least stable per commit.
        return if (content.length >= 7) content.substring(0, 7) else null
    }

    private fun updateBranch(newBranch: String) {
        val oldBranch = currentBranch
        if (oldBranch == newBranch) return
        currentBranch = newBranch

        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            listeners.forEach { listener ->
                try {
                    listener.onBranchChanged(oldBranch, newBranch)
                } catch (e: Exception) {
                    thisLogger().error("Branch change listener failed", e)
                }
            }
        }, ModalityState.any())
    }

    // ============== Lifecycle ==============

    override fun dispose() {
        pollTask?.cancel(false)
        pollTask = null
        listeners.clear()
    }

    companion object {
        private const val POLL_INTERVAL_SECONDS = 2L
        private const val SYMBOLIC_REF_PREFIX = "ref: refs/heads/"

        fun getInstance(project: Project): GitBranchService = project.service()
    }

    fun interface BranchChangeListener {
        fun onBranchChanged(oldBranch: String, newBranch: String)
    }
}