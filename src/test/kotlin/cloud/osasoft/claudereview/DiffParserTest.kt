package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.diff.DiffParser
import cloud.osasoft.claudereview.model.FileStatus
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class DiffParserTest : FreeSpec({

    "parseChangedFiles returns one modified file for simple diff" {
        // GIVEN a diff with a simple modified file
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

        // WHEN parsing changed files
        val result = DiffParser.parseChangedFiles(diff)

        // THEN it returns one modified file with correct paths and status
        result shouldHaveSize 1
        result[0].newPath shouldBe "src/Main.kt"
        result[0].oldPath shouldBe "src/Main.kt"
        result[0].status shouldBe FileStatus.MODIFIED
    }

    "parseChangedFiles detects a new file" {
        // GIVEN a diff with a new file
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

        // WHEN parsing changed files
        val result = DiffParser.parseChangedFiles(diff)

        // THEN it returns one new file with null oldPath
        result shouldHaveSize 1
        result[0].newPath shouldBe "src/NewFile.kt"
        result[0].oldPath.shouldBeNull()
        result[0].status shouldBe FileStatus.NEW
    }

    "parseChangedFiles detects a deleted file" {
        // GIVEN a diff with a deleted file
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

        // WHEN parsing changed files
        val result = DiffParser.parseChangedFiles(diff)

        // THEN it returns one deleted file
        result shouldHaveSize 1
        result[0].newPath shouldBe "src/OldFile.kt"
        result[0].oldPath shouldBe "src/OldFile.kt"
        result[0].status shouldBe FileStatus.DELETED
    }

    "parseChangedFiles detects a renamed file" {
        // GIVEN a diff with a renamed file
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

        // WHEN parsing changed files
        val result = DiffParser.parseChangedFiles(diff)

        // THEN it returns one renamed file with old and new paths
        result shouldHaveSize 1
        result[0].newPath shouldBe "src/NewName.kt"
        result[0].oldPath shouldBe "src/OldName.kt"
        result[0].status shouldBe FileStatus.RENAMED
    }

    "parseChangedFiles handles multiple files in one diff" {
        // GIVEN a diff with three files (modified, new, deleted)
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

        // WHEN parsing changed files
        val result = DiffParser.parseChangedFiles(diff)

        // THEN it returns all three files with correct statuses
        result shouldHaveSize 3

        result[0].newPath shouldBe "src/FileA.kt"
        result[0].status shouldBe FileStatus.MODIFIED

        result[1].newPath shouldBe "src/FileB.kt"
        result[1].status shouldBe FileStatus.NEW
        result[1].oldPath.shouldBeNull()

        result[2].newPath shouldBe "src/FileC.kt"
        result[2].status shouldBe FileStatus.DELETED
    }

    "parseChangedFiles returns empty list for empty diff input" {
        // GIVEN various empty/blank inputs
        // WHEN parsing changed files
        // THEN it returns an empty list
        DiffParser.parseChangedFiles("").shouldBeEmpty()
        DiffParser.parseChangedFiles("   ").shouldBeEmpty()
        DiffParser.parseChangedFiles("\n\n").shouldBeEmpty()
    }

    "parseUntrackedFiles extracts untracked file paths" {
        // GIVEN a porcelain status output with mixed statuses
        val statusOutput = """
            M  src/Modified.kt
            A  src/Added.kt
            ?? src/Untracked.kt
            ?? build/output/
        """.trimIndent()

        // WHEN parsing untracked files
        val result = DiffParser.parseUntrackedFiles(statusOutput)

        // THEN it returns only the ?? entries with trailing slash stripped
        result shouldHaveSize 2
        result[0] shouldBe "src/Untracked.kt"
        result[1] shouldBe "build/output"
    }

    "parseUntrackedFiles returns empty list when no untracked files" {
        // GIVEN status output with no ?? entries
        val statusOutput = """
            M  src/Modified.kt
            A  src/Added.kt
        """.trimIndent()

        // WHEN parsing untracked files
        // THEN it returns an empty list
        DiffParser.parseUntrackedFiles(statusOutput).shouldBeEmpty()
    }

    "parseStagedNewFiles extracts A and AM status files" {
        // GIVEN status output with A and AM entries
        val statusOutput = "A  src/NewFile.kt\nAM src/EditedAfterAdd.kt\nM  src/Modified.kt\n?? src/Untracked.kt"

        // WHEN parsing staged new files
        val result = DiffParser.parseStagedNewFiles(statusOutput)

        // THEN it returns only A and AM entries
        result shouldHaveSize 2
        result[0] shouldBe "src/NewFile.kt"
        result[1] shouldBe "src/EditedAfterAdd.kt"
    }

    "parseStagedNewFiles returns empty when no staged files" {
        // GIVEN status output with no A entries
        val statusOutput = "M  src/Modified.kt\n?? src/Untracked.kt"

        // WHEN parsing staged new files
        // THEN it returns an empty list
        DiffParser.parseStagedNewFiles(statusOutput).shouldBeEmpty()
    }

    "parseStagedNewFiles handles blank input" {
        // GIVEN various empty/blank inputs
        // WHEN parsing staged new files
        // THEN it returns an empty list
        DiffParser.parseStagedNewFiles("").shouldBeEmpty()
        DiffParser.parseStagedNewFiles("   ").shouldBeEmpty()
        DiffParser.parseStagedNewFiles("\n\n").shouldBeEmpty()
    }

    "parseStagedNewFiles strips trailing slash" {
        // GIVEN a staged directory entry with trailing slash
        val statusOutput = "A  src/newdir/"

        // WHEN parsing staged new files
        val result = DiffParser.parseStagedNewFiles(statusOutput)

        // THEN it strips the trailing slash
        result shouldHaveSize 1
        result[0] shouldBe "src/newdir"
    }

    "staged new files captured when diff output is empty (no-HEAD scenario)" {
        // GIVEN empty diff but staged new files in status
        val diffOutput = ""
        val statusOutput = "A  src/FirstFile.kt\nA  src/SecondFile.kt"

        // WHEN running the combined pipeline
        val parsedFiles = DiffParser.parseChangedFiles(diffOutput)
        val untrackedPaths = DiffParser.parseUntrackedFiles(statusOutput)
        val stagedNewPaths = DiffParser.parseStagedNewFiles(statusOutput)

        val alreadyTracked = parsedFiles.map { it.newPath }.toSet()
        val additionalNewPaths = (untrackedPaths + stagedNewPaths)
            .distinct()
            .filter { it !in alreadyTracked }

        // THEN staged files appear as additional new paths
        parsedFiles.shouldBeEmpty()
        additionalNewPaths shouldHaveSize 2
        additionalNewPaths[0] shouldBe "src/FirstFile.kt"
        additionalNewPaths[1] shouldBe "src/SecondFile.kt"
    }

    "staged new files deduplicated against diff output" {
        // GIVEN a diff that already contains the file and status also shows it as A
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

        // WHEN running the combined pipeline
        val parsedFiles = DiffParser.parseChangedFiles(diffOutput)
        val untrackedPaths = DiffParser.parseUntrackedFiles(statusOutput)
        val stagedNewPaths = DiffParser.parseStagedNewFiles(statusOutput)

        val alreadyTracked = parsedFiles.map { it.newPath }.toSet()
        val additionalNewPaths = (untrackedPaths + stagedNewPaths)
            .distinct()
            .filter { it !in alreadyTracked }

        // THEN the file is not duplicated
        parsedFiles shouldHaveSize 1
        parsedFiles[0].newPath shouldBe "src/NewFile.kt"
        additionalNewPaths.shouldBeEmpty()
    }

    "untracked files inside directories listed individually with -u flag" {
        // GIVEN porcelain output with expanded untracked directory entries
        val statusOutput = """
            M  src/Existing.kt
            ?? controller/legacy/OldApi.kt
            ?? controller/legacy/OldService.kt
            ?? controller/legacy/OldModel.kt
        """.trimIndent()

        // WHEN parsing untracked files
        val result = DiffParser.parseUntrackedFiles(statusOutput)

        // THEN each file is listed individually
        result shouldHaveSize 3
        result[0] shouldBe "controller/legacy/OldApi.kt"
        result[1] shouldBe "controller/legacy/OldService.kt"
        result[2] shouldBe "controller/legacy/OldModel.kt"
    }

    "mixed scenario - diff modified files plus status staged-new and untracked" {
        // GIVEN a diff with one modified file and status with staged + untracked
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

        // WHEN running the combined pipeline
        val parsedFiles = DiffParser.parseChangedFiles(diffOutput)
        val untrackedPaths = DiffParser.parseUntrackedFiles(statusOutput)
        val stagedNewPaths = DiffParser.parseStagedNewFiles(statusOutput)

        val alreadyTracked = parsedFiles.map { it.newPath }.toSet()
        val additionalNewPaths = (untrackedPaths + stagedNewPaths)
            .distinct()
            .filter { it !in alreadyTracked }

        // THEN diff has the modified file and additional paths have staged + untracked
        parsedFiles shouldHaveSize 1
        parsedFiles[0].status shouldBe FileStatus.MODIFIED
        additionalNewPaths shouldHaveSize 2
        additionalNewPaths shouldContain "src/Staged.kt"
        additionalNewPaths shouldContain "src/Untracked.kt"
    }
})
