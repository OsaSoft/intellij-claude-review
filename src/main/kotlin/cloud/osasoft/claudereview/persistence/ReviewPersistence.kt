package cloud.osasoft.claudereview.persistence

import cloud.osasoft.claudereview.model.CommentSeverity
import cloud.osasoft.claudereview.model.LineComment
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

private const val MAX_PERSISTED_COMMENTS = 500

/**
 * Project-level service that persists review comments to .idea/claudeReviewComments.xml.
 *
 * Only comments are persisted — diff content (old/new file text) is never stored,
 * keeping the .idea/ file small.
 *
 * The comment map keys use the format "${sourceId}:${filePath}" matching
 * WorktreeState's internal commentKey format, grouped under worktree paths.
 *
 * Schema version 1: initial structure.
 */
@State(
    name = "ClaudeReviewComments",
    storages = [Storage("claudeReviewComments.xml")]
)
@Service(Service.Level.PROJECT)
class ReviewPersistence : PersistentStateComponent<ReviewPersistence.State> {

    // XML-serializable data classes — all fields must be mutable with defaults
    // so IntelliJ's XmlSerializer can instantiate them via no-arg constructor.

    data class PersistedComment(
        var filePath: String = "",
        var lineNumber: Int = 0,
        var text: String = "",
        var severity: String = "ISSUE"
    )

    data class WorktreeComments(
        var comments: MutableMap<String, MutableList<PersistedComment>> = mutableMapOf()
    )

    data class State(
        var schemaVersion: Int = 1,
        var worktrees: MutableMap<String, WorktreeComments> = mutableMapOf()
    )

    private var _state = State()

    override fun getState(): State = _state

    override fun loadState(state: State) {
        _state = state
    }

    // ---------------------------------------------------------------------------
    // Read
    // ---------------------------------------------------------------------------

    /**
     * Returns all persisted comments for a worktree, keyed by "${sourceId}:${filePath}".
     * The returned map is a snapshot — callers may mutate their own state freely.
     */
    fun getComments(worktreePath: String): Map<String, List<LineComment>> {
        val worktreeComments = _state.worktrees[worktreePath] ?: return emptyMap()
        return worktreeComments.comments.mapValues { (_, list) ->
            list.map { it.toLineComment() }
        }
    }

    // ---------------------------------------------------------------------------
    // Write
    // ---------------------------------------------------------------------------

    /**
     * Replaces the entire comment map for a worktree with the given snapshot.
     * The key format is "${sourceId}:${filePath}" — same as WorktreeState uses internally.
     *
     * If the total number of comments across all worktrees would exceed [MAX_PERSISTED_COMMENTS],
     * entries are trimmed from the start of the map (lowest natural order keys first) until
     * the cap is satisfied.
     */
    fun saveComments(worktreePath: String, commentsByKey: Map<String, List<LineComment>>) {
        val persisted = commentsByKey
            .filter { it.value.isNotEmpty() }
            .mapValues { (_, list) -> list.map { it.toPersistedComment() }.toMutableList() }
            .toMutableMap()

        _state.worktrees[worktreePath] = WorktreeComments(persisted)
        enforceCap()
    }

    /**
     * Removes all persisted data for a worktree.
     */
    fun clearWorktree(worktreePath: String) {
        _state.worktrees.remove(worktreePath)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun enforceCap() {
        val totalComments = _state.worktrees.values.sumOf { wt ->
            wt.comments.values.sumOf { it.size }
        }
        if (totalComments <= MAX_PERSISTED_COMMENTS) return

        // Collect all entries as (worktreePath, commentKey, PersistedComment) triples
        // sorted by worktreePath then commentKey so trimming is deterministic.
        val allEntries = mutableListOf<Triple<String, String, PersistedComment>>()
        for ((wtPath, wtComments) in _state.worktrees.entries.sortedBy { it.key }) {
            for ((key, list) in wtComments.comments.entries.sortedBy { it.key }) {
                for (comment in list) {
                    allEntries.add(Triple(wtPath, key, comment))
                }
            }
        }

        val excess = allEntries.size - MAX_PERSISTED_COMMENTS
        val toRemove = allEntries.take(excess)

        for ((wtPath, key, comment) in toRemove) {
            _state.worktrees[wtPath]?.comments?.get(key)?.remove(comment)
        }

        // Prune empty lists and empty worktree entries
        for (wtComments in _state.worktrees.values) {
            wtComments.comments.entries.removeIf { it.value.isEmpty() }
        }
        _state.worktrees.entries.removeIf { it.value.comments.isEmpty() }
    }

    // ---------------------------------------------------------------------------
    // Conversion extensions (private to this file)
    // ---------------------------------------------------------------------------

    private fun PersistedComment.toLineComment(): LineComment {
        val severity = runCatching { CommentSeverity.valueOf(severity) }
            .getOrDefault(CommentSeverity.ISSUE)
        return LineComment(filePath, lineNumber, text, severity)
    }

    private fun LineComment.toPersistedComment(): PersistedComment =
        PersistedComment(filePath, lineNumber, text, severity.name)
}
