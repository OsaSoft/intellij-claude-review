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
            1710000000
            def456abc123def456abc123def456abc123def45678
            def4567
            Refactor API client
            5 days ago
            1709740800
        """.trimIndent()

        val result = CommitLogParser.parse(logOutput)

        result shouldHaveSize 2
        result[0].sha shouldBe "abc123def456abc123def456abc123def456abc12345"
        result[0].shortSha shouldBe "abc1234"
        result[0].message shouldBe "Fix the login flow"
        result[0].relativeDate shouldBe "2 days ago"
        result[0].timestamp shouldBe 1710000000L
        result[1].sha shouldBe "def456abc123def456abc123def456abc123def45678"
        result[1].shortSha shouldBe "def4567"
        result[1].message shouldBe "Refactor API client"
        result[1].relativeDate shouldBe "5 days ago"
        result[1].timestamp shouldBe 1709740800L
    }

    "parse returns single commit" {
        val logOutput = """
            abc123def456abc123def456abc123def456abc12345
            abc1234
            Initial commit
            3 weeks ago
            1708300000
        """.trimIndent()

        val result = CommitLogParser.parse(logOutput)

        result shouldHaveSize 1
        result[0].sha shouldBe "abc123def456abc123def456abc123def456abc12345"
        result[0].message shouldBe "Initial commit"
        result[0].timestamp shouldBe 1708300000L
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
            1710000000
        """.trimIndent()

        val result = CommitLogParser.parse(logOutput)
        result shouldHaveSize 1
        result[0].message shouldBe ""
        result[0].timestamp shouldBe 1710000000L
    }

    "parse handles many commits" {
        val sb = StringBuilder()
        for (i in 1..5) {
            sb.appendLine("sha${i}full0000000000000000000000000000000000")
            sb.appendLine("sha${i}s")
            sb.appendLine("Commit message $i")
            sb.appendLine("$i days ago")
            sb.appendLine("${1710000000L - i * 86400}")
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
            1710000000
        """.trimIndent()

        val result = CommitLogParser.parse(logOutput)
        result[0].displayName shouldBe "abc1234  Fix the login flow"
    }
})
