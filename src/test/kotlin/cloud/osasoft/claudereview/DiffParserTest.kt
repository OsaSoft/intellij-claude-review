package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.diff.DiffParser
import cloud.osasoft.claudereview.model.FileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffParserTest {

    @Test
    fun `parseChangedFiles with simple modified file`() {
        val diff = """
            diff --git a/src/Main.kt b/src/Main.kt
            index abc1234..def5678 100644
            --- a/src/Main.kt
            +++ b/src/Main.kt
            @@ -1,3 +1,4 @@
             fun main() {
            +    println("hello")
             }
        """.trimIndent()

        val result = DiffParser.parseChangedFiles(diff)

        assertEquals(1, result.size)
        assertEquals("src/Main.kt", result[0].newPath)
        assertEquals("src/Main.kt", result[0].oldPath)
        assertEquals(FileStatus.MODIFIED, result[0].status)
    }

    @Test
    fun `parseChangedFiles with new file`() {
        val diff = """
            diff --git a/src/NewFile.kt b/src/NewFile.kt
            new file mode 100644
            index 0000000..abc1234
            --- /dev/null
            +++ b/src/NewFile.kt
            @@ -0,0 +1,3 @@
            +package example
            +
            +class NewFile
        """.trimIndent()

        val result = DiffParser.parseChangedFiles(diff)

        assertEquals(1, result.size)
        assertEquals("src/NewFile.kt", result[0].newPath)
        assertNull(result[0].oldPath)
        assertEquals(FileStatus.NEW, result[0].status)
    }

    @Test
    fun `parseChangedFiles with deleted file`() {
        val diff = """
            diff --git a/src/OldFile.kt b/src/OldFile.kt
            deleted file mode 100644
            index abc1234..0000000
            --- a/src/OldFile.kt
            +++ /dev/null
            @@ -1,3 +0,0 @@
            -package example
            -
            -class OldFile
        """.trimIndent()

        val result = DiffParser.parseChangedFiles(diff)

        assertEquals(1, result.size)
        assertEquals("src/OldFile.kt", result[0].newPath)
        assertEquals("src/OldFile.kt", result[0].oldPath)
        assertEquals(FileStatus.DELETED, result[0].status)
    }

    @Test
    fun `parseChangedFiles with renamed file`() {
        val diff = """
            diff --git a/src/OldName.kt b/src/NewName.kt
            similarity index 95%
            rename from src/OldName.kt
            rename to src/NewName.kt
            index abc1234..def5678 100644
            --- a/src/OldName.kt
            +++ b/src/NewName.kt
            @@ -1,3 +1,3 @@
             package example
            -class OldName
            +class NewName
        """.trimIndent()

        val result = DiffParser.parseChangedFiles(diff)

        assertEquals(1, result.size)
        assertEquals("src/NewName.kt", result[0].newPath)
        assertEquals("src/OldName.kt", result[0].oldPath)
        assertEquals(FileStatus.RENAMED, result[0].status)
    }

    @Test
    fun `parseChangedFiles with multiple files in one diff`() {
        val diff = """
            diff --git a/src/FileA.kt b/src/FileA.kt
            index abc1234..def5678 100644
            --- a/src/FileA.kt
            +++ b/src/FileA.kt
            @@ -1,3 +1,4 @@
             fun a() {
            +    println("a")
             }
            diff --git a/src/FileB.kt b/src/FileB.kt
            new file mode 100644
            index 0000000..abc1234
            --- /dev/null
            +++ b/src/FileB.kt
            @@ -0,0 +1,3 @@
            +package example
            +
            +class FileB
            diff --git a/src/FileC.kt b/src/FileC.kt
            deleted file mode 100644
            index abc1234..0000000
            --- a/src/FileC.kt
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -package example
            -class FileC
        """.trimIndent()

        val result = DiffParser.parseChangedFiles(diff)

        assertEquals(3, result.size)

        assertEquals("src/FileA.kt", result[0].newPath)
        assertEquals(FileStatus.MODIFIED, result[0].status)

        assertEquals("src/FileB.kt", result[1].newPath)
        assertEquals(FileStatus.NEW, result[1].status)
        assertNull(result[1].oldPath)

        assertEquals("src/FileC.kt", result[2].newPath)
        assertEquals(FileStatus.DELETED, result[2].status)
    }

    @Test
    fun `parseChangedFiles with empty diff input`() {
        assertEquals(emptyList<Any>(), DiffParser.parseChangedFiles(""))
        assertEquals(emptyList<Any>(), DiffParser.parseChangedFiles("   "))
        assertEquals(emptyList<Any>(), DiffParser.parseChangedFiles("\n\n"))
    }

    @Test
    fun `parseUntrackedFiles extracts untracked file paths`() {
        val statusOutput = """
            M  src/Modified.kt
            A  src/Added.kt
            ?? src/Untracked.kt
            ?? build/output/
        """.trimIndent()

        val result = DiffParser.parseUntrackedFiles(statusOutput)

        assertEquals(2, result.size)
        assertEquals("src/Untracked.kt", result[0])
        assertEquals("build/output", result[1])
    }

    @Test
    fun `parseUntrackedFiles returns empty list when no untracked files`() {
        val statusOutput = """
            M  src/Modified.kt
            A  src/Added.kt
        """.trimIndent()

        assertTrue(DiffParser.parseUntrackedFiles(statusOutput).isEmpty())
    }
}
