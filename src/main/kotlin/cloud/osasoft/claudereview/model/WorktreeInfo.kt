package cloud.osasoft.claudereview.model

data class WorktreeInfo(
    val path: String,
    val head: String,
    val branch: String?,
    val isBare: Boolean = false
) {
    val displayName: String
        get() = branch?.substringAfterLast('/') ?: "(detached HEAD)"
}
