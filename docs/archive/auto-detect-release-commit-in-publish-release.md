# Auto-detect release commit in publish-release.yml

## Goal
- Allow workflow_dispatch runs to omit `releaseCommit` by auto-detecting the release commit on the default branch.

## Non-goals
- Auto-detecting `releaseVersion`.
- Changing the PR-merge trigger behavior.

## Implementation notes
- When `releaseCommit` is blank on workflow_dispatch, locate the commit that added `release/<releaseVersion>.md` on `origin/<default_branch>`.
- Fail with a clear error when the default branch is unknown or the release note file cannot be found on the default branch.

## Checklist
- [x] Make `releaseCommit` input optional for workflow_dispatch.
- [x] Add auto-detect logic to the metadata extraction step.
- [x] Keep existing ancestry/tag checks intact.
- [x] Decide whether any docs need updating (no additional docs updates needed).
