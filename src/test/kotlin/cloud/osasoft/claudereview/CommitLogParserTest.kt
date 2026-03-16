package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.git.CommitLogParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class CommitLogParserTest : FreeSpec({

    "parse returns commits from normal multi-commit output" {
        val logOutput = """
            abc123def456abc123def456abc123def456abc12345
            abc1234
            Fix the login flow
            2 days ago
            def456abc123def456abc123def456abc123def45678
            def4567
            Refactor API client
            5 days ago
        """.trimIndent()

        val result = CommitLogParser.parse(logOutput)

        result shouldHaveSize 2
        result[0].sha shouldBe "abc123def456abc123def456abc123def456abc12345"
        result[0].shortSha shouldBe "abc1234"
        result[0].message shouldBe "Fix the login flow"
        result[0].relativeDate shouldBe "2 days ago"
        result[1].sha shouldBe "def456abc123def456abc123def456abc123def45678"
        result[1].shortSha shouldBe "def4567"
        result[1].message shouldBe "Refactor API client"
        result[1].relativeDate shouldBe "5 days ago"
    }

    "parse returns single commit" {
        val logOutput = """
            abc123def456abc123def456abc123def456abc12345
            abc1234
            Initial commit
            3 weeks ago
        """.trimIndent()

        val result = CommitLogParser.parse(logOutput)

        result shouldHaveSize 1
        result[0].sha shouldBe "abc123def456abc123def456abc123def456abc12345"
        result[0].message shouldBe "Initial commit"
    }

    "parse returns empty list for empty output" {
        CommitLogParser.parse("").shouldBeEmpty()
        CommitLogParser.parse("   ").shouldBeEmpty()
        CommitLogParser.parse("\n\n").shouldBeEmpty()
    }

    "parse handles commit with empty subject line" {
        val logOutput = """
            abc123def456abc123def456abc123def456abc12345
            abc1234

            2 days ago
        """.trimIndent()

        // Empty subject line gets filtered out as empty line, leaving only 3 non-empty lines
        // which is < 4, so no commit is parsed
        val result = CommitLogParser.parse(logOutput)
        result shouldHaveSize 0
    }

    "parse handles many commits" {
        val sb = StringBuilder()
        for (i in 1..5) {
            sb.appendLine("sha${i}full0000000000000000000000000000000000")
            sb.appendLine("sha${i}s")
            sb.appendLine("Commit message $i")
            sb.appendLine("$i days ago")
        }

        val result = CommitLogParser.parse(sb.toString())

        result shouldHaveSize 5
        result[0].shortSha shouldBe "sha1s"
        result[4].shortSha shouldBe "sha5s"
    }

    "displayName shows short sha and message" {
        val logOutput = """
            abc123def456abc123def456abc123def456abc12345
            abc1234
            Fix the login flow
            2 days ago
        """.trimIndent()

        val result = CommitLogParser.parse(logOutput)
        result[0].displayName shouldBe "abc1234  Fix the login flow"
    }
})
