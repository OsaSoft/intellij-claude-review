package cloud.osasoft.claudereview.listener

import cloud.osasoft.claudereview.vfs.ReviewVirtualFile
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager

class DynamicUnloadListener : DynamicPluginListener {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId.idString != "cloud.osasoft.claudereview") return

        ApplicationManager.getApplication().invokeLater {
            for (project in ProjectManager.getInstance().openProjects) {
                val editorManager = FileEditorManager.getInstance(project)
                for (file in editorManager.openFiles) {
                    if (file is ReviewVirtualFile) {
                        editorManager.closeFile(file)
                    }
                }
            }
        }
    }
}
