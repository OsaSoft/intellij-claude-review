package cloud.osasoft.claudereview.git

import cloud.osasoft.claudereview.model.DiffSource

object CommitLogParser {

    /**
     * Parse output of `git log --format="%H%n%h%n%s%n%ar%n%at" -N HEAD`.
     * Each commit is 5 lines: full SHA, short SHA, subject, relative date, unix timestamp.
     * Empty subject lines are preserved (no blank-line filtering).
     */
    fun parse(logOutput: String): List<DiffSource.Commit> {
        if (logOutput.isBlank()) return emptyList()

        val lines = logOutput.lines()
        // Drop trailing empty line produced by a final newline
        val trimmed = if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines
        if (trimmed.size < 5) return emptyList()

        val commits = mutableListOf<DiffSource.Commit>()
        var i = 0
        while (i + 4 < trimmed.size) {
            commits.add(
                DiffSource.Commit(
                    sha = trimmed[i],
                    shortSha = trimmed[i + 1],
                    message = trimmed[i + 2],
                    relativeDate = trimmed[i + 3],
                    timestamp = trimmed[i + 4].toLongOrDefault(0)
                )
            )
            i += 5
        }
        return commits
    }

    private fun String.toLongOrDefault(default: Long): Long =
        toLongOrNull() ?: default
}
