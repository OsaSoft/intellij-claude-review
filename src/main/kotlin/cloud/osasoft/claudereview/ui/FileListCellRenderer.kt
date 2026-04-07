package cloud.osasoft.claudereview.ui

import cloud.osasoft.claudereview.model.FileDiff
import cloud.osasoft.claudereview.model.FileStatus
import cloud.osasoft.claudereview.model.WorktreeState
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

class FileListCellRenderer(private val state: WorktreeState) : ColoredListCellRenderer<FileDiff>() {
    override fun customizeCellRenderer(
        list: JList<out FileDiff>,
        value: FileDiff?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        if (value == null) return

        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(value.filePath)
        icon = fileType.icon

        // Show just the filename, with full path as tooltip
        val fileName = value.filePath.substringAfterLast('/')
        append(fileName, SimpleTextAttributes.REGULAR_ATTRIBUTES)

        // Status indicator
        val statusText = when (value.status) {
            FileStatus.NEW -> " [new]"
            FileStatus.DELETED -> " [deleted]"
            FileStatus.RENAMED -> " [renamed]"
            FileStatus.MODIFIED -> ""
        }
        if (statusText.isNotEmpty()) {
            append(statusText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }

        // Comment count badge
        val commentCount = state.getComments(value.filePath).size
        if (commentCount > 0) {
            append(" ($commentCount)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }

        toolTipText = value.filePath
    }
}
