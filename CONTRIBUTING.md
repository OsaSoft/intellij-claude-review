# Contributing

This is a small hobby project. Contributions are welcome -- bug fixes, UX improvements, and test coverage are all useful.

## Setup

1. Clone the repository
2. Open in IntelliJ IDEA (Community or Ultimate)
3. Ensure JDK 21 is installed and selected
4. Run `./gradlew runIde` to launch a sandbox IDE with the plugin loaded

## Build Commands

```bash
./gradlew build          # Compile + run tests
./gradlew test           # Run tests only
./gradlew runIde         # Launch sandbox IDE with plugin
```

## Project Structure

All source lives under `src/main/kotlin/cloud/osasoft/claudereview/`. See [CLAUDE.md](CLAUDE.md) for the full source layout and architecture notes.

Key design decisions:

- **No persistence.** All review state lives in `ReviewModel` (a project-scoped service) and disappears when the tool window closes.
- **No settings UI.** The plugin works with zero configuration.
- **Git4Idea only.** Git operations use the bundled `git4idea` APIs, not shell commands.

## Conventions

- **Commit messages:** conventional commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`)
- **Tests:** pure-logic classes (`DiffParser`, `ReviewCompiler`) have unit tests. UI classes do not -- IntelliJ UI testing is heavyweight and not worth it for a hobby project.
- **Threading:** git commands run on `Task.Backgroundable`. UI mutations go through `invokeLater`.

## Submitting Changes

1. Fork and create a feature branch
2. Make sure `./gradlew build` passes
3. Open a pull request with a description of what changed and why

There is no CI pipeline yet. Just make sure the build is green locally.

## License

By contributing, you agree that your contributions are licensed under the [EUPL v1.2](LICENSE).
