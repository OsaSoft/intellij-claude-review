package cloud.osasoft.claudereview.diff

import cloud.osasoft.claudereview.model.FileStatus

data class ParsedFileDiff(
    val oldPath: String?,
    val newPath: String,
    val status: FileStatus
)

object DiffParser {

    /**
     * Parse unified diff output to get a list of changed files.
     *
     * Recognizes these git diff header patterns:
     * - `diff --git a/old/path b/new/path` -- starts each file section
     * - `new file mode 100644` -- the file is newly created
     * - `deleted file mode 100644` -- the file was deleted
     * - `rename from old/path` + `rename to new/path` -- the file was renamed
     * - Otherwise the file is treated as MODIFIED
     *
     * The `a/` and `b/` prefixes are stripped from paths.
     */
    fun parseChangedFiles(diffOutput: String): List<ParsedFileDiff> {
        if (diffOutput.isBlank()) return emptyList()

        val result = mutableListOf<ParsedFileDiff>()
        val lines = diffOutput.lines()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            if (line.startsWith("diff --git ")) {
                val (oldPath, newPath) = parseDiffGitLine(line)
                var status = FileStatus.MODIFIED
                var renameOldPath: String? = null

                // Scan subsequent header lines until the next "diff --git" or end of input
                var j = i + 1
                while (j < lines.size && !lines[j].startsWith("diff --git ")) {
                    val headerLine = lines[j]
                    when {
                        headerLine.startsWith("new file mode") -> status = FileStatus.NEW
                        headerLine.startsWith("deleted file mode") -> status = FileStatus.DELETED
                        headerLine.startsWith("rename from ") -> {
                            renameOldPath = headerLine.removePrefix("rename from ")
                            status = FileStatus.RENAMED
                        }
                        headerLine.startsWith("rename to ") -> {
                            // status already set by "rename from", but handle standalone case
                            if (status != FileStatus.RENAMED) {
                                status = FileStatus.RENAMED
                            }
                        }
                    }
                    j++
                }

                val effectiveOldPath = when (status) {
                    FileStatus.NEW -> null
                    FileStatus.RENAMED -> renameOldPath ?: oldPath
                    else -> oldPath
                }

                result.add(ParsedFileDiff(effectiveOldPath, newPath, status))
                i = j
            } else {
                i++
            }
        }

        return result
    }

    /**
     * Parse git status --porcelain output to find untracked files.
     * Untracked files are lines starting with "?? ".
     */
    fun parseUntrackedFiles(statusOutput: String): List<String> {
        return statusOutput.lines()
            .filter { it.startsWith("?? ") }
            .map { it.removePrefix("?? ").trimEnd('/') }
    }

    /**
     * Extract old and new paths from a `diff --git a/X b/Y` line.
     * Strips the `a/` and `b/` prefixes.
     */
    private fun parseDiffGitLine(line: String): Pair<String, String> {
        // Format: "diff --git a/old/path b/new/path"
        // We strip "diff --git " then split on " b/" to handle paths with spaces in them.
        val afterPrefix = line.removePrefix("diff --git ")

        // The line has the form "a/old/path b/new/path".
        // We need to find the split point. The "b/" marker always has a space before it.
        // Strategy: find " b/" -- but the old path could contain " b/" as well.
        // Git guarantees the format so we split on the last " b/" occurrence,
        // but actually git uses the first space that yields valid a/ and b/ prefixes.
        // Simplest robust approach: the a-side starts with "a/" and the b-side starts with "b/".
        // We look for " b/" starting after position 2 (to skip "a/b/..." edge case at pos 0).
        val splitIndex = afterPrefix.indexOf(" b/")
        if (splitIndex == -1) {
            // Fallback: shouldn't happen with valid git diff output
            val path = afterPrefix.removePrefix("a/")
            return Pair(path, path)
        }

        val oldPath = afterPrefix.substring(0, splitIndex).removePrefix("a/")
        val newPath = afterPrefix.substring(splitIndex + 1).removePrefix("b/")
        return Pair(oldPath, newPath)
    }
}
