#!/usr/bin/env python3
"""Utilities shared by ta4j release GitHub Actions workflows."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import re
import subprocess
import sys
import textwrap
import urllib.request
import xml.etree.ElementTree as ET
from typing import Any


SECRET_PATTERNS = (
    (re.compile(r"https?://\S+"), "[REDACTED_URL]"),
    (re.compile(r"gh[oprsu]_[A-Za-z0-9_]{20,}"), "[REDACTED_TOKEN]"),
    (
        re.compile(r"(?<![A-Za-z0-9_])(?=[A-Za-z0-9+/=_-]{32,})(?=.*[0-9])[A-Za-z0-9+/=_-]{32,}"),
        "[REDACTED_SECRET]",
    ),
)

EXPECTED_RELEASE_ARTIFACTS = (
    "ta4j-core/target/ta4j-core-{version}.jar",
    "ta4j-core/target/ta4j-core-{version}-sources.jar",
    "ta4j-core/target/ta4j-core-{version}-javadoc.jar",
    "ta4j-core/target/ta4j-core-{version}-tests.jar",
    "ta4j-examples/target/ta4j-examples-{version}.jar",
    "ta4j-examples/target/ta4j-examples-{version}-sources.jar",
    "ta4j-examples/target/ta4j-examples-{version}-javadoc.jar",
)

JAVADOC_PATH_PATTERN = re.compile(r"^.*?(ta4j-(?:core|examples)/src/.+)$")
SNAPSHOT_METADATA_URL = "https://central.sonatype.com/repository/maven-snapshots/org/ta4j/ta4j-parent/maven-metadata.xml"


def redact(value: str) -> str:
    redacted = value
    for pattern, replacement in SECRET_PATTERNS:
        redacted = pattern.sub(replacement, redacted)
    return redacted


def redact_log_path(value: str) -> str:
    redacted = value
    for pattern, replacement in SECRET_PATTERNS[:2]:
        redacted = pattern.sub(replacement, redacted)
    return redacted


def run_git(args: list[str], *, check: bool = True) -> str:
    completed = subprocess.run(
        ["git", *args],
        check=check,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return completed.stdout


def write_json(path: pathlib.Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_text(path: pathlib.Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")


def append_output(name: str, value: str, output_path: str | None = None) -> None:
    target = output_path or os.environ.get("GITHUB_OUTPUT")
    if not target:
        return
    with open(target, "a", encoding="utf-8") as handle:
        if "\n" in value:
            delimiter = f"EOF_{name}_{os.getpid()}"
            handle.write(f"{name}<<{delimiter}\n{value}\n{delimiter}\n")
        else:
            handle.write(f"{name}={value}\n")


def load_catalog(args: argparse.Namespace) -> list[dict[str, Any]]:
    if args.catalog_file:
        with open(args.catalog_file, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    else:
        request = urllib.request.Request(
            args.catalog_url,
            headers={
                "Accept": "application/json",
                "User-Agent": "ta4j-release-automation",
            },
        )
        with urllib.request.urlopen(request, timeout=args.timeout_seconds) as response:
            data = json.load(response)
    if not isinstance(data, list):
        raise ValueError("model catalog response must be a JSON array")
    return data


def command_catalog_preflight(args: argparse.Namespace) -> int:
    model_id = args.model.strip()
    if not model_id:
        raise ValueError("--model is required")

    catalog = load_catalog(args)
    selected = next((entry for entry in catalog if entry.get("id") == model_id), None)
    if selected is None:
        available = ", ".join(sorted(str(entry.get("id")) for entry in catalog if entry.get("id")))
        print(f"::error::Configured RELEASE_AI_MODEL '{model_id}' was not found in the GitHub Models catalog.", file=sys.stderr)
        print(f"Available models: {available}", file=sys.stderr)
        return 1

    limits = selected.get("limits") if isinstance(selected.get("limits"), dict) else {}
    max_input_tokens = str(limits.get("max_input_tokens", ""))
    max_output_tokens = str(limits.get("max_output_tokens", ""))
    result = {
        "id": selected.get("id", model_id),
        "name": selected.get("name", ""),
        "publisher": selected.get("publisher", ""),
        "summary": selected.get("summary", ""),
        "rate_limit_tier": selected.get("rate_limit_tier", ""),
        "max_input_tokens": max_input_tokens,
        "max_output_tokens": max_output_tokens,
        "html_url": selected.get("html_url", ""),
    }
    write_json(args.output, result)

    append_output("model_id", str(result["id"]))
    append_output("model_name", str(result["name"]))
    append_output("model_summary", str(result["summary"]))
    append_output("model_rate_limit_tier", str(result["rate_limit_tier"]))
    append_output("model_max_input_tokens", max_input_tokens)
    append_output("model_max_output_tokens", max_output_tokens)
    append_output("model_html_url", str(result["html_url"]))

    print(
        "audit:model_catalog_preflight "
        f"model={result['id']} max_input_tokens={max_input_tokens or 'unknown'} "
        f"max_output_tokens={max_output_tokens or 'unknown'} rate_limit_tier={result['rate_limit_tier'] or 'unknown'}"
    )
    return 0


def changed_files_since(last_tag: str) -> list[str]:
    if last_tag == "none":
        output = run_git(["ls-tree", "-r", "--name-only", "HEAD"])
    else:
        output = run_git(["diff", "--name-only", f"{last_tag}..HEAD"])
    return sorted(path for path in output.splitlines() if path)


def category_for(path: str) -> str:
    if path == "pom.xml" or path.endswith("/pom.xml"):
        return "build metadata"
    if "/src/main/" in f"/{path}":
        return "production code"
    if "/src/test/" in f"/{path}":
        return "tests"
    if path.startswith(".github/workflows/"):
        return "workflows"
    if path.startswith("scripts/"):
        return "release/tooling scripts" if "release" in path or path.startswith("scripts/tests/") else "scripts"
    if path == "CHANGELOG.md" or path.startswith("release/") or path == "RELEASE_PROCESS.md" or path == "README.md":
        return "release documentation"
    return "other"


def extract_unreleased_changelog() -> str:
    path = pathlib.Path("CHANGELOG.md")
    if not path.exists():
        return "(CHANGELOG.md not found)"
    lines = path.read_text(encoding="utf-8").splitlines()
    captured: list[str] = []
    in_section = False
    for line in lines:
        if re.match(r"^##\s+\[?Unreleased\]?", line):
            in_section = True
            continue
        if in_section and line.startswith("## "):
            break
        if in_section:
            captured.append(line)
    text = "\n".join(captured).strip()
    return text or "(Unreleased section is empty)"


def collect_diff(last_tag: str, paths: list[str], max_chars: int) -> tuple[str, bool]:
    if last_tag == "none" or not paths:
        return "(No prior release tag diff available.)", False

    priority = sorted(
        paths,
        key=lambda path: (
            0 if category_for(path) in {"production code", "build metadata"} else 1,
            path,
        ),
    )
    try:
        diff = run_git(["diff", "--no-ext-diff", "--find-renames", "--unified=80", f"{last_tag}..HEAD", "--", *priority])
    except subprocess.CalledProcessError as exc:
        diff = exc.stdout + "\n" + exc.stderr
    redacted = redact(diff)
    if len(redacted) > max_chars:
        return redacted[:max_chars] + "\n\n[TRUNCATED: selected diff exceeded dossier budget]\n", True
    return redacted, False


def public_api_signals(diff_text: str) -> list[str]:
    signals: list[str] = []
    pattern = re.compile(
        r"^[+-]\s*(?:public|protected)\s+(?:static\s+|final\s+|abstract\s+|default\s+|sealed\s+|non-sealed\s+)*"
        r"(?:class|interface|enum|record|@interface|[A-Za-z0-9_<>\[\], ?]+)\s+[A-Za-z0-9_]+",
        re.MULTILINE,
    )
    for match in pattern.finditer(diff_text):
        line = match.group(0).strip()
        if line not in signals:
            signals.append(line)
        if len(signals) >= 80:
            signals.append("[TRUNCATED: more public API signal lines omitted]")
            break
    return signals


def javadoc_signals(diff_text: str) -> list[str]:
    signals: list[str] = []
    for line in diff_text.splitlines():
        stripped = line.strip()
        if not stripped.startswith(("+", "-")):
            continue
        if "@since" in stripped or "/**" in stripped or stripped.startswith(("+ *", "- *")):
            signals.append(stripped)
        if len(signals) >= 80:
            signals.append("[TRUNCATED: more Javadoc signal lines omitted]")
            break
    return signals


def command_build_dossier(args: argparse.Namespace) -> int:
    files = changed_files_since(args.last_tag)
    categories: dict[str, list[str]] = {}
    for path in files:
        categories.setdefault(category_for(path), []).append(path)

    diff_text, diff_truncated = collect_diff(args.last_tag, files, args.max_diff_chars)
    api_signals = public_api_signals(diff_text)
    doc_signals = javadoc_signals(diff_text)
    test_files = [path for path in files if category_for(path) == "tests"]

    generated_at = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()
    audit = {
        "generated_at": generated_at,
        "last_tag": args.last_tag,
        "current_version": args.current_version,
        "pom_base": args.pom_base,
        "changed_file_count": len(files),
        "category_counts": {key: len(value) for key, value in sorted(categories.items())},
        "selected_diff_chars": len(diff_text),
        "selected_diff_truncated": diff_truncated,
        "public_api_signal_count": len(api_signals),
        "javadoc_signal_count": len(doc_signals),
        "test_file_count": len(test_files),
    }

    sections = [
        "# ta4j Release Dossier",
        "",
        "## Metadata",
        "",
        f"- generated_at: {generated_at}",
        f"- current_version: {args.current_version}",
        f"- pom_base: {args.pom_base}",
        f"- last_reachable_tag: {args.last_tag}",
        f"- changed_file_count: {len(files)}",
        "",
        "## Changed Files by Category",
        "",
    ]
    for category in sorted(categories):
        sections.append(f"### {category} ({len(categories[category])})")
        sections.extend(f"- `{path}`" for path in categories[category])
        sections.append("")

    sections.extend(
        [
            "## Unreleased Changelog Context",
            "",
            "```markdown",
            redact(extract_unreleased_changelog()),
            "```",
            "",
            "## Public API Signals",
            "",
        ]
    )
    sections.extend(f"- `{line}`" for line in api_signals) if api_signals else sections.append("- (none detected)")
    sections.extend(["", "## Javadoc and @since Signals", ""])
    sections.extend(f"- `{line}`" for line in doc_signals) if doc_signals else sections.append("- (none detected)")
    sections.extend(["", "## Test File Signals", ""])
    sections.extend(f"- `{path}`" for path in test_files) if test_files else sections.append("- (none detected)")
    sections.extend(
        [
            "",
            "## Selected Diff",
            "",
            "```diff",
            diff_text,
            "```",
            "",
        ]
    )

    write_text(args.output, "\n".join(sections))
    write_json(args.audit_output, audit)
    print(
        "audit:release_dossier "
        f"file={args.output} changed_files={len(files)} selected_diff_chars={len(diff_text)} "
        f"selected_diff_truncated={'true' if diff_truncated else 'false'}"
    )
    append_output("dossier_path", str(args.output))
    append_output("audit_path", str(args.audit_output))
    append_output("changed_file_count", str(len(files)))
    append_output("selected_diff_chars", str(len(diff_text)))
    append_output("selected_diff_truncated", "true" if diff_truncated else "false")
    return 0


def command_build_ai_request(args: argparse.Namespace) -> int:
    dossier = args.dossier.read_text(encoding="utf-8")
    if len(dossier) > args.max_dossier_chars:
        dossier = dossier[: args.max_dossier_chars] + "\n\n[TRUNCATED: dossier exceeded request budget]\n"

    if args.semver_rules.exists() and args.semver_rules.stat().st_size > 0:
        semver_rules = args.semver_rules.read_text(encoding="utf-8")
        rules_source = str(args.semver_rules)
    else:
        semver_rules = textwrap.dedent(
            """\
            Decide release go/no-go from unreleased binary-impacting changes.
            If go, choose bump: patch|minor.
            MINOR: backward-compatible, user-visible new features, and breaking-change cases.
            PATCH: backward-compatible bug fixes or internal improvements.
            If binary change count is 0 => should_release=false.
            If binary change list empty or changelog-only => should_release=false.
            If unsure between MINOR and PATCH, prefer PATCH.
            Pre-1.0.0: breaking changes can be MINOR.
            """
        ).strip()
        rules_source = "default"

    request = {
        "model": args.model,
        "temperature": 0,
        "messages": [
            {
                "role": "system",
                "content": (
                    "You are a SemVer release reviewer for a Java library. "
                    "Return JSON only. Base every conclusion on the release dossier."
                ),
            },
            {
                "role": "user",
                "content": (
                    "Decide whether ta4j should cut a release from this dossier. "
                    "If yes, choose bump patch or minor. Major is disabled for this workflow.\n\n"
                    "SemVer rules:\n"
                    f"{semver_rules}\n\n"
                    "Return JSON only with this shape:\n"
                    "{"
                    "\"should_release\": true|false, "
                    "\"bump\": \"patch|minor\", "
                    "\"confidence\": 0.0-1.0, "
                    "\"reason\": \"1-2 sentences\", "
                    "\"evidence\": [\"specific dossier facts\"], "
                    "\"risks\": [\"release risks or empty array\"], "
                    "\"missing\": [\"missing changelog/javadoc/test evidence or empty array\"]"
                    "}.\n\n"
                    f"{dossier}"
                ),
            },
        ],
    }
    write_json(args.output, request)
    request_size = args.output.stat().st_size
    print(
        "audit:ai_request "
        f"file={args.output} model={args.model} semver_rules_source={rules_source} request_json_size_bytes={request_size}"
    )
    append_output("request_json_size_bytes", str(request_size))
    append_output("dossier_chars", str(len(dossier)))
    return 0


def parse_json_object(raw: str) -> dict[str, Any] | None:
    candidate = raw.strip()
    if candidate.startswith("```"):
        candidate = re.sub(r"^```(?:json)?\s*", "", candidate)
        candidate = re.sub(r"\s*```$", "", candidate)
    try:
        parsed = json.loads(candidate)
        return parsed if isinstance(parsed, dict) else None
    except json.JSONDecodeError:
        pass

    start = candidate.find("{")
    end = candidate.rfind("}")
    if start >= 0 and end > start:
        try:
            parsed = json.loads(candidate[start : end + 1])
            return parsed if isinstance(parsed, dict) else None
        except json.JSONDecodeError:
            return None
    return None


def parse_release_flag(value: Any) -> tuple[bool, str]:
    if isinstance(value, bool):
        return value, ""
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if value == 1:
            return True, ""
        if value == 0:
            return False, ""
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"true", "1", "yes", "y", "on"}:
            return True, ""
        if normalized in {"false", "0", "no", "n", "off", ""}:
            return False, ""
    return False, f"invalid should_release '{value}', defaulted to false"


def normalize_decision(parsed: dict[str, Any] | None, raw: str) -> dict[str, Any]:
    if parsed is None:
        hint = raw[:200].replace("\n", " ").strip()
        reason = "AI response was not valid JSON"
        if hint:
            reason = f"{reason}: {hint}"
        return {
            "should_release": False,
            "bump": "patch",
            "confidence": 0.0,
            "warning": "Invalid AI JSON",
            "reason": reason,
            "evidence": [],
            "risks": ["AI response could not be parsed"],
            "missing": [],
        }

    should_release, flag_warning = parse_release_flag(parsed.get("should_release", False))
    bump = str(parsed.get("bump") or "patch").strip().lower()
    warning = str(parsed.get("warning") or "")
    if flag_warning:
        warning = (warning + "; " if warning else "") + flag_warning
    if bump == "major":
        bump = "minor"
        warning = (warning + "; " if warning else "") + "major bump disabled, downgraded to minor"
    if bump not in {"patch", "minor"}:
        warning = (warning + "; " if warning else "") + f"invalid bump '{bump}', defaulted to patch"
        bump = "patch"
    if not should_release:
        bump = "patch"

    confidence_raw = parsed.get("confidence", 0.0)
    try:
        confidence = float(confidence_raw)
    except (TypeError, ValueError):
        confidence = 0.0
    confidence = max(0.0, min(1.0, confidence))

    def string_list(key: str) -> list[str]:
        value = parsed.get(key, [])
        if not isinstance(value, list):
            return [str(value)]
        return [str(item) for item in value]

    return {
        "should_release": should_release,
        "bump": bump,
        "confidence": confidence,
        "warning": warning,
        "reason": str(parsed.get("reason") or ""),
        "evidence": string_list("evidence"),
        "risks": string_list("risks"),
        "missing": string_list("missing"),
    }


def command_parse_decision(args: argparse.Namespace) -> int:
    raw = args.raw_file.read_text(encoding="utf-8") if args.raw_file.exists() else ""
    decision = normalize_decision(parse_json_object(raw), raw)
    write_json(args.output, decision)
    append_output("should_release", "true" if decision["should_release"] else "false", args.github_output)
    append_output("bump", str(decision["bump"]), args.github_output)
    append_output("confidence", str(decision["confidence"]), args.github_output)
    append_output("warning", str(decision["warning"]), args.github_output)
    append_output("reason", str(decision["reason"]), args.github_output)
    print(
        "audit:ai_decision "
        f"should_release={'true' if decision['should_release'] else 'false'} bump={decision['bump']} "
        f"confidence={decision['confidence']} output={args.output}"
    )
    return 0


def normalize_javadoc_warning(line: str) -> str | None:
    value = line.strip()
    if not value:
        return None
    value = re.sub(r"^\[[A-Z]+\]\s*", "", value)
    if value == "Javadoc Warnings":
        return None
    lower = value.lower()

    is_javadoc_warning = (
        ("javadoc" in lower and "warning" in lower)
        or re.search(r"\bwarning\s*[-:]", lower) is not None
    )
    if not is_javadoc_warning:
        return None

    # Compiler deprecation diagnostics are Maven warnings, not Javadoc debt.
    if re.search(r"\.java:\[\d+,\d+\]", value):
        return None

    path_match = JAVADOC_PATH_PATTERN.match(value)
    if path_match:
        value = path_match.group(1)
    value = re.sub(r"(\.java):\d+:\s+warning:", r"\1: warning:", value)
    return redact_log_path(value)


def unique_preserving_order(values: list[str]) -> list[str]:
    seen: set[str] = set()
    unique: list[str] = []
    for value in values:
        if value not in seen:
            seen.add(value)
            unique.append(value)
    return unique


def collect_javadoc_warnings(logs: list[pathlib.Path]) -> tuple[list[str], list[str]]:
    warnings: list[str] = []
    missing_logs: list[str] = []
    for log_file in logs:
        if not log_file.exists():
            missing_logs.append(str(log_file))
            continue
        for line in log_file.read_text(encoding="utf-8", errors="replace").splitlines():
            warning = normalize_javadoc_warning(line)
            if warning:
                warnings.append(warning)
    return unique_preserving_order(warnings), missing_logs


def load_javadoc_baseline(path: pathlib.Path) -> list[str]:
    if not path.exists():
        return []
    values: list[str] = []
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line and not line.startswith("#"):
            normalized = normalize_javadoc_warning(line)
            values.append(normalized if normalized else line)
    return unique_preserving_order(values)


def command_javadoc_warnings(args: argparse.Namespace) -> int:
    current, missing_logs = collect_javadoc_warnings(args.logs)
    baseline = load_javadoc_baseline(args.baseline)
    baseline_set = set(baseline)
    current_set = set(current)
    new_warnings = [warning for warning in current if warning not in baseline_set]
    resolved_warnings = [warning for warning in baseline if warning not in current_set]

    lines = [
        "# Javadoc Warning Baseline Check",
        "",
        f"baseline={args.baseline}",
        f"current_count={len(current)}",
        f"baseline_count={len(baseline)}",
        f"new_count={len(new_warnings)}",
        f"resolved_count={len(resolved_warnings)}",
        "",
        "## Current warnings",
        *(current if current else ["(none)"]),
        "",
        "## New warnings",
        *(new_warnings if new_warnings else ["(none)"]),
        "",
        "## Baseline warnings not seen in this run",
        *(resolved_warnings if resolved_warnings else ["(none)"]),
        "",
        "## Missing logs",
        *(missing_logs if missing_logs else ["(none)"]),
        "",
    ]
    write_text(args.output, "\n".join(lines))
    append_output("javadoc_warning_count", str(len(current)), args.github_output)
    append_output("javadoc_warning_baseline_count", str(len(baseline)), args.github_output)
    append_output("javadoc_warning_new_count", str(len(new_warnings)), args.github_output)
    append_output("javadoc_warning_resolved_count", str(len(resolved_warnings)), args.github_output)

    print(
        "audit:javadoc_warnings "
        f"current={len(current)} baseline={len(baseline)} new={len(new_warnings)} "
        f"resolved={len(resolved_warnings)} output={args.output}"
    )
    for missing_log in missing_logs:
        print(f"::warning::Javadoc warning log not found: {missing_log}")
    if new_warnings and args.fail_on_new:
        for warning in new_warnings:
            print(f"::error::New Javadoc warning: {warning}", file=sys.stderr)
        return 1
    return 0


def command_artifact_manifest(args: argparse.Namespace) -> int:
    version = args.version.strip()
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        print(f"::error::Release artifact version must be major.minor.patch: {version}", file=sys.stderr)
        return 1

    expected = [pathlib.Path(template.format(version=version)) for template in EXPECTED_RELEASE_ARTIFACTS]
    existing_jars = sorted(pathlib.Path(".").glob("*/target/*.jar"))
    expected_set = {path.as_posix() for path in expected}
    existing_set = {path.as_posix().removeprefix("./") for path in existing_jars}

    missing = sorted(path.as_posix() for path in expected if not path.exists())
    unexpected = sorted(path for path in existing_set if path not in expected_set)
    manifest_lines = [
        f"version={version}",
        "",
        "Expected release artifacts:",
        *[f"- {path.as_posix()}" for path in expected],
        "",
        "Missing release artifacts:",
        *([f"- {path}" for path in missing] if missing else ["- (none)"]),
        "",
        "Unexpected target jars:",
        *([f"- {path}" for path in unexpected] if unexpected else ["- (none)"]),
        "",
    ]
    write_text(args.output, "\n".join(manifest_lines))

    files_output = "\n".join(path.as_posix() for path in expected)
    append_output("files", files_output, args.github_output)
    append_output("artifact_manifest", str(args.output), args.github_output)
    append_output("missing_count", str(len(missing)), args.github_output)
    append_output("unexpected_count", str(len(unexpected)), args.github_output)

    print(
        "audit:artifact_manifest "
        f"version={version} expected={len(expected)} missing={len(missing)} unexpected={len(unexpected)} output={args.output}"
    )
    if missing or (unexpected and args.strict):
        if missing:
            print(f"::error::Missing release artifacts: {', '.join(missing)}", file=sys.stderr)
        if unexpected and args.strict:
            print(f"::error::Unexpected target jars: {', '.join(unexpected)}", file=sys.stderr)
        return 1
    return 0


def snapshot_publication_result(
    *,
    version: str,
    published: str,
    latest: str,
    last_updated: str,
    source: str,
    error: str = "",
    versions: list[str] | None = None,
) -> dict[str, Any]:
    """Return a stable snapshot-publication payload for workflow consumers."""
    return {
        "version": version,
        "published": published,
        "latest": latest,
        "lastUpdated": last_updated,
        "source": source,
        "error": error,
        "versions": versions or [],
    }


def emit_snapshot_publication_outputs(result: dict[str, Any], github_output: str | None) -> None:
    """Expose snapshot-publication fields through the GitHub Actions output contract."""
    append_output("snapshot_publication", str(result["published"]), github_output)
    append_output("snapshot_publication_latest", str(result["latest"]), github_output)
    append_output("snapshot_publication_last_updated", str(result["lastUpdated"]), github_output)
    append_output("snapshot_publication_source", str(result["source"]), github_output)
    append_output("snapshot_publication_error", str(result["error"]), github_output)


def load_snapshot_metadata(args: argparse.Namespace) -> tuple[str, str]:
    """Load snapshot metadata XML from a local fixture or the live snapshot repository."""
    if args.metadata_file:
        source = args.metadata_file.resolve().as_uri()
        return args.metadata_file.read_text(encoding="utf-8"), source

    request = urllib.request.Request(
        args.metadata_url,
        headers={
            "Accept": "application/xml",
            "User-Agent": "ta4j-release-automation",
        },
    )
    with urllib.request.urlopen(request, timeout=args.timeout_seconds) as response:
        return response.read().decode("utf-8"), args.metadata_url


def parse_snapshot_metadata(metadata_text: str) -> tuple[str, str, list[str]]:
    """Extract latest version, timestamp, and published versions from metadata XML."""
    root = ET.fromstring(metadata_text)
    versioning = root.find("versioning")
    if versioning is None:
        raise ValueError("snapshot metadata is missing the <versioning> section")

    latest = (versioning.findtext("latest") or "").strip()
    last_updated = (versioning.findtext("lastUpdated") or "").strip()
    versions_parent = versioning.find("versions")
    versions = []
    if versions_parent is not None:
        versions = [
            (node.text or "").strip()
            for node in versions_parent.findall("version")
            if (node.text or "").strip()
        ]
    return latest, last_updated, versions


def command_snapshot_publication(args: argparse.Namespace) -> int:
    """Resolve whether the requested ta4j snapshot version is currently published."""
    version = args.version.strip()
    if not version:
        print("::error::--version is required", file=sys.stderr)
        return 1

    if not version.endswith("-SNAPSHOT"):
        result = snapshot_publication_result(
            version=version,
            published="n/a",
            latest="",
            last_updated="",
            source=args.metadata_file.resolve().as_uri() if args.metadata_file else args.metadata_url,
        )
        write_json(args.output, result)
        emit_snapshot_publication_outputs(result, args.github_output)
        print(f"audit:snapshot_publication version={version} published=n/a output={args.output}")
        return 0

    try:
        metadata_text, source = load_snapshot_metadata(args)
        latest, last_updated, versions = parse_snapshot_metadata(metadata_text)
    except Exception as exc:
        result = snapshot_publication_result(
            version=version,
            published="unknown",
            latest="",
            last_updated="",
            source=args.metadata_file.resolve().as_uri() if args.metadata_file else args.metadata_url,
            error=str(exc),
        )
        write_json(args.output, result)
        emit_snapshot_publication_outputs(result, args.github_output)
        print(f"::warning::Unable to verify snapshot publication for {version}: {exc}")
        print(f"audit:snapshot_publication version={version} published=unknown output={args.output}")
        return 0

    published = "true" if version in set(versions) else "false"
    result = snapshot_publication_result(
        version=version,
        published=published,
        latest=latest,
        last_updated=last_updated,
        source=source,
        versions=versions,
    )
    write_json(args.output, result)
    emit_snapshot_publication_outputs(result, args.github_output)
    print(
        "audit:snapshot_publication "
        f"version={version} published={published} latest={latest or 'unknown'} "
        f"last_updated={last_updated or 'unknown'} output={args.output}"
    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    catalog = subparsers.add_parser("catalog-preflight")
    catalog.add_argument("--model", required=True)
    catalog.add_argument("--catalog-url", default="https://models.github.ai/catalog/models")
    catalog.add_argument("--catalog-file")
    catalog.add_argument("--timeout-seconds", type=int, default=30)
    catalog.add_argument("--output", type=pathlib.Path, default=pathlib.Path("release-ai-model.json"))
    catalog.set_defaults(func=command_catalog_preflight)

    dossier = subparsers.add_parser("build-dossier")
    dossier.add_argument("--last-tag", required=True)
    dossier.add_argument("--current-version", required=True)
    dossier.add_argument("--pom-base", required=True)
    dossier.add_argument("--max-diff-chars", type=int, default=600_000)
    dossier.add_argument("--output", type=pathlib.Path, default=pathlib.Path("release-dossier.md"))
    dossier.add_argument("--audit-output", type=pathlib.Path, default=pathlib.Path("release-audit.json"))
    dossier.set_defaults(func=command_build_dossier)

    request = subparsers.add_parser("build-ai-request")
    request.add_argument("--model", required=True)
    request.add_argument("--dossier", type=pathlib.Path, default=pathlib.Path("release-dossier.md"))
    request.add_argument("--semver-rules", type=pathlib.Path, default=pathlib.Path(".github/workflows/semver-rules-override.txt"))
    request.add_argument("--max-dossier-chars", type=int, default=900_000)
    request.add_argument("--output", type=pathlib.Path, default=pathlib.Path("request.json"))
    request.set_defaults(func=command_build_ai_request)

    parse = subparsers.add_parser("parse-decision")
    parse.add_argument("--raw-file", type=pathlib.Path, default=pathlib.Path("ai-content.txt"))
    parse.add_argument("--output", type=pathlib.Path, default=pathlib.Path("release-decision.json"))
    parse.add_argument("--github-output")
    parse.set_defaults(func=command_parse_decision)

    javadoc = subparsers.add_parser("javadoc-warnings")
    javadoc.add_argument("--baseline", type=pathlib.Path, default=pathlib.Path("scripts/release/javadoc-warning-baseline.txt"))
    javadoc.add_argument("--output", type=pathlib.Path, default=pathlib.Path("javadoc-warnings.txt"))
    javadoc.add_argument("--github-output")
    javadoc.add_argument("--fail-on-new", action="store_true")
    javadoc.add_argument("logs", type=pathlib.Path, nargs="*")
    javadoc.set_defaults(func=command_javadoc_warnings)

    manifest = subparsers.add_parser("artifact-manifest")
    manifest.add_argument("--version", required=True)
    manifest.add_argument("--output", type=pathlib.Path, default=pathlib.Path("artifact-manifest.txt"))
    manifest.add_argument("--github-output")
    manifest.add_argument("--strict", action="store_true")
    manifest.set_defaults(func=command_artifact_manifest)

    snapshot = subparsers.add_parser("snapshot-publication")
    snapshot.add_argument("--version", required=True)
    snapshot.add_argument("--metadata-url", default=SNAPSHOT_METADATA_URL)
    snapshot.add_argument("--metadata-file", type=pathlib.Path)
    snapshot.add_argument("--timeout-seconds", type=int, default=30)
    snapshot.add_argument("--output", type=pathlib.Path, default=pathlib.Path("snapshot-publication.json"))
    snapshot.add_argument("--github-output")
    snapshot.set_defaults(func=command_snapshot_publication)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        return int(args.func(args))
    except Exception as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
