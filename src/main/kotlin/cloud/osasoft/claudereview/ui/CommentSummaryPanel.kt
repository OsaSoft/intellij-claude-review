package cloud.osasoft.claudereview.ui

import cloud.osasoft.claudereview.model.LineComment
import cloud.osasoft.claudereview.model.WorktreeState
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Collapsible panel showing all comments across all files for the current worktree state.
 * Selecting a comment triggers [onCommentClicked] so the caller can navigate to it.
 */
class CommentSummaryPanel(
    private val state: WorktreeState,
    private val onCommentClicked: (LineComment) -> Unit
) : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<LineComment?>()
    private val commentList = JBList<LineComment?>(listModel)
    private var suppressSelectionEvent = false

    init {
        commentList.cellRenderer = CommentCellRenderer()
        commentList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        commentList.addListSelectionListener { e ->
            if (e.valueIsAdjusting || suppressSelectionEvent) return@addListSelectionListener
            val selected = commentList.selectedValue
            if (selected != null) {
                onCommentClicked(selected)
            }
        }

        add(JBScrollPane(commentList), BorderLayout.CENTER)
        refresh()
    }

    /** Reloads the list from state. Safe to call on the EDT at any time. */
    fun refresh() {
        val comments = state.getAllComments()

        suppressSelectionEvent = true
        try {
            listModel.clear()
            if (comments.isEmpty()) {
                listModel.addElement(null) // sentinel for empty-state row
            } else {
                comments.forEach { listModel.addElement(it) }
            }
        } finally {
            suppressSelectionEvent = false
        }
        // Clear selection so the list doesn't hold a stale highlight after refresh
        commentList.clearSelection()
    }

    /**
     * Clears the list selection without triggering the navigation callback.
     * Call this after navigation completes so the list doesn't remain highlighted
     * when the user clicks a different file manually.
     */
    fun clearSelection() {
        suppressSelectionEvent = true
        try {
            commentList.clearSelection()
        } finally {
            suppressSelectionEvent = false
        }
    }

    private class CommentCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val comment = value as? LineComment
            val display = if (comment == null) {
                "(no comments)"
            } else {
                val fileName = comment.filePath.substringAfterLast('/')
                val truncated = if (comment.text.length > 60) comment.text.take(57) + "\u2026" else comment.text
                "$fileName:${comment.lineNumber} \u2014 $truncated"
            }
            val component = super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus)
            if (comment == null) {
                foreground = list?.foreground?.let {
                    java.awt.Color(it.red, it.green, it.blue, 128)
                }
            }
            toolTipText = comment?.let { "${it.filePath}:${it.lineNumber}" }
            return component
        }
    }
}
