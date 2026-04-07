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

private val LOG = logger<CommitDiffLoader>()

class CommitDiffLoader(private val sha: String) : DiffLoader {

    override fun load(project: Project, repoRoot: VirtualFile, indicator: ProgressIndicator): List<FileDiff> {
        indicator.text = "Running git diff for $sha\u2026"

        val isInitialCommit = !hasParent(project, repoRoot)

        val diffHandler = GitLineHandler(project, repoRoot, GitCommand.DIFF)
        if (isInitialCommit) {
            diffHandler.addParameters("--root", sha)
        } else {
            diffHandler.addParameters("$sha~1..$sha")
        }
        val diffResult = Git.getInstance().runCommand(diffHandler)

        if (!diffResult.success()) {
            LOG.warn("git diff for commit $sha failed: ${diffResult.errorOutputAsJoinedString}")
            return emptyList()
        }

        val diffOutput = diffResult.outputAsJoinedString
        LOG.info("Git diff for commit $sha collected: ${diffOutput.length} chars")

        indicator.text = "Parsing diff output\u2026"
        val parsedFiles = DiffParser.parseChangedFiles(diffOutput)
        val fileDiffs = mutableListOf<FileDiff>()

        for (parsed in parsedFiles) {
            indicator.text = "Reading ${parsed.newPath}\u2026"

            val oldContent = when (parsed.status) {
                FileStatus.NEW -> ""
                else -> getCommitFileContent(project, repoRoot, if (isInitialCommit) null else "$sha~1", parsed.oldPath ?: parsed.newPath)
            }

            val newContent = when (parsed.status) {
                FileStatus.DELETED -> ""
                else -> getCommitFileContent(project, repoRoot, sha, parsed.newPath)
            }

            // Check binary: if content contains null bytes
            if (isBinaryContent(oldContent) || isBinaryContent(newContent)) {
                fileDiffs.add(FileDiff(parsed.newPath, "(binary file)", "(binary file)", parsed.status))
                continue
            }

            fileDiffs.add(FileDiff(parsed.newPath, oldContent, newContent, parsed.status))
        }

        LOG.info("Parsed ${fileDiffs.size} file diffs for commit $sha")
        return fileDiffs
    }

    private fun hasParent(project: Project, repoRoot: VirtualFile): Boolean {
        return try {
            val handler = GitLineHandler(project, repoRoot, GitCommand.REV_PARSE)
            handler.addParameters("--verify", "$sha~1")
            val result = Git.getInstance().runCommand(handler)
            result.success()
        } catch (e: Exception) {
            false
        }
    }

    private fun getCommitFileContent(project: Project, repoRoot: VirtualFile, ref: String?, filePath: String): String {
        if (ref == null) return ""
        return try {
            val handler = GitLineHandler(project, repoRoot, GitCommand.SHOW)
            handler.addParameters("$ref:$filePath")
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) result.outputAsJoinedString else ""
        } catch (e: Exception) {
            LOG.warn("Failed to get content for $ref:$filePath: ${e.message}")
            ""
        }
    }

    private fun isBinaryContent(content: String): Boolean {
        // Check first 8KB for null bytes
        val checkLength = minOf(content.length, 8192)
        for (i in 0 until checkLength) {
            if (content[i] == '\u0000') return true
        }
        return false
    }
}
