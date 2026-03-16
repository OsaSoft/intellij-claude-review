package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.model.DiffSource
import cloud.osasoft.claudereview.model.FileDiff
import cloud.osasoft.claudereview.model.FileStatus
import cloud.osasoft.claudereview.model.LineComment
import cloud.osasoft.claudereview.model.ReviewModel
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe

class ReviewModelTest : FreeSpec({

    fun createModel(): ReviewModel = ReviewModel()

    val uncommitted = DiffSource.Uncommitted
    val commit1 = DiffSource.Commit("abc123", "abc1", "Fix login", "2 days ago")
    val commit2 = DiffSource.Commit("def456", "def4", "Refactor API", "5 days ago")

    "comments are scoped to active source" {
        val model = createModel()
        model.trackSource(uncommitted)
        model.trackSource(commit1)

        model.loadSource(uncommitted, emptyList())
        model.addComment(LineComment("Main.kt", 10, "Fix this"))

        model.loadSource(commit1, emptyList())
        model.addComment(LineComment("Main.kt", 10, "Different comment"))

        // Switch back and verify scoping
        model.loadSource(uncommitted, emptyList())
        val uncommittedComments = model.getComments("Main.kt")
        uncommittedComments shouldHaveSize 1
        uncommittedComments[0].text shouldBe "Fix this"

        model.loadSource(commit1, emptyList())
        val commitComments = model.getComments("Main.kt")
        commitComments shouldHaveSize 1
        commitComments[0].text shouldBe "Different comment"
    }

    "source switching preserves comments" {
        val model = createModel()
        model.trackSource(uncommitted)
        model.trackSource(commit1)

        // Add comments to uncommitted
        model.loadSource(uncommitted, emptyList())
        model.addComment(LineComment("A.kt", 1, "Comment A"))
        model.addComment(LineComment("B.kt", 5, "Comment B"))

        // Switch to commit, add comment
        model.loadSource(commit1, emptyList())
        model.addComment(LineComment("C.kt", 3, "Comment C"))

        // Switch back — comments preserved
        model.loadSource(uncommitted, emptyList())
        model.getComments("A.kt") shouldHaveSize 1
        model.getComments("B.kt") shouldHaveSize 1
    }

    "getAllSourcedComments returns grouped results" {
        val model = createModel()
        model.trackSource(uncommitted)
        model.trackSource(commit1)

        model.loadSource(uncommitted, emptyList())
        model.addComment(LineComment("A.kt", 1, "Comment 1"))

        model.loadSource(commit1, emptyList())
        model.addComment(LineComment("B.kt", 2, "Comment 2"))
        model.addComment(LineComment("B.kt", 5, "Comment 3"))

        val sourced = model.getAllSourcedComments()
        sourced shouldHaveSize 2
        sourced[uncommitted]!! shouldHaveSize 1
        sourced[commit1]!! shouldHaveSize 2
    }

    "getAllComments returns all comments across sources" {
        val model = createModel()
        model.trackSource(uncommitted)
        model.trackSource(commit1)

        model.loadSource(uncommitted, emptyList())
        model.addComment(LineComment("A.kt", 1, "Comment 1"))

        model.loadSource(commit1, emptyList())
        model.addComment(LineComment("B.kt", 2, "Comment 2"))

        model.getAllComments() shouldHaveSize 2
    }

    "getCommentCount counts across all sources" {
        val model = createModel()
        model.trackSource(uncommitted)
        model.trackSource(commit1)

        model.loadSource(uncommitted, emptyList())
        model.addComment(LineComment("A.kt", 1, "Comment 1"))

        model.loadSource(commit1, emptyList())
        model.addComment(LineComment("B.kt", 2, "Comment 2"))
        model.addComment(LineComment("C.kt", 3, "Comment 3"))

        model.getCommentCount() shouldBe 3
    }

    "getFileDiffs returns diffs for active source" {
        val model = createModel()
        val diffs1 = listOf(FileDiff("A.kt", "", "content", FileStatus.NEW))
        val diffs2 = listOf(FileDiff("B.kt", "old", "new", FileStatus.MODIFIED))

        model.loadSource(uncommitted, diffs1)
        model.getFileDiffs() shouldHaveSize 1
        model.getFileDiffs()[0].filePath shouldBe "A.kt"

        model.loadSource(commit1, diffs2)
        model.getFileDiffs() shouldHaveSize 1
        model.getFileDiffs()[0].filePath shouldBe "B.kt"

        // Switch back
        model.loadSource(uncommitted, diffs1)
        model.getFileDiffs()[0].filePath shouldBe "A.kt"
    }

    "clear wipes everything" {
        val model = createModel()
        model.trackSource(uncommitted)
        model.trackSource(commit1)

        model.loadSource(uncommitted, listOf(FileDiff("A.kt", "", "content", FileStatus.NEW)))
        model.addComment(LineComment("A.kt", 1, "Comment 1"))

        model.loadSource(commit1, listOf(FileDiff("B.kt", "old", "new", FileStatus.MODIFIED)))
        model.addComment(LineComment("B.kt", 2, "Comment 2"))

        model.clear()

        model.getActiveSource() shouldBe DiffSource.Uncommitted
        model.getFileDiffs().shouldBeEmpty()
        model.getAllComments().shouldBeEmpty()
        model.getCommentCount() shouldBe 0
    }

    "removeComment removes from active source only" {
        val model = createModel()
        model.trackSource(uncommitted)
        model.trackSource(commit1)

        val comment = LineComment("A.kt", 1, "Same comment")

        model.loadSource(uncommitted, emptyList())
        model.addComment(comment)

        model.loadSource(commit1, emptyList())
        model.addComment(comment)

        // Remove from commit1 scope
        model.removeComment(comment)
        model.getComments("A.kt").shouldBeEmpty()

        // Uncommitted still has it
        model.loadSource(uncommitted, emptyList())
        model.getComments("A.kt") shouldHaveSize 1
    }

    "concurrent loadSource and getComments does not throw" {
        val model = createModel()
        model.trackSource(uncommitted)
        model.trackSource(commit1)
        model.trackSource(commit2)

        model.loadSource(uncommitted, emptyList())
        model.addComment(LineComment("A.kt", 1, "Comment"))

        val threads = (1..10).map { i ->
            Thread {
                val source = if (i % 2 == 0) commit1 else commit2
                model.loadSource(source, emptyList())
                model.getComments("A.kt")
                model.getCommentCount()
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }

        // If we get here without exception, the test passes
        model.getCommentCount() shouldBe 1
    }
})
