package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.git.WorktreeParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class WorktreeParserTest : FreeSpec({

    "parse" - {

        "single worktree" {
            val output = """
                worktree /home/user/project
                HEAD abc123def456
                branch refs/heads/main

            """.trimIndent()

            val result = WorktreeParser.parse(output)
            result shouldHaveSize 1
            result[0].path shouldBe "/home/user/project"
            result[0].head shouldBe "abc123def456"
            result[0].branch shouldBe "refs/heads/main"
            result[0].isBare shouldBe false
            result[0].displayName shouldBe "main"
        }

        "multiple worktrees" {
            val output = """
                worktree /home/user/project
                HEAD abc123
                branch refs/heads/main

                worktree /home/user/project-feature
                HEAD def456
                branch refs/heads/feature/login

            """.trimIndent()

            val result = WorktreeParser.parse(output)
            result shouldHaveSize 2
            result[0].path shouldBe "/home/user/project"
            result[0].displayName shouldBe "main"
            result[1].path shouldBe "/home/user/project-feature"
            result[1].displayName shouldBe "login"
            result[1].branch shouldBe "refs/heads/feature/login"
        }

        "detached HEAD worktree" {
            val output = """
                worktree /home/user/project
                HEAD abc123
                branch refs/heads/main

                worktree /home/user/project-detached
                HEAD def456
                detached

            """.trimIndent()

            val result = WorktreeParser.parse(output)
            result shouldHaveSize 2
            result[1].branch shouldBe null
            result[1].displayName shouldBe "(detached HEAD)"
        }

        "bare repository is filtered out" {
            val output = """
                worktree /home/user/project.git
                HEAD abc123
                bare

                worktree /home/user/project-main
                HEAD def456
                branch refs/heads/main

            """.trimIndent()

            val result = WorktreeParser.parse(output)
            result shouldHaveSize 1
            result[0].path shouldBe "/home/user/project-main"
        }

        "empty output" {
            WorktreeParser.parse("").shouldBeEmpty()
            WorktreeParser.parse("   ").shouldBeEmpty()
        }

        "no trailing newline" {
            val output = """
                worktree /home/user/project
                HEAD abc123
                branch refs/heads/main
            """.trimIndent()

            val result = WorktreeParser.parse(output)
            result shouldHaveSize 1
            result[0].path shouldBe "/home/user/project"
        }
    }
})
