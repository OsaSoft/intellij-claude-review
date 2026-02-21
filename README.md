# intellij-claude-review

Claude Code edits ten files at once. You review them one `git diff` screenful at a time, scrolling back and forth in the terminal, trying to remember what you wanted to say about line 42 three files ago. Comments get lost. Context gets lost. You give up and type "looks good."

**intellij-claude-review** opens all uncommitted changes in a merge-request-style diff viewer inside IntelliJ, lets you click any line to leave a comment, and copies every comment to your clipboard in a format you can paste straight back into the Claude Code conversation.

## What It Does

- **Ctrl+Alt+R** (or Tools > Review Claude Changes) collects `git diff HEAD` and untracked files
- Opens a **split-pane tool window** with a file list on the left and a side-by-side diff on the right
- **Click any line** in the right-side gutter to add, edit, or delete a review comment
- **"Finish Review"** compiles all comments and copies them to the clipboard

## What the Output Looks Like

After reviewing and clicking "Finish Review," your clipboard contains:

```
# Claude Code Review Comments
# 3 comment(s) on 2 file(s)

[src/main/kotlin/Foo.kt:42] Rename this variable -- it shadows the parameter
[src/main/kotlin/Foo.kt:87] Missing null check before calling .save()
[src/service/Api.kt:15] Add exponential backoff to this retry loop
```

Paste this directly into your Claude Code session. Claude reads the `[file:line]` references and knows exactly where each comment applies.

## Install

Requires **JDK 21** and a git-backed project open in IntelliJ IDEA 2024.1 or later.

Build and run in a sandbox IDE:

```bash
./gradlew runIde
```

Build the plugin distribution:

```bash
./gradlew build
```

The plugin ZIP appears in `build/distributions/`. Install it via **Settings > Plugins > Install Plugin from Disk**.

## How It Works

```
Ctrl+Alt+R pressed
       |
       v
+-------------------+     +------------------+
| git diff HEAD     |---->| DiffParser       |
| git status        |     | (unified diff +  |
| --porcelain       |     |  porcelain)      |
+-------------------+     +------------------+
                                  |
                                  v
                          +------------------+
                          | ReviewModel      |
                          | (project service,|
                          |  in-memory only) |
                          +------------------+
                                  |
                                  v
                          +------------------+
                          | ReviewPanel      |
                          | (file list +     |
                          |  diff viewer)    |
                          +------------------+
                                  |
                           gutter click
                                  |
                                  v
                          +------------------+
                          | CommentPopup     |
                          | (Save / Delete)  |
                          +------------------+
                                  |
                          "Finish Review"
                                  |
                                  v
                          +------------------+
                          | ReviewCompiler   |
                          | -> clipboard     |
                          +------------------+
```

## Compatibility

| Setting | Value |
|---|---|
| IntelliJ Platform | 2024.1 -- 2025.3.x (`sinceBuild=241`, `untilBuild=253.*`) |
| JVM target | 17 |
| JDK toolchain | 21 |
| Kotlin | 1.9.25 |
| Required bundled plugin | Git4Idea |

## Relationship to the Official Claude Code Plugin

This plugin **complements** the official Claude Code IntelliJ integration. The official plugin handles the conversation loop, code generation, and per-hunk accept/reject. This plugin fills a different gap: reviewing the aggregate result of a multi-file edit and producing structured feedback comments. Use both together.

## Known Limitations

- `ReviewDiffExtension` fires for all `SimpleDiffViewer` instances globally, guarded only by title-format matching
- Comment count updates via 500ms polling timer, not a listener/callback
- Single-click gutter interaction only (no right-click menu or double-click filter)
- No persistence -- comments exist only for the duration of the review session

## License

EUPL v1.2. See [LICENSE](LICENSE).
