package cloud.osasoft.claudereview

import cloud.osasoft.claudereview.model.CommentSeverity
import cloud.osasoft.claudereview.model.LineComment
import cloud.osasoft.claudereview.persistence.ReviewPersistence
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe

class ReviewPersistenceTest : FreeSpec({

    fun freshPersistence(): ReviewPersistence = ReviewPersistence()

    // ---------------------------------------------------------------------------
    // Serialization round-trip
    // ---------------------------------------------------------------------------

    "round-trip preserves comment fields for uncommitted source" {
        // GIVEN a persistence instance with one saved comment
        val persistence = freshPersistence()
        val comment = LineComment("src/Main.kt", 42, "Rename this", CommentSeverity.SUGGESTION)
        persistence.saveComments(
            "/worktree/main",
            mapOf("uncommitted:src/Main.kt" to listOf(comment))
        )

        // WHEN reading back
        val restored = persistence.getComments("/worktree/main")

        // THEN the comment round-trips without data loss
        restored shouldHaveSize 1
        val restoredList = restored["uncommitted:src/Main.kt"]!!
        restoredList shouldHaveSize 1
        restoredList[0].filePath shouldBe "src/Main.kt"
        restoredList[0].lineNumber shouldBe 42
        restoredList[0].text shouldBe "Rename this"
        restoredList[0].severity shouldBe CommentSeverity.SUGGESTION
    }

    "round-trip preserves all CommentSeverity variants" {
        val persistence = freshPersistence()
        val comments = CommentSeverity.entries.mapIndexed { idx, severity ->
            LineComment("src/File.kt", idx + 1, "Comment $idx", severity)
        }

        persistence.saveComments(
            "/worktree/main",
            mapOf("uncommitted:src/File.kt" to comments)
        )

        val restored = persistence.getComments("/worktree/main")["uncommitted:src/File.kt"]!!
        restored shouldHaveSize CommentSeverity.entries.size
        CommentSeverity.entries.forEachIndexed { idx, severity ->
            restored[idx].severity shouldBe severity
        }
    }

    "round-trip defaults unknown severity string to ISSUE" {
        // GIVEN a persistence state with a corrupted severity string (simulates future migration)
        val persistence = freshPersistence()

        // Directly manipulate the state to inject a bad severity string
        val badComment = ReviewPersistence.PersistedComment(
            filePath = "src/Bad.kt",
            lineNumber = 1,
            text = "bad severity",
            severity = "NONEXISTENT_VALUE"
        )
        val state = persistence.state
        state.worktrees["/worktree/main"] = ReviewPersistence.WorktreeComments(
            mutableMapOf("uncommitted:src/Bad.kt" to mutableListOf(badComment))
        )

        // WHEN reading back via public API
        val restored = persistence.getComments("/worktree/main")["uncommitted:src/Bad.kt"]!!

        // THEN severity defaults to ISSUE instead of throwing
        restored shouldHaveSize 1
        restored[0].severity shouldBe CommentSeverity.ISSUE
    }

    "round-trip preserves comments across multiple worktrees" {
        val persistence = freshPersistence()
        persistence.saveComments(
            "/worktree/a",
            mapOf("uncommitted:A.kt" to listOf(LineComment("A.kt", 1, "A comment")))
        )
        persistence.saveComments(
            "/worktree/b",
            mapOf("uncommitted:B.kt" to listOf(LineComment("B.kt", 2, "B comment")))
        )

        val restoredA = persistence.getComments("/worktree/a")["uncommitted:A.kt"]!!
        val restoredB = persistence.getComments("/worktree/b")["uncommitted:B.kt"]!!

        restoredA[0].text shouldBe "A comment"
        restoredB[0].text shouldBe "B comment"
    }

    "getComments returns empty map for unknown worktree" {
        val persistence = freshPersistence()
        persistence.getComments("/worktree/nonexistent").shouldBeEmpty()
    }

    // ---------------------------------------------------------------------------
    // 500-comment cap
    // ---------------------------------------------------------------------------

    "saveComments enforces 500-comment cap by trimming oldest entries" {
        val persistence = freshPersistence()

        // Add 501 comments across distinct keys so each entry is a single-comment list
        val commentsByKey = (1..501).associate { i ->
            "uncommitted:src/File$i.kt" to listOf(LineComment("src/File$i.kt", i, "Comment $i"))
        }
        persistence.saveComments("/worktree/main", commentsByKey)

        val restored = persistence.getComments("/worktree/main")
        val totalRestored = restored.values.sumOf { it.size }

        totalRestored shouldBe 500
    }

    "cap trims from lowest-sorted keys first" {
        val persistence = freshPersistence()

        // Keys "a_*" sort before "z_*", so "a_*" comments should be trimmed first
        val aComments = (1..300).associate { i ->
            "uncommitted:a_file$i.kt" to listOf(LineComment("a_file$i.kt", i, "A comment $i"))
        }
        val zComments = (1..300).associate { i ->
            "uncommitted:z_file$i.kt" to listOf(LineComment("z_file$i.kt", i, "Z comment $i"))
        }
        persistence.saveComments("/worktree/main", aComments + zComments)

        val restored = persistence.getComments("/worktree/main")
        val totalRestored = restored.values.sumOf { it.size }
        totalRestored shouldBe 500

        // Some "a_*" keys should have been dropped (they sort first)
        val aCount = restored.keys.count { it.contains(":a_file") }
        val zCount = restored.keys.count { it.contains(":z_file") }

        // All z entries preserved; only 200 a entries remain (500 - 300 z = 200)
        zCount shouldBe 300
        aCount shouldBe 200
    }

    "cap applies across worktrees" {
        val persistence = freshPersistence()

        // Worktree "a" gets 300 comments, "b" gets 300 — total 600 exceeds cap
        val aComments = (1..300).associate { i ->
            "uncommitted:a_file$i.kt" to listOf(LineComment("a_file$i.kt", i, "A $i"))
        }
        val bComments = (1..300).associate { i ->
            "uncommitted:b_file$i.kt" to listOf(LineComment("b_file$i.kt", i, "B $i"))
        }
        persistence.saveComments("/worktree/a", aComments)
        // Saving worktree/b triggers the cap enforcement
        persistence.saveComments("/worktree/b", bComments)

        val totalA = persistence.getComments("/worktree/a").values.sumOf { it.size }
        val totalB = persistence.getComments("/worktree/b").values.sumOf { it.size }

        totalA + totalB shouldBe 500
    }

    "cap does not trim when exactly at limit" {
        val persistence = freshPersistence()
        val commentsByKey = (1..500).associate { i ->
            "uncommitted:src/File$i.kt" to listOf(LineComment("src/File$i.kt", i, "Comment $i"))
        }
        persistence.saveComments("/worktree/main", commentsByKey)

        val total = persistence.getComments("/worktree/main").values.sumOf { it.size }
        total shouldBe 500
    }

    // ---------------------------------------------------------------------------
    // Schema version
    // ---------------------------------------------------------------------------

    "state has schema version 1 by default" {
        val persistence = freshPersistence()
        persistence.state.schemaVersion shouldBe 1
    }

    "loadState preserves schema version from loaded data" {
        val persistence = freshPersistence()
        val customState = ReviewPersistence.State(schemaVersion = 2)
        persistence.loadState(customState)
        persistence.state.schemaVersion shouldBe 2
    }

    // ---------------------------------------------------------------------------
    // clearWorktree
    // ---------------------------------------------------------------------------

    "clearWorktree removes persisted data for that path only" {
        val persistence = freshPersistence()
        persistence.saveComments("/worktree/a", mapOf("uncommitted:A.kt" to listOf(LineComment("A.kt", 1, "A"))))
        persistence.saveComments("/worktree/b", mapOf("uncommitted:B.kt" to listOf(LineComment("B.kt", 2, "B"))))

        persistence.clearWorktree("/worktree/a")

        persistence.getComments("/worktree/a").shouldBeEmpty()
        persistence.getComments("/worktree/b") shouldHaveSize 1
    }

    "saveComments with empty lists does not persist empty entries" {
        val persistence = freshPersistence()
        persistence.saveComments(
            "/worktree/main",
            mapOf("uncommitted:A.kt" to emptyList())
        )

        persistence.getComments("/worktree/main").shouldBeEmpty()
    }
})
