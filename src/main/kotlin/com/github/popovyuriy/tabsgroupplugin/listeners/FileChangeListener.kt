package com.github.popovyuriy.tabsgroupplugin.listeners

import com.github.popovyuriy.tabsgroupplugin.services.TabGroupService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps stored paths in sync with the file system.
 *
 * In theory a move produces a [VFileMoveEvent]. In practice the IDE frequently emits a
 * create + delete pair instead, so recent creates are remembered briefly and matched against
 * subsequent deletes by name.
 *
 * Two things the previous implementation got wrong:
 *  - it spawned a `Thread` per created file just to expire the entry five seconds later, which a
 *    branch checkout or a build turns into thousands of sleeping threads. Entries are now expired
 *    lazily, with no threads at all;
 *  - `VFS_CHANGES` is an application-wide topic, so it fires for every open project and every
 *    internal IDE file. Only names that actually appear in a tracked path are recorded now.
 */
class FileChangeListener(private val project: Project) : BulkFileListener {

    private val recentCreates = ConcurrentHashMap<String, CreatedEntry>()

    override fun after(events: MutableList<out VFileEvent>) {
        if (project.isDisposed) return
        val service = TabGroupService.getInstance(project)

        expireStaleCreates()

        for (event in events) {
            when (event) {
                is VFilePropertyChangeEvent ->
                    if (event.propertyName == VirtualFile.PROP_NAME) handleRename(event, service)

                is VFileMoveEvent -> {
                    val file = event.file
                    val oldPath = "${event.oldParent.path}/${file.name}"
                    service.handlePathMoved(oldPath, file.path, file.isDirectory)
                }

                is VFileCreateEvent -> rememberCreate(event, service)

                is VFileDeleteEvent -> handleDelete(event, service)
            }
        }
    }

    private fun handleRename(event: VFilePropertyChangeEvent, service: TabGroupService) {
        val file = event.file
        val oldName = event.oldValue as? String ?: return
        val parentPath = file.parent?.path ?: return
        service.handlePathMoved("$parentPath/$oldName", file.path, file.isDirectory)
    }

    private fun rememberCreate(event: VFileCreateEvent, service: TabGroupService) {
        val name = event.childName
        if (!service.isTrackedName(name)) return
        if (recentCreates.size >= MAX_TRACKED_CREATES) return
        recentCreates[name] = CreatedEntry(event.path, event.isDirectory, System.currentTimeMillis())
    }

    private fun handleDelete(event: VFileDeleteEvent, service: TabGroupService) {
        val file = event.file
        val deletedPath = file.path
        val created = recentCreates.remove(file.name)

        if (created != null && created.path != deletedPath && created.isDirectory == file.isDirectory) {
            service.handlePathMoved(deletedPath, created.path, file.isDirectory)
        } else {
            service.handlePathRemoved(deletedPath, file.isDirectory)
        }
    }

    private fun expireStaleCreates() {
        if (recentCreates.isEmpty()) return
        val cutoff = System.currentTimeMillis() - CREATE_TTL_MILLIS
        recentCreates.entries.removeIf { it.value.timestamp < cutoff }
    }

    private data class CreatedEntry(val path: String, val isDirectory: Boolean, val timestamp: Long)

    private companion object {
        const val CREATE_TTL_MILLIS = 5_000L
        const val MAX_TRACKED_CREATES = 256
    }
}