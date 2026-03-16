package cloud.osasoft.claudereview.action

import cloud.osasoft.claudereview.git.CommitLogParser
import cloud.osasoft.claudereview.git.UncommittedDiffLoader
import cloud.osasoft.claudereview.model.DiffSource
import cloud.osasoft.claudereview.editor.ReviewFileEditor
import cloud.osasoft.claudereview.model.ReviewModel
import cloud.osasoft.claudereview.ui.ReviewPanel
import cloud.osasoft.claudereview.vfs.ReviewVirtualFile
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager

private val LOG = logger<StartClaudeReviewAction>()

fun notifyClaudeReview(project: Project, content: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("ClaudeReview")
        .createNotification(content, type)
        .notify(project)
}

fun loadCommitList(
    project: Project,
    repoRoot: VirtualFile,
    skip: Int,
    count: Int,
): List<DiffSource.Commit> = try {
    val handler = GitLineHandler(project, repoRoot, GitCommand.LOG)
    handler.addParameters("--format=%H%n%h%n%s%n%ar%n%at", "-$count")
    if (skip > 0) {
        handler.addParameters("--skip=$skip")
    }
    handler.addParameters("HEAD")
    val result = Git.getInstance().runCommand(handler)
    if (result.success()) {
        CommitLogParser.parse(result.outputAsJoinedString)
    } else {
        LOG.warn("git log failed: ${result.errorOutputAsJoinedString}")
        emptyList()
    }
} catch (e: Exception) {
    LOG.warn("Failed to load commit list: ${e.message}")
    emptyList()
}

class StartClaudeReviewAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val repos = GitRepositoryManager.getInstance(project).repositories
        if (repos.isEmpty()) {
            notifyClaudeReview(project, "No git repository found in this project.", NotificationType.WARNING)
            return
        }

        val repo = repos.first()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Collecting Git Diff\u2026", true) {
            override fun run(indicator: ProgressIndicator) {
                val model = project.getService(ReviewModel::class.java)
                model.clear()

                // Load uncommitted changes
                val loader = UncommittedDiffLoader()
                val fileDiffs = loader.load(project, repo.root, indicator)

                if (fileDiffs.isEmpty() && !loader.lastLoadHadChanges) {
                    LOG.info("No uncommitted changes found, will still open for commit browsing")
                }

                val source = DiffSource.Uncommitted
                model.trackSource(source)
                model.loadSource(source, fileDiffs)

                // Load initial commit list
                indicator.text = "Loading commit history\u2026"
                val commits = loadCommitList(project, repo.root, 0, 10)
                for (commit in commits) {
                    model.trackSource(commit)
                }

                ApplicationManager.getApplication().invokeLater {
                    val reviewPanel = openReviewInEditor(project)
                    reviewPanel?.populateFiles()
                    reviewPanel?.populateCommitList(commits)
                }
            }
        })
    }

    private fun openReviewInEditor(project: Project): ReviewPanel? {
        val editorManager = FileEditorManager.getInstance(project)

        for (file in editorManager.openFiles) {
            if (file is ReviewVirtualFile) {
                editorManager.closeFile(file)
            }
        }

        val reviewFile = ReviewVirtualFile()
        editorManager.openFile(reviewFile, true)

        val editors = editorManager.getEditors(reviewFile)
        val reviewEditor = editors.filterIsInstance<ReviewFileEditor>().firstOrNull()
        return reviewEditor?.reviewPanel
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
