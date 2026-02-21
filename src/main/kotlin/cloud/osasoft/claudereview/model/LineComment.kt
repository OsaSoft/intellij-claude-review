package cloud.osasoft.claudereview.model

data class LineComment(
    val filePath: String,
    val lineNumber: Int,
    val text: String
)
