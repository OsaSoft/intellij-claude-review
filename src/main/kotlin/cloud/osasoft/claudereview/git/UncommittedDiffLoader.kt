package cloud.osasoft.claudereview.git

import cloud.osasoft.claudereview.diff.DiffParser
import cloud.osasoft.claudereview.model.FileDiff
import cloud.osasoft.claudereview.model.FileStatus
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

private val LOG = logger<UncommittedDiffLoader>()

class UncommittedDiffLoader : DiffLoader {

    override fun load(project: Project, repoRoot: VirtualFile, indicator: ProgressIndicator): List<FileDiff> {
        indicator.text = "Running git diff HEAD\u2026"

        val diffHandler = GitLineHandler(project, repoRoot, GitCommand.DIFF)
        diffHandler.addParameters("HEAD")
        val diffResult = Git.getInstance().runCommand(diffHandler)

        val statusHandler = GitLineHandler(project, repoRoot, GitCommand.STATUS)
        statusHandler.addParameters("--porcelain", "-u")
        val statusResult = Git.getInstance().runCommand(statusHandler)

        if (!diffResult.success()) {
            LOG.warn("git diff HEAD failed (may be a fresh repo with no commits): ${diffResult.errorOutputAsJoinedString}")
        }
        if (!statusResult.success()) {
            LOG.warn("git status --porcelain failed: ${statusResult.errorOutputAsJoinedString}")
        }

        val diffOutput = if (diffResult.success()) diffResult.outputAsJoinedString else ""
        val statusOutput = if (statusResult.success()) statusResult.outputAsJoinedString else ""
        lastLoadHadChanges = diffOutput.isNotBlank() || statusOutput.isNotBlank()

        LOG.info("Git diff HEAD collected: ${diffOutput.length} chars")
        LOG.info("Git status collected: ${statusOutput.length} chars")

        indicator.text = "Parsing diff output\u2026"

        val parsedFiles = DiffParser.parseChangedFiles(diffOutput)
        val untrackedPaths = DiffParser.parseUntrackedFiles(statusOutput)
        val stagedNewPaths = DiffParser.parseStagedNewFiles(statusOutput)
        val rootPath = repoRoot.path
        val fileDiffs = mutableListOf<FileDiff>()

        for (parsed in parsedFiles) {
            indicator.text = "Reading ${parsed.newPath}\u2026"

            val isBinary = isBinaryFile(rootPath, parsed.newPath)
            if (isBinary) {
                fileDiffs.add(FileDiff(parsed.newPath, "(binary file)", "(binary file)", parsed.status))
                continue
            }

            val oldContent = when (parsed.status) {
                FileStatus.NEW -> ""
                FileStatus.DELETED -> getOldContent(project, repoRoot, parsed.oldPath ?: parsed.newPath)
                FileStatus.RENAMED -> getOldContent(project, repoRoot, parsed.oldPath ?: parsed.newPath)
                FileStatus.MODIFIED -> getOldContent(project, repoRoot, parsed.oldPath ?: parsed.newPath)
            }

            val newContent = when (parsed.status) {
                FileStatus.DELETED -> ""
                else -> readWorkingDirFile(rootPath, parsed.newPath)
            }

            fileDiffs.add(FileDiff(parsed.newPath, oldContent, newContent, parsed.status))
        }

        val alreadyTracked = parsedFiles.map { it.newPath }.toSet()
        val additionalNewPaths = (untrackedPaths + stagedNewPaths)
            .distinct()
            .filter { it !in alreadyTracked }

        for (untrackedPath in additionalNewPaths) {
            indicator.text = "Reading $untrackedPath\u2026"

            val fileOnDisk = java.io.File(rootPath, untrackedPath)
            if (!fileOnDisk.isFile) continue

            if (isBinaryFile(rootPath, untrackedPath)) {
                fileDiffs.add(FileDiff(untrackedPath, "", "(binary file)", FileStatus.NEW))
                continue
            }

            val newContent = readWorkingDirFile(rootPath, untrackedPath)
            fileDiffs.add(FileDiff(untrackedPath, "", newContent, FileStatus.NEW))
        }

        LOG.info("Parsed ${fileDiffs.size} file diffs")
        return fileDiffs
    }

    /** Whether the last [load] call found any changes (diff or status output was non-blank). */
    var lastLoadHadChanges: Boolean = false
        private set

    companion object {
        fun getOldContent(project: Project, root: VirtualFile, filePath: String): String {
            return try {
                val handler = GitLineHandler(project, root, GitCommand.SHOW)
                handler.addParameters("HEAD:$filePath")
                val result = Git.getInstance().runCommand(handler)
                if (result.success()) result.outputAsJoinedString else ""
            } catch (e: Exception) {
                LOG.warn("Failed to get old content for $filePath: ${e.message}")
                ""
            }
        }

        fun readWorkingDirFile(rootPath: String, filePath: String): String {
            return try {
                java.io.File(rootPath, filePath).readText()
            } catch (e: Exception) {
                LOG.warn("Failed to read working directory file $filePath: ${e.message}")
                ""
            }
        }

        fun isBinaryFile(rootPath: String, filePath: String): Boolean {
            val file = java.io.File(rootPath, filePath)
            if (!file.exists() || !file.isFile) return false
            return try {
                val bytes = file.inputStream().use { it.readNBytes(8192) }
                bytes.any { it == 0.toByte() }
            } catch (e: Exception) {
                false
            }
        }
    }
}
