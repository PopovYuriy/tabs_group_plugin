package com.github.popovyuriy.tabsgroupplugin.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * Translates between the absolute paths used at runtime and the paths written to `tabGroups.xml`.
 *
 * Anything inside the project root is stored relative to it, so groups survive the project being
 * moved, re-cloned, or opened by a teammate on a different machine. Files outside the project
 * (library sources, scratches) keep their absolute path.
 */
object ProjectPaths {

    fun toStored(project: Project, path: String): String {
        if (!isAbsolute(path)) return path
        val base = project.basePath ?: return path
        val relative = FileUtil.getRelativePath(base, path, '/') ?: return path
        return if (relative.startsWith("..")) path else relative
    }

    fun toAbsolute(project: Project, path: String): String {
        if (isAbsolute(path)) return path
        val base = project.basePath ?: return path
        return "$base/$path"
    }

    fun isAbsolute(path: String): Boolean =
        path.startsWith('/') || path.contains("://") || File(path).isAbsolute
}