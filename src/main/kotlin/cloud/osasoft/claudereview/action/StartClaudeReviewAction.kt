package cloud.osasoft.claudereview.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
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

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Collecting Git Diff…", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Running git diff HEAD…"

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

                notify(project, "Collected ${diffOutput.lines().size} lines of diff output.", NotificationType.INFORMATION)
            }
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    companion object {
        fun notify(project: com.intellij.openapi.project.Project, content: String, type: NotificationType) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ClaudeReview")
                .createNotification(content, type)
                .notify(project)
        }
    }
}
