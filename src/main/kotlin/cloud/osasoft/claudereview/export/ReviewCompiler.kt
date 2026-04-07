package cloud.osasoft.claudereview.export

import cloud.osasoft.claudereview.model.CommentSeverity
import cloud.osasoft.claudereview.model.DiffSource
import cloud.osasoft.claudereview.model.LineComment

object ReviewCompiler {

    /**
     * Compile a list of line comments into a human-readable review summary.
     * Comments are sorted by file path then line number.
     * Returns an empty string when there are no comments.
     */
    fun compile(comments: List<LineComment>): String {
        if (comments.isEmpty()) return ""

        val sorted = comments.sortedWith(compareBy({ it.filePath }, { it.lineNumber }))
        val fileCount = sorted.map { it.filePath }.distinct().size

        val sb = StringBuilder()
        sb.appendLine("# Claude Code Review Comments")
        sb.appendLine("# ${sorted.size} comment(s) on $fileCount file(s)")
        sb.appendLine()

        for (comment in sorted) {
            sb.appendLine(formatComment(comment))
        }

        return sb.toString().trimEnd()
    }

    /**
     * Compile sourced comments grouped by diff source.
     * If only one source has comments, delegates to [compile] (no section headers).
     * Returns an empty string when there are no comments.
     */
    fun compileSourced(sourcedComments: Map<DiffSource, List<LineComment>>): String {
        val nonEmpty = sourcedComments.filter { it.value.isNotEmpty() }
        if (nonEmpty.isEmpty()) return ""

        // Single source: use flat format without section headers
        if (nonEmpty.size == 1) {
            return compile(nonEmpty.values.first())
        }

        val allComments = nonEmpty.values.flatten()
        val fileCount = allComments.map { it.filePath }.distinct().size

        // Order: Uncommitted first (highest sortKey), then commits newest-to-oldest
        val ordered = nonEmpty.entries.sortedByDescending { it.key.sortKey }

        return buildString {
            appendLine("# Claude Code Review Comments")
            appendLine("# ${allComments.size} comment(s) on $fileCount file(s)")

            for ((source, comments) in ordered) {
                appendLine()
                appendLine("## ${source.displayName}")
                val sorted = comments.sortedWith(compareBy({ it.filePath }, { it.lineNumber }))
                for (comment in sorted) {
                    appendLine(formatComment(comment))
                }
            }
        }.trimEnd()
    }

    private fun formatComment(comment: LineComment): String {
        val prefix = if (comment.severity != CommentSeverity.ISSUE) {
            "[${comment.severity.name.lowercase()}] "
        } else {
            ""
        }
        return "[${comment.filePath}:${comment.lineNumber}] $prefix${comment.text}"
    }
}
