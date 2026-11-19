#!/usr/bin/env python3
"""Deterministic release eligibility, version planning and bounded run reporting."""
import argparse
from datetime import date, datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

import assessment


ANCHOR = date(2026, 6, 29)
RECOMMENDATIONS = {"release_patch", "release_minor", "defer", "insufficient_evidence", "unavailable"}


def git(*args):
    return subprocess.check_output(["git", *args], text=True, stderr=subprocess.PIPE).strip()


def output(**values):
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
            for key, value in values.items():
                value = str(value).lower() if isinstance(value, bool) else str(value)
                if "\n" in value or "\r" in value:
                    raise ValueError("Workflow output must be a single line")
                stream.write(f"{key}={value}\n")


def version_tuple(value):
    match = re.fullmatch(r"v?(0|[1-9]\d*)\.(0|[1-9]\d*)(?:\.(0|[1-9]\d*))?", value)
    if not match:
        raise ValueError("Unsupported release version")
    return tuple(int(part or 0) for part in match.groups())


def cadence(event, dry_run, run_date):
    if event not in ("schedule", "workflow_dispatch"):
        raise ValueError("Unsupported scheduler event")
    if dry_run not in ("true", "false", ""):
        raise ValueError("Invalid dry-run flag")
    days = (run_date - ANCHOR).days
    return {"anchorDate": ANCHOR.isoformat(), "runDate": run_date.isoformat(),
            "due": event == "workflow_dispatch" or (days >= 0 and days % 14 == 0),
            "dryRun": dry_run != "false" if event == "workflow_dispatch" else False}


def context(directory, event, dry_run, mode, run_date):
    root = Path(directory)
    state = assessment.read(root / "repository-state.json")
    source = git("rev-parse", "HEAD")
    if not re.fullmatch(r"[0-9a-f]{40}", source) or state["sourceSha"] != source:
        raise ValueError("Repository state does not identify the checkout")
    if mode not in ("full", "skip"):
        raise ValueError("Unsupported assessment mode")
    schedule = cadence(event, dry_run, run_date)
    blockers = []
    if state["defaultSha"] != source or os.environ.get("GITHUB_REF", "refs/heads/" + state["defaultBranch"]) != "refs/heads/" + state["defaultBranch"]:
        blockers.append("source_is_not_current_default_branch")
    if not schedule["due"]:
        blockers.append("outside_biweekly_window")
    if mode == "skip":
        blockers.append("assessment_disabled_by_input")
    tag_lines = subprocess.check_output(["bash", "scripts/resolve-release-tags.sh", source], text=True)
    tags = dict(line.split("=", 1) for line in tag_lines.splitlines() if "=" in line)
    last_tag = tags.get("last_reachable_tag", "none")
    if tags.get("latest_tag_reachable") == "false":
        blockers.append("latest_release_tag_not_reachable")
    if last_tag == "none":
        blockers.append("no_release_baseline")
    current_version = (ET.parse("pom.xml").getroot().findtext("{*}version") or "").strip()
    if not current_version.endswith("-SNAPSHOT"):
        blockers.append("default_branch_is_not_snapshot")
    try:
        pom_base = version_tuple(current_version.removesuffix("-SNAPSHOT"))
        if last_tag != "none":
            baseline = version_tuple(last_tag)
            if pom_base < baseline:
                blockers.append("pom_version_behind_last_release")
            if pom_base[0] > baseline[0]:
                blockers.append("major_release_requires_human_review")
    except ValueError:
        blockers.append("unsupported_version")
    ci = state.get("ci")
    if not ci or ci.get("sourceSha") != source or ci.get("status") != "completed" or ci.get("conclusion") != "success":
        blockers.append("candidate_ci_not_successful")
    refs = state.get("refs", {})
    if any(ref.startswith("refs/heads/release-freeze/") for ref in refs):
        blockers.append("release_freeze_active")
    if state.get("openReleasePullRequests"):
        blockers.append("release_pull_request_open")
    for ref, sha in refs.items():
        if not ref.startswith(("refs/heads/release-pending/", "refs/heads/release-publishing/")):
            continue
        release_version = ref.rsplit("/", 1)[1]
        published = refs.get("refs/heads/release-published/" + release_version)
        try:
            complete = bool(published) and git("rev-parse", f"refs/tags/{release_version}^{{}}") == published
            if ref.startswith("refs/heads/release-pending/"):
                complete = complete and git("rev-parse", sha + "^") == published
            else:
                complete = complete and sha == published
        except subprocess.CalledProcessError:
            complete = False
        if not complete:
            blockers.append("previous_release_incomplete")
            break
    # Count both sides of a move so a production deletion cannot disappear behind its test destination.
    paths = git("diff", "--no-renames", "--name-only", "-z", last_tag + "..HEAD").split("\0") if last_tag != "none" else []
    paths = [path for path in paths if path]
    binary_count = sum(bool(re.search(r"(^|/)pom\.xml$|(^|/)src/main/", path)) for path in paths)
    if binary_count == 0:
        blockers.append("no_binary_changes")
    last_date = subprocess.check_output(
        ["bash", "scripts/release/release_helpers.sh", "last-release-date", "--tag", last_tag], text=True
    ).strip() if last_tag != "none" else "none"
    policy = Path(".github/workflows/semver-rules-override.txt").read_text(encoding="utf-8")
    policy += ("\nRelease only when the accumulated production changes have concrete user value and sufficient supporting evidence. "
               "A cadence window is an opportunity, not an obligation. Distinguish a justified deferral from insufficient evidence. "
               "Infrastructure and changelog volume alone never justify a binary release. "
               "Missing or truncated evidence must be investigated or reported as missing_evidence. "
               "Deterministic preconditions cannot be overridden by the model. "
               "Major releases are disabled; breaking changes at or above 1.0 require human review.\n")
    last_tag_sha = git("rev-parse", f"refs/tags/{last_tag}^{{commit}}") if last_tag != "none" else ""
    value = {"schemaVersion": 2, "sourceSha": source, "lastTag": last_tag, "lastTagSha": last_tag_sha, "lastReleaseDate": last_date,
             "pomVersion": current_version, "policy": policy, "cadence": schedule, "mode": mode,
             "metrics": {"binaryChangedFiles": binary_count, "changedFiles": len(paths)},
             "preconditions": {"eligible": not blockers, "blockers": blockers},
             "ci": ci, "openReleasePullRequests": state.get("openReleasePullRequests", [])}
    assessment.write(root / "context.json", value)
    output(eligible=not blockers, source_sha=source, last_tag=last_tag, pom_version=current_version,
           dry_run=schedule["dryRun"], due=schedule["due"])
    # Counts and fixed reason codes explain the gate without printing repository/model content.
    print(f"audit:eligibility eligible={str(not blockers).lower()} binary_files={binary_count} blockers={','.join(blockers) or 'none'}")


def freeze(directory):
    root = Path(directory)
    value = assessment.read(root / "context.json")
    if git("rev-parse", "HEAD") != value["sourceSha"]:
        raise ValueError("Dossier checkout differs from eligible candidate")
    value["evidenceCoverage"] = assessment.read(root / "release-audit.json").get("diff_coverage", {})
    assessment.write(root / "context.json", value)
    assessment.package(directory, value["sourceSha"])


def plan(directory, recommendation, assessor_status):
    root = Path(directory)
    value = assessment.read(root / "context.json")
    assessment.verify(directory, value["sourceSha"])
    if git("rev-parse", "HEAD") != value["sourceSha"]:
        raise ValueError("Plan checkout differs from assessed candidate")
    if assessor_status != "success":
        recommendation = "unavailable"
    if recommendation not in RECOMMENDATIONS:
        raise ValueError("Invalid assessment recommendation")
    should_release = recommendation in ("release_patch", "release_minor")
    version = ""
    if should_release:
        if value["preconditions"] != {"eligible": True, "blockers": []}:
            raise ValueError("Assessment cannot override deterministic blockers")
        base = version_tuple(value["pomVersion"].removesuffix("-SNAPSHOT"))
        last = version_tuple(value["lastTag"])
        if base < last:
            raise ValueError("POM version is behind the release baseline")
        major, minor, patch = last
        bumped = (major, minor + 1, 0) if recommendation == "release_minor" else (major, minor, patch + 1)
        planned = max(base, bumped)
        if planned[0] != last[0]:
            raise ValueError("Major releases require human review")
        if recommendation == "release_patch" and planned[:2] != last[:2]:
            raise ValueError("POM baseline requires a minor assessment")
        version = ".".join(map(str, planned))
        for tag in (version, "v" + version):
            if subprocess.run(["git", "show-ref", "--verify", "--quiet", "refs/tags/" + tag]).returncode == 0:
                raise ValueError("Planned release tag already exists")
    result = {"schemaVersion": 1, "sourceSha": value["sourceSha"], "recommendation": recommendation,
              "shouldRelease": should_release, "version": version, "dryRun": value["cadence"]["dryRun"]}
    assessment.write(root / "release-plan.json", result)
    output(should_release=should_release, version=version, recommendation=recommendation)
    print(f"audit:release_plan recommendation={recommendation} version={version or 'none'} dry_run={str(result['dryRun']).lower()}")


def summarize(directory, stages):
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)
    eligibility = stages.get("eligibility", {})
    values = eligibility.get("outputs", {})
    policy = stages.get("release_policy", {})
    recommendation = policy.get("outputs", {}).get("recommendation", "not_assessed")
    if recommendation not in RECOMMENDATIONS | {"not_assessed"}:
        recommendation = "unavailable"
    status = "failed" if any(stage.get("result") in ("failure", "cancelled") for stage in stages.values()) else recommendation
    context_file = root / "context.json"
    blockers = assessment.read(context_file)["preconditions"]["blockers"] if context_file.exists() else []
    # Producer-owned names identify the actual retained files even after a partial rerun.
    artifacts = {}
    for name, stage in stages.items():
        artifact = stage.get("outputs", {}).get("artifact_name", "")
        if artifact:
            artifacts[name] = artifact if re.fullmatch(r"[a-z0-9][a-z0-9-]{0,127}", artifact) else "unavailable"
    result = {"schemaVersion": 1, "status": status, "recommendation": recommendation,
              "sourceSha": values.get("source_sha", "unavailable"), "dryRun": values.get("dry_run", "unknown"),
              "version": policy.get("outputs", {}).get("version", ""), "blockers": blockers,
              "stages": {name: stage.get("result", "unknown") for name, stage in stages.items()}, "artifacts": artifacts}
    assessment.write(root / "scheduler-outcome.json", result)
    lines = ["## Release scheduler outcome", "", f"**{status}**", "",
             f"Candidate: `{result['sourceSha']}` · Version: `{result['version'] or 'none'}` · Dry run: `{result['dryRun']}`", "",
             "| Stage | Result | Retained artifact |", "|---|---|---|"]
    lines.extend(f"| {name.replace('_', ' ')} | {stage} | `{artifacts.get(name, 'none')}` |"
                 for name, stage in result["stages"].items())
    if blockers:
        lines += ["", "Eligibility: " + ", ".join(f"`{item}`" for item in blockers) + "."]
    lines += ["", "Assessment evidence and any missing information are in the release assessment summary.",
              "A dispatched preparation is not a published release. Preparation rechecks the assessed commit before taking a freeze.",
              "For a stale candidate or changed evidence, start a new scheduler run. For a preparation failure, inspect its freeze/publication state before retrying."]
    run_url = f"https://github.com/{os.environ.get('GITHUB_REPOSITORY', 'ta4j/ta4j')}/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}"
    lines += ["", f"[Workflow and artifacts]({run_url})"]
    (root / "scheduler-outcome.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("context", "freeze", "plan", "summarize"))
    parser.add_argument("--directory", default="release-evidence")
    args = parser.parse_args()
    if args.command == "context":
        context(args.directory, os.environ["GITHUB_EVENT_NAME"], os.environ.get("DRY_RUN_INPUT", ""),
                os.environ.get("AI_MODE", "full"), datetime.now(timezone.utc).date())
    elif args.command == "freeze":
        freeze(args.directory)
    elif args.command == "plan":
        plan(args.directory, os.environ.get("RECOMMENDATION", "unavailable"), os.environ.get("ASSESSOR_STATUS", "failure"))
    else:
        summarize(args.directory, json.loads(os.environ["STAGE_RESULTS"]))


if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        raise SystemExit(f"Release scheduler validation failed: {error}")
    except (OSError, KeyError, TypeError, subprocess.CalledProcessError):
        # Avoid echoing command output, model text or API response bodies.
        raise SystemExit("Release scheduler input/state validation failed; inspect the named stage and structured artifacts.")
