package cloud.osasoft.claudereview.git

import cloud.osasoft.claudereview.model.FileDiff
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface DiffLoader {
    fun load(project: Project, repoRoot: VirtualFile, indicator: ProgressIndicator): List<FileDiff>
}
