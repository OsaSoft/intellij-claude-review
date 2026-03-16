package cloud.osasoft.claudereview.model

sealed interface DiffSource {
    val id: String
    val displayName: String

    data object Uncommitted : DiffSource {
        override val id = "uncommitted"
        override val displayName = "Uncommitted changes"
    }

    data class Commit(val sha: String, val shortSha: String, val message: String, val relativeDate: String) : DiffSource {
        override val id = sha
        override val displayName = "$shortSha  $message"
    }
}
