package cloud.osasoft.claudereview.model

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class WorktreeState {
    private var activeDiffSource: DiffSource = DiffSource.Uncommitted
    private val diffsBySource = ConcurrentHashMap<String, List<FileDiff>>()
    private val comments = ConcurrentHashMap<String, MutableList<LineComment>>()
    private val loadedSources = ConcurrentHashMap<String, DiffSource>()
    private val commentChangeListeners = CopyOnWriteArrayList<() -> Unit>()

    @Synchronized
    fun loadSource(source: DiffSource, diffs: List<FileDiff>) {
        activeDiffSource = source
        diffsBySource[source.id] = diffs
        loadedSources[source.id] = source
    }

    @Synchronized
    fun setActiveSource(source: DiffSource) {
        activeDiffSource = source
        loadedSources[source.id] = source
    }

    @Synchronized
    fun getActiveSource(): DiffSource = activeDiffSource

    fun hasSourceDiffs(source: DiffSource): Boolean {
        return diffsBySource.containsKey(source.id)
    }

    @Synchronized
    fun getFileDiffs(): List<FileDiff> {
        return diffsBySource[activeDiffSource.id] ?: emptyList()
    }

    @Synchronized
    private fun commentKey(filePath: String): String {
        return "${activeDiffSource.id}:$filePath"
    }

    fun addCommentChangeListener(listener: () -> Unit) {
        commentChangeListeners.add(listener)
    }

    fun removeCommentChangeListener(listener: () -> Unit) {
        commentChangeListeners.remove(listener)
    }

    private fun fireCommentChanged() {
        commentChangeListeners.forEach { it() }
    }

    fun addComment(comment: LineComment) {
        comments.getOrPut(commentKey(comment.filePath)) { mutableListOf() }.add(comment)
        fireCommentChanged()
    }

    fun removeComment(comment: LineComment) {
        comments[commentKey(comment.filePath)]?.remove(comment)
        fireCommentChanged()
    }

    fun getComments(filePath: String): List<LineComment> {
        return comments[commentKey(filePath)]?.toList() ?: emptyList()
    }

    fun getAllComments(): List<LineComment> {
        return comments.values.flatten().sortedWith(compareBy({ it.filePath }, { it.lineNumber }))
    }

    fun getAllSourcedComments(): Map<DiffSource, List<LineComment>> {
        return comments.entries
            .filter { it.value.isNotEmpty() }
            .groupBy(
                keySelector = { loadedSources[it.key.substringBefore(':')] },
                valueTransform = { it.value }
            )
            .filterKeys { it != null }
            .mapKeys { it.key!! }
            .mapValues { (_, lists) ->
                lists.flatten().sortedWith(compareBy({ it.filePath }, { it.lineNumber }))
            }
            .entries.sortedByDescending { it.key.sortKey }
            .associate { it.key to it.value }
    }

    @Synchronized
    fun trackSource(source: DiffSource) {
        loadedSources[source.id] = source
    }

    fun getCommentCount(): Int {
        return comments.values.sumOf { it.size }
    }

    fun getCommentedFileCount(): Int {
        return comments.values.flatten().map { it.filePath }.distinct().size
    }

    @Synchronized
    fun clear() {
        activeDiffSource = DiffSource.Uncommitted
        diffsBySource.clear()
        comments.clear()
        loadedSources.clear()
    }
}

@Service(Service.Level.PROJECT)
class ReviewModel(@Suppress("unused") private val project: Project?) {
    /** Test-only constructor */
    internal constructor() : this(null)

    private val states = ConcurrentHashMap<String, WorktreeState>()

    fun getOrCreateState(worktreePath: String): WorktreeState {
        return states.computeIfAbsent(worktreePath) { WorktreeState() }
    }

    fun clearWorktree(worktreePath: String) {
        states.remove(worktreePath)
    }

    @Synchronized
    fun clear() {
        states.clear()
    }
}
