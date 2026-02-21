package cloud.osasoft.claudereview.ui

import cloud.osasoft.claudereview.action.StartClaudeReviewAction
import cloud.osasoft.claudereview.export.ReviewCompiler
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.Timer

class ReviewPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val model = project.getService(ReviewModel::class.java)
    private val fileList = JBList<FileDiff>()
    private var diffPanel: DiffRequestPanel? = null
    private val commentLabel = JLabel("  0 comments")
    private val commentCountTimer: Timer

    init {
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

        // Poll comment count every 500ms to keep the label in sync
        commentCountTimer = Timer(500) { updateCommentCount() }
        commentCountTimer.isRepeats = true
        commentCountTimer.start()
    }

    fun populateFiles() {
        val listModel = DefaultListModel<FileDiff>()
        model.fileDiffs.forEach { listModel.addElement(it) }
        fileList.model = listModel
        if (listModel.size() > 0) {
            fileList.selectedIndex = 0
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

        val finishButton = JButton("Finish Review").apply {
            addActionListener { finishReview() }
        }
        buttonPanel.add(finishButton)
        buttonPanel.add(commentLabel)

        panel.add(buttonPanel, BorderLayout.WEST)
        return panel
    }

    private fun finishReview() {
        val comments = model.getAllComments()
        if (comments.isEmpty()) {
            StartClaudeReviewAction.notify(project, "No comments to export.", NotificationType.INFORMATION)
            return
        }
        val compiled = ReviewCompiler.compile(comments)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(compiled), null)
        StartClaudeReviewAction.notify(
            project,
            "${comments.size} comment(s) copied to clipboard.",
            NotificationType.INFORMATION
        )
    }

    override fun dispose() {
        commentCountTimer.stop()
    }
}
