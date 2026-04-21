#!/usr/bin/env python3
"""Scan ta4j sources for deprecations scheduled for the current snapshot version."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

REMOVAL_VERSION_PATTERNS = (
    re.compile(r"scheduled\s+for\s+removal\s+in\s+(\d+\.\d+\.\d+)", re.IGNORECASE),
    re.compile(r"removal\s+in\s+(\d+\.\d+\.\d+)", re.IGNORECASE),
    re.compile(r'DeprecationNotifier\.warnOnce\([^;]*?"(\d+\.\d+\.\d+)"\s*\)', re.DOTALL),
)
DEPRECATED_ANNOTATION_RE = re.compile(r"@Deprecated\s*\((?P<body>[^)]*forRemoval\s*=\s*true[^)]*)\)", re.DOTALL)
VERSION_RE = re.compile(r"<version>\s*([^<]+?)\s*</version>")

SKIP_PARTS = {".agents", ".git", ".idea", "target"}
MAIN_SOURCE_MARKER = ("src", "main", "java")


def parse_args() -> argparse.Namespace:
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", default=".", help="Repository root to scan")
    parser.add_argument("--pom-file", default="pom.xml", help="Pom file with the authoritative snapshot version")
    parser.add_argument("--output-json", required=True, help="Where to write the JSON report")
    parser.add_argument("--output-md", required=True, help="Where to write the Markdown report")
    return parser.parse_args()


def read_snapshot_version(pom_file: Path) -> tuple[str, str]:
    """Read the authoritative snapshot and target removal version from pom.xml."""
    text = pom_file.read_text(encoding="utf-8")
    match = VERSION_RE.search(text)
    if match is None:
        raise ValueError(f"unable to find a project version in {pom_file}")

    snapshot_version = match.group(1).strip()
    if not snapshot_version.endswith("-SNAPSHOT"):
        raise ValueError(f"expected a SNAPSHOT version in {pom_file}, found {snapshot_version!r}")
    return snapshot_version, snapshot_version.removesuffix("-SNAPSHOT")


def iter_java_files(repo_root: Path) -> list[Path]:
    """Return tracked Java source files under src/main/java-like roots."""
    java_files: list[Path] = []
    for path in repo_root.rglob("*.java"):
        if any(part in SKIP_PARTS for part in path.parts):
            continue
        if not any(path.parts[index : index + 3] == MAIN_SOURCE_MARKER for index in range(len(path.parts) - 2)):
            continue
        java_files.append(path)
    return sorted(java_files)


def removal_versions(text: str) -> set[str]:
    """Extract scheduled removal versions from free text or notifier calls."""
    versions: set[str] = set()
    for pattern in REMOVAL_VERSION_PATTERNS:
        versions.update(pattern.findall(text))
    return versions


def find_symbols(text: str, file_stem: str, target_removal_version: str) -> list[dict[str, object]]:
    """Extract only the deprecated symbols scheduled for the target removal version."""
    lines = text.splitlines()
    candidates: list[dict[str, object]] = []
    seen: set[tuple[str, str, int]] = set()
    matches = list(DEPRECATED_ANNOTATION_RE.finditer(text))

    for index, match in enumerate(matches):
        explicit_versions = removal_versions(symbol_context(text, matches, index, match))
        annotation_line = text.count("\n", 0, match.end()) + 1
        declaration_line = annotation_line
        declaration_lines: list[str] = []

        for raw_line in lines[annotation_line:]:
            declaration_line += 1
            stripped = raw_line.strip()
            if not stripped or stripped.startswith("@") or stripped.startswith("*") or stripped.startswith("/*"):
                continue
            declaration_lines.append(stripped)
            if "{" in stripped or ";" in stripped:
                break

        declaration = " ".join(declaration_lines)
        symbol = parse_symbol(declaration, file_stem)
        if symbol is None:
            continue

        key = (str(symbol["name"]), str(symbol["kind"]), declaration_line)
        if key in seen:
            continue
        seen.add(key)
        symbol["line"] = declaration_line
        symbol["explicitRemovalVersions"] = sorted(explicit_versions)
        candidates.append(symbol)

    symbols: list[dict[str, object]] = []
    inherited_versions: set[str] = set()
    for candidate in candidates:
        explicit_versions = set(candidate.pop("explicitRemovalVersions", []))
        if is_type_kind(str(candidate["kind"])):
            candidate_versions = explicit_versions
            if explicit_versions:
                inherited_versions = explicit_versions
        else:
            candidate_versions = explicit_versions or inherited_versions

        if target_removal_version in candidate_versions:
            symbols.append(candidate)

    return symbols


def parse_symbol(declaration: str, file_stem: str) -> dict[str, object] | None:
    """Parse the declaration line into a symbol name and kind."""
    compact = " ".join(declaration.split())
    if not compact:
        return None

    kind_patterns = (
        ("class", re.compile(r"\bclass\s+([A-Za-z_][A-Za-z0-9_]*)")),
        ("interface", re.compile(r"\binterface\s+([A-Za-z_][A-Za-z0-9_]*)")),
        ("enum", re.compile(r"\benum\s+([A-Za-z_][A-Za-z0-9_]*)")),
        ("record", re.compile(r"\brecord\s+([A-Za-z_][A-Za-z0-9_]*)")),
    )
    for kind, pattern in kind_patterns:
        match = pattern.search(compact)
        if match is not None:
            return {"name": match.group(1), "kind": kind}

    callable_match = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\s*\(", compact)
    if callable_match is not None:
        name = callable_match.group(1)
        kind = "constructor" if name == file_stem else "method"
        return {"name": name, "kind": kind}

    field_match = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\s*(?:=|;)", compact)
    if field_match is not None:
        return {"name": field_match.group(1), "kind": "field"}

    return None


def is_type_kind(kind: str) -> bool:
    """Return whether the symbol kind establishes an enclosing type scope."""
    return kind in {"class", "interface", "enum", "record"}


def module_name(path: Path) -> str:
    """Return the module name that owns the source file."""
    for index, part in enumerate(path.parts):
        if path.parts[index + 1 : index + 4] == MAIN_SOURCE_MARKER:
            return part
    return path.parts[0]


def symbol_context(text: str, matches: list[re.Match[str]], index: int, match: re.Match[str]) -> str:
    """Return nearby text that belongs to the current deprecated declaration."""
    start = javadoc_start(text, match.start())
    if start == -1:
        start = match.start()

    end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
    return text[start:end]


def javadoc_start(text: str, annotation_start: int) -> int:
    """Return the start of the javadoc directly attached to the annotation, if any."""
    javadoc_open = text.rfind("/**", 0, annotation_start)
    if javadoc_open == -1:
        return -1

    javadoc_close = text.find("*/", javadoc_open, annotation_start)
    if javadoc_close == -1:
        return -1

    gap = text[javadoc_close + 2 : annotation_start]
    if gap.strip():
        return -1
    return javadoc_open


def build_issue_plan(snapshot_version: str, removal_version: str, relative_path: str,
        module: str, symbols: list[dict[str, object]]) -> dict[str, object]:
    """Build one deduplicated GitHub issue plan for a file's matching symbols."""
    symbol_count = len(symbols)
    if symbol_count == 1:
        symbol_label = str(symbols[0]["name"])
        issue_title = f"Remove {removal_version}-ready deprecation: {symbol_label}"
    else:
        issue_title = f"Remove {removal_version}-ready deprecations in {Path(relative_path).name}"

    dedupe_key = f"{removal_version}:{relative_path}"
    issue_marker = f"<!-- ta4j:deprecation-removal dedupe={dedupe_key} -->"
    symbol_lines = "\n".join(
        f"- `{symbol['name']}` ({symbol['kind']}, line {symbol['line']})" for symbol in symbols
    )
    issue_body = "\n".join(
        [
            f"Snapshot bump automation detected removal-ready deprecations for `{snapshot_version}`.",
            "",
            f"Module: `{module}`",
            f"File: `{relative_path}`",
            "",
            "Symbols:",
            symbol_lines,
            "",
            "Acceptance checks:",
            f"- remove or migrate the compatibility symbols scheduled for removal in `{removal_version}`",
            "- update callers, tests, and documentation as needed",
            "- keep the full build green",
            "",
            issue_marker,
        ]
    )
    return {
        "dedupeKey": dedupe_key,
        "issueMarker": issue_marker,
        "issueTitle": issue_title,
        "issueBody": issue_body,
        "module": module,
        "filePath": relative_path,
        "symbolCount": symbol_count,
        "symbols": symbols,
    }


def generate_report(repo_root: Path, pom_file: Path) -> dict[str, object]:
    """Generate the full removal-ready deprecation report for the current snapshot."""
    snapshot_version, removal_version = read_snapshot_version(pom_file)
    issue_plans: list[dict[str, object]] = []

    for java_file in iter_java_files(repo_root):
        text = java_file.read_text(encoding="utf-8")
        if "forRemoval = true" not in text and "forRemoval=true" not in text:
            continue
        relative_path = java_file.relative_to(repo_root).as_posix()
        symbols = find_symbols(text, java_file.stem, removal_version)
        if not symbols:
            continue

        issue_plans.append(
            build_issue_plan(snapshot_version, removal_version, relative_path, module_name(java_file), symbols)
        )

    finding_count = sum(int(plan["symbolCount"]) for plan in issue_plans)
    report = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "repoRoot": str(repo_root),
        "snapshotVersion": snapshot_version,
        "removalVersion": removal_version,
        "findingCount": finding_count,
        "issuePlanCount": len(issue_plans),
        "issuePlans": issue_plans,
    }
    report["summaryMarkdown"] = render_markdown(report)
    return report


def render_markdown(report: dict[str, object]) -> str:
    """Render a concise Markdown summary for workflow artifacts and logs."""
    lines = [
        f"# Removal-ready deprecations for {report['snapshotVersion']}",
        "",
        f"- Planned issues: {report['issuePlanCount']}",
        f"- Removal-ready symbols: {report['findingCount']}",
        "",
    ]
    issue_plans = report.get("issuePlans", [])
    if not issue_plans:
        lines.append("No removal-ready deprecations matched the current snapshot version.")
        return "\n".join(lines)

    for plan in issue_plans:
        lines.append(f"## {plan['issueTitle']}")
        lines.append("")
        lines.append(f"- Module: `{plan['module']}`")
        lines.append(f"- File: `{plan['filePath']}`")
        lines.append("- Symbols:")
        for symbol in plan["symbols"]:
            lines.append(f"  - `{symbol['name']}` ({symbol['kind']}, line {symbol['line']})")
        lines.append("")

    return "\n".join(lines)


def write_output(path_text: str, content: str) -> None:
    """Write a UTF-8 text file, creating parent directories as needed."""
    path = Path(path_text)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> int:
    """Run the scanner CLI."""
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    pom_file = Path(args.pom_file)
    if not pom_file.is_absolute():
        pom_file = (repo_root / pom_file).resolve()

    try:
        report = generate_report(repo_root, pom_file)
    except (OSError, ValueError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1

    write_output(args.output_json, json.dumps(report, indent=2, sort_keys=True))
    write_output(args.output_md, str(report["summaryMarkdown"]))
    print(json.dumps({"snapshotVersion": report["snapshotVersion"], "issuePlanCount": report["issuePlanCount"]}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
