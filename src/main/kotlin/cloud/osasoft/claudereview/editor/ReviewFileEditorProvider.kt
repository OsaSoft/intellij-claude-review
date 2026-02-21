package cloud.osasoft.claudereview.editor

import cloud.osasoft.claudereview.vfs.ReviewVirtualFile
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ReviewFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is ReviewVirtualFile
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return ReviewFileEditor(project)
    }

    override fun getEditorTypeId(): String = "claude-review-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
