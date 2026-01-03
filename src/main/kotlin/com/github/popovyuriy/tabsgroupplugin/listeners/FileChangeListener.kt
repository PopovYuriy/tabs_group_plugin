package com.github.popovyuriy.tabsgroupplugin.listeners

import com.github.popovyuriy.tabsgroupplugin.services.tabGroup.TabGroupService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import java.util.concurrent.ConcurrentHashMap

/**
 * LESSON: Real-World Event Handling
 *
 * In theory: Moving a file triggers VFileMoveEvent
 * In practice: IDE often does CREATE + DELETE instead
 *
 * Solution: Track recently created files, and when we see
 * a DELETE with the same filename, treat it as a MOVE.
 */
class FileChangeListener(private val project: Project) : BulkFileListener {

    // Track recently created files: filename -> full path
    // We use filename as key because moved files keep the same name
    private val recentlyCreated = ConcurrentHashMap<String, String>()

    override fun after(events: MutableList<out VFileEvent>) {
        val service = try {
            TabGroupService.getInstance(project)
        } catch (e: Exception) {
            return
        }

        // Process events in order
        for (event in events) {
            when (event) {
                // True rename (same directory, different name)
                is VFilePropertyChangeEvent -> {
                    if (event.propertyName == "name") {
                        handleRename(event, service)
                    }
                }

                // True move (rare, but handle it)
                is VFileMoveEvent -> {
                    handleMove(event, service)
                }

                // File created - might be part of a move operation
                is VFileCreateEvent -> {
                    val file = event.file ?: continue
                    val fileName = file.name
                    val newPath = file.path

                    // Remember this file was just created
                    recentlyCreated[fileName] = newPath
                    println("TabGroups: Tracking created file: $fileName -> $newPath")

                    // Clean up old entries after a delay (avoid memory leak)
                    scheduleCleanup(fileName)
                }

                // File deleted - check if it's part of a move
                is VFileDeleteEvent -> {
                    val deletedPath = event.file.path
                    val fileName = event.file.name

                    // Check if we recently saw a CREATE with the same filename
                    val newPath = recentlyCreated.remove(fileName)

                    if (newPath != null && newPath != deletedPath) {
                        // This is a MOVE: old location deleted, new location created
                        println("TabGroups: >>> DETECTED MOVE: $deletedPath -> $newPath")
                        service.handleFileMoved(deletedPath, newPath)
                    } else {
                        // This is a real delete
                        println("TabGroups: >>> DELETE: $deletedPath")
                        service.handleFileDeleted(deletedPath)
                    }
                }
            }
        }
    }

    private fun handleRename(event: VFilePropertyChangeEvent, service: TabGroupService) {
        val file = event.file
        val oldName = event.oldValue as String
        val parent = file.parent?.path ?: return
        val oldPath = "$parent/$oldName"
        val newPath = file.path

        println("TabGroups: >>> RENAME: $oldPath -> $newPath")
        service.handleFileMoved(oldPath, newPath)
    }

    private fun handleMove(event: VFileMoveEvent, service: TabGroupService) {
        val file = event.file
        val oldParent = event.oldParent.path
        val oldPath = "$oldParent/${file.name}"
        val newPath = file.path

        println("TabGroups: >>> MOVE: $oldPath -> $newPath")
        service.handleFileMoved(oldPath, newPath)
    }

    private fun scheduleCleanup(fileName: String) {
        // Remove from tracking after 5 seconds
        // This handles the case where a file is created but not as part of a move
        Thread {
            try {
                Thread.sleep(5000)
                recentlyCreated.remove(fileName)
            } catch (e: InterruptedException) {
                // Ignore
            }
        }.start()
    }
}