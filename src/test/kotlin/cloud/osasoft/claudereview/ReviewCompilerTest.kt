package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.export.ReviewCompiler
import cloud.osasoft.claudereview.model.DiffSource
import cloud.osasoft.claudereview.model.LineComment
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ReviewCompilerTest : FreeSpec({

    "compile returns empty string for empty comments list" {
        // GIVEN an empty comments list
        // WHEN compiling
        val result = ReviewCompiler.compile(emptyList())

        // THEN it returns an empty string
        result shouldBe ""
    }

    "compile with single comment includes header and comment line" {
        // GIVEN a single comment
        val comments = listOf(
            LineComment("src/Main.kt", 10, "Consider using a constant here")
        )

        // WHEN compiling
        val result = ReviewCompiler.compile(comments)

        // THEN it contains the header and the formatted comment
        result shouldContain "# Claude Code Review Comments"
        result shouldContain "# 1 comment(s) on 1 file(s)"
        result shouldContain "[src/Main.kt:10] Consider using a constant here"
    }

    "compile with multiple comments on same file sorts by line number" {
        // GIVEN two comments on the same file in reverse order
        val comments = listOf(
            LineComment("src/Main.kt", 20, "Second comment"),
            LineComment("src/Main.kt", 5, "First comment")
        )

        // WHEN compiling
        val result = ReviewCompiler.compile(comments)
        val commentLines = result.lines().filter { it.startsWith("[") }

        // THEN comments are sorted by line number
        result shouldContain "# 2 comment(s) on 1 file(s)"
        commentLines shouldHaveSize 2
        commentLines[0] shouldBe "[src/Main.kt:5] First comment"
        commentLines[1] shouldBe "[src/Main.kt:20] Second comment"
    }

    "compile with comments across multiple files sorts by file path then line number" {
        // GIVEN comments across multiple files in random order
        val comments = listOf(
            LineComment("src/Z.kt", 1, "Z file comment"),
            LineComment("src/A.kt", 30, "A file second comment"),
            LineComment("src/A.kt", 10, "A file first comment"),
            LineComment("src/M.kt", 5, "M file comment")
        )

        // WHEN compiling
        val result = ReviewCompiler.compile(comments)
        val commentLines = result.lines().filter { it.startsWith("[") }

        // THEN comments are sorted by file path, then by line number
        commentLines shouldHaveSize 4
        commentLines[0] shouldBe "[src/A.kt:10] A file first comment"
        commentLines[1] shouldBe "[src/A.kt:30] A file second comment"
        commentLines[2] shouldBe "[src/M.kt:5] M file comment"
        commentLines[3] shouldBe "[src/Z.kt:1] Z file comment"
    }

    "compile header shows correct comment and file counts" {
        // GIVEN five comments across three files
        val comments = listOf(
            LineComment("src/A.kt", 1, "Comment 1"),
            LineComment("src/A.kt", 2, "Comment 2"),
            LineComment("src/B.kt", 1, "Comment 3"),
            LineComment("src/C.kt", 5, "Comment 4"),
            LineComment("src/C.kt", 10, "Comment 5")
        )

        // WHEN compiling
        val result = ReviewCompiler.compile(comments)

        // THEN the header reflects correct counts
        result shouldContain "# 5 comment(s) on 3 file(s)"
    }

    "compile output does not end with trailing newline" {
        // GIVEN a single comment
        val comments = listOf(
            LineComment("src/Main.kt", 1, "A comment")
        )

        // WHEN compiling
        val result = ReviewCompiler.compile(comments)

        // THEN there is no trailing whitespace
        result shouldBe result.trimEnd()
    }

    // --- compileSourced tests ---

    "compileSourced returns empty string for empty map" {
        val result = ReviewCompiler.compileSourced(emptyMap())
        result shouldBe ""
    }

    "compileSourced returns empty string when all sources have empty comments" {
        val result = ReviewCompiler.compileSourced(mapOf(
            DiffSource.Uncommitted to emptyList(),
            DiffSource.Commit("abc", "abc1", "Fix", "1d ago", 1710000000) to emptyList()
        ))
        result shouldBe ""
    }

    "compileSourced with single source uses flat format without section headers" {
        val comments = listOf(
            LineComment("src/Main.kt", 10, "Fix this"),
            LineComment("src/Main.kt", 20, "And this")
        )
        val result = ReviewCompiler.compileSourced(mapOf(
            DiffSource.Uncommitted to comments
        ))

        // Should use flat compile() format
        result shouldContain "# Claude Code Review Comments"
        result shouldContain "# 2 comment(s) on 1 file(s)"
        result shouldContain "[src/Main.kt:10] Fix this"
        result shouldNotContain "## "
    }

    "compileSourced with multiple sources includes section headers" {
        val uncommitted = DiffSource.Uncommitted
        val commit = DiffSource.Commit("abc123", "abc1234", "Fix the login flow", "2 days ago", 1710000000)

        val result = ReviewCompiler.compileSourced(mapOf(
            uncommitted to listOf(
                LineComment("src/Main.kt", 42, "Rename this variable")
            ),
            commit to listOf(
                LineComment("src/Auth.kt", 15, "Add exponential backoff"),
                LineComment("src/Auth.kt", 87, "Missing null check")
            )
        ))

        result shouldContain "# Claude Code Review Comments"
        result shouldContain "# 3 comment(s) on 2 file(s)"
        result shouldContain "## Uncommitted changes"
        result shouldContain "[src/Main.kt:42] Rename this variable"
        result shouldContain "## abc1234  Fix the login flow"
        result shouldContain "[src/Auth.kt:15] Add exponential backoff"
        result shouldContain "[src/Auth.kt:87] Missing null check"
    }

    "compileSourced skips sources with no comments" {
        val uncommitted = DiffSource.Uncommitted
        val commit = DiffSource.Commit("abc123", "abc1234", "Fix login", "2d ago", 1710000000)

        val result = ReviewCompiler.compileSourced(mapOf(
            uncommitted to emptyList(),
            commit to listOf(
                LineComment("src/Auth.kt", 15, "Add backoff")
            )
        ))

        // Single non-empty source → flat format
        result shouldContain "[src/Auth.kt:15] Add backoff"
        result shouldNotContain "## "
    }

    "compileSourced output does not end with trailing newline" {
        val result = ReviewCompiler.compileSourced(mapOf(
            DiffSource.Uncommitted to listOf(LineComment("A.kt", 1, "Comment")),
            DiffSource.Commit("abc", "abc1", "Msg", "1d", 1710000000) to listOf(LineComment("B.kt", 2, "Comment 2"))
        ))

        result shouldBe result.trimEnd()
    }

    "compileSourced puts Uncommitted section first" {
        val uncommitted = DiffSource.Uncommitted
        val commit = DiffSource.Commit("abc123", "abc1234", "Fix", "2d ago", 1710000000)

        val result = ReviewCompiler.compileSourced(mapOf(
            commit to listOf(LineComment("B.kt", 2, "Commit comment")),
            uncommitted to listOf(LineComment("A.kt", 1, "Uncommitted comment"))
        ))

        val uncommittedIdx = result.indexOf("## Uncommitted changes")
        val commitIdx = result.indexOf("## abc1234")
        (uncommittedIdx < commitIdx) shouldBe true
    }

    // --- single-file subset export tests ---

    "compile with subset of comments for one file produces correct output" {
        val subset = listOf(
            LineComment("src/Auth.kt", 15, "Add backoff"),
            LineComment("src/Auth.kt", 42, "Missing null check")
        )

        val result = ReviewCompiler.compile(subset)

        result shouldContain "# 2 comment(s) on 1 file(s)"
        result shouldContain "[src/Auth.kt:15] Add backoff"
        result shouldContain "[src/Auth.kt:42] Missing null check"
        result shouldNotContain "src/Main.kt"
    }

    "compile with empty list returns empty string for single-file export" {
        val result = ReviewCompiler.compile(emptyList())

        result shouldBe ""
    }
})
