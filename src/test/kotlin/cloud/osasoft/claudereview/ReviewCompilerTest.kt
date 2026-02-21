package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.export.ReviewCompiler
import cloud.osasoft.claudereview.model.LineComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewCompilerTest {

    @Test
    fun `compile returns empty string for empty comments list`() {
        assertEquals("", ReviewCompiler.compile(emptyList()))
    }

    @Test
    fun `compile with single comment`() {
        val comments = listOf(
            LineComment("src/Main.kt", 10, "Consider using a constant here")
        )

        val result = ReviewCompiler.compile(comments)

        assertTrue(result.contains("# Claude Code Review Comments"))
        assertTrue(result.contains("# 1 comment(s) on 1 file(s)"))
        assertTrue(result.contains("[src/Main.kt:10] Consider using a constant here"))
    }

    @Test
    fun `compile with multiple comments on same file`() {
        val comments = listOf(
            LineComment("src/Main.kt", 20, "Second comment"),
            LineComment("src/Main.kt", 5, "First comment")
        )

        val result = ReviewCompiler.compile(comments)
        val lines = result.lines()

        assertTrue(result.contains("# 2 comment(s) on 1 file(s)"))

        // Find the comment lines and verify they are sorted by line number
        val commentLines = lines.filter { it.startsWith("[") }
        assertEquals(2, commentLines.size)
        assertEquals("[src/Main.kt:5] First comment", commentLines[0])
        assertEquals("[src/Main.kt:20] Second comment", commentLines[1])
    }

    @Test
    fun `compile with comments across multiple files sorts by file path then line number`() {
        val comments = listOf(
            LineComment("src/Z.kt", 1, "Z file comment"),
            LineComment("src/A.kt", 30, "A file second comment"),
            LineComment("src/A.kt", 10, "A file first comment"),
            LineComment("src/M.kt", 5, "M file comment")
        )

        val result = ReviewCompiler.compile(comments)
        val commentLines = result.lines().filter { it.startsWith("[") }

        assertEquals(4, commentLines.size)
        assertEquals("[src/A.kt:10] A file first comment", commentLines[0])
        assertEquals("[src/A.kt:30] A file second comment", commentLines[1])
        assertEquals("[src/M.kt:5] M file comment", commentLines[2])
        assertEquals("[src/Z.kt:1] Z file comment", commentLines[3])
    }

    @Test
    fun `compile header shows correct comment and file counts`() {
        val comments = listOf(
            LineComment("src/A.kt", 1, "Comment 1"),
            LineComment("src/A.kt", 2, "Comment 2"),
            LineComment("src/B.kt", 1, "Comment 3"),
            LineComment("src/C.kt", 5, "Comment 4"),
            LineComment("src/C.kt", 10, "Comment 5")
        )

        val result = ReviewCompiler.compile(comments)

        assertTrue(result.contains("# 5 comment(s) on 3 file(s)"))
    }

    @Test
    fun `compile output does not end with trailing newline`() {
        val comments = listOf(
            LineComment("src/Main.kt", 1, "A comment")
        )

        val result = ReviewCompiler.compile(comments)

        // trimEnd() in the implementation should remove trailing whitespace/newlines
        assertEquals(result, result.trimEnd())
    }
}
