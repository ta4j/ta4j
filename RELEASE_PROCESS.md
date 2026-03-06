# Ta4j Release Process

This document is the operator guide for shipping ta4j releases safely and repeatedly.

Default branch: `master`

Core release invariant:
- the release tag must point to the intended release commit
- that release commit must be reachable from `master`

---

## 1. At a Glance

Preferred path (PR mode):
1. Prepare release (`prepare-release.yml`) creates release + next-snapshot commits on `release/<version>`.
2. Maintainer reviews and merges release PR into `master` using a **merge commit**.
3. Publish release (`publish-release.yml`) validates metadata/ancestry, tags, deploys to Maven Central, pushes tag.
4. GitHub release (`github-release.yml`) runs on tag push.
5. Release health (`release-health.yml`) audits drift and posts summary.

Emergency path (direct push mode):
1. `prepare-release.yml` pushes both commits directly to `master` when `RELEASE_DIRECT_PUSH=true`.
2. It then dispatches `publish-release.yml`.

Validation path (dry run):
1. Run prepare and/or publish with `dryRun=true`.
2. Checks run, but no tag push and no Maven Central deploy.

---

## 2. Release Modes

| Mode | How it starts | Git changes | Tag/deploy | Typical use |
|---|---|---|---|---|
| PR mode (default) | scheduler or manual prepare | release branch + merged PR | yes | normal production releases |
| Direct push | manual prepare + `RELEASE_DIRECT_PUSH=true` | commits land directly on `master` | yes | emergency/unblocked maintenance |
| Dry run | manual prepare/publish + `dryRun=true` | none | no | preflight validation |

---

## 3. Preconditions (Do This Before Releasing)

Code and docs:
1. Update `CHANGELOG.md` under `Unreleased`.
2. Keep README version references current.
3. Ensure release notes can be generated cleanly (`release/<version>.md`).

Repository settings:
1. Actions workflow permissions must allow write operations (dispatch, PR updates, tag push).
2. Use merge-commit discipline for release PRs (no squash/rebase for those PRs).

Secrets and variables:
1. Required secrets for publish/snapshot must be configured.
2. Scheduler-related variables must be set intentionally (especially `RELEASE_SCHEDULER_ENABLED`).

---

## 4. Production Runbook (PR Mode)

1. Trigger **Prepare Release**
- Via scheduler or manual `workflow_dispatch`.
- Inputs: `releaseVersion` (optional if auto-detected), `nextVersion` (optional), `dryRun=false`.
- If `nextVersion` is omitted and `releaseVersion` is a plain `X.Y.Z`, it is auto-generated as `<major>.<minor>.<patch+1>-SNAPSHOT` (for example `0.22.2` -> `0.22.3-SNAPSHOT`).
- For RC/non-plain release versions, provide `nextVersion` explicitly.

2. Review generated release PR
- Confirm release commit + next snapshot commit are present.
- Confirm release notes file exists (`release/<version>.md`).

3. Merge release PR to `master`
- Use a **merge commit**.
- Do not squash/rebase release PRs.

4. Observe **Publish Release**
- Workflow validates release metadata and ancestry.
- Creates annotated tag.
- Deploys to Maven Central.
- Pushes tag only after successful deploy.
- Verifies pushed tag points to expected release commit and is reachable from default branch.

5. Observe post-publish workflows
- `github-release.yml` should run from tag push.
- `release-health.yml` should run and report `OK`.

---

## 5. Dry-Run Runbook

Prepare dry-run:
1. Run `prepare-release.yml` with `dryRun=true`.
2. Validate version calculations, notes generation, and preflight checks.

Publish dry-run:
1. Run `publish-release.yml` with `dryRun=true`.
2. Pass `releaseVersion`.
3. `releaseCommit` is optional for dispatch and can be auto-detected.

Expected behavior:
- dry-run can warn about missing deploy secrets/resources.
- no tag push and no Maven Central deployment.

---

## 6. Workflow Map (Trigger + Responsibility)

| Workflow | Trigger(s) | Primary responsibility | Critical guardrails |
|---|---|---|---|
| `release-scheduler.yml` | schedule, manual | decide whether/how to release | binary-impact gate, semver safety, tag collision checks |
| `prepare-release.yml` | manual (or scheduler dispatch) | generate release artifacts and release PR/direct-push commits | version validation, metadata validation, push capability probes |
| `publish-release.yml` | merged release PR close, manual | tag + Maven Central deploy + release summary | merge discipline + ancestry checks, post-push tag integrity/reachability checks |
| `release-health.yml` | push to `master`, publish workflow completion, schedule, manual | detect drift in release state | fails on tag reachability drift, snapshot drift, missing notes, stale release PRs |
| `github-release.yml` | tag push, manual | GitHub release publication | tag/ref validation |
| `snapshot.yml` | push to `master` | publish snapshots | deploy prechecks |

---

## 7. Verification Checklist (Post-Release)

1. `prepare-release.yml` and `publish-release.yml` succeeded.
2. Tag exists for released version.
3. Tag reachability from `master` is true.
4. Maven Central artifacts are visible (allow propagation time).
5. GitHub release exists with expected notes/artifacts.
6. `master` is on next `-SNAPSHOT` version.
7. `release-health.yml` reports no drift.

Quick checks:
```bash
git fetch origin --tags
VERSION=0.22.3

git rev-list -n 1 "refs/tags/${VERSION}"
git merge-base --is-ancestor "${VERSION}" origin/master && echo "reachable"
```

---

## 8. Troubleshooting

### 8.1 Publish succeeded, Health failed

Is this possible?
- Yes. `publish-release.yml` and `release-health.yml` are separate workflows.

What should fail earlier now?
- If the just-pushed tag is wrong/unreachable, publish should fail first due to post-push tag checks.

Why health can still fail afterward:
- `pom.xml` snapshot not ahead of latest tag.
- missing `release/<version>.md` for latest tag.
- stale release PRs.
- existing repository drift not introduced by this publish run.

Remediation playbook:
1. Open the failed `release-health.yml` run and read `Drift reasons`.
2. Apply targeted fix:
   - `latest tag not reachable from <branch>`: make tagged commit reachable from `master` (typically a reachability merge commit; avoid retagging published releases).
   - `pom.xml snapshot version not ahead of latest tag`: bump `master` to next `-SNAPSHOT`.
   - `missing release notes for latest tag`: add `release/<version>.md`.
   - `stale release PRs detected`: merge or close stale release PRs.
3. Re-run health via `workflow_dispatch`.
4. If Maven deploy already succeeded, treat this as repo-state remediation, not republish.

### 8.2 Release PR waiting for merge

- Ensure required checks pass.
- Ensure required maintainer approval exists.
- Merge with merge commit.

### 8.3 Tag exists error

- Version already tagged.
- Use a new version unless you have an explicit rollback/recovery plan.

### 8.4 Release notes missing

- Ensure `release/<version>.md` exists in the release commit.

### 8.5 Branch advanced during prepare/push

- `master` moved while workflow was preparing/pushing.
- Re-run prepare once branch is stable.

### 8.6 Dry-run warnings

- Missing deploy secrets in dry-run are expected warnings.
- Fix before production run.

---

## 9. Discussion Posts (Markers and Cleanup)

Release-related workflows post to GitHub Discussions with machine-readable markers:

```html
<!-- ta4j:post-type=<type>;run=<real|dry-run> -->
```

Post types:
- `release-scheduler`
- `publish-release`
- `release-health`

Cleanup rules:
- `release-health`: removes prior real health posts before posting latest.
- `release-scheduler`: on dry-run, removes prior matching dry-run summaries for same release context.
- `publish-release`: keeps historical posts (audit trail).

Do not key automation off author/body heuristics; key off marker metadata.

---

## 10. Required Resources

### 10.1 Required secrets

| Secret | Used by | Purpose |
|---|---|---|
| `MAVEN_CENTRAL_TOKEN_USER` | publish, snapshot | Maven Central auth user/token |
| `MAVEN_CENTRAL_TOKEN_PASS` | publish, snapshot | Maven Central auth password/token |
| `GPG_PRIVATE_KEY` | publish, snapshot | artifact signing key |
| `GPG_PASSPHRASE` | publish, snapshot | signing key passphrase |
| `GH_MODELS_TOKEN` | scheduler | model API access |
| `GH_TA4J_REPO_TOKEN` | prepare, publish, github-release | PAT for release pushes/release creation |

### 10.2 Optional secrets

| Secret | Used by | Purpose |
|---|---|---|
| `MAVEN_MASTER_PASSPHRASE` | publish, snapshot | optional Maven settings-security |

### 10.3 Variables

| Variable | Used by | Purpose |
|---|---|---|
| `RELEASE_DIRECT_PUSH` | prepare | direct-push mode switch |
| `RELEASE_NOTIFY_USER` | publish, scheduler, health | discussion mention target |
| `RELEASE_DISCUSSION_NUMBER` | publish | publish summary discussion |
| `RELEASE_SCHEDULER_DISCUSSION_NUMBER` | scheduler, health | scheduler/health discussion |
| `RELEASE_SCHEDULER_ENABLED` | scheduler | enable scheduled release decisions |
| `RELEASE_AI_MODEL` | scheduler | model override |
| `RELEASE_PR_STALE_DAYS` | health | stale release PR threshold |

### 10.4 Environment

| Environment | Purpose |
|---|---|
| `major-release` | manual approval gate for major bumps |

---

## 11. Repository Policy Notes

1. Keep release tags reachable from `master`.
2. Use merge commits for release PRs.
3. Avoid force-retagging already-published versions.
4. Prefer PR mode; use direct push only when justified.

---

## 12. Responsibilities Summary

| Task | Maintainer/manual | GitHub Actions |
|---|---|---|
| maintain changelog/readme release readiness | yes | no |
| generate release notes | yes (via prepare script/workflow) | yes |
| set release version + next snapshot | no | yes |
| create/push release tag | no | yes (non-dry-run) |
| deploy to Maven Central | no | yes (non-dry-run) |
| publish GitHub release | no | yes |
| release drift auditing | no | yes |

---

## 13. RC Builds

RC versions are supported if they are Maven-valid (for example `0.22.2-rc1`).
Use the same workflows and verification checklist.
