# Ta4j Release Process

This document is the operator guide for shipping ta4j releases safely and repeatedly.

Default branch: `master`

Core release invariant:
- the release tag must point to the intended release commit
- that release commit must be reachable from `master`

---

## 1. At a Glance

Preferred path (PR mode):
1. Scheduler (`release-scheduler.yml`) builds a release dossier, validates the configured GitHub Models catalog entry, asks the model for a SemVer decision, and dispatches prepare when appropriate.
2. Prepare release (`prepare-release.yml`) creates release + next-snapshot commits on `release/<version>`.
3. Maintainer reviews and merges release PR into `master` using a **merge commit**.
4. Publish release (`publish-release.yml`) validates metadata/ancestry, runs the release-candidate gate, validates the artifact manifest, tags, deploys to Maven Central, pushes tag, and explicitly dispatches snapshot publication.
5. GitHub release (`github-release.yml`) runs on tag push.
6. Release health (`release-health.yml`) audits drift, verifies the current snapshot version is published once snapshot publication is authoritative, and posts summary.

Emergency path (direct push mode):
1. `prepare-release.yml` pushes both commits directly to `master` when `RELEASE_DIRECT_PUSH=true`.
2. It then dispatches `publish-release.yml`.

Validation path (dry run):
1. Run prepare and/or publish with `dryRun=true`.
2. Prepare dry-runs still run the read-only release-ready and next-cycle deprecation scans and upload their reports.
3. No release commits, managed cleanup issue mutations, tag push, or Maven Central deploy occur.

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
4. Confirm release PRs are visible to release owner:
   - release PR must be labeled `release`.
   - release PR is auto-assigned to `TheCookieLab`.
   - `TheCookieLab` is auto-requested as reviewer.
5. While any labeled release PR is open, only that release PR can be merged.
6. Open PRs will receive an automatic release-freeze notice comment while the freeze is active. The workflow removes that notice once no release PR remains open.

Repository settings:
1. Actions workflow permissions must allow write operations (dispatch, PR updates, tag push).
2. Use merge-commit discipline for release PRs (no squash/rebase for those PRs).
3. Add `Release Merge Freeze` as a required status check on `master` in branch protection.

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
- Before the workflow commits the next snapshot version, it runs the Java-based `ta4jexamples.doc.RemovalReadyDeprecationScanner` against the release version and fails if any `@Deprecated(forRemoval = true)` symbols are due or overdue for removal. This read-only scan also runs in dry-run mode.
- After the next snapshot version is known, it scans sources scheduled for that planned snapshot. Non-dry-run runs then sync deduplicated GitHub cleanup issues, including reopening still-valid managed issues and closing stale managed issues for the same removal version; dry-runs only upload the scan report.
- The workflow uploads release-gate and next-snapshot removal-ready deprecation report artifacts with grouped findings, symbols, lifecycle status, replacement hints when available, and synced issue links when issue sync runs.
- The scanner JSON is the stable handoff contract for future automation: it includes `schemaVersion`, `automationNamespace`, grouped issue `planKind`, and per-symbol `trackingKey` fields so a later AI-driven planner can split work into one issue per deprecated item while remaining restart-safe by searching managed markers before mutation.
- The workflow auto-labels the PR with `release`, assigns it to `TheCookieLab`, and requests review from `TheCookieLab`.
- Opening a release PR automatically triggers freeze notices on other open PRs.

2. Review generated release PR
- Confirm release commit + next snapshot commit are present.
- Confirm release notes file exists (`release/<version>.md`).
- If a release PR is open, wait for it to merge before merging any non-release PRs to `master`.

3. Merge release PR to `master`
- Use a **merge commit**.
- Do not squash/rebase release PRs.
- This merge is allowed because the `Release Merge Freeze` check allows only release PRs to merge during freeze.

4. Observe **Publish Release**
- Workflow validates release metadata and ancestry.
- Creates annotated tag.
- Deploys to Maven Central.
- Pushes tag only after successful deploy.
- Verifies pushed tag points to expected release commit and is reachable from default branch.

5. Observe post-publish workflows
- `github-release.yml` should run from tag push.
- `release-health.yml` may first report snapshot publication as pending during the async handoff, then should report `OK` after `snapshot.yml` completes.

---

## 5. Dry-Run Runbook

Prepare dry-run:
1. Run `prepare-release.yml` with `dryRun=true`.
2. Validate version calculations, notes generation, preflight checks, and deprecation scan reports.

Publish dry-run:
1. Run `publish-release.yml` with `dryRun=true`.
2. Pass `releaseVersion`.
3. `releaseCommit` is optional for dispatch and can be auto-detected.

Expected behavior:
- dry-run can warn about missing deploy secrets/resources.
- no tag push and no Maven Central deployment.
- prepare dry-runs still run push capability probes with `git push --dry-run`.
- prepare dry-runs run deprecation scans and upload report artifacts, but skip managed GitHub cleanup issue sync. If the release-ready gate finds due or overdue removals, the dry-run fails after the reports are available.
- publish dry-runs run the same metadata, ancestry, release-candidate, and artifact manifest checks without deploying.
- release-candidate checks use the repository default `integration,slow` test-tag exclusions and log that policy in the workflow output.
- workflows upload audit artifacts such as release dossiers, decisions, manifests, logs, and tag-resolution files so failures can be diagnosed from the exact phase that produced them.

---

## 6. Workflow Map (Trigger + Responsibility)

| Workflow | Trigger(s) | Primary responsibility | Critical guardrails |
|---|---|---|---|
| `release-scheduler.yml` | schedule, manual | decide whether/how to release | binary-impact gate, model catalog preflight, release dossier, semver safety, tag collision checks |
| `prepare-release.yml` | manual (or scheduler dispatch) | generate release artifacts and release PR/direct-push commits | version validation, metadata validation, dry-run push capability probes |
| `publish-release.yml` | merged release PR close, manual | release-candidate verification + tag + Maven Central deploy + snapshot dispatch + release summary | merge discipline + ancestry checks, artifact manifest checks, post-push tag integrity/reachability checks |
| `release-health.yml` | push to `master`, publish workflow completion, snapshot workflow completion, schedule, manual | detect drift in release state | fails on tag reachability drift, snapshot version drift, missing snapshot publication once snapshot publication is authoritative, missing notes, stale release PRs |
| `github-release.yml` | semver-like tag push, manual | GitHub release publication | semver tag validation, exact artifact manifest |
| `snapshot.yml` | push to `master`, publish workflow dispatch, manual | publish snapshots | build/test/deploy prechecks and source-release audit fields |

Tag metrics used by release automation:
- `latest tag`: newest release tag by tag creation date, preferring bare numeric tags before `v`-prefixed tags.
- `last reachable tag`: newest release tag merged into `master`; `release-scheduler.yml` uses this as its diff and version baseline.
- `last first-parent tag`: nearest release tag visible on `master`'s first-parent chain; informational only for diagnosing merge topology.
- `latest tag reachable`: whether the newest release tag is an ancestor of `master`.

---

## 7. Verification Checklist (Post-Release)

1. `prepare-release.yml` and `publish-release.yml` succeeded.
2. Tag exists for released version.
3. Tag reachability from `master` is true.
4. Maven Central artifacts are visible (allow propagation time).
5. GitHub release exists with expected notes/artifacts.
6. Release-ready deprecation gate report exists for the released version and did not find due or overdue removals.
7. Removal-ready deprecation report artifact exists for the new snapshot version, and any matching cleanup issues were created, refreshed, reopened, or closed as stale successfully.
8. The chained `snapshot.yml` run succeeded for the next `-SNAPSHOT` version.
9. `master` is on next `-SNAPSHOT` version.
10. `release-health.yml` reports no drift and confirms the current snapshot version is published after `snapshot.yml` completes.

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
- current snapshot version is missing from the Maven snapshot repository after snapshot publication becomes authoritative.
- missing `release/<version>.md` for latest tag.
- stale release PRs.
- existing repository drift not introduced by this publish run.

Why a large `last first-parent tag` gap can still be healthy:
- release PRs merge back to `master` with merge commits, so the tagged release commit often lives on the merge's second parent rather than the first-parent spine.
- `release-health.yml` only fails when the newest release tag is not reachable from `master`; a lagging first-parent tag is diagnostic context, not drift by itself.

Remediation playbook:
1. Open the failed `release-health.yml` run and read `Drift reasons`.
2. Apply targeted fix:
   - `latest tag not reachable from <branch>`: make tagged commit reachable from `master` (typically a reachability merge commit; avoid retagging published releases).
   - `pom.xml snapshot version not ahead of latest tag`: bump `master` to next `-SNAPSHOT`.
   - `current snapshot version not published to Maven snapshot repository`: inspect the latest `snapshot.yml` run, fix the publication failure, and rerun health after the snapshot version appears in metadata.
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

### 8.7 Finding the failure point

Release workflows use grouped log sections and upload audit artifacts on every run.

Look for these files first:
- `release-dossier.md`: scheduler context sent to the model.
- `release-decision.json`: normalized AI release decision.
- `release-audit.json`: workflow-local release metadata.
- `tag-resolution.txt`: resolved latest/reachable/first-parent tag state.
- `artifact-manifest.txt`: exact jars expected for publish/GitHub Release.
- `javadoc-warnings.txt`: Javadoc warning baseline comparison from release artifact/deploy logs; new warnings beyond the tracked `scripts/release/javadoc-warning-baseline.txt` debt fail publish.
- `.agents/logs/full-build-*.log`: release-candidate full build log.

If a grouped section fails, inspect the matching artifact before rerunning. Avoid rerunning publish until tag state, artifact state, and Central deployment state are understood.

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
| `RELEASE_AI_MODEL` | scheduler | GitHub Models model override; default should be `openai/gpt-4.1` |
| `RELEASE_PR_STALE_DAYS` | health | stale release PR threshold |

### 10.4 Environment

| Environment | Purpose |
|---|---|
| `major-release` | manual approval gate for major bumps |
| `release-recovery` | manual approval gate for `publish-release.yml` recovery mode when an explicit release commit is not reachable from `master` |

---

## 11. Repository Policy Notes

1. Keep release tags reachable from `master`.
2. Use merge commits for release PRs.
3. Avoid force-retagging already-published versions.
4. Prefer PR mode; use direct push only when justified.
5. Keep `RELEASE_DIRECT_PUSH=false` for normal releases.
6. Use `recoveryMode=true` only for manual publish recovery when the release commit is intentionally not reachable from `master`; this path is protected by the `release-recovery` environment.

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
