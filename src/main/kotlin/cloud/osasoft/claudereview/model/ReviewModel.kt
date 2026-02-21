package cloud.osasoft.claudereview.model

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ReviewModel(private val project: Project) {
    val fileDiffs = mutableListOf<FileDiff>()
    private val comments = ConcurrentHashMap<String, MutableList<LineComment>>()

    fun addComment(comment: LineComment) {
        comments.getOrPut(comment.filePath) { mutableListOf() }.add(comment)
    }

    fun removeComment(comment: LineComment) {
        comments[comment.filePath]?.remove(comment)
    }

    fun getComments(filePath: String): List<LineComment> {
        return comments[filePath]?.toList() ?: emptyList()
    }

    fun getAllComments(): List<LineComment> {
        return comments.values.flatten().sortedWith(compareBy({ it.filePath }, { it.lineNumber }))
    }

    fun getCommentCount(): Int {
        return comments.values.sumOf { it.size }
    }

    fun getCommentedFileCount(): Int {
        return comments.count { it.value.isNotEmpty() }
    }

    fun clear() {
        fileDiffs.clear()
        comments.clear()
    }
}
