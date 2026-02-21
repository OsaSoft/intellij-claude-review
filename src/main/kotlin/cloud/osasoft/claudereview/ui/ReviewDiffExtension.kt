package cloud.osasoft.claudereview.ui

import cloud.osasoft.claudereview.model.ReviewModel
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

class ReviewDiffExtension : DiffExtension() {
    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        if (viewer !is SimpleDiffViewer) return
        if (request !is SimpleDiffRequest) return

        val project = context.project ?: return
        val model = project.getService(ReviewModel::class.java)

        // Extract file path from the diff request title.
        // Title format is "path/to/file" or "path/to/file (status)"
        val title = request.title ?: return
        val filePath = title.replace(Regex("\\s*\\(.*\\)$"), "")

        // Get the right-side editor (new content / "After" side)
        val editor = viewer.editor2

        // Install gutter click listener on the right editor
        val gutter = editor.gutter as? EditorGutterComponentEx ?: return
        gutter.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 1) return

                // Convert the click point from gutter coordinates to editor coordinates
                val editorComponent = editor.contentComponent
                val pointInEditor = SwingUtilities.convertPoint(gutter, Point(0, e.y), editorComponent)
                val logicalPosition = editor.xyToLogicalPosition(pointInEditor)
                val lineNumber = logicalPosition.line + 1 // 1-based for user display

                // Check if there's already a comment on this line
                val existingComment = model.getComments(filePath).find { it.lineNumber == lineNumber }

                CommentPopup.show(editor, lineNumber, filePath, existingComment, model) {
                    refreshGutterIcons(editor, filePath, model)
                }
            }
        })

        // Restore any existing comments as gutter icons
        refreshGutterIcons(editor, filePath, model)
    }

    companion object {
        fun refreshGutterIcons(editor: Editor, filePath: String, model: ReviewModel) {
            val markupModel = editor.markupModel

            // Remove old comment highlighters
            markupModel.allHighlighters
                .filter { it.gutterIconRenderer is CommentGutterIconRenderer }
                .forEach { markupModel.removeHighlighter(it) }

            // Add highlighters for current comments
            for (comment in model.getComments(filePath)) {
                val lineIndex = comment.lineNumber - 1 // 0-based for editor
                if (lineIndex < 0 || lineIndex >= editor.document.lineCount) continue

                val startOffset = editor.document.getLineStartOffset(lineIndex)
                val endOffset = editor.document.getLineEndOffset(lineIndex)

                val highlighter = markupModel.addRangeHighlighter(
                    startOffset,
                    endOffset,
                    HighlighterLayer.LAST + 1,
                    null,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                highlighter.gutterIconRenderer = CommentGutterIconRenderer(comment, editor, filePath, model)
            }
        }
    }
}
