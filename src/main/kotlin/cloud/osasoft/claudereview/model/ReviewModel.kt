package cloud.osasoft.claudereview.model

import cloud.osasoft.claudereview.persistence.ReviewPersistence
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Callback invoked by WorktreeState after any comment mutation.
 * Receives the full comment snapshot keyed by "${sourceId}:${filePath}".
 * Used by ReviewModel to persist comments without WorktreeState depending on persistence APIs.
 */
typealias CommentPersistCallback = (commentsByKey: Map<String, List<LineComment>>) -> Unit

class WorktreeState(
    private val onCommentsChanged: CommentPersistCallback = {}
) {
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
        onCommentsChanged(comments.mapValues { it.value.toList() })
    }

    fun addComment(comment: LineComment) {
        comments.getOrPut(commentKey(comment.filePath)) { mutableListOf() }.add(comment)
        fireCommentChanged()
    }

    fun removeComment(comment: LineComment) {
        comments[commentKey(comment.filePath)]?.remove(comment)
        fireCommentChanged()
    }

    /**
     * Restores previously persisted comments into this state.
     * Called once by ReviewModel immediately after state creation.
     */
    internal fun restoreComments(commentsByKey: Map<String, List<LineComment>>) {
        for ((key, list) in commentsByKey) {
            if (list.isNotEmpty()) {
                comments[key] = list.toMutableList()
                val sourceId = key.substringBefore(':')
                if (!loadedSources.containsKey(sourceId)) {
                    val source = if (sourceId == DiffSource.Uncommitted.id) {
                        DiffSource.Uncommitted
                    } else {
                        DiffSource.Commit(sourceId, sourceId.take(8), "(restored)", "", 0L)
                    }
                    loadedSources[sourceId] = source
                }
            }
        }
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
class ReviewModel(private val project: Project?) {
    /** Test-only constructor — no persistence wired */
    internal constructor() : this(null)

    private val states = ConcurrentHashMap<String, WorktreeState>()

    fun getOrCreateState(worktreePath: String): WorktreeState {
        return states.computeIfAbsent(worktreePath) { path ->
            val persistence = project?.service<ReviewPersistence>()
            val callback: CommentPersistCallback = if (persistence != null) {
                { commentsByKey -> persistence.saveComments(path, commentsByKey) }
            } else {
                {}
            }
            val state = WorktreeState(callback)
            if (persistence != null) {
                state.restoreComments(persistence.getComments(path))
            }
            state
        }
    }

    fun clearWorktree(worktreePath: String) {
        states.remove(worktreePath)
        project?.service<ReviewPersistence>()?.clearWorktree(worktreePath)
    }

    @Synchronized
    fun clear() {
        states.clear()
    }
}
