#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ROOT_DIR

python3 <<'PY'
import os
import re
import sys
from pathlib import Path

root = Path(os.environ["ROOT_DIR"])
wiki_root = root.parent / "ta4j-wiki"
wiki_available = wiki_root.exists()

docs_for_link_checks = [root / "README.md", root / "ta4j-examples" / "README.md"]
if wiki_available:
    docs_for_link_checks.extend(sorted(wiki_root.glob("*.md")))

docs_for_command_checks = [
    root / "README.md",
    root / "ta4j-examples" / "README.md",
]
if wiki_available:
    docs_for_command_checks.append(wiki_root / "Usage-examples.md")

todo_forbidden_docs = [
    root / "README.md",
    root / "ta4j-examples" / "README.md",
]
if wiki_available:
    todo_forbidden_docs.extend(
        [
            wiki_root / "Home.md",
            wiki_root / "Getting-started.md",
            wiki_root / "Backtesting.md",
            wiki_root / "Live-trading.md",
        ]
    )

link_pattern = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
main_class_pattern = re.compile(r"-Dexec\.mainClass=([A-Za-z0-9_.]+)")

errors = []


def normalize_target(raw_target: str) -> str:
    target = raw_target.strip()
    if " " in target and target.startswith(("http://", "https://")):
        target = target.split(" ", 1)[0]
    if "#" in target:
        target = target.split("#", 1)[0]
    if "?" in target:
        target = target.split("?", 1)[0]
    return target.strip()


def check_markdown_links(path: Path) -> None:
    if not path.exists():
        errors.append(f"Missing documentation file for link check: {path}")
        return
    content = path.read_text(encoding="utf-8")
    for match in link_pattern.finditer(content):
        raw_target = match.group(1).strip()
        target = normalize_target(raw_target)
        if not target:
            continue
        if target.startswith(("http://", "https://", "mailto:")):
            continue
        if target.startswith("#"):
            continue
        candidate = (path.parent / target).resolve()
        if not candidate.exists():
            errors.append(f"{path}: broken relative link target '{raw_target}'")


def check_exec_main_classes(path: Path) -> None:
    if not path.exists():
        errors.append(f"Missing documentation file for command check: {path}")
        return
    content = path.read_text(encoding="utf-8")
    for main_class in main_class_pattern.findall(content):
        class_file = (
            root
            / "ta4j-examples"
            / "src"
            / "main"
            / "java"
            / Path(*main_class.split(".")).with_suffix(".java")
        )
        if not class_file.exists():
            errors.append(
                f"{path}: -Dexec.mainClass points to missing class '{main_class}' ({class_file})"
            )


def check_for_todo_markers(path: Path) -> None:
    if not path.exists():
        errors.append(f"Missing documentation file for TODO check: {path}")
        return
    content = path.read_text(encoding="utf-8")
    if "TODO" in content:
        errors.append(f"{path}: contains TODO marker in user-facing docs")


for doc_path in docs_for_link_checks:
    check_markdown_links(doc_path)

for doc_path in docs_for_command_checks:
    check_exec_main_classes(doc_path)

for doc_path in todo_forbidden_docs:
    check_for_todo_markers(doc_path)

if errors:
    print("docs-integrity:fail")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

if not wiki_available:
    print("docs-integrity:note ta4j-wiki checkout not found; skipping wiki checks")
print("docs-integrity:pass")
PY
