# Build Errors on origin/master - PRD/TODO

## Goal
- Validate full build on origin/master and fix any build/test failures.

## Scope
- Run `scripts/run-full-build-quiet.sh`.
- If failures occur, identify root cause and implement minimal fixes.
- Add/adjust tests if needed to prevent regressions.

## Decisions
- Fixed macOS mktemp template issue in `scripts/run-full-build-quiet.sh`.

## Checklist
- [x] Run full build script and capture results
- [x] Triage failures and determine root cause
- [x] Implement fix(es) on bugfix branch
- [x] Update/add tests if required
- [x] Re-run full build to confirm green
- [x] Update CHANGELOG.md if code changes
- [x] Move this PRD to `docs/archive/` once done
