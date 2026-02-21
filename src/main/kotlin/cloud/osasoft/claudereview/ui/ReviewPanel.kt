package cloud.osasoft.claudereview.ui

import cloud.osasoft.claudereview.model.FileDiff
import cloud.osasoft.claudereview.model.FileStatus
import cloud.osasoft.claudereview.model.ReviewModel
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class ReviewPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {
    private val model = project.getService(ReviewModel::class.java)
    private val fileList = JBList<FileDiff>()
    private var diffPanel: DiffRequestPanel? = null

    init {
        val toolbar = createToolbar()
        setToolbar(toolbar)

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
        diffPanel = diffPanelInstance
        splitter.secondComponent = diffPanelInstance.component

        setContent(splitter)
    }

    fun populateFiles() {
        val listModel = DefaultListModel<FileDiff>()
        model.fileDiffs.forEach { listModel.addElement(it) }
        fileList.model = listModel
        if (listModel.size() > 0) {
            fileList.selectedIndex = 0
        }
    }

    private fun showDiffForFile(fileDiff: FileDiff) {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileDiff.filePath)
        val factory = DiffContentFactory.getInstance()

        val oldContent = factory.create(project, fileDiff.oldContent, fileType)
        val newContent = factory.create(project, fileDiff.newContent, fileType)

        val title = when (fileDiff.status) {
            FileStatus.NEW -> "${fileDiff.filePath} (new)"
            FileStatus.DELETED -> "${fileDiff.filePath} (deleted)"
            FileStatus.RENAMED -> "${fileDiff.filePath} (renamed)"
            FileStatus.MODIFIED -> fileDiff.filePath
        }

        val request = SimpleDiffRequest(title, oldContent, newContent, "Before", "After")
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

        val commentLabel = JLabel("  0 comments")
        buttonPanel.add(commentLabel)

        panel.add(buttonPanel, BorderLayout.WEST)
        return panel
    }

    private fun finishReview() {
        // Will be wired in Phase 5
    }
}
