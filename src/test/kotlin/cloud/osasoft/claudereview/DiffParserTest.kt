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

    // --- parseStagedNewFiles tests ---

    @Test
    fun `parseStagedNewFiles extracts A and AM status files`() {
        val statusOutput = "A  src/NewFile.kt\nAM src/EditedAfterAdd.kt\nM  src/Modified.kt\n?? src/Untracked.kt"

        val result = DiffParser.parseStagedNewFiles(statusOutput)

        assertEquals(2, result.size)
        assertEquals("src/NewFile.kt", result[0])
        assertEquals("src/EditedAfterAdd.kt", result[1])
    }

    @Test
    fun `parseStagedNewFiles returns empty when no staged files`() {
        val statusOutput = "M  src/Modified.kt\n?? src/Untracked.kt"

        assertTrue(DiffParser.parseStagedNewFiles(statusOutput).isEmpty())
    }

    @Test
    fun `parseStagedNewFiles handles blank input`() {
        assertTrue(DiffParser.parseStagedNewFiles("").isEmpty())
        assertTrue(DiffParser.parseStagedNewFiles("   ").isEmpty())
        assertTrue(DiffParser.parseStagedNewFiles("\n\n").isEmpty())
    }

    @Test
    fun `parseStagedNewFiles strips trailing slash`() {
        val statusOutput = "A  src/newdir/"

        val result = DiffParser.parseStagedNewFiles(statusOutput)

        assertEquals(1, result.size)
        assertEquals("src/newdir", result[0])
    }

    // --- Combined pipeline integration tests ---

    @Test
    fun `staged new files captured when diff output is empty (no-HEAD scenario)`() {
        val diffOutput = ""
        val statusOutput = "A  src/FirstFile.kt\nA  src/SecondFile.kt"

        val parsedFiles = DiffParser.parseChangedFiles(diffOutput)
        val untrackedPaths = DiffParser.parseUntrackedFiles(statusOutput)
        val stagedNewPaths = DiffParser.parseStagedNewFiles(statusOutput)

        val alreadyTracked = parsedFiles.map { it.newPath }.toSet()
        val additionalNewPaths = (untrackedPaths + stagedNewPaths)
            .distinct()
            .filter { it !in alreadyTracked }

        assertTrue(parsedFiles.isEmpty())
        assertEquals(2, additionalNewPaths.size)
        assertEquals("src/FirstFile.kt", additionalNewPaths[0])
        assertEquals("src/SecondFile.kt", additionalNewPaths[1])
    }

    @Test
    fun `staged new files deduplicated against diff output`() {
        // Simulate: git diff HEAD shows the file as new, status also shows it as A
        val diffOutput = """
            diff --git a/src/NewFile.kt b/src/NewFile.kt
            new file mode 100644
            index 0000000..abc1234
            --- /dev/null
            +++ b/src/NewFile.kt
            @@ -0,0 +1,3 @@
            +package example
        """.trimIndent()
        val statusOutput = "A  src/NewFile.kt"

        val parsedFiles = DiffParser.parseChangedFiles(diffOutput)
        val untrackedPaths = DiffParser.parseUntrackedFiles(statusOutput)
        val stagedNewPaths = DiffParser.parseStagedNewFiles(statusOutput)

        val alreadyTracked = parsedFiles.map { it.newPath }.toSet()
        val additionalNewPaths = (untrackedPaths + stagedNewPaths)
            .distinct()
            .filter { it !in alreadyTracked }

        assertEquals(1, parsedFiles.size)
        assertEquals("src/NewFile.kt", parsedFiles[0].newPath)
        assertTrue("Staged file already in diff should be deduplicated", additionalNewPaths.isEmpty())
    }

    @Test
    fun `mixed scenario - diff modified files plus status staged-new and untracked`() {
        val diffOutput = """
            diff --git a/src/Existing.kt b/src/Existing.kt
            index abc1234..def5678 100644
            --- a/src/Existing.kt
            +++ b/src/Existing.kt
            @@ -1,3 +1,4 @@
             fun main() {
            +    println("hello")
             }
        """.trimIndent()
        val statusOutput = "M  src/Existing.kt\nA  src/Staged.kt\n?? src/Untracked.kt"

        val parsedFiles = DiffParser.parseChangedFiles(diffOutput)
        val untrackedPaths = DiffParser.parseUntrackedFiles(statusOutput)
        val stagedNewPaths = DiffParser.parseStagedNewFiles(statusOutput)

        val alreadyTracked = parsedFiles.map { it.newPath }.toSet()
        val additionalNewPaths = (untrackedPaths + stagedNewPaths)
            .distinct()
            .filter { it !in alreadyTracked }

        assertEquals(1, parsedFiles.size)
        assertEquals(FileStatus.MODIFIED, parsedFiles[0].status)
        assertEquals(2, additionalNewPaths.size)
        assertTrue(additionalNewPaths.contains("src/Staged.kt"))
        assertTrue(additionalNewPaths.contains("src/Untracked.kt"))
    }
}
