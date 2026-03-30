package cloud.osasoft.claudereview.vfs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

class ReviewVirtualFile(
    val worktreePath: String,
    val branchName: String,
    val repoRoot: VirtualFile
) : LightVirtualFile("Review: $branchName", "") {
    init {
        isWritable = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReviewVirtualFile) return false
        return worktreePath == other.worktreePath
    }

    override fun hashCode(): Int = worktreePath.hashCode()
}
