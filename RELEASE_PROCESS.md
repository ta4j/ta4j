# Ta4j Release Process

This guide explains how Ta4j releases move from documentation updates to Maven Central, which workflow does what, and why each step exists. It is written for maintainers who want a predictable, auditable release flow with clear Git history and tags.

The default branch is `master`. Release tags must be reachable from `master` for the scheduler to produce sane diffs and version bumps.

---

## Quick Start

### Production release (typical)
1. Update `CHANGELOG.md` under **Unreleased**.
2. Run `scripts/prepare-release.sh <version>` (e.g., `0.22.2`).
3. Commit and push the docs: `git add CHANGELOG.md README.md release/ && git commit -m "Prepare release 0.22.2" && git push`.
4. Trigger the release:
   - Wait for the scheduler, or
   - Run **Publish Release to Maven Central** manually.

### Dry-run (validation only)
1. Follow the same prep steps as production.
2. Run **Publish Release to Maven Central** with `dryRun=true`.
3. Confirm no tags, no deploys, and no PRs/pushes occurred.

---

## Key Concepts (What Changes in Git)

- **Release commit**: `pom.xml` version set to the release version and `release/<version>.md` added.
- **Tag**: annotated Git tag (e.g., `0.22.2`) pointing at the release commit. Tag push triggers `github-release.yml`.
- **Snapshot commit**: `pom.xml` bumped to the next `-SNAPSHOT` version.
- **Release PR (default mode)**: contains the release commit + snapshot commit and merges into `master` with a merge commit.
- **Direct push mode**: skips the PR and pushes both commits directly to `master`.

Tags are created before the PR exists and are not part of PR diffs. They become reachable from `master` only after a merge commit (or direct push) lands on `master`.

---

## System Overview (Who Does What)

1. **prepare-release.sh**
   - **What**: Updates CHANGELOG/README and generates `release/<version>.md`.
   - **Why**: Ensures release notes are accurate before the workflows run.

2. **Release Scheduler (`release-scheduler.yml`)**
   - **What**: Looks at binary-impacting changes + Unreleased changelog, asks GitHub Models for a SemVer bump, and decides whether to release.
   - **Why**: Automates “should we release?” decisions and reduces manual churn.

3. **Release Workflow (`release.yml`)**
   - **What**: Sets release version, tags, deploys to Maven Central, bumps next snapshot, then updates `master` via PR or direct push.
   - **Why**: Keeps release steps consistent and fully automated.

4. **GitHub Release Workflow (`github-release.yml`)**
   - **What**: Builds and publishes the GitHub Release when a tag is pushed.
   - **Why**: Ensures the GitHub Release matches the tagged artifacts.

5. **Snapshot Workflow (`snapshot.yml`)**
   - **What**: Publishes snapshots on every push to `master`.
   - **Why**: Provides a current snapshot build for users and CI consumers.

---

## Step-by-Step (What Happens and Why)

### Release Scheduler (`release-scheduler.yml`)
1. **Find last tag on `master`**
   - **What**: Finds the most recent tag reachable from the default branch.
   - **Why**: Ensures diffs are based on what was actually released on `master`.
2. **Collect binary-impacting changes + changelog**
   - **What**: Looks for changes in `pom.xml` or `src/main/**`, plus Unreleased notes.
   - **Why**: Avoids releasing for workflow-only or doc-only changes.
3. **AI decision (SemVer bump)**
   - **What**: Calls GitHub Models to choose patch/minor/major.
   - **Why**: Consistent semantics without manual guessing.
4. **Compute version and gate**
   - **What**: Calculates the next version, checks for tag collisions.
   - **Why**: Prevents duplicate or backward releases.
5. **Major approval (if needed)**
   - **What**: Waits for approval in the `major-release` environment.
   - **Why**: Human sign-off for breaking changes.
6. **Dispatch `release.yml`**
   - **What**: Starts the release workflow with the chosen version.
   - **Why**: Keeps release logic centralized in one workflow.
7. **Post discussion summary**
   - **What**: Writes a decision summary to the Release Scheduler discussion.
   - **Why**: Auditable history and notifications for maintainers.

### Release Workflow (`release.yml`)
1. **Validate secrets and compute versions**
   - **Why**: Fail fast if required credentials are missing.
2. **Generate release notes**
   - **Why**: Ensures `release/<version>.md` exists and is current.
3. **Commit release version**
   - **Why**: Locks the source version for the tagged release.
4. **Create annotated tag** (skipped on `dryRun=true`)
   - **Why**: Tags are the source of truth for releases and GitHub Release workflow triggers.
5. **Build and deploy to Maven Central** (skipped on `dryRun=true`)
   - **Why**: Publish signed artifacts for consumption.
6. **Push tag**
   - **Why**: Triggers `github-release.yml`.
7. **Commit next snapshot**
   - **Why**: Ensures ongoing development is versioned correctly.
8. **Update `master`**
   - **Default (PR mode)**: create a PR (`release/<version>` -> `master`) and wait for maintainer review/approval.
     - **Why**: Keeps a required review gate and ensures tags become reachable from `master` via a merge commit.
   - **Direct push (`RELEASE_DIRECT_PUSH=true`)**: push both commits directly to `master`.
     - **Why**: Skips PR friction when org permissions allow it.
9. **Post discussion summary**
   - **Why**: Provides an audit trail and notifications.

**Merge note:** merge the release PR using a merge commit (no squash). Required checks and maintainer approval must pass.

**Tag reachability note:** if the PR is squash-merged, the tag commit will not be reachable from `master`. Use merge commits.

### GitHub Release (`github-release.yml`)
- Triggered by tag push; builds artifacts and publishes the GitHub Release using `release/<version>.md`.

### Snapshot (`snapshot.yml`)
- Runs on every push to `master` and publishes snapshot artifacts.

---

## Example Scenarios

### Scenario A: Production release via scheduler (PR mode)
**Context:** You prepared docs for `0.22.2` and pushed to `master`.
1. Scheduler runs, detects binary changes, chooses `0.22.2`.
2. `release.yml` runs with `dryRun=false`.
3. Release commit + tag `0.22.2` created, artifacts deployed.
4. Tag pushed, `github-release.yml` creates the GitHub Release.
5. Snapshot commit created.
6. PR `release/0.22.2 -> master` opened for maintainer review and approval.
7. After a merge commit lands on `master`, tag `0.22.2` is reachable from `master`.

### Scenario B: Dry-run (validation only)
**Context:** You want to verify the pipeline without publishing.
1. Run `release.yml` with `dryRun=true`.
2. Version checks and validation run.
3. No tag is created, no deploy occurs, no PR/direct push occurs.
4. Discussion summary notes dry-run mode and timestamps.

### Scenario C: Production release with direct push
**Context:** Org permissions allow direct pushes; `RELEASE_DIRECT_PUSH=true`.
1. `release.yml` runs and deploys normally.
2. Tag is pushed.
3. Release commit + snapshot commit are pushed directly to `master`.
4. No PR is created; tag is immediately reachable from `master`.

---

## Verification Checklist

1. **Actions run**: release workflows completed successfully.
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
| `MAVEN_CENTRAL_TOKEN_USER` | `release.yml`, `snapshot.yml` | Maven Central authentication username |
| `MAVEN_CENTRAL_TOKEN_PASS` | `release.yml`, `snapshot.yml` | Maven Central authentication password |
| `GPG_PRIVATE_KEY` | `release.yml`, `snapshot.yml` | GPG private key for signing artifacts |
| `GPG_PASSPHRASE` | `release.yml`, `snapshot.yml` | Passphrase for the GPG private key |
| `GH_MODELS_TOKEN` | `release-scheduler.yml` | GitHub Models API token |
| `GH_TA4J_REPO_TOKEN` | `github-release.yml` | Classic PAT used for GitHub Release creation |

### Optional Repository Secrets

| Secret Name | Used By | Purpose |
|------------|---------|---------|
| `MAVEN_MASTER_PASSPHRASE` | `release.yml`, `snapshot.yml` | Optional Maven master password for `settings-security.xml` |

### Optional Repository Variables

| Variable Name | Used By | Purpose |
|---------------|---------|---------|
| `RELEASE_DIRECT_PUSH` | `release.yml` | When `true`, skip the release PR and push commits directly to `master` |

### Required GitHub Environment

For major releases, configure the protected environment:
- **Name**: `major-release`
- **Purpose**: manual approval for major bumps

### Required Repo Settings

- **Actions → Workflow permissions**: set to **Read and write** for dispatch/tag/PR.

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
- `release.yml` performs the release steps and ensures `master` reflects the tag.
- `github-release.yml` creates the GitHub Release when a tag is pushed.
- Snapshot publishing happens automatically on every push to `master`.

For questions, check the workflow logs in **GitHub → Actions** or review `.github/workflows/`.
