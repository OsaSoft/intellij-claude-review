package cloud.osasoft.claudereview.export

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
            sb.appendLine("[${comment.filePath}:${comment.lineNumber}] ${comment.text}")
        }

        return sb.toString().trimEnd()
    }
}
