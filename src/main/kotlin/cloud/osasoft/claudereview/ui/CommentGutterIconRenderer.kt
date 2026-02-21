package cloud.osasoft.claudereview.ui

import cloud.osasoft.claudereview.model.LineComment
import cloud.osasoft.claudereview.model.ReviewModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import java.awt.Point
import javax.swing.Icon
import javax.swing.SwingUtilities

class CommentGutterIconRenderer(
    private val comment: LineComment,
    private val editor: Editor,
    private val filePath: String,
    private val model: ReviewModel
) : GutterIconRenderer() {

    override fun getIcon(): Icon {
        return try {
            IconLoader.getIcon("/icons/comment.svg", CommentGutterIconRenderer::class.java)
        } catch (_: Exception) {
            com.intellij.icons.AllIcons.General.Balloon
        }
    }

    override fun getTooltipText(): String = comment.text

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            val screenPoint = if (editor.contentComponent.isShowing) {
                val logicalPos = LogicalPosition(comment.lineNumber - 1, 0)
                val editorPoint = editor.logicalPositionToXY(logicalPos)
                val point = Point(editorPoint)
                SwingUtilities.convertPointToScreen(point, editor.contentComponent)
                point
            } else null
            CommentPopup.show(editor, comment.lineNumber, filePath, comment, model, screenPoint) {
                ReviewDiffExtension.refreshGutterIcons(editor, filePath, model)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommentGutterIconRenderer) return false
        return comment == other.comment && filePath == other.filePath
    }

    override fun hashCode(): Int {
        var result = comment.hashCode()
        result = 31 * result + filePath.hashCode()
        return result
    }

    override fun getAlignment(): Alignment = Alignment.RIGHT
}
