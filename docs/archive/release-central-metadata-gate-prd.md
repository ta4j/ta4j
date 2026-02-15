# Release Central Metadata Gate PRD

## Context
- Release `0.22.2` failed in publish due to missing `<developers>` metadata.
- We need to fail earlier during prepare-release before creating/pushing a release commit.
- Constraint: avoid adding Python-based XML parsing; prefer bash + Maven.

## Goals
- Add a pre-publish metadata gate in release preparation.
- Validate Central-required metadata for all published modules.
- Keep implementation dependency-light (bash + Maven only).

## Non-Goals
- Do not change Maven Central publication mode (`autoPublish=true`).
- Do not replace existing release note/version logic in `prepare-release.sh`.

## Requirements
- Check effective metadata for:
  - `ta4j-parent` (root)
  - `ta4j-core`
  - `ta4j-examples`
- Required fields (non-empty):
  - `project.name`
  - `project.description`
  - `project.url`
  - `project.licenses[0].name`
  - `project.scm.connection`
  - `project.scm.url`
  - `project.developers[0].id`
  - `project.developers[0].name`
- Gate must fail workflow with actionable error messages.
- Gate must run before `Release X.Y.Z` commit creation.

## Design
- Add `scripts/validate-central-metadata.sh`.
- Script uses `mvn -q help:evaluate -DforceStdout` to evaluate each expression per module.
- Missing or invalid expression values are accumulated and printed as `::error::`.
- Exit non-zero if any required metadata is missing.
- Wire script into `prepare-release.yml` after `versions:set`/`versions:commit` for release, before release commit.

## Test Plan
- Add shell tests in `scripts/tests/test_validate_central_metadata.sh`:
  - passes for complete metadata
  - fails when parent POM lacks developers
  - fails when developer id/name missing
- Execute targeted shell tests.
- Run full mandatory build script before completion.

## Checklist
- [x] Add validator script
- [x] Add/adjust workflow step
- [x] Add shell tests
- [x] Update changelog
- [x] Run targeted tests
- [x] Run full build script
- [x] Archive this PRD to `docs/archive/`
