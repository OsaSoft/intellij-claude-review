package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.export.ReviewCompiler
import cloud.osasoft.claudereview.model.LineComment
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

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
})
