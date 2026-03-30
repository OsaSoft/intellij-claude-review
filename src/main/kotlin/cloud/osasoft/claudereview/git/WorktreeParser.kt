package cloud.osasoft.claudereview.git

import cloud.osasoft.claudereview.model.WorktreeInfo
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

private val LOG = logger<WorktreeParser>()

object WorktreeParser {

    fun listWorktrees(@Suppress("unused") project: Project, repoRoot: VirtualFile): List<WorktreeInfo> {
        return try {
            val cmd = GeneralCommandLine("git", "worktree", "list", "--porcelain")
            cmd.workDirectory = java.io.File(repoRoot.path)
            val handler = CapturingProcessHandler(cmd)
            val result = handler.runProcess(10_000)
            if (result.exitCode == 0) {
                parse(result.stdout)
            } else {
                LOG.warn("git worktree list failed: ${result.stderr}")
                emptyList()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to list worktrees: ${e.message}")
            emptyList()
        }
    }

    fun parse(porcelainOutput: String): List<WorktreeInfo> {
        if (porcelainOutput.isBlank()) return emptyList()

        val worktrees = mutableListOf<WorktreeInfo>()
        var currentPath: String? = null
        var currentHead: String? = null
        var currentBranch: String? = null
        var isBare = false

        for (line in porcelainOutput.lines()) {
            when {
                line.startsWith("worktree ") -> {
                    // Flush previous entry if any
                    if (currentPath != null && currentHead != null) {
                        worktrees.add(WorktreeInfo(currentPath, currentHead, currentBranch, isBare))
                    }
                    currentPath = line.removePrefix("worktree ")
                    currentHead = null
                    currentBranch = null
                    isBare = false
                }
                line.startsWith("HEAD ") -> currentHead = line.removePrefix("HEAD ")
                line.startsWith("branch ") -> currentBranch = line.removePrefix("branch ")
                line == "bare" -> isBare = true
                line == "detached" -> { /* head is detached, branch stays null */ }
            }
        }

        // Flush last entry
        if (currentPath != null && currentHead != null) {
            worktrees.add(WorktreeInfo(currentPath, currentHead, currentBranch, isBare))
        }

        return worktrees.filter { !it.isBare }
    }
}
