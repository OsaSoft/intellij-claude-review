package cloud.osasoft.claudereview.ui

import cloud.osasoft.claudereview.model.LineComment
import cloud.osasoft.claudereview.model.ReviewModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

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
            CommentPopup.show(editor, comment.lineNumber, filePath, comment, model) {
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
