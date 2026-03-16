package cloud.osasoft.claudereview.git

import cloud.osasoft.claudereview.model.DiffSource

object CommitLogParser {

    /**
     * Parse output of `git log --format="%H%n%h%n%s%n%ar" -N HEAD`.
     * Each commit is 4 lines: full SHA, short SHA, subject, relative date.
     * Commits are separated by blank groups of 4 lines.
     */
    fun parse(logOutput: String): List<DiffSource.Commit> {
        if (logOutput.isBlank()) return emptyList()

        val lines = logOutput.lines().filter { it.isNotEmpty() }
        if (lines.size < 4) return emptyList()

        val commits = mutableListOf<DiffSource.Commit>()
        var i = 0
        while (i + 3 < lines.size) {
            commits.add(
                DiffSource.Commit(
                    sha = lines[i],
                    shortSha = lines[i + 1],
                    message = lines[i + 2],
                    relativeDate = lines[i + 3]
                )
            )
            i += 4
        }
        return commits
    }
}
