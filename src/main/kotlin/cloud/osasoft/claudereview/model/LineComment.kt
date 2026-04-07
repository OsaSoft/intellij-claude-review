package cloud.osasoft.claudereview.model

enum class CommentSeverity { ISSUE, SUGGESTION, QUESTION, NITPICK }

data class LineComment(
    val filePath: String,
    val lineNumber: Int,
    val text: String,
    val severity: CommentSeverity = CommentSeverity.ISSUE
)
