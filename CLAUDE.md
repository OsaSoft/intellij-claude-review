# intellij-claude-review

IntelliJ plugin for reviewing Claude Code multi-file changes in a merge-request-style diff viewer with inline comments.

## Build & Run

```bash
./gradlew build          # Compile + run tests
./gradlew build -x test  # Compile only
./gradlew test           # Run tests only
./gradlew runIde         # Launch sandbox IDE with plugin loaded
```

Requires JDK 21 installed. Targets JVM 17 bytecode for IntelliJ 2024.1+ compatibility.

## Stack

- **Language**: Kotlin 1.9.25
- **Build**: Gradle with IntelliJ Platform Plugin 2.2.1 (version managed in `settings.gradle.kts`)
- **Platform**: IntelliJ IDEA 2024.1 (sinceBuild=241, untilBuild=253.*)
- **Dependencies**: Git4Idea (bundled), JUnit 4 (test)
- **Package**: `cloud.osasoft.claudereview`
- **Plugin ID**: `cloud.osasoft.claudereview`

## Architecture

No persistence. All state lives in `ReviewModel` (project service) for the duration of a review session.

**Flow**: Toolbar action -> `git diff HEAD` + `git status --porcelain` via git4idea -> parse changed files -> show multi-file diff in tool window -> gutter click to add comments -> "Finish Review" copies formatted block to clipboard.

### Source Layout

```
src/main/kotlin/cloud/osasoft/claudereview/
  action/StartClaudeReviewAction.kt   # Entry point: git commands, file content collection, opens tool window
  diff/DiffParser.kt                  # Parses unified diff headers + porcelain status output
  model/LineComment.kt                # Data class: filePath, lineNumber, text
  model/FileDiff.kt                   # Data class + FileStatus enum (NEW/MODIFIED/DELETED/RENAMED)
  model/ReviewModel.kt                # Project service: ConcurrentHashMap comment store, fileDiff list
  ui/ReviewPanel.kt                   # Tool window: JBSplitter file list + DiffRequestPanel + toolbar
  ui/ReviewDiffExtension.kt           # DiffExtension EP: gutter click listener on right-side editor
  ui/CommentPopup.kt                  # JBPopup with JBTextArea, Save/Delete, Ctrl+Enter
  ui/CommentGutterIconRenderer.kt     # Gutter icon with tooltip, click-to-edit
  ui/FileListCellRenderer.kt          # File type icons + status indicators
  export/ReviewCompiler.kt            # Formats [file:line] comment block for clipboard
src/main/resources/
  META-INF/plugin.xml                 # Action, service, extension point registrations
  icons/comment.svg                   # 16x16 gutter icon
src/test/kotlin/cloud/osasoft/claudereview/
  DiffParserTest.kt                   # 8 tests: modified/new/deleted/renamed/multiple/empty/untracked
  ReviewCompilerTest.kt               # 6 tests: empty/single/multi-file/sorting/header format
```

## Key IntelliJ APIs

- **Git**: `GitLineHandler` + `Git.getInstance().runCommand()`, `GitRepositoryManager`
- **Diff viewer**: `DiffManager.createRequestPanel()`, `DiffContentFactory.create()`, `SimpleDiffRequest`
- **DiffExtension EP**: `com.intellij.diff.DiffExtension` — hooks into `SimpleDiffViewer` creation
- **Gutter**: `editor.markupModel.addRangeHighlighter()` with `GutterIconRenderer`
- **Tool window**: `ToolWindowManager.registerToolWindow()` (programmatic, Kotlin DSL)
- **Notifications**: `NotificationGroupManager` with group ID `ClaudeReview`

## Conventions

- Commit messages: conventional commits (`feat:`, `fix:`, etc.)
- No persistence, no settings UI, no telemetry
- Threading: git commands on background thread (`Task.Backgroundable`), UI mutations via `invokeLater`
- Binary files detected by null-byte scan of first 8KB, shown as `(binary file)` placeholder

## Export Format

```
# Claude Code Review Comments
# 3 comment(s) on 2 file(s)

[src/main/kotlin/Foo.kt:42] Rename this variable
[src/main/kotlin/Foo.kt:87] Missing null check
[src/service/Api.kt:15] Add exponential backoff
```

## Known Limitations

- `ReviewDiffExtension` fires for ALL `SimpleDiffViewer` instances globally, not just this plugin's; guarded only by title format matching
- Comment count updates via 500ms Swing Timer polling rather than a listener/callback pattern
- Every gutter click opens the comment popup (no right-click menu or double-click filter)
- No per-hunk accept/reject (that's the official Claude Code plugin's job)
