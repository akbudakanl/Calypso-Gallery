# Contributing to Calypso Gallery

Calypso Gallery is an independently maintained, unofficial fork. Contributions should target this fork and its users; upstream Fossify Gallery issues and discussions are not used for fork-specific reports or pull requests.

## Reporting a bug or requesting a feature

Before opening a new report, search the [fork issue tracker](https://github.com/akbudakanl/Gallery-Cipher/issues), including closed issues, to avoid duplicates.

Include:

- the affected Calypso Gallery version;
- the Android version and device model;
- clear reproduction steps;
- the expected and actual behavior;
- logs or screenshots when they help diagnose the problem.

Use the repository's bug-report and feature-request templates when possible.

## Contributing as a developer

1. Keep changes focused and consistent with the existing Kotlin and Android XML style.
2. Build the affected variant and run the relevant unit tests before submitting a pull request.
3. Add or update tests for behavior changes, especially cryptographic, vault, and file-format changes.
4. Update [FORK_CHANGELOG.md](FORK_CHANGELOG.md) for user-visible or fork-specific changes. Do not add fork releases to the upstream-only [CHANGELOG.md](CHANGELOG.md).
5. Keep the project under GPLv3. Document the license and source location of any new dependency and do not introduce incompatible code.
6. Do not silently change the application ID, vault format, or compatibility behavior. Such changes require an explicit migration plan and documentation.

Useful build and test commands include:

```text
./gradlew assembleFossDebug
./gradlew testFossDebugUnitTest
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Contributing as a non-developer

Translation improvements are welcome through pull requests. Preserve the existing XML structure and encode resource files as UTF-8 without introducing unrelated re-encoding changes. When changing wording, keep the meaning consistent across the default English strings and the affected localized resources.

For documentation, architecture, or user-guide improvements, update [DOCS.md](DOCS.md) and [FORK_CHANGELOG.md](FORK_CHANGELOG.md) as appropriate.
