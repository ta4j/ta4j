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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", default=".", help="Repository root to scan")
    parser.add_argument("--pom-file", default="pom.xml", help="Pom file with the authoritative snapshot version")
    parser.add_argument("--output-json", required=True, help="Where to write the JSON report")
    parser.add_argument("--output-md", required=True, help="Where to write the Markdown report")
    return parser.parse_args()


def read_snapshot_version(pom_file: Path) -> tuple[str, str]:
    text = pom_file.read_text(encoding="utf-8")
    match = VERSION_RE.search(text)
    if match is None:
        raise ValueError(f"unable to find a project version in {pom_file}")

    snapshot_version = match.group(1).strip()
    if not snapshot_version.endswith("-SNAPSHOT"):
        raise ValueError(f"expected a SNAPSHOT version in {pom_file}, found {snapshot_version!r}")
    return snapshot_version, snapshot_version.removesuffix("-SNAPSHOT")


def iter_java_files(repo_root: Path) -> list[Path]:
    java_files: list[Path] = []
    for path in repo_root.rglob("*.java"):
        if any(part in SKIP_PARTS for part in path.parts):
            continue
        if not any(path.parts[index : index + 3] == MAIN_SOURCE_MARKER for index in range(len(path.parts) - 2)):
            continue
        java_files.append(path)
    return sorted(java_files)


def removal_versions(text: str) -> set[str]:
    versions: set[str] = set()
    for pattern in REMOVAL_VERSION_PATTERNS:
        versions.update(pattern.findall(text))
    return versions


def find_symbols(text: str, file_stem: str) -> list[dict[str, object]]:
    lines = text.splitlines()
    symbols: list[dict[str, object]] = []
    seen: set[tuple[str, str, int]] = set()

    for match in DEPRECATED_ANNOTATION_RE.finditer(text):
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
        symbols.append(symbol)

    return symbols


def parse_symbol(declaration: str, file_stem: str) -> dict[str, object] | None:
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


def module_name(path: Path) -> str:
    for index, part in enumerate(path.parts):
        if path.parts[index + 1 : index + 4] == MAIN_SOURCE_MARKER:
            return part
    return path.parts[0]


def build_issue_plan(snapshot_version: str, removal_version: str, relative_path: str,
        module: str, symbols: list[dict[str, object]]) -> dict[str, object]:
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
    snapshot_version, removal_version = read_snapshot_version(pom_file)
    issue_plans: list[dict[str, object]] = []

    for java_file in iter_java_files(repo_root):
        text = java_file.read_text(encoding="utf-8")
        if "forRemoval = true" not in text and "forRemoval=true" not in text:
            continue
        if removal_version not in removal_versions(text):
            continue

        relative_path = java_file.relative_to(repo_root).as_posix()
        symbols = find_symbols(text, java_file.stem)
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
    path = Path(path_text)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> int:
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
