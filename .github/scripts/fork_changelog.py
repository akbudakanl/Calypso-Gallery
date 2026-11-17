#!/usr/bin/env python3
"""Fork-specific changelog and release-version helpers.

This script intentionally operates only on FORK_CHANGELOG.md. The upstream
CHANGELOG.md is retained as Fossify Gallery's release history and is never
read, rewritten, or normalized by the fork's release tooling.
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

CHANGELOG_PATH = Path("FORK_CHANGELOG.md")
GRADLE_PROPERTIES_PATH = Path("gradle.properties")
RELEASE_MARKER_PATH = Path(".fossify/release-marker.txt")
DEFAULT_REPOSITORY = "akbudakanl/Gallery-Cipher"
MAX_FASTLANE_NOTE_LENGTH = 500
FASTLANE_SUMMARY_LENGTH = 160

RELEASE_HEADING_RE = re.compile(r"^## \[([^\]]+)\](?:\s+-\s+.*)?\s*$")
SECTION_HEADING_RE = re.compile(r"^###\s+(.+?)\s*$")
TOP_LEVEL_BULLET_RE = re.compile(r"^-\s+(.*)$")
FORK_VERSION_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)-cipher$")


@dataclass
class Change:
    lines: list[str]

    @property
    def summary(self) -> str:
        first_line = self.lines[0]
        match = TOP_LEVEL_BULLET_RE.match(first_line)
        return match.group(1).strip() if match else first_line.strip()


@dataclass
class Section:
    title: str
    changes: list[Change]


@dataclass
class Release:
    version: str
    sections: list[Section]


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="")


def parse_release(lines: list[str], start: int, version: str) -> Release:
    sections: list[Section] = []
    current_section: Section | None = None
    current_change: Change | None = None
    index = start + 1

    while index < len(lines):
        line = lines[index]
        if line.startswith("## "):
            break

        section_match = SECTION_HEADING_RE.match(line)
        if section_match:
            current_section = Section(title=section_match.group(1), changes=[])
            sections.append(current_section)
            current_change = None
            index += 1
            continue

        if TOP_LEVEL_BULLET_RE.match(line):
            if current_section is None:
                current_section = Section(title="Changed", changes=[])
                sections.append(current_section)
            current_change = Change(lines=[line])
            current_section.changes.append(current_change)
            index += 1
            continue

        if current_change is not None:
            if line.strip():
                current_change.lines.append(line)
            else:
                current_change.lines.append("")
        index += 1

    return Release(version=version, sections=sections)


def parse_releases(content: str) -> list[Release]:
    lines = content.splitlines()
    releases: list[Release] = []
    index = 0

    while index < len(lines):
        match = RELEASE_HEADING_RE.match(lines[index])
        if match:
            releases.append(parse_release(lines, index, match.group(1)))
        index += 1

    return releases


def find_release(content: str, version: str) -> Release | None:
    return next((release for release in parse_releases(content) if release.version == version), None)


def release_content(release: Release) -> str:
    parts: list[str] = []
    for section in release.sections:
        if not section.changes:
            continue
        parts.append(f"### {section.title}\n")
        for change in section.changes:
            while change.lines and not change.lines[-1].strip():
                change.lines.pop()
            parts.append("\n".join(change.lines) + "\n")
        parts.append("")
    return "\n".join(parts).strip()


def truncate_summary(summary: str, limit: int) -> str:
    if len(summary) <= limit:
        return summary

    shortened = summary[: max(0, limit - 1)].rsplit(" ", 1)[0].rstrip()
    if not shortened:
        shortened = summary[: max(0, limit - 1)].rstrip()
    return shortened + "…"


def next_fork_version(current_version: str, bump: str) -> str:
    match = FORK_VERSION_RE.fullmatch(current_version)
    if match is None:
        raise ValueError(f"Invalid current fork version: {current_version}")

    major, minor, patch = (int(part) for part in match.groups())
    if bump == "major":
        major += 1
        minor = 0
        patch = 0
    elif bump == "minor":
        minor += 1
        patch = 0
    elif bump == "patch":
        patch += 1
    else:
        raise ValueError(f"Unsupported version bump: {bump}")

    return f"{major}.{minor}.{patch}-cipher"


def format_fastlane(release: Release, repository: str = DEFAULT_REPOSITORY) -> str:
    summaries: list[tuple[str, str]] = []
    for section in release.sections:
        for change in section.changes:
            summaries.append((section.title, truncate_summary(change.summary, FASTLANE_SUMMARY_LENGTH)))

    footer = (
        "\n\nComplete GitHub release notes: "
        f"https://github.com/{repository}/releases/tag/{release.version}\n"
        "Corresponding source archive: "
        f"https://github.com/{repository}/archive/refs/tags/{release.version}.tar.gz"
    )

    if not summaries:
        return f"No specific changes in this version.{footer}"

    def render(entries: list[tuple[str, str]]) -> str:
        parts: list[str] = []
        previous_title: str | None = None
        for title, summary in entries:
            if title != previous_title:
                if parts:
                    parts.append("")
                parts.append(f"{title}:\n")
                previous_title = title
            parts.append(f"• {summary}")
        body = "\n".join(parts).strip()
        return f"{body}{footer}"

    selected = list(summaries)
    notes = render(selected)
    if len(notes) <= MAX_FASTLANE_NOTE_LENGTH:
        return notes

    for limit in (120, 80, 40, 20):
        selected = [(title, truncate_summary(summary, limit)) for title, summary in selected]
        while len(selected) > 1 and len(render(selected)) > MAX_FASTLANE_NOTE_LENGTH:
            selected.pop()
        notes = render(selected)
        if len(notes) <= MAX_FASTLANE_NOTE_LENGTH:
            return notes

    if not selected:
        return f"Release details are published with the complete notes and corresponding source.{footer}"

    return render(selected)


def format_github(release: Release, repository: str, releases: list[Release]) -> str:
    notes = release_content(release)
    if not notes:
        notes = "*No specific changes in this version.*"

    release_index = next(index for index, item in enumerate(releases) if item.version == release.version)
    previous = releases[release_index + 1] if release_index + 1 < len(releases) else None
    if previous:
        compare_url = f"https://github.com/{repository}/compare/{previous.version}...{release.version}"
    else:
        compare_url = f"https://github.com/{repository}/commits/{release.version}"

    return (
        f"{notes}\n\n"
        f"**Full Changelog:** {compare_url}\n\n"
        "**Corresponding source archive:** "
        f"https://github.com/{repository}/archive/refs/tags/{release.version}.tar.gz\n"
        f"**Source repository:** https://github.com/{repository}\n"
        "**License:** GNU General Public License version 3\n"
    )


def command_has_unreleased(_: argparse.Namespace) -> int:
    content = read_text(CHANGELOG_PATH)
    release = find_release(content, "Unreleased")
    has_changes = bool(release and any(section.changes for section in release.sections))
    print("true" if has_changes else "false")
    return 0


def command_next_version(args: argparse.Namespace) -> int:
    print(next_fork_version(args.current, args.bump))
    return 0


def command_ensure_translations(args: argparse.Namespace) -> int:
    content = read_text(CHANGELOG_PATH)
    release = find_release(content, "Unreleased")
    if release is None:
        print("FORK_CHANGELOG.md has no Unreleased section.", file=sys.stderr)
        return 1

    if any("Updated translations" in change.summary for section in release.sections for change in section.changes):
        print("Translation note already present.")
        return 0

    changed_files = subprocess.run(
        [
            "git",
            "diff",
            "--name-only",
            "--no-renames",
            f"refs/tags/{args.tag}",
            "HEAD",
            "--",
            "app/src/*/res/values-*/strings.xml",
            "fastlane/metadata/android/*/title.txt",
            "fastlane/metadata/android/*/short_description.txt",
            "fastlane/metadata/android/*/full_description.txt",
            ":(exclude)fastlane/metadata/android/en-US/*",
        ],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()

    if not changed_files:
        print("No translation metadata changed.")
        return 0

    lines = content.splitlines()
    release_heading = next(index for index, line in enumerate(lines) if line == "## [Unreleased]")
    next_release = next(
        (index for index in range(release_heading + 1, len(lines)) if lines[index].startswith("## ")),
        len(lines),
    )
    changed_heading = next(
        (index for index in range(release_heading + 1, next_release) if lines[index] == "### Changed"),
        None,
    )

    if changed_heading is None:
        insertion = ["### Changed", "", "- Updated translations", ""]
        lines[next_release:next_release] = insertion
    else:
        insertion_index = changed_heading + 1
        while insertion_index < next_release and not lines[insertion_index].strip():
            insertion_index += 1
        lines[insertion_index:insertion_index] = ["- Updated translations", ""]

    write_text(CHANGELOG_PATH, "\n".join(lines) + "\n")
    print("Added Updated translations to Unreleased.")
    return 0


def command_prepare(args: argparse.Namespace) -> int:
    if not args.version.endswith("-cipher"):
        print("Fork release versions must end with '-cipher'.", file=sys.stderr)
        return 1

    content = read_text(CHANGELOG_PATH)
    unreleased = find_release(content, "Unreleased")
    if unreleased is None or not any(section.changes for section in unreleased.sections):
        print("FORK_CHANGELOG.md has no entries in its Unreleased section.", file=sys.stderr)
        return 1
    if find_release(content, args.version) is not None:
        print(f"FORK_CHANGELOG.md already contains release {args.version}.", file=sys.stderr)
        return 1

    properties = read_text(GRADLE_PROPERTIES_PATH)
    previous_match = re.search(r"^VERSION_NAME=(.+)$", properties, re.MULTILINE)
    if not previous_match:
        print("VERSION_NAME is missing from gradle.properties.", file=sys.stderr)
        return 1
    previous_version = previous_match.group(1).strip()
    if previous_version == args.version:
        print(f"Fork release version is already {args.version}.", file=sys.stderr)
        return 1
    if args.code <= 0:
        print("Release version code must be positive.", file=sys.stderr)
        return 1

    properties = re.sub(r"^VERSION_NAME=.*$", f"VERSION_NAME={args.version}", properties, flags=re.MULTILINE)
    properties = re.sub(r"^VERSION_CODE=.*$", f"VERSION_CODE={args.code}", properties, flags=re.MULTILINE)
    write_text(GRADLE_PROPERTIES_PATH, properties)

    today = dt.date.today().isoformat()
    unreleased_heading = re.search(r"^## \[Unreleased\]\s*$", content, re.MULTILINE)
    if unreleased_heading is None:
        print("FORK_CHANGELOG.md has no Unreleased heading.", file=sys.stderr)
        return 1

    version_heading = f"\n\n## [{args.version}] - {today}"
    content = content[: unreleased_heading.end()] + version_heading + content[unreleased_heading.end() :]

    link_lines = [
        f"[Unreleased]: https://github.com/{args.repository}/compare/{args.version}...HEAD",
        f"[{args.version}]: https://github.com/{args.repository}/compare/{previous_version}...{args.version}",
    ]
    content = re.sub(r"^\[Unreleased\]:.*$", link_lines[0], content, flags=re.MULTILINE)
    content = re.sub(rf"^\[{re.escape(args.version)}\]:.*$", link_lines[1], content, flags=re.MULTILINE)

    if "[Unreleased]:" not in content:
        content = content.rstrip() + "\n\n" + link_lines[0] + "\n"
    if f"[{args.version}]:" not in content:
        content = content.rstrip() + "\n" + link_lines[1] + "\n"

    write_text(CHANGELOG_PATH, content)
    write_text(
        RELEASE_MARKER_PATH,
        f"# Auto-generated file. DO NOT EDIT.\n{args.version} ({args.code})\n",
    )
    print(f"Prepared {args.version} ({args.code}) from FORK_CHANGELOG.md.")
    return 0


def command_format(args: argparse.Namespace) -> int:
    content = read_text(CHANGELOG_PATH)
    releases = parse_releases(content)
    release = next((item for item in releases if item.version == args.version), None)
    if release is None:
        print(f"No FORK_CHANGELOG.md entry found for {args.version}.", file=sys.stderr)
        return 1

    if args.format == "github":
        notes = format_github(release, args.repository, releases)
    else:
        notes = format_fastlane(release)

    if args.output:
        write_text(Path(args.output), notes + "\n")
    else:
        print(notes)
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("has-unreleased", help="Print whether Unreleased contains changes.").set_defaults(
        func=command_has_unreleased
    )

    next_version = subparsers.add_parser(
        "next-version", help="Calculate the next X.Y.Z-cipher version for a release bump."
    )
    next_version.add_argument("--current", required=True)
    next_version.add_argument("--bump", required=True, choices=("major", "minor", "patch"))
    next_version.set_defaults(func=command_next_version)

    translations = subparsers.add_parser(
        "ensure-translations", help="Add the upstream translation note when metadata changed."
    )
    translations.add_argument("--tag", required=True)
    translations.set_defaults(func=command_ensure_translations)

    prepare = subparsers.add_parser("prepare", help="Prepare fork release version files.")
    prepare.add_argument("--version", required=True)
    prepare.add_argument("--code", required=True, type=int)
    prepare.add_argument("--repository", required=True)
    prepare.set_defaults(func=command_prepare)

    formatter = subparsers.add_parser("format", help="Format fork release notes.")
    formatter.add_argument("--version", required=True)
    formatter.add_argument("--format", choices=("github", "fastlane"), required=True)
    formatter.add_argument("--repository", default=DEFAULT_REPOSITORY)
    formatter.add_argument("--output")
    formatter.set_defaults(func=command_format)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        return args.func(args)
    except (OSError, subprocess.SubprocessError, ValueError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
