package cloud.osasoft.claudereview.action

import cloud.osasoft.claudereview.git.CommitLogParser
import cloud.osasoft.claudereview.git.UncommittedDiffLoader
import cloud.osasoft.claudereview.git.WorktreeParser
import cloud.osasoft.claudereview.model.DiffSource
import cloud.osasoft.claudereview.model.ReviewModel
import cloud.osasoft.claudereview.model.WorktreeInfo
import cloud.osasoft.claudereview.ui.ReviewPanel
import cloud.osasoft.claudereview.editor.ReviewFileEditor
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
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import java.awt.Component

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
        val worktrees = WorktreeParser.listWorktrees(project, repo.root)

        if (worktrees.size <= 1) {
            // Single worktree (or none detected) — open directly as before
            val worktree = worktrees.firstOrNull()
            val worktreePath = worktree?.path ?: repo.root.path
            val branchName = worktree?.displayName ?: (repo.currentBranch?.name ?: "main")
            val repoRoot = if (worktree != null) {
                LocalFileSystem.getInstance().findFileByPath(worktree.path) ?: repo.root
            } else {
                repo.root
            }
            openWorktreeReview(project, worktreePath, branchName, repoRoot)
        } else {
            // Multiple worktrees — show chooser popup
            showWorktreeChooser(project, worktrees, repo.root)
        }
    }

    private fun showWorktreeChooser(project: Project, worktrees: List<WorktreeInfo>, fallbackRoot: VirtualFile) {
        val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(worktrees)
            .setTitle("Select Worktree to Review")
            .setRenderer(object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val wt = value as? WorktreeInfo
                    val display = if (wt != null) "${wt.displayName}  (${wt.path})" else value?.toString() ?: ""
                    return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus)
                }
            })
            .setItemChosenCallback { worktree ->
                val repoRoot = LocalFileSystem.getInstance().findFileByPath(worktree.path) ?: fallbackRoot
                openWorktreeReview(project, worktree.path, worktree.displayName, repoRoot)
            }
            .createPopup()

        popup.showInFocusCenter()
    }

    private fun openWorktreeReview(project: Project, worktreePath: String, branchName: String, repoRoot: VirtualFile) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Collecting Git Diff\u2026", true) {
            override fun run(indicator: ProgressIndicator) {
                val model = project.getService(ReviewModel::class.java)
                val state = model.getOrCreateState(worktreePath)
                state.clear()

                // Load uncommitted changes
                val loader = UncommittedDiffLoader()
                val fileDiffs = loader.load(project, repoRoot, indicator)

                if (fileDiffs.isEmpty() && !loader.lastLoadHadChanges) {
                    LOG.info("No uncommitted changes found in $worktreePath, will still open for commit browsing")
                }

                val source = DiffSource.Uncommitted
                state.trackSource(source)
                state.loadSource(source, fileDiffs)

                // Load initial commit list
                indicator.text = "Loading commit history\u2026"
                val commits = loadCommitList(project, repoRoot, 0, 10)
                for (commit in commits) {
                    state.trackSource(commit)
                }

                ApplicationManager.getApplication().invokeLater {
                    val reviewPanel = openReviewInEditor(project, worktreePath, branchName, repoRoot)
                    reviewPanel?.populateFiles()
                    reviewPanel?.populateCommitList(commits)
                }
            }
        })
    }

    private fun openReviewInEditor(project: Project, worktreePath: String, branchName: String, repoRoot: VirtualFile): ReviewPanel? {
        val editorManager = FileEditorManager.getInstance(project)

        // Close existing tab for the same worktree (but leave other worktree tabs open)
        for (file in editorManager.openFiles) {
            if (file is ReviewVirtualFile && file.worktreePath == worktreePath) {
                editorManager.closeFile(file)
            }
        }

        val reviewFile = ReviewVirtualFile(worktreePath, branchName, repoRoot)
        editorManager.openFile(reviewFile, true)

        val editors = editorManager.getEditors(reviewFile)
        val reviewEditor = editors.filterIsInstance<ReviewFileEditor>().firstOrNull()
        return reviewEditor?.reviewPanel
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
