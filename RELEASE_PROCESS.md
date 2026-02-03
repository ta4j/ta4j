# Ta4j Release Process

This guide explains how Ta4j releases move from documentation updates to Maven Central, which workflow does what, and why each step exists. It is written for maintainers who want a predictable, auditable release flow with clear Git history and tags.

The default branch is `master`. Release tags must be reachable from `master` for the scheduler to produce sane diffs and version bumps.

---

## Quick Start

### Production release (typical)
1. Update `CHANGELOG.md` under **Unreleased** and keep README version references current.
2. Trigger **Prepare Release** (manually or via the scheduler) with the target `releaseVersion`.
3. Review the generated release PR and merge using a merge commit.
4. **Publish Release** runs automatically on PR merge and deploys/tag/releases.

### Dry-run (validation only)
1. Follow the same prep steps as production.
2. Run **Prepare Release** with `dryRun=true` to validate version detection and release notes generation.
3. (Optional) Run **Publish Release** with `dryRun=true` and explicit `releaseVersion` (and optional `releaseCommit`) to validate deployment prechecks without tagging or deploying.

---

## Key Concepts (What Changes in Git)

- **Release commit**: `pom.xml` version set to the release version and `release/<version>.md` added.
- **Tag**: annotated Git tag (e.g., `0.22.2`) created by the publish workflow and pointing at the release commit. Tag push triggers `github-release.yml`.
- **Snapshot commit**: `pom.xml` bumped to the next `-SNAPSHOT` version.
- **Release PR (default mode)**: contains the release commit + snapshot commit and merges into `master` with a merge commit.
- **Direct push mode**: skips the PR and pushes both commits directly to `master`.

Tags are created only after the release commit is on `master`, so tag reachability is guaranteed.

---

## System Overview (Who Does What)

1. **prepare-release.sh**
   - **What**: Updates CHANGELOG/README and generates `release/<version>.md`.
   - **Why**: Ensures release notes are accurate before the workflows run.

2. **Release Scheduler (`release-scheduler.yml`)**
   - **What**: Looks at binary-impacting changes + Unreleased changelog (summarized and sanitized), asks GitHub Models for a SemVer bump, and decides whether to release.
   - **Why**: Automates “should we release?” decisions and reduces manual churn.

3. **Prepare Release Workflow (`prepare-release.yml`)**
   - **What**: Generates release notes, bumps the release and next snapshot versions, and opens the release PR (or direct-pushes in emergencies).
   - **Why**: Ensures the release commit lands on `master` before tags and deployment.

4. **Publish Release Workflow (`publish-release.yml`)**
   - **What**: Tags the release commit, deploys to Maven Central, and posts release summaries after the PR is merged.
   - **Why**: Guarantees tags are created from commits already on `master`.

5. **Release Health Workflow (`release-health.yml`)**
   - **What**: Audits tag reachability, snapshot drift, stale release PRs, and missing release notes.
   - **Why**: Detects drift and keeps release hygiene visible to maintainers.

6. **GitHub Release Workflow (`github-release.yml`)**
   - **What**: Builds and publishes the GitHub Release when a tag is pushed.
   - **Why**: Ensures the GitHub Release matches the tagged artifacts.

7. **Snapshot Workflow (`snapshot.yml`)**
   - **What**: Publishes snapshots on every push to `master`.
   - **Why**: Provides a current snapshot build for users and CI consumers.

---

## Step-by-Step (What Happens and Why)

### Release Scheduler (`release-scheduler.yml`)
1. **Find last tag on `master`**
   - **What**: Finds the most recent tag reachable from the default branch.
   - **Why**: Ensures diffs are based on what was actually released on `master`.
2. **Collect binary-impacting changes + changelog**
   - **What**: Looks for changes in `pom.xml` or `src/main/**`, plus Unreleased notes. The change list is sanitized (URLs/tokens redacted), summarized by path buckets, and only includes sample paths when the list is small; discussion output is truncated for safety. Changelog text is filtered to headings/bullets and truncated before sending to the model.
   - **Why**: Avoids releasing for workflow-only or doc-only changes while keeping the AI request and discussion payloads bounded.
3. **AI decision (SemVer bump)**
   - **What**: Calls GitHub Models to choose patch/minor/major using the summarized binary-change prompt and filtered changelog highlights.
   - **Why**: Consistent semantics without manual guessing, without sending an oversized payload.
4. **Compute version and gate**
   - **What**: Calculates the next version, checks for tag collisions.
   - **Why**: Prevents duplicate or backward releases.
5. **Major approval (if needed)**
   - **What**: Waits for approval in the `major-release` environment.
   - **Why**: Human sign-off for breaking changes.
6. **Dispatch `prepare-release.yml`**
   - **What**: Starts the prepare workflow with the chosen version.
   - **Why**: Keeps release prep centralized and ready for review.
7. **Post-discussion summary**
   - **What**: Writes a decision summary to the Release Scheduler discussion.
   - **Why**: Auditable history and notifications for maintainers.

**Schedule gate:** scheduled runs only proceed when `RELEASE_SCHEDULER_ENABLED=true`. Unset or empty disables the schedule without affecting manual dispatch.

### Prepare Release (`prepare-release.yml`)
1. **Validate inputs and compute versions**
   - **Why**: Fail fast on invalid or regressive versions.
2. **Generate release notes**
   - **Why**: Ensures `release/<version>.md` exists and is current.
3. **Commit release version**
   - **Why**: Locks the source version for the release.
4. **Commit next snapshot**
   - **Why**: Ensures ongoing development is versioned correctly.
5. **Update `master`**
   - **Default (PR mode)**: create a PR (`release/<version>` -> `master`) with a release metadata block and `release` label.
     - **Why**: Keeps a required review gate and preserves merge history.
   - **Direct push (`RELEASE_DIRECT_PUSH=true`)**: push both commits directly to `master`, then dispatch Publish Release.
     - **Why**: Skips PR friction when org permissions allow it.

**Merge note:** merge the release PR using a merge commit (no squash). Required checks and maintainer approval must pass.

### Publish Release (`publish-release.yml`)
1. **Read metadata**
   - **What**: Parses the PR metadata block or workflow inputs for `releaseVersion` and `releaseCommit`. For workflow_dispatch, `releaseCommit` can be blank and is auto-detected from `release/<version>.md` on the default branch.
2. **Verify merge discipline**
   - **Why**: Ensures the release commit is on `master` and was merged via a merge commit.
3. **Create annotated tag**
   - **Why**: Tags are the source of truth for releases and GitHub Release workflow triggers.
4. **Build and deploy to Maven Central** (skipped on `dryRun=true`)
   - **Why**: Publish signed artifacts for consumption.
5. **Push tag**
   - **Why**: Triggers `github-release.yml`.
6. **Post-discussion summary**
   - **Why**: Provides an audit trail and notifications.

### Release Health (`release-health.yml`)
- Scheduled daily (plus manual dispatch) to verify tag reachability, snapshot drift, stale release PRs, and missing release notes.
- Posts results to the Release Scheduler discussion and fails if drift is detected.

### GitHub Release (`github-release.yml`)
- Triggered by tag push; builds artifacts and publishes the GitHub Release using `release/<version>.md`.

### Snapshot (`snapshot.yml`)
- Runs on every push to `master` and publishes snapshot artifacts.

---

## Example Scenarios

### Scenario A: Production release via scheduler (PR mode)
**Context:** You prepared docs for `0.22.2` and pushed to `master`.
1. Scheduler runs, detects binary changes, chooses `0.22.2`.
2. `prepare-release.yml` runs and opens PR `release/0.22.2 -> master`.
3. Maintainer merges the PR with a merge commit.
4. `publish-release.yml` runs, tags `0.22.2`, deploys, and pushes the tag.
5. Tag push triggers `github-release.yml` to create the GitHub Release.
6. `master` already contains the next snapshot commit from the PR.

### Scenario B: Dry-run (validation only)
**Context:** You want to verify the pipeline without publishing.
1. Run `prepare-release.yml` with `dryRun=true`.
2. Version checks and release note generation run.
3. No commits, PRs, tags, or deploys occur.
4. (Optional) Run `publish-release.yml` with `dryRun=true` and explicit `releaseVersion` (and optional `releaseCommit`) to validate deploy prechecks without tagging or deploying.

### Scenario C: Production release with direct push
**Context:** Org permissions allow direct pushes; `RELEASE_DIRECT_PUSH=true`.
1. `prepare-release.yml` runs and pushes the release + snapshot commits directly to `master`.
2. `prepare-release.yml` dispatches `publish-release.yml`.
3. `publish-release.yml` tags the release commit, deploys artifacts, and pushes the tag.
4. Tag push triggers `github-release.yml` to create the GitHub Release.

---

## Verification Checklist

1. **Actions run**: `prepare-release.yml` and `publish-release.yml` completed successfully.
2. **Tag**: `git tag | grep <version>` exists (not in dry-run).
3. **Tag reachability**: `git merge-base --is-ancestor <version> origin/master` returns true.
4. **Maven Central**: artifacts appear in Central (may take 10-30 minutes).
5. **GitHub Release**: release exists with notes and artifacts.
6. **Snapshot version**: `pom.xml` on `master` is next `-SNAPSHOT` version.

---

## Required Resources

### Required Repository Secrets

| Secret Name | Used By | Purpose |
|------------|---------|---------|
| `MAVEN_CENTRAL_TOKEN_USER` | `publish-release.yml`, `snapshot.yml` | Maven Central authentication username |
| `MAVEN_CENTRAL_TOKEN_PASS` | `publish-release.yml`, `snapshot.yml` | Maven Central authentication password |
| `GPG_PRIVATE_KEY` | `publish-release.yml`, `snapshot.yml` | GPG private key for signing artifacts |
| `GPG_PASSPHRASE` | `publish-release.yml`, `snapshot.yml` | Passphrase for the GPG private key |
| `GH_MODELS_TOKEN` | `release-scheduler.yml` | GitHub Models API token |
| `GH_TA4J_REPO_TOKEN` | `prepare-release.yml`, `publish-release.yml`, `github-release.yml` | Classic PAT used for release pushes and GitHub Release creation; prepare-release falls back to `github.token` when the PAT is missing/insufficient (except when `RELEASE_DIRECT_PUSH=true`) |

### Optional Repository Secrets

| Secret Name | Used By | Purpose |
|------------|---------|---------|
| `MAVEN_MASTER_PASSPHRASE` | `publish-release.yml`, `snapshot.yml` | Optional Maven master password for `settings-security.xml` |

### Optional Repository Variables

| Variable Name | Used By | Purpose |
|---------------|---------|---------|
| `RELEASE_DIRECT_PUSH` | `prepare-release.yml` | When `true`, skip the release PR and push commits directly to `master` |
| `RELEASE_NOTIFY_USER` | `publish-release.yml`, `release-scheduler.yml`, `release-health.yml` | Optional GitHub username to @mention in discussion summaries (defaults to `TheCookieLab`) |
| `RELEASE_DISCUSSION_NUMBER` | `publish-release.yml` | Discussion number for release run summaries (defaults to 1415) |
| `RELEASE_SCHEDULER_DISCUSSION_NUMBER` | `release-scheduler.yml`, `release-health.yml` | Discussion number for scheduler summaries (defaults to 1414) |
| `RELEASE_SCHEDULER_ENABLED` | `release-scheduler.yml` | Set to `true` to allow scheduled runs; unset/empty/false disables |
| `RELEASE_AI_MODEL` | `release-scheduler.yml` | Override the GitHub Models API model (defaults to `openai/gpt-4.1-nano`) |
| `RELEASE_PR_STALE_DAYS` | `release-health.yml` | Days before a release PR is considered stale |

### Required GitHub Environment

For major releases, configure the protected environment:
- **Name**: `major-release`
- **Purpose**: manual approval for major bumps

### Required Repo Settings

- **Actions → Workflow permissions**: set to **Read and write** for dispatch/tag/PR.
- **Merge settings**: enforce merge commits for release PRs (no squash) so publish-release can validate ancestry.

---

## Responsibilities Summary

| Task | Contributor | GitHub Actions |
|------|-------------|----------------|
| Edit CHANGELOG.md | ✔ | ❌ |
| Update README version references | ✔ | ❌ |
| Generate release notes | ✔ (prepare script) | ❌ |
| Bump release version | ❌ | ✔ |
| Tag release | ❌ | ✔ (skipped on dryRun) |
| Deploy to Maven Central | ❌ | ✔ (skipped on dryRun) |
| Bump to next snapshot | ❌ | ✔ |
| Publish snapshots | ❌ | ✔ |

---

## Troubleshooting

### Release PR is waiting for merge
- Ensure required checks pass and a maintainer approval is recorded.
- Merge using a **merge commit** (not squash/rebase) so tags stay reachable.

### Tag exists error
- You may be re-running a release for an existing version.
- Use a new version or delete the tag if safe.

### Release notes missing
- Ensure `release/<version>.md` exists and is committed.

### Branch advanced during release
- Someone pushed to `master` during the release; re-run once the branch is stable.

### Dry-run still warns
- In `dryRun=true`, missing secrets are warnings only. This is expected.

---

## Notes on RC Builds

RCs are not currently required, but the workflows accept any Maven-valid version. If RCs are introduced, use versions like `0.22.2-rc1` and run the same workflow.

---

## Summary

- The scheduler decides **if** and **what** to release.
- `prepare-release.yml` prepares the release commits and opens the release PR (or direct-pushes in emergencies).
- `publish-release.yml` tags and deploys after the release commit is on `master`.
- `github-release.yml` creates the GitHub Release when a tag is pushed.
- Snapshot publishing happens automatically on every push to `master`.

For questions, check the workflow logs in **GitHub → Actions** or review `.github/workflows/`.
