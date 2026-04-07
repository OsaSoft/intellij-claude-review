package cloud.osasoft.claudereview.model

data class FileDiff(
    val filePath: String,
    val oldContent: String,
    val newContent: String,
    val status: FileStatus
)

enum class FileStatus { NEW, MODIFIED, DELETED, RENAMED }
