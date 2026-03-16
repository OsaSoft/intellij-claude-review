package cloud.osasoft.claudereview.ui

import cloud.osasoft.claudereview.action.loadCommitList
import cloud.osasoft.claudereview.action.notifyClaudeReview
import cloud.osasoft.claudereview.export.ReviewCompiler
import cloud.osasoft.claudereview.git.CommitDiffLoader
import cloud.osasoft.claudereview.model.DiffSource
import cloud.osasoft.claudereview.model.FileDiff
import cloud.osasoft.claudereview.model.FileStatus
import cloud.osasoft.claudereview.model.ReviewModel
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class ReviewPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val model = project.getService(ReviewModel::class.java)
    private val fileList = JBList<FileDiff>()
    private var diffPanel: DiffRequestPanel? = null
    private val commentLabel = JLabel("  0 comments")
    private val sourceComboBox: ComboBox<Any>
    private val sourceComboModel = DefaultComboBoxModel<Any>()
    private var isLoading = false
    private var loadedCommitCount = 0
    private val finishButton: JButton

    init {
        // Set initial button reference (before createToolbar uses it)
        finishButton = JButton("Finish Review").apply {
            addActionListener { finishReview() }
        }

        // Initialize combo box (before createToolbar uses it)
        sourceComboBox = ComboBox(sourceComboModel)
        sourceComboModel.addElement(DiffSource.Uncommitted)
        sourceComboBox.renderer = DiffSourceRenderer()
        sourceComboBox.addActionListener {
            if (isLoading) return@addActionListener
            val selected = sourceComboBox.selectedItem ?: return@addActionListener
            if (selected is LoadMoreSentinel) {
                loadMoreCommits()
                return@addActionListener
            }
            if (selected is DiffSource && selected != model.getActiveSource()) {
                switchSource(selected)
            }
        }

        val toolbar = createToolbar()
        add(toolbar, BorderLayout.NORTH)

        val splitter = JBSplitter(false, 0.25f)

        // Left: file list
        fileList.cellRenderer = FileListCellRenderer()
        fileList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        fileList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                fileList.selectedValue?.let { showDiffForFile(it) }
            }
        }
        splitter.firstComponent = JBScrollPane(fileList)

        // Right: diff viewer
        val diffPanelInstance = DiffManager.getInstance().createRequestPanel(project, {}, null)
        Disposer.register(this, diffPanelInstance)
        diffPanel = diffPanelInstance
        splitter.secondComponent = diffPanelInstance.component

        add(splitter, BorderLayout.CENTER)

        // Register comment change listener for live count updates
        model.addCommentChangeListener(::updateCommentCount)
    }

    fun populateFiles() {
        val listModel = DefaultListModel<FileDiff>()
        model.getFileDiffs().forEach { listModel.addElement(it) }
        fileList.model = listModel
        if (listModel.size() > 0) {
            fileList.selectedIndex = 0
        }
    }

    fun populateCommitList(commits: List<DiffSource.Commit>) {
        // Remove existing "Load more..." if present
        for (i in sourceComboModel.size - 1 downTo 0) {
            if (sourceComboModel.getElementAt(i) is LoadMoreSentinel) {
                sourceComboModel.removeElementAt(i)
            }
        }

        for (commit in commits) {
            sourceComboModel.addElement(commit)
        }
        loadedCommitCount += commits.size

        // Add "Load more..." sentinel if we got a full batch
        if (commits.size >= 10) {
            sourceComboModel.addElement(LoadMoreSentinel)
        }
    }

    private fun updateCommentCount() {
        val count = model.getCommentCount()
        val fileCount = model.getCommentedFileCount()
        commentLabel.text = if (count == 0) {
            "  0 comments"
        } else {
            "  $count comment${if (count != 1) "s" else ""} on $fileCount file${if (fileCount != 1) "s" else ""}"
        }
    }

    private fun showDiffForFile(fileDiff: FileDiff) {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileDiff.filePath)
        val factory = DiffContentFactory.getInstance()

        val oldContent = factory.create(project, fileDiff.oldContent, fileType)
        val newContent = if (fileDiff.virtualFile != null) {
            factory.create(project, fileDiff.virtualFile)
        } else {
            factory.create(project, fileDiff.newContent, fileType)
        }

        val title = when (fileDiff.status) {
            FileStatus.NEW -> "${fileDiff.filePath} (new)"
            FileStatus.DELETED -> "${fileDiff.filePath} (deleted)"
            FileStatus.RENAMED -> "${fileDiff.filePath} (renamed)"
            FileStatus.MODIFIED -> fileDiff.filePath
        }

        val request = SimpleDiffRequest(title, oldContent, newContent, "Before", "After")
        request.putUserData(ReviewDiffExtension.REVIEW_FILE_PATH_KEY, fileDiff.filePath)
        diffPanel?.setRequest(request)
    }

    private fun createToolbar(): JComponent {
        val panel = JPanel(BorderLayout())
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
        }

        buttonPanel.add(sourceComboBox)
        buttonPanel.add(finishButton)
        buttonPanel.add(commentLabel)

        panel.add(buttonPanel, BorderLayout.WEST)
        return panel
    }

    private fun switchSource(source: DiffSource) {
        if (isLoading) return
        setLoading(true)

        // If diffs are already cached for this source, just switch
        if (model.hasSourceDiffs(source)) {
            model.setActiveSource(source)
            populateFiles()
            setLoading(false)
            return
        }

        // For uncommitted with no cached diffs (shouldn't normally happen), just switch
        if (source is DiffSource.Uncommitted) {
            model.setActiveSource(source)
            populateFiles()
            setLoading(false)
            return
        }

        // Load committed diff on background thread
        val commit = source as DiffSource.Commit
        val repos = GitRepositoryManager.getInstance(project).repositories
        if (repos.isEmpty()) {
            setLoading(false)
            return
        }
        val repoRoot = repos.first().root

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading commit diff\u2026", true) {
            override fun run(indicator: ProgressIndicator) {
                val loader = CommitDiffLoader(commit.sha)
                val fileDiffs = loader.load(project, repoRoot, indicator)

                ApplicationManager.getApplication().invokeLater {
                    model.loadSource(source, fileDiffs)
                    populateFiles()
                    setLoading(false)
                }
            }
        })
    }

    private fun loadMoreCommits() {
        if (isLoading) return
        val repos = GitRepositoryManager.getInstance(project).repositories
        if (repos.isEmpty()) return

        setLoading(true)
        val repoRoot = repos.first().root
        // Reset selection to current active source to avoid triggering switch
        sourceComboBox.selectedItem = model.getActiveSource()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading more commits\u2026", true) {
            override fun run(indicator: ProgressIndicator) {
                val commits = loadCommitList(project, repoRoot, loadedCommitCount, 10)
                for (commit in commits) {
                    model.trackSource(commit)
                }

                ApplicationManager.getApplication().invokeLater {
                    populateCommitList(commits)
                    setLoading(false)
                }
            }
        })
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        sourceComboBox.isEnabled = !loading
        finishButton.isEnabled = !loading
    }

    private fun finishReview() {
        val sourcedComments = model.getAllSourcedComments()
        val allComments = sourcedComments.values.flatten()
        if (allComments.isEmpty()) {
            notifyClaudeReview(project, "No comments to export.", NotificationType.INFORMATION)
            return
        }
        val compiled = ReviewCompiler.compileSourced(sourcedComments)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(compiled), null)
        notifyClaudeReview(
            project,
            "${allComments.size} comment(s) copied to clipboard.",
            NotificationType.INFORMATION
        )
    }

    override fun dispose() {
        model.removeCommentChangeListener(::updateCommentCount)
    }
}

private object LoadMoreSentinel {
    override fun toString() = "Load more\u2026"
}

private class DiffSourceRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val display = when (value) {
            is DiffSource -> value.displayName
            is LoadMoreSentinel -> "Load more\u2026"
            else -> value?.toString() ?: ""
        }
        return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus)
    }
}
