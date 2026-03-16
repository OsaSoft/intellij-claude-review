package cloud.osasoft.claudereview.model

sealed interface DiffSource {
    val id: String
    val displayName: String
    val sortKey: Long

    data object Uncommitted : DiffSource {
        override val id = "uncommitted"
        override val displayName = "Uncommitted changes"
        override val sortKey = Long.MAX_VALUE
    }

    data class Commit(val sha: String, val shortSha: String, val message: String, val relativeDate: String, val timestamp: Long) : DiffSource {
        override val id = sha
        override val displayName = "$shortSha  $message"
        override val sortKey = timestamp
    }
}
