package cloud.osasoft.claudereview.action

import cloud.osasoft.claudereview.diff.DiffParser
import cloud.osasoft.claudereview.model.FileDiff
import cloud.osasoft.claudereview.model.FileStatus
import cloud.osasoft.claudereview.model.ReviewModel
import cloud.osasoft.claudereview.ui.ReviewPanel
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager

private val LOG = logger<StartClaudeReviewAction>()

class StartClaudeReviewAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val repos = GitRepositoryManager.getInstance(project).repositories
        if (repos.isEmpty()) {
            notify(project, "No git repository found in this project.", NotificationType.WARNING)
            return
        }

        val repo = repos.first()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Collecting Git Diff\u2026", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Running git diff HEAD\u2026"

                val diffHandler = GitLineHandler(project, repo.root, GitCommand.DIFF)
                diffHandler.addParameters("HEAD")
                val diffResult = Git.getInstance().runCommand(diffHandler)

                val statusHandler = GitLineHandler(project, repo.root, GitCommand.STATUS)
                statusHandler.addParameters("--porcelain")
                val statusResult = Git.getInstance().runCommand(statusHandler)

                if (!diffResult.success() && !statusResult.success()) {
                    notify(project, "Git command failed: ${diffResult.errorOutputAsJoinedString}", NotificationType.ERROR)
                    return
                }

                val diffOutput = diffResult.outputAsJoinedString
                val statusOutput = statusResult.outputAsJoinedString

                LOG.info("Git diff HEAD collected: ${diffOutput.length} chars")
                LOG.info("Git status collected: ${statusOutput.length} chars")

                if (diffOutput.isBlank() && statusOutput.isBlank()) {
                    notify(project, "No uncommitted changes found.", NotificationType.INFORMATION)
                    return
                }

                indicator.text = "Parsing diff output\u2026"

                val parsedFiles = DiffParser.parseChangedFiles(diffOutput)
                val untrackedPaths = DiffParser.parseUntrackedFiles(statusOutput)
                val rootPath = repo.root.path
                val fileDiffs = mutableListOf<FileDiff>()

                // Process tracked changed files from diff output
                for (parsed in parsedFiles) {
                    indicator.text = "Reading ${parsed.newPath}\u2026"

                    if (isBinaryFile(rootPath, parsed.newPath)) {
                        fileDiffs.add(FileDiff(parsed.newPath, "(binary file)", "(binary file)", parsed.status))
                        continue
                    }

                    val oldContent = when (parsed.status) {
                        FileStatus.NEW -> ""
                        FileStatus.DELETED -> getOldContent(project, repo.root, parsed.oldPath ?: parsed.newPath)
                        FileStatus.RENAMED -> getOldContent(project, repo.root, parsed.oldPath ?: parsed.newPath)
                        FileStatus.MODIFIED -> getOldContent(project, repo.root, parsed.oldPath ?: parsed.newPath)
                    }

                    val newContent = when (parsed.status) {
                        FileStatus.DELETED -> ""
                        else -> readWorkingDirFile(rootPath, parsed.newPath)
                    }

                    fileDiffs.add(FileDiff(parsed.newPath, oldContent, newContent, parsed.status))
                }

                // Process untracked files (new files not yet staged)
                val alreadyTracked = parsedFiles.map { it.newPath }.toSet()
                for (untrackedPath in untrackedPaths) {
                    if (untrackedPath in alreadyTracked) continue

                    indicator.text = "Reading $untrackedPath\u2026"

                    val fileOnDisk = java.io.File(rootPath, untrackedPath)
                    if (!fileOnDisk.isFile) continue

                    if (isBinaryFile(rootPath, untrackedPath)) {
                        fileDiffs.add(FileDiff(untrackedPath, "", "(binary file)", FileStatus.NEW))
                        continue
                    }

                    val newContent = readWorkingDirFile(rootPath, untrackedPath)
                    fileDiffs.add(FileDiff(untrackedPath, "", newContent, FileStatus.NEW))
                }

                LOG.info("Parsed ${fileDiffs.size} file diffs")

                // Populate the model and open the tool window on the EDT
                val model = project.getService(ReviewModel::class.java)
                model.clear()
                model.fileDiffs.addAll(fileDiffs)

                ApplicationManager.getApplication().invokeLater {
                    openReviewToolWindow(project)
                }
            }
        })
    }

    private fun getOldContent(project: Project, root: com.intellij.openapi.vfs.VirtualFile, filePath: String): String {
        return try {
            val handler = GitLineHandler(project, root, GitCommand.SHOW)
            handler.addParameters("HEAD:$filePath")
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) result.outputAsJoinedString else ""
        } catch (e: Exception) {
            LOG.warn("Failed to get old content for $filePath: ${e.message}")
            ""
        }
    }

    private fun readWorkingDirFile(rootPath: String, filePath: String): String {
        return try {
            java.io.File(rootPath, filePath).readText()
        } catch (e: Exception) {
            LOG.warn("Failed to read working directory file $filePath: ${e.message}")
            ""
        }
    }

    private fun isBinaryFile(rootPath: String, filePath: String): Boolean {
        val file = java.io.File(rootPath, filePath)
        if (!file.exists() || !file.isFile) return false
        return try {
            val bytes = file.inputStream().use { it.readNBytes(8192) }
            bytes.any { it == 0.toByte() }
        } catch (e: Exception) {
            false
        }
    }

    private fun openReviewToolWindow(project: Project) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        var toolWindow = toolWindowManager.getToolWindow("Claude Review")
        if (toolWindow == null) {
            toolWindow = toolWindowManager.registerToolWindow("Claude Review") {
                anchor = ToolWindowAnchor.BOTTOM
            }
        }
        val reviewPanel = ReviewPanel(project)
        reviewPanel.populateFiles()

        val contentManager = toolWindow.contentManager
        contentManager.removeAllContents(true)
        val content = contentManager.factory.createContent(reviewPanel, "Review", false)
        contentManager.addContent(content)
        toolWindow.show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    companion object {
        fun notify(project: Project, content: String, type: NotificationType) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ClaudeReview")
                .createNotification(content, type)
                .notify(project)
        }
    }
}
