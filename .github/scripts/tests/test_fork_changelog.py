import argparse
import contextlib
import io
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = SCRIPT_DIR.parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

import fork_changelog  # noqa: E402


class ForkChangelogTests(unittest.TestCase):
    def test_all_release_fastlane_notes_fit_metadata_limit(self):
        content = (REPOSITORY_ROOT / "FORK_CHANGELOG.md").read_text(encoding="utf-8")
        releases = fork_changelog.parse_releases(content)

        self.assertGreater(len(releases), 0)
        for release in releases:
            with self.subTest(version=release.version):
                notes = fork_changelog.format_fastlane(release)
                self.assertLessEqual(len(notes.strip()), fork_changelog.MAX_FASTLANE_NOTE_LENGTH)
                self.assertIn(f"releases/tag/{release.version}", notes)
                self.assertIn(f"archive/refs/tags/{release.version}.tar.gz", notes)

    def test_fastlane_notes_keep_a_readable_summary(self):
        content = (REPOSITORY_ROOT / "FORK_CHANGELOG.md").read_text(encoding="utf-8")
        releases = fork_changelog.parse_releases(content)
        release = next(item for item in releases if item.version != "Unreleased")

        notes = fork_changelog.format_fastlane(release)

        self.assertIn(f"releases/tag/{release.version}", notes)

    def test_github_notes_include_version_source_and_gplv3(self):
        content = (REPOSITORY_ROOT / "FORK_CHANGELOG.md").read_text(encoding="utf-8")
        releases = fork_changelog.parse_releases(content)
        release = next(item for item in releases if item.version != "Unreleased")

        notes = fork_changelog.format_github(release, "akbudakanl/Calypso-Gallery", releases)

        self.assertIn(
            f"https://github.com/akbudakanl/Calypso-Gallery/archive/refs/tags/{release.version}.tar.gz",
            notes,
        )
        self.assertIn("GNU General Public License version 3", notes)

    def test_next_fork_version_bumps_the_numeric_base(self):
        self.assertEqual(
            fork_changelog.next_fork_version("1.0.0", "patch"),
            "1.0.1",
        )
        self.assertEqual(
            fork_changelog.next_fork_version("1.0.0", "minor"),
            "1.1.0",
        )
        self.assertEqual(
            fork_changelog.next_fork_version("1.0.0", "major"),
            "2.0.0",
        )

    def test_prepare_moves_unreleased_entries_and_updates_files(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            changelog_fixture = """# Changelog

## [Unreleased]
### Changed
- Fixture change

## [1.0.0] - 2026-11-17
### Added
- Previous release

[Unreleased]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.0.0...HEAD
[1.0.0]: https://github.com/akbudakanl/Calypso-Gallery/releases/tag/1.0.0
"""
            (root / "FORK_CHANGELOG.md").write_text(changelog_fixture, encoding="utf-8")
            (root / "gradle.properties").write_text(
                "VERSION_NAME=1.0.0\nVERSION_CODE=1\n"
                "APP_ID=org.fossify.gallery\nAPPLICATION_ID=com.calypso.gallery\n",
                encoding="utf-8",
            )
            upstream_changelog = root / "CHANGELOG.md"
            upstream_changelog.write_text("upstream-only\n", encoding="utf-8")
            original = changelog_fixture

            previous_directory = Path.cwd()
            try:
                os.chdir(root)
                arguments = argparse.Namespace(
                    version="1.1.0",
                    code=2,
                    repository="akbudakanl/Calypso-Gallery",
                )
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(fork_changelog.command_prepare(arguments), 0)
            finally:
                os.chdir(previous_directory)

            properties = (root / "gradle.properties").read_text(encoding="utf-8")
            changelog = (root / "FORK_CHANGELOG.md").read_text(encoding="utf-8")
            marker = (root / ".fossify/release-marker.txt").read_text(encoding="utf-8")

            self.assertIn("VERSION_NAME=1.1.0", properties)
            self.assertIn("VERSION_CODE=2", properties)
            self.assertIn("## [1.1.0] - ", changelog)
            self.assertIn(
                "[1.1.0]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.0.0...1.1.0",
                changelog,
            )
            self.assertIn("1.1.0 (2)", marker)
            self.assertEqual(upstream_changelog.read_text(encoding="utf-8"), "upstream-only\n")

            releases = fork_changelog.parse_releases(changelog)
            unreleased = next(item for item in releases if item.version == "Unreleased")
            prepared = next(item for item in releases if item.version == "1.1.0")
            self.assertFalse(any(section.changes for section in unreleased.sections))
            self.assertTrue(any(section.changes for section in prepared.sections))
            self.assertIn("Fixture change", original)

    def test_release_workflow_uses_release_application_id(self):
        workflow = (REPOSITORY_ROOT / ".github" / "workflows" / "release.yml").read_text(
            encoding="utf-8"
        )

        self.assertIn("grep '^APPLICATION_ID=' gradle.properties", workflow)
        self.assertNotIn("grep '^APP_ID=' gradle.properties", workflow)
        self.assertIn("PACKAGE_NAME: ${{ needs.prepare.outputs.package_name }}", workflow)
        self.assertIn("Create version-specific source tag", workflow)
        self.assertIn("needs: [prepare, release_foss]", workflow)

    def test_prepare_rejects_suffixed_version(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            shutil.copy2(REPOSITORY_ROOT / "FORK_CHANGELOG.md", root / "FORK_CHANGELOG.md")
            shutil.copy2(REPOSITORY_ROOT / "gradle.properties", root / "gradle.properties")

            previous_directory = Path.cwd()
            try:
                os.chdir(root)
                arguments = argparse.Namespace(
                    version="1.0.0-invalid",
                    code=40,
                    repository="akbudakanl/Calypso-Gallery",
                )
                with contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(fork_changelog.command_prepare(arguments), 1)
            finally:
                os.chdir(previous_directory)


if __name__ == "__main__":
    unittest.main()
