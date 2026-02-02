# PRD: Automated Release Process (Ta4j)

Status: Draft
Owner: Maintainers
Last updated: 2026-01-16

## Problem Statement
Ta4j's current release process is functional but fragile. Tags can become unreachable from `master`, release PRs can be merged in a way that breaks tag reachability, and the scheduler can make decisions that don't align with actual binary changes. The workflow is also split across multiple steps without strong guardrails or automated health checks. This leads to drift, manual cleanup, and higher cognitive load for maintainers.

We need an automated release system that is robust, adaptive, and requires minimal hands-on maintenance while preserving governance (required review/approval) and ensuring release tags always reflect what is in `master`.

## Goals
- Tags are always reachable from `master`.
- Releases are reproducible from a tag built from a commit already in `master`.
- Minimal manual steps for maintainers beyond review/approval.
- Strong, automated guardrails to detect drift (tags, versions, PRs).
- Clear and auditable decision trail in Discussions.

## Non-goals
- Removing required PR review/approval.
- Automatically merging PRs without human review.
- Replacing the changelog-driven release notes process.
- Changing Maven Central publishing mechanics.

## Stakeholders
- Maintainers (primary)
- Release scheduler operators
- Contributors who prepare release notes
- Users relying on Maven Central artifacts

## Current State (A)
- Scheduler (`release-scheduler.yml`) uses AI to decide bump and dispatches `release.yml`.
- `release.yml` creates the release commit, tags it, deploys, then opens a PR (or direct-pushes if enabled).
- Tag is pushed before PR merge; reachability depends on a merge commit.
- No automated health check for tag reachability or stale release PRs.

## Gaps (A -> B)
- Tag reachability can be broken if PR is delayed or squash-merged.
- Tags are created before the release commits land on `master`.
- Scheduler can contradict binary-change gates in its reasoning.
- No scheduled audit for drift (tags, snapshot version, stale PRs).
- Release PR base branch is not always aligned to default branch.

## Target State (B)
- Release tags are created only after the release commit is on `master`.
- Release workflow is split into "prepare" and "publish" phases.
- Automated health checks detect drift and post to Discussions.
- Scheduler decisions are deterministic when binary change count is zero.

---

## Proposed Solution

### Phase 1: Guardrails and Observability (Low Risk)

1) **Release Health Workflow**
- Add `.github/workflows/release-health.yml` (scheduled daily + manual dispatch).
- Checks:
  - Latest tag reachable from `master` vs latest tag overall.
  - `pom.xml` snapshot version on `master` is greater than the latest tag.
  - Open release PRs older than N days.
  - Missing `release/<version>.md` for the latest tag.
- Posts summary to the Release Scheduler discussion.
- Fails the run if it detects drift.

2) **Scheduler Determinism**
- Add binary-change count to the AI prompt and instruct: if count == 0, `should_release=false`.
- Include explicit gate reasons in the summary.

3) **Default Branch Handling**
- Normalize workflow base branch using `github.event.repository.default_branch`.

### Phase 2: Two-Phase Release (Robust)

Split the current `release.yml` into two workflows:

**A) Prepare Release (`prepare-release.yml`)**
- Triggered by the scheduler or manual dispatch.
- Steps:
  1. Validate secrets and compute `releaseVersion` and `nextVersion`.
  2. Run `scripts/prepare-release.sh`.
  3. Commit release version and release notes.
  4. Commit next snapshot version.
  5. Open PR `release/<version> -> master` with label `release` and a metadata block:
     ```
     <!-- release-meta
     releaseVersion: 0.22.2
     nextVersion: 0.22.3-SNAPSHOT
     releaseCommit: <sha>
     -->
     ```
- No tags, no deploys.
- Requires maintainer review and a merge commit.

**B) Publish Release (`publish-release.yml`)**
- Triggered on `pull_request` closed (merged) for PRs labeled `release`.
- Steps:
  1. Read PR body metadata to get `releaseVersion` and `releaseCommit`.
  2. Verify merge method is a merge commit (not squash).
  3. Create annotated tag on `releaseCommit`.
  4. Check out `releaseCommit` and deploy to Maven Central.
  5. Push the tag to trigger `github-release.yml`.
  6. Post summary to the Maven Central Releases discussion.

This ensures the tag is always an ancestor of `master` and removes reliance on human merge discipline.

### Phase 3: Policy Hardening
- Enforce merge-commit-only for release PRs via repo settings.
- Require at least one maintainer approval.
- Keep `RELEASE_DIRECT_PUSH=true` as an emergency override (optional).

---

## Functional Requirements
- Scheduler only releases when binary-impacting changes exist.
- Release PR must contain both release and next snapshot commits.
- Tags must be created from commits already on `master`.
- Release health checks are automated and visible to maintainers.
- Discussion summaries include mode (dry-run/production) and timestamp.

## Non-functional Requirements
- Zero manual Git commands required in the happy path (beyond changelog update and PR approval).
- Tag reachability must be guaranteed.
- Workflows must be idempotent and safe to re-run.

---

## Implementation Details (Concrete)

### New/Modified Workflows

1) **release-health.yml (new)**
- Schedule: daily at 09:00 UTC + manual dispatch.
- Steps:
  - `git fetch --tags`.
  - `last_reachable_tag=$(git describe --tags --abbrev=0 --first-parent origin/master ...)`.
  - `latest_tag=$(git tag --sort=-creatordate | head -1)`.
  - `git merge-base --is-ancestor $latest_tag origin/master` to detect drift.
  - Parse `pom.xml` for version and ensure it is a `-SNAPSHOT` greater than `latest_tag`.
  - Use `gh` or `github-script` to check for open PRs labeled `release` older than N days.
  - Post summary to the Release Scheduler discussion.

2) **prepare-release.yml (new)**
- Based on current `release.yml` steps up to "Commit next snapshot".
- Outputs a PR with metadata block.
- Labels PR with `release`.
- Uses `github.event.repository.default_branch` as base.

3) **publish-release.yml (new)**
- Triggered on `pull_request` closed (merged) where label includes `release`.
- Uses `github-script` to parse PR body metadata.
- Verifies merge commit (ensure PR merge method is merge commit or check that release commit is ancestor of merge commit).
- Creates tag on release commit and deploys.
- Posts discussion summary.

4) **release-scheduler.yml (update)**
- Dispatches `prepare-release.yml` instead of `release.yml`.
- Adds binary change count to prompt and hard constraints in prompt.

5) **github-release.yml (unchanged)**
- Remains triggered by tag push.

### Metadata Block
- Stored in PR body to avoid external state.
- Parsed by `publish-release.yml` to find release version and commit.

### Direct Push Override
- `RELEASE_DIRECT_PUSH=true` bypasses PR creation and publishes directly.
- Intended for emergencies only.

---

## Rollout Plan

1. Implement Phase 1 and deploy.
2. Validate health workflow and scheduler decisions.
3. Implement Phase 2 in a branch and test with dry-run.
4. Switch scheduler to dispatch `prepare-release.yml`.
5. Announce new process to maintainers.

---

## Risks and Mitigations

- **Risk:** PR body metadata missing or malformed.
  - **Mitigation:** Validate in publish workflow; fail fast and post error.
- **Risk:** Merge method not merge commit.
  - **Mitigation:** Enforce merge-commit-only for release PRs.
- **Risk:** Tag push after deploy fails.
  - **Mitigation:** Fail before deploy if tag exists; retryable workflow.

---

## Success Metrics
- 100% of release tags reachable from `master`.
- No manual tag-reachability fixes needed.
- Release PRs merged with merge commits in >95% of releases.
- Release health checks are green 30 days after rollout.

---

## Open Questions
- Should the scheduler open the release PR automatically or only suggest it?
- Should AI be advisory only if changelog is present?
- Should release PRs include a required checklist?

