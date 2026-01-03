package com.github.popovyuriy.tabsgroupplugin.util

import com.intellij.openapi.project.Project

object GitUtils {

    fun getCurrentBranchName(project: Project): String {
        return try {
            // Method 1: Use Git4Idea directly via reflection
            val branchName = getBranchViaGit4Idea(project)
            if (!branchName.isNullOrBlank()) {
                println("TabGroups: Git4Idea branch: $branchName")
                return branchName
            }

            // Method 2: Read .git/HEAD file
            val headBranch = getBranchFromGitHead(project)
            if (!headBranch.isNullOrBlank()) {
                println("TabGroups: .git/HEAD branch: $headBranch")
                return headBranch
            }

            println("TabGroups: Could not detect branch, using 'default'")
            "default"
        } catch (e: Exception) {
            println("TabGroups: Branch detection failed: ${e.message}")
            "default"
        }
    }

    private fun getBranchViaGit4Idea(project: Project): String? {
        try {
            val managerClass = Class.forName("git4idea.repo.GitRepositoryManager")
            val getInstanceMethod = managerClass.getMethod("getInstance", Project::class.java)
            val manager = getInstanceMethod.invoke(null, project) ?: return null

            val getReposMethod = managerClass.getMethod("getRepositories")
            val repositories = getReposMethod.invoke(manager) as? List<*> ?: return null

            val repository = repositories.firstOrNull() ?: return null

            // Try multiple methods to get branch name
            val methods = listOf("getCurrentBranchName", "getCurrentRevision")
            for (methodName in methods) {
                try {
                    val method = repository.javaClass.getMethod(methodName)
                    val result = method.invoke(repository)
                    if (result is String && result.isNotBlank()) {
                        return result
                    }
                } catch (e: NoSuchMethodException) {
                    continue
                }
            }

            // Try getCurrentBranch().getName()
            try {
                val getBranchMethod = repository.javaClass.getMethod("getCurrentBranch")
                val branch = getBranchMethod.invoke(repository) ?: return null
                val getNameMethod = branch.javaClass.getMethod("getName")
                return getNameMethod.invoke(branch) as? String
            } catch (e: Exception) {
                return null
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun getBranchFromGitHead(project: Project): String? {
        try {
            val basePath = project.basePath ?: return null
            val headFile = java.io.File(basePath, ".git/HEAD")

            if (!headFile.exists()) {
                // Check if .git is a file (worktree or submodule)
                val gitFile = java.io.File(basePath, ".git")
                if (gitFile.isFile) {
                    val gitDir = gitFile.readText().trim().removePrefix("gitdir: ")
                    val actualHeadFile = java.io.File(gitDir, "HEAD")
                    if (actualHeadFile.exists()) {
                        return parseBranchFromHead(actualHeadFile.readText())
                    }
                }
                return null
            }

            return parseBranchFromHead(headFile.readText())
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseBranchFromHead(headContent: String): String? {
        val content = headContent.trim()

        // Format: "ref: refs/heads/branch-name"
        if (content.startsWith("ref: refs/heads/")) {
            return content.removePrefix("ref: refs/heads/")
        }

        // Detached HEAD - return short commit hash
        if (content.length >= 7) {
            return content.substring(0, 7)
        }

        return null
    }
}