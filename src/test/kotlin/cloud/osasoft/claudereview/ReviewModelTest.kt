package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.model.DiffSource
import cloud.osasoft.claudereview.model.FileDiff
import cloud.osasoft.claudereview.model.FileStatus
import cloud.osasoft.claudereview.model.LineComment
import cloud.osasoft.claudereview.model.ReviewModel
import cloud.osasoft.claudereview.model.WorktreeState
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ReviewModelTest : FreeSpec({

    val uncommitted = DiffSource.Uncommitted
    val commit1 = DiffSource.Commit("abc123", "abc1", "Fix login", "2 days ago", 1710000000)
    val commit2 = DiffSource.Commit("def456", "def4", "Refactor API", "5 days ago", 1709740800)

    "WorktreeState" - {

        fun createState(): WorktreeState = WorktreeState()

        "comments are scoped to active source" {
            val state = createState()
            state.trackSource(uncommitted)
            state.trackSource(commit1)

            state.loadSource(uncommitted, emptyList())
            state.addComment(LineComment("Main.kt", 10, "Fix this"))

            state.loadSource(commit1, emptyList())
            state.addComment(LineComment("Main.kt", 10, "Different comment"))

            // Switch back and verify scoping
            state.loadSource(uncommitted, emptyList())
            val uncommittedComments = state.getComments("Main.kt")
            uncommittedComments shouldHaveSize 1
            uncommittedComments[0].text shouldBe "Fix this"

            state.loadSource(commit1, emptyList())
            val commitComments = state.getComments("Main.kt")
            commitComments shouldHaveSize 1
            commitComments[0].text shouldBe "Different comment"
        }

        "source switching preserves comments" {
            val state = createState()
            state.trackSource(uncommitted)
            state.trackSource(commit1)

            // Add comments to uncommitted
            state.loadSource(uncommitted, emptyList())
            state.addComment(LineComment("A.kt", 1, "Comment A"))
            state.addComment(LineComment("B.kt", 5, "Comment B"))

            // Switch to commit, add comment
            state.loadSource(commit1, emptyList())
            state.addComment(LineComment("C.kt", 3, "Comment C"))

            // Switch back — comments preserved
            state.loadSource(uncommitted, emptyList())
            state.getComments("A.kt") shouldHaveSize 1
            state.getComments("B.kt") shouldHaveSize 1
        }

        "getAllSourcedComments returns grouped results" {
            val state = createState()
            state.trackSource(uncommitted)
            state.trackSource(commit1)

            state.loadSource(uncommitted, emptyList())
            state.addComment(LineComment("A.kt", 1, "Comment 1"))

            state.loadSource(commit1, emptyList())
            state.addComment(LineComment("B.kt", 2, "Comment 2"))
            state.addComment(LineComment("B.kt", 5, "Comment 3"))

            val sourced = state.getAllSourcedComments()
            sourced shouldHaveSize 2
            sourced[uncommitted]!! shouldHaveSize 1
            sourced[commit1]!! shouldHaveSize 2
        }

        "getAllComments returns all comments across sources" {
            val state = createState()
            state.trackSource(uncommitted)
            state.trackSource(commit1)

            state.loadSource(uncommitted, emptyList())
            state.addComment(LineComment("A.kt", 1, "Comment 1"))

            state.loadSource(commit1, emptyList())
            state.addComment(LineComment("B.kt", 2, "Comment 2"))

            state.getAllComments() shouldHaveSize 2
        }

        "getCommentCount counts across all sources" {
            val state = createState()
            state.trackSource(uncommitted)
            state.trackSource(commit1)

            state.loadSource(uncommitted, emptyList())
            state.addComment(LineComment("A.kt", 1, "Comment 1"))

            state.loadSource(commit1, emptyList())
            state.addComment(LineComment("B.kt", 2, "Comment 2"))
            state.addComment(LineComment("C.kt", 3, "Comment 3"))

            state.getCommentCount() shouldBe 3
        }

        "getFileDiffs returns diffs for active source" {
            val state = createState()
            val diffs1 = listOf(FileDiff("A.kt", "", "content", FileStatus.NEW))
            val diffs2 = listOf(FileDiff("B.kt", "old", "new", FileStatus.MODIFIED))

            state.loadSource(uncommitted, diffs1)
            state.getFileDiffs() shouldHaveSize 1
            state.getFileDiffs()[0].filePath shouldBe "A.kt"

            state.loadSource(commit1, diffs2)
            state.getFileDiffs() shouldHaveSize 1
            state.getFileDiffs()[0].filePath shouldBe "B.kt"

            // Switch back
            state.loadSource(uncommitted, diffs1)
            state.getFileDiffs()[0].filePath shouldBe "A.kt"
        }

        "clear wipes everything" {
            val state = createState()
            state.trackSource(uncommitted)
            state.trackSource(commit1)

            state.loadSource(uncommitted, listOf(FileDiff("A.kt", "", "content", FileStatus.NEW)))
            state.addComment(LineComment("A.kt", 1, "Comment 1"))

            state.loadSource(commit1, listOf(FileDiff("B.kt", "old", "new", FileStatus.MODIFIED)))
            state.addComment(LineComment("B.kt", 2, "Comment 2"))

            state.clear()

            state.getActiveSource() shouldBe DiffSource.Uncommitted
            state.getFileDiffs().shouldBeEmpty()
            state.getAllComments().shouldBeEmpty()
            state.getCommentCount() shouldBe 0
        }

        "removeComment removes from active source only" {
            val state = createState()
            state.trackSource(uncommitted)
            state.trackSource(commit1)

            val comment = LineComment("A.kt", 1, "Same comment")

            state.loadSource(uncommitted, emptyList())
            state.addComment(comment)

            state.loadSource(commit1, emptyList())
            state.addComment(comment)

            // Remove from commit1 scope
            state.removeComment(comment)
            state.getComments("A.kt").shouldBeEmpty()

            // Uncommitted still has it
            state.loadSource(uncommitted, emptyList())
            state.getComments("A.kt") shouldHaveSize 1
        }

        "concurrent loadSource and getComments does not throw" {
            val state = createState()
            state.trackSource(uncommitted)
            state.trackSource(commit1)
            state.trackSource(commit2)

            state.loadSource(uncommitted, emptyList())
            state.addComment(LineComment("A.kt", 1, "Comment"))

            val threads = (1..10).map { i ->
                Thread {
                    val source = if (i % 2 == 0) commit1 else commit2
                    state.loadSource(source, emptyList())
                    state.getComments("A.kt")
                    state.getCommentCount()
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join(5000) }
            threads.all { !it.isAlive } shouldBe true

            state.getCommentCount() shouldBe 1
        }
    }

    "ReviewModel worktree isolation" - {

        "comments in one worktree do not leak to another" {
            val model = ReviewModel()
            val stateA = model.getOrCreateState("/worktree/a")
            val stateB = model.getOrCreateState("/worktree/b")

            // Both worktrees have Uncommitted source with same id
            stateA.loadSource(uncommitted, emptyList())
            stateB.loadSource(uncommitted, emptyList())

            stateA.addComment(LineComment("Main.kt", 10, "Comment in A"))
            stateB.addComment(LineComment("Main.kt", 10, "Comment in B"))

            stateA.getComments("Main.kt") shouldHaveSize 1
            stateA.getComments("Main.kt")[0].text shouldBe "Comment in A"

            stateB.getComments("Main.kt") shouldHaveSize 1
            stateB.getComments("Main.kt")[0].text shouldBe "Comment in B"
        }

        "clearWorktree only clears targeted worktree" {
            val model = ReviewModel()
            val stateA = model.getOrCreateState("/worktree/a")
            val stateB = model.getOrCreateState("/worktree/b")

            stateA.loadSource(uncommitted, emptyList())
            stateA.addComment(LineComment("A.kt", 1, "Comment A"))

            stateB.loadSource(uncommitted, emptyList())
            stateB.addComment(LineComment("B.kt", 1, "Comment B"))

            model.clearWorktree("/worktree/a")

            // A is gone — fresh state returned
            val freshA = model.getOrCreateState("/worktree/a")
            freshA.getAllComments().shouldBeEmpty()

            // B is untouched
            stateB.getComments("B.kt") shouldHaveSize 1
        }

        "getOrCreateState returns same instance for same path" {
            val model = ReviewModel()
            val state1 = model.getOrCreateState("/worktree/x")
            val state2 = model.getOrCreateState("/worktree/x")
            state1 shouldBe state2
        }

        "getOrCreateState returns different instances for different paths" {
            val model = ReviewModel()
            val state1 = model.getOrCreateState("/worktree/a")
            val state2 = model.getOrCreateState("/worktree/b")
            state1 shouldNotBe state2
        }
    }
})
