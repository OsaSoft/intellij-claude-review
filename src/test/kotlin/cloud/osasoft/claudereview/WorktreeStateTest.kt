package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.model.DiffSource
import cloud.osasoft.claudereview.model.FileDiff
import cloud.osasoft.claudereview.model.FileStatus
import cloud.osasoft.claudereview.model.LineComment
import cloud.osasoft.claudereview.model.WorktreeState
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize as mapShouldHaveSize
import io.kotest.matchers.shouldBe

class WorktreeStateTest : FreeSpec({

    val testDiff = FileDiff("src/Main.kt", "old", "new", FileStatus.MODIFIED)
    val testDiff2 = FileDiff("src/Other.kt", "old2", "new2", FileStatus.MODIFIED)
    val commit = DiffSource.Commit("abc123", "abc", "test commit", "1 hour ago", 1000L)

    "loadSource stores diffs and sets active source" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff))

        state.getActiveSource() shouldBe DiffSource.Uncommitted
        state.getFileDiffs() shouldHaveSize 1
        state.getFileDiffs()[0].filePath shouldBe "src/Main.kt"
    }

    "loadSource preserves comments when reloading same source" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff))
        state.addComment(LineComment("src/Main.kt", 10, "fix this"))

        // Reload the same source with different diffs
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff, testDiff2))

        state.getFileDiffs() shouldHaveSize 2
        state.getComments("src/Main.kt") shouldHaveSize 1
        state.getComments("src/Main.kt")[0].text shouldBe "fix this"
    }

    "comments are scoped to active source" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff))
        state.addComment(LineComment("src/Main.kt", 10, "uncommitted comment"))

        state.loadSource(commit, listOf(testDiff))
        state.getComments("src/Main.kt").shouldBeEmpty()

        state.addComment(LineComment("src/Main.kt", 5, "commit comment"))
        state.getComments("src/Main.kt") shouldHaveSize 1
        state.getComments("src/Main.kt")[0].text shouldBe "commit comment"

        // Switch back — uncommitted comment is still there
        state.setActiveSource(DiffSource.Uncommitted)
        state.getComments("src/Main.kt") shouldHaveSize 1
        state.getComments("src/Main.kt")[0].text shouldBe "uncommitted comment"
    }

    "addComment and removeComment fire change listeners" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff))
        var fireCount = 0
        state.addCommentChangeListener { fireCount++ }

        val comment = LineComment("src/Main.kt", 1, "test")
        state.addComment(comment)
        fireCount shouldBe 1

        state.removeComment(comment)
        fireCount shouldBe 2
    }

    "getCommentCount returns total across all sources" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff))
        state.addComment(LineComment("src/Main.kt", 1, "one"))
        state.addComment(LineComment("src/Main.kt", 2, "two"))

        state.loadSource(commit, listOf(testDiff))
        state.addComment(LineComment("src/Main.kt", 5, "three"))

        state.getCommentCount() shouldBe 3
    }

    "getCommentedFileCount returns distinct files with comments" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff, testDiff2))
        state.addComment(LineComment("src/Main.kt", 1, "one"))
        state.addComment(LineComment("src/Main.kt", 2, "two"))
        state.addComment(LineComment("src/Other.kt", 1, "three"))

        state.getCommentedFileCount() shouldBe 2
    }

    "getAllComments returns sorted flat list across active and other sources" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff, testDiff2))
        state.addComment(LineComment("src/Other.kt", 5, "second file"))
        state.addComment(LineComment("src/Main.kt", 10, "first file"))

        val all = state.getAllComments()
        all shouldHaveSize 2
        all[0].filePath shouldBe "src/Main.kt"
        all[1].filePath shouldBe "src/Other.kt"
    }

    "getAllSourcedComments groups by source with correct ordering" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff))
        state.addComment(LineComment("src/Main.kt", 1, "uncommitted"))

        state.loadSource(commit, listOf(testDiff))
        state.addComment(LineComment("src/Main.kt", 5, "from commit"))

        val sourced = state.getAllSourcedComments()
        sourced.mapShouldHaveSize(2)

        // Uncommitted has highest sortKey (Long.MAX_VALUE), so it comes first
        val keys = sourced.keys.toList()
        keys[0] shouldBe DiffSource.Uncommitted
        keys[1] shouldBe commit

        sourced[DiffSource.Uncommitted]!! shouldHaveSize 1
        sourced[commit]!! shouldHaveSize 1
    }

    "getAllSourcedComments excludes sources with empty comment lists" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff))
        // No comments added for uncommitted

        state.loadSource(commit, listOf(testDiff))
        state.addComment(LineComment("src/Main.kt", 5, "only here"))

        val sourced = state.getAllSourcedComments()
        sourced.mapShouldHaveSize(1)
        sourced.keys.first() shouldBe commit
    }

    "comment key uses source id prefix - colon in file path does not corrupt grouping" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff))
        // File path with colon should not break substringBefore(':') parsing in getAllSourcedComments
        state.addComment(LineComment("C:/Users/file.kt", 1, "windows path"))

        val sourced = state.getAllSourcedComments()
        sourced.mapShouldHaveSize(1)
        sourced[DiffSource.Uncommitted]!! shouldHaveSize 1
    }

    "clear removes all state" {
        val state = WorktreeState()
        state.loadSource(DiffSource.Uncommitted, listOf(testDiff))
        state.addComment(LineComment("src/Main.kt", 1, "test"))

        state.clear()

        state.getActiveSource() shouldBe DiffSource.Uncommitted
        state.getFileDiffs().shouldBeEmpty()
        state.getCommentCount() shouldBe 0
    }

    "hasSourceDiffs returns correct values" {
        val state = WorktreeState()
        state.hasSourceDiffs(DiffSource.Uncommitted) shouldBe false

        state.loadSource(DiffSource.Uncommitted, listOf(testDiff))
        state.hasSourceDiffs(DiffSource.Uncommitted) shouldBe true
        state.hasSourceDiffs(commit) shouldBe false
    }
})
