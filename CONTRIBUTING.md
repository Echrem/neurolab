# Contributing

Thanks for helping improve NeuroLab. This is an experimental research tool; distinguish measured behavior from hypotheses and game-world approximations in issues and pull requests.

## Before opening a pull request

1. Use JDK 21.
2. Run `./gradlew clean test build`.
3. State Minecraft and Forge versions, and whether you tested in a live client or server.
4. Add or update tests for changes to graph parsing, simulation, telemetry, or decoding where practical.
5. Keep comments, identifiers, user-facing text, and documentation in English.
6. Preserve dataset attribution. Review `DATA_PROVENANCE.md` before changing, replacing, or redistributing the connectome asset.

## Publishing a release

For each release, update `mod_version` in `gradle.properties` and any versioned documentation, then commit and push the change to `main`. Create a matching version tag (for example, `v0.4.1`) and push it with `git push origin v0.4.1`. GitHub Actions checks that the tag matches `mod_version`, runs the test and build workflow, and publishes the regular mod JAR plus the sources JAR to GitHub Releases. A tag whose tests or version check fail is not published.

## Scope and scientific claims

Prefer small, independently testable components. Keep Minecraft entity/world access on the game thread; background simulation tasks should exchange immutable numeric snapshots. Document assumptions, parameter sources, and limitations. Do not describe behavior as biologically validated unless it has been validated against an appropriate reference.

## Bug reports

Include the mod version, Minecraft version, loader/API versions, relevant log excerpt, reproduction steps, and whether the issue occurs in single-player or on a dedicated server. Remove private server addresses, account names, and other sensitive details from logs before posting.
