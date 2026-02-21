package cloud.osasoft.claudereview.model

import com.intellij.openapi.vfs.VirtualFile

data class FileDiff(
    val filePath: String,
    val oldContent: String,
    val newContent: String,
    val status: FileStatus,
    val virtualFile: VirtualFile? = null
)

enum class FileStatus { NEW, MODIFIED, DELETED, RENAMED }
