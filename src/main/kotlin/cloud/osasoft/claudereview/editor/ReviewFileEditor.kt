package cloud.osasoft.claudereview.editor

import cloud.osasoft.claudereview.ui.ReviewPanel
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class ReviewFileEditor(project: Project) : UserDataHolderBase(), FileEditor {
    val reviewPanel = ReviewPanel(project)

    init {
        Disposer.register(this, reviewPanel)
    }

    override fun getComponent(): JComponent = reviewPanel

    override fun getPreferredFocusedComponent(): JComponent = reviewPanel

    override fun getName(): String = "Claude Review"

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {}
}
