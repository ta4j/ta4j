# Ta4j Release Process

This document is the operator guide for shipping ta4j releases safely and repeatedly.

Default branch: `master`

Core release invariant:
- the release tag must point to the intended release commit
- that release commit must be reachable from `master`

---

## 1. At a Glance

Preferred path (PR mode):
1. Scheduler (`release-scheduler.yml`) checks release eligibility, freezes evidence for one exact commit, assesses it with the official Codex action, validates the recommendation and version, then dispatches preparation with that same source SHA. These are separate named jobs in the Actions graph. Optional Luna 6 Decisions evaluation uses the official OpenAI SDK and records a comparison; it cannot authorize a release. See [Release assessment](docs/release/reusable-assessment.md) for the stage graph, decision contract, runner configuration and output policy.
2. Prepare release (`prepare-release.yml`) creates release + next-snapshot commits on `release/<version>`.
3. Maintainer reviews and merges release PR into `master` using a **merge commit**.
4. The merge's `master` push immediately starts `snapshot.yml`; in parallel, `publish-release.yml` validates metadata/ancestry, runs the release-candidate gate, validates the artifact manifest, tags, deploys to Maven Central, and pushes the tag without dispatching a duplicate snapshot run.
5. GitHub release (`github-release.yml`) runs on tag push.
6. Release health (`release-health.yml`) audits drift, retrieves the current snapshot's exact timestamped POM/JAR once publication is authoritative, and posts summary.

Opt-in automatic path (`RELEASE_DIRECT_PUSH=true`, `1`, or `yes`, case-insensitive):
1. Capture immutable starting master **B**; prepare release **R** and next snapshot **S** on `release/<version>` without changing master.
2. Separate read-only jobs run the canonical non-mutating full gate at exact R and S. At R, also enforce release-ready deprecations, production artifact inventory/Maven metadata and hashes, and the Javadoc warning baseline. Both check tracked-tree/index cleanliness.
3. Consume receipts bound to B/R/S, versions, repository, workflow SHA/ref and run/attempt. Recheck the candidate still equals S and master still equals B. An atomic push fast-forwards master to S and deletes the consumed candidate branch, with explicit expected-S and expected-B leases. No history is replaced.
4. Call the existing publisher at R while retaining the lifecycle lock through publication. The publisher requires R on master, revalidates, deploys, and tags **R**, never S. `recoveryMode` does not authorize an automatic candidate.

The switch remains **off by default**; merging this implementation does not enable it. Activating it requires a separate maintainer decision. The existing Monday 09:00 UTC wake-up and fortnightly guard anchored June 29, 2026 are unchanged. Existing branch protection, merge approvals, bypass membership and auto-merge settings are unchanged. If the existing actor cannot promote, the workflow fails closed.

Validation path (dry run):
1. Manually run any mutating release workflow and keep its default `dryRun=true`.
2. The scheduler's manual `aiMode` default is `full`, which performs a billed assessment after eligibility passes. Choose `aiMode=skip` for deterministic checks without a model call. `evaluateLuna=true` adds a separate billed comparison; it is off by default.
3. Inspect the computed release/tag/snapshot values, workflow summary, and audit artifacts.
4. Rerun manually with `dryRun=false` only when intentionally mutating, or let the scheduled/push/merge release path run with its explicit `dryRun=false`.
5. Prepare dry-runs still run the read-only release-ready and next-cycle deprecation scans and upload their reports.
6. No release commits, managed cleanup issue mutations, branch/tag push, Maven Central deploy, GitHub Release creation, snapshot deploy, or discussion/comment mutation occur.

---

## Release freeze and concurrency

Automatic preparation acquires `release-freeze/prepare-<run-id>` at B and synchronously blocks every currently open PR's head and test-merge SHA before preparing any candidate. Staging atomically replaces that marker with `release-freeze/<version>` at S. This automatic freeze blocks **all** PRs, including trusted release PRs, throughout exact-R/S validation, promotion and publication. A competing open trusted release PR stops automatic preparation. Manual publication also acquires a durable marker bound to R and the frozen master SHA; resuming that same R adopts its existing marker.

The existing Release Merge Freeze workflow is now a trusted, metadata-only `pull_request_target`/`workflow_run` controller. It checks out default-branch code only. It refreshes PR metadata, paginates discovery, and sets a **commit status** named `Build and Verify` on both head and test-merge SHAs. This status is distinct from the actual CI **CheckRun** with the same required name: GitHub requires both to pass. Clearing a freeze cannot turn a failed Maven/script check into success. No branch-protection/ruleset change is required. All acquire/reconcile/release jobs share a short concurrency group; long validation retains the separate lifecycle lock.

The required CI aggregate also checks the live freeze and its original tested head/base/merge. This catches new or reopened PRs and heads that change during verification. After unfreezing, the controller may retry **only the failed aggregate job**, at most once per exact workflow run, when all its selected test lanes already succeeded. It does not rerun successful Maven or script jobs or reuse old test evidence as proof for a changed base. A later base change needs fresh CI.

The short freeze concurrency group uses GitHub's `queue: max` so new reconciliation runs do not replace pending acquisition or cleanup jobs. GitHub still rejects arrivals beyond 100 pending jobs/runs; a cancelled or failed critical job leaves its durable freeze for manual reconciliation. The pinned actionlint predates this queue key, so only that unsupported-key diagnostic is ignored and an isolated fixture checks all five queue declarations, values and cancellation policy.

| Edge case | Guard and recovery |
|---|---|
| Already-green PRs when release starts | Block current head **and test-merge** statuses, rediscover targets, then confirm B and the owned freeze before preparation. |
| New, reopened, fork or changed PR during release | Trusted metadata reconciliation plus a live guard in the required aggregate. Fork code/artifacts never run with status-write credentials. |
| Master or candidate moves, or freeze marker mutates | Exact refs/receipts and promotion CAS fail closed; frozen master is checked again before upload and tag publication. |
| Verification fails and promotion is skipped | Delete only the owned freeze with an exact lease, only while master still equals B and no publication was attempted. Failed/cancelled promotion always retains the freeze, even if a read still sees B. Candidate/pending evidence remains for diagnosis. |
| Cancellation or uncertain promotion result | Retain the durable freeze. It never expires automatically; inspect master and publication journals before recovery. |
| Promotion succeeds but publication fails or upload is ambiguous | Keep the freeze and resume the same R; no second version bump, blind Maven redeploy or rollback. |
| Maven succeeds, but tag push fails | Completion checkpoint allows the same R to skip Maven and finish the tag. Release the freeze only after the checkpoint and exact R tag are verified. |
| One of several freezes is removed | Reconcile the remaining freezes before allowing merges; consuming one marker cannot clear another. |
| API failure, changing discovery or rate limit | Stop acquisition/validation, retain uncertain state and reconcile; notices alone are best-effort. |
| Notification/snapshot handoff fails after verified Maven/tag completion | Actual release completion can release the freeze; post-release reporting can be retried separately. |

This is a merge barrier, not a distributed transaction over GitHub and Maven. Asynchronous reconciliation has a narrow in-flight/reopen window, and the existing owner bypass can deliberately override checks. Those permissions are unchanged. The synchronous barrier, exact-SHA guards and CAS protect the release against ordinary races; an unexpected master change stops publication for reconciliation. Only changing repository rules/bypass could provide a stronger administrative lock, which this implementation does not do.

---

## 2. Release Modes

| Mode | How it starts | Git changes | Tag/deploy | Typical use |
|---|---|---|---|---|
| PR mode (default) | scheduler or manual prepare | release branch + merged PR | yes | normal production releases |
| Automatic branch-first | scheduler/manual prepare + opt-in `RELEASE_DIRECT_PUSH` | validate R/S on branch, then atomic fast-forward to S | after promotion, at R | unattended releases after separate activation |
| Dry run | manual mutating release workflow, default `dryRun=true` | none | no | inspection-first preflight validation |

---

## 3. Preconditions (Do This Before Releasing)

Code and docs:
1. Update `CHANGELOG.md` under `Unreleased`.
2. Keep README version references current.
3. Run `scripts/docs-integrity-check.sh` and resolve failures before preparing a release.
   - This gate verifies canonical docs presence, link validity, command references, and TODO-free user-facing entry docs.
4. Ensure release notes can be generated cleanly (`release/<version>.md`).
5. Confirm release PRs are visible to release owner:
   - release PR must be labeled `release`.
   - release PR is auto-assigned to `RELEASE_OWNER` (default: `TheCookieLab`).
   - a non-author user reviewer or team is requested when configured through `RELEASE_REVIEWERS` or `RELEASE_REVIEW_TEAMS`.
   - if the release owner authored the PR and no fallback reviewer/team is configured, prepare-release emits a warning and the PR may require the documented admin or bypass merge path.
6. While any labeled release PR is open, only that release PR can be merged.
7. Open PRs will receive an automatic release-freeze notice comment while the freeze is active. The workflow removes that notice once no release PR remains open.

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
- Inputs: `releaseVersion` (optional if auto-detected), `nextVersion` (optional), `dryRun`, and `sourceSha` (optional for manual preparation; supplied by the scheduler).
- Manual runs default `dryRun=true`. Set `dryRun=false` only for an intentional mutating run after reviewing a dry-run. Scheduled release automation dispatches prepare with explicit `dryRun=false`.
- For scheduled production runs, inspect the scheduler's **Decision path and outcome** summary and the **Release handoff** audit line for the source SHA, version and `dry_run=false`. Preparation checks the assessed SHA against its checkout and current default branch before taking a release freeze. A stale candidate requires a fresh scheduler run.
- If `nextVersion` is omitted and `releaseVersion` is a plain `X.Y.Z`, it is auto-generated as `<major>.<minor>.<patch+1>-SNAPSHOT` (for example `0.22.2` -> `0.22.3-SNAPSHOT`).
- For RC/non-plain release versions, provide `nextVersion` explicitly.
- Before the workflow commits the next snapshot version, it runs the Java-based `ta4jexamples.doc.RemovalReadyDeprecationScanner` against the release version and fails if any `@Deprecated(forRemoval = true)` symbols are due or overdue for removal. This read-only scan also runs in dry-run mode.
- After the next snapshot version is known, it scans sources scheduled for that planned snapshot or any earlier removal version because ta4j versions may jump across major, minor, or patch positions. Non-dry-run runs then sync deduplicated GitHub cleanup issues, including reopening still-valid managed issues and closing stale managed issues for the same removal version; dry-runs only upload the scan report.
- The workflow uploads release-gate and next-snapshot removal-ready deprecation report artifacts with grouped findings, symbols, lifecycle status, replacement hints when available, and synced issue links when issue sync runs.
- The scanner JSON is the stable handoff contract for future automation: it includes `schemaVersion`, `automationNamespace`, grouped issue `planKind`, and per-symbol `trackingKey` fields so a later AI-driven planner can split work into one issue per deprecated item while remaining restart-safe by searching managed markers before mutation.
- The workflow auto-labels the PR with `release`, assigns it to `RELEASE_OWNER` (default: `TheCookieLab`), and requests review from eligible `RELEASE_REVIEWERS` / `RELEASE_REVIEW_TEAMS` targets. If no reviewer list is configured, `RELEASE_OWNER` remains the default reviewer candidate only when that user did not author the PR. The PR author is always filtered from user reviewer requests because GitHub rejects self-review requests.
- A self-authored release PR with no eligible fallback reviewer/team leaves a warning in the logs, summary, and audit artifact instead of failing the review request. Normal branch protection still requires one approving review, so configure a non-author reviewer/team to merge without an admin or bypass path.
- In PR mode, the handoff is the release branch push plus release PR creation. Automatic mode keeps preparation, verification, promotion and the reusable publisher in one lifecycle concurrency group; standalone publishing uses the same group. A durable pending candidate prevents a new version bump while an earlier automatic release is unfinished.
- Opening a release PR automatically triggers freeze notices on other open PRs.

2. Review generated release PR
- Confirm release commit + next snapshot commit are present.
- Confirm release notes file exists (`release/<version>.md`).
- Confirm docs delta is complete for changed APIs/examples (README/wiki/examples index/changelog consistency).
- Confirm canonical documentation artifacts are present and linked (`Home`, `Getting-started`, journey/runbook/checklist/troubleshooting, decision matrix, migration map, expected outputs, performance characterization).
- If a release PR is open, wait for it to merge before merging any non-release PRs to `master`.

3. Merge release PR to `master`
- Use a **merge commit**.
- Do not squash/rebase release PRs.
- This merge is allowed because the `Release Merge Freeze` check allows only release PRs to merge during freeze.
- The same human merge pushes the next `-SNAPSHOT` commit to `master`, immediately starting `snapshot.yml`. Snapshot concurrency queues later master pushes instead of cancelling an active multi-module deployment.

4. Observe **Publish Release**
- Workflow validates release metadata and ancestry.
- Creates annotated tag.
- Deploys to Maven Central.
- Pushes tag only after successful deploy.
- Verifies pushed tag points to expected release commit and is reachable from default branch.

5. Observe post-publish workflows
- The `snapshot.yml` run started by the release PR merge should deploy the next snapshot, then retry an isolated Maven Dependency Plugin `3.11.0` consumer every 15 seconds for at most five minutes. Each attempt fetches cache-busted artifact metadata directly from the snapshot repository, resolves the resulting immutable parent/core/examples timestamped coordinates, and compares the core/examples JAR checksums with the publisher workspace. This avoids stale CDN `-SNAPSHOT` metadata causing a false verification failure.
- `github-release.yml` should run from tag push.
- `release-health.yml` may first report snapshot publication as pending during the async handoff, then should report `OK` after it retrieves the exact timestamped core POM/JAR. Top-level `<latest>` is informational only.

---

## 5. Dry-Run Runbook

Manual release workflows are inspection-first. `release-scheduler.yml`, `prepare-release.yml`, `publish-release.yml`, `github-release.yml`, `snapshot.yml`, and `release-health.yml` all default manual `workflow_dispatch` runs to `dryRun=true`.

Operator flow:
1. Run the workflow manually and leave `dryRun=true`.
2. Inspect the computed values in the workflow summary and audit artifacts: release version, next snapshot, tag, publish target, snapshot version, and planned mutation steps.
3. If the computed values are correct and mutation is intended, rerun the same workflow with `dryRun=false`.
4. If no manual mutation is needed, let the official scheduled, push, merge, tag-push, or workflow-run trigger continue; those paths normalize to `dryRun=false`.

Prepare dry-runs may leave `releaseVersion` and `nextVersion` blank where auto-detection is supported. Publish dry-runs require `releaseVersion`; `releaseCommit` remains optional and can be auto-detected.

Expected behavior:
- dry-run can warn about missing deploy secrets/resources.
- no managed cleanup issue sync, release PR creation, branch push, tag push, Maven Central deployment, GitHub Release creation, snapshot deployment, or discussion/comment mutation.
- prepare dry-runs still run push capability probes with `git push --dry-run`.
- prepare dry-runs run deprecation scans and upload report artifacts, but skip managed GitHub cleanup issue sync. If the release-ready gate finds due or overdue removals, the dry-run fails after the reports are available.
- publish dry-runs run the same metadata, ancestry, release-candidate, and artifact manifest checks without deploying. If the release tag already exists, the dry-run records a warning and continues so historical release validation remains possible.
- GitHub Release dry-runs build and validate the exact release artifact manifest from the selected tag while using workflow support files from the workflow commit.
- Release-candidate checks use the canonical `--validate-only` gate and its non-demo test-tag policy, including integration and slow tests. Formatter/license repairs are rejected, not applied.
- workflows upload audit artifacts such as release dossiers, decisions, manifests, logs, and tag-resolution files so failures can be diagnosed from the exact phase that produced them.
- `release-scheduler.yml` records deterministic eligibility reasons, evidence coverage, the validated assessment, the version plan and the final stage outcomes as separate artifacts. Production changes receive priority in bounded diff excerpts; API and Javadoc signals are extracted before diff sampling. Codex can inspect the exact candidate checkout when the excerpts are incomplete.
- The scheduler keeps its weekly Monday cron and 14-day cadence guard anchored at `2026-06-29`. Scheduled runs outside that window skip assessment, handoff and discussion updates. Due runs also require successful CI for the exact candidate, a usable release baseline, a snapshot POM, binary changes and no conflicting release lifecycle state.
- A missing, failed, malformed or unsupported assessment cannot dispatch preparation. A release recommendation must identify the assessed commit, provide evidence and have no unresolved blockers or missing evidence. A justified deferral and insufficient evidence remain distinct outcomes. Review the named failing stage before starting another billed run; the workflow does not substitute a fallback release decision.

---

## 6. Workflow Map (Trigger + Responsibility)

| Workflow | Trigger(s) | Primary responsibility | Critical guardrails |
|---|---|---|---|
| `release-scheduler.yml` | schedule, manual | decide whether/how to release through named eligibility, context, assessment, policy, handoff and outcome jobs | manual runs default dry-run; biweekly cadence; exact-commit CI and lifecycle checks; strict assessment policy, monotonic version and tag checks; source SHA rechecked at handoff |
| `reusable-release-assessment.yml` | reusable call from scheduler | assess frozen evidence with Codex and optionally compare Luna 6 Decisions | official action and SDK; read-only assessment; schema and source validation; optional comparison has no release authority |
| `prepare-release.yml` | manual (or scheduler dispatch) | prepare PR or verify/promote/publish an automatic branch candidate | manual runs default dry-run; scheduler passes `dryRun` and `sourceSha`; stale assessment rejected before freeze; docs-integrity checks, version validation, metadata validation, dry-run push capability probes |
| `publish-release.yml` | merged release PR close, manual, reusable call after promotion | release-candidate verification + tag + Maven Central deploy + release summary; manual recovery also dispatches snapshot | manual runs default dry-run; merged release PRs normalize to production and rely on the existing master-push snapshot run; merge discipline + ancestry checks, artifact manifest checks, post-push tag integrity/reachability checks |
| `release-health.yml` | push to `master`, publish workflow completion, snapshot workflow completion, schedule, manual | detect drift in release state | manual runs default dry-run; non-manual triggers normalize to production; fails on tag reachability drift, snapshot version drift, missing exact snapshot POM/JAR once publication is authoritative, missing notes, stale release PRs |
| `github-release.yml` | semver-like tag push, manual | GitHub release publication | manual runs default dry-run; tag pushes normalize to production; semver tag validation, exact artifact manifest using current workflow support files |
| `snapshot.yml` | push to `master`, manual/recovery dispatch | publish and externally consume snapshots | manual runs default dry-run; master pushes normalize to production; publications queue rather than cancel; a non-dry-run succeeds only after exact isolated Maven resolution and checksum verification |

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
4. Maven Central release artifacts are retrievable.
5. GitHub release exists with expected notes/artifacts.
6. Release-ready deprecation gate report exists for the released version and did not find due or overdue removals.
7. Removal-ready deprecation report artifact exists for the new snapshot version, due or overdue removal versions were included, and any matching cleanup issues were created, refreshed, reopened, or closed as stale successfully.
8. The `snapshot.yml` run started by the human release-PR merge succeeded for the next `-SNAPSHOT` version, with `snapshot-consumption.json` showing `mavenConsumable=true` and matching publisher/resolved checksums.
9. `master` is on next `-SNAPSHOT` version.
10. `release-health.yml` reports no drift and the same resolved timestamped snapshot coordinate after `snapshot.yml` completes.

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
   - `current snapshot version POM/JAR not consumable from Maven snapshot repository`: inspect `snapshot-consumption.json` and `snapshot-consumption.log`, fix deployment or propagation failures, and rerun health after an isolated Maven consumer resolves the exact artifacts.
   - `missing release notes for latest tag`: add `release/<version>.md`.
   - `stale release PRs detected`: merge or close stale release PRs.
3. Re-run health via `workflow_dispatch`.
4. If Maven deploy already succeeded, treat this as repo-state remediation, not republish.

### 8.2 Release PR waiting for merge

- Ensure required checks pass.
- Ensure required maintainer approval exists.
- Merge with merge commit.
- If the release owner authored the PR, confirm `RELEASE_REVIEWERS` or `RELEASE_REVIEW_TEAMS` names a non-author reviewer/team. Without one, branch protection can still require an admin or bypass merge even though `prepare-release.yml` no longer fails while requesting review.

### 8.3 Tag exists error

- Version already tagged.
- Use a new version unless you have an explicit rollback/recovery plan.

### 8.4 Release notes missing

- Ensure `release/<version>.md` exists in the release commit.

### 8.5 Automatic candidate failure or branch drift

- A verification failure leaves master, tags and Maven release publication untouched. The candidate and `release-pending/<version>` remain for diagnosis. Receipt digests are exported by successful jobs; immutable Actions artifacts retain the logs, manifests and receipts for 30 days.
- If B or the candidate changes, receipts are invalid. Promotion fails closed; there is no automatic history replacement or rollback. The durable automatic freeze blocks ordinary merges. If an already in-flight merge or the existing bypass advances master, retain the freeze until the outcome is reconciled, then prepare and fully validate a fresh candidate.
- Before promotion, do not rerun prepare over an existing candidate: the durable pending guard intentionally blocks another version preparation, and a new run/attempt invalidates old receipts. Inspect master, candidate, validation reports and publication journals and prove that neither promotion nor publication occurred. To abandon that candidate, a maintainer removes only its exact candidate and pending refs using explicit expected-S leases. If a freeze remains, also consume only this run's preparation marker at expected B or its version marker at expected S with an exact lease; never remove another release's marker. Run the Release Merge Freeze reconciliation workflow to clear only obsolete status overlays, then fix any source failure through the ordinary PR process and start fresh B/R/S with complete validation. Do not delete publication journal refs as part of candidate cleanup. If the outcome is uncertain, keep the freeze and investigate instead.
- After promotion, resume the failed publication of the **same R** (rerun the failed publication job or manually dispatch `publish-release.yml` with that version/R). Do not rerun prepare to manufacture a second version bump. Never roll master back.

### 8.6 Publication checkpoints and partial success

Before the first Maven upload, the publisher creates `release-publishing/<version>` pointing at R. Only a successful Maven `deploy` (configured to wait until published) creates `release-published/<version>`, also at R, before the tag is pushed.

- No checkpoint: publication may start after all validation gates pass.
- Both checkpoints at R: skip Maven deploy and finish/verify the tag and downstream handoff. This covers a successful deploy followed by a tag-push failure. An existing matching tag is accepted only with this completed checkpoint; a mismatched tag fails.
- Start checkpoint without completion: deployment may be partial or complete. Stop for operator reconciliation; never blindly redeploy. Inspect the Central deployment and published coordinates, including the run's manifest and deploy log. After confirming full publication, a maintainer can record the completion ref at the same R and resume. If Central definitively accepted no upload, a maintainer can remove the start ref with an explicit expected-R lease and retry the same R. Do not remove it based only on a missing/eventually-consistent Central search result.
- Conflicting checkpoint SHAs: fail closed and investigate.

The checkpoint refs are an operational journal, not evidence that deployed bytes equal candidate bytes. The publisher rebuilds from exact R; it repeats the canonical build, release-ready deprecation gate, tracked-tree cleanliness, strict production artifact/Maven metadata inventory and Javadoc baseline before credentials are installed. The deployment then rebuilds again. Hash manifests provide audit evidence, **not byte-for-byte promotion of the preverified JARs**. Signing and external Maven publishing are not atomic with Git refs; a failure between upload and recording completion remains deliberately ambiguous.

The durable `release-pending/<version>` at S blocks later automatic preparations until a completed publication checkpoint and tag identify R. Keep these journal refs until the release has been reconciled. Do not infer release success from master containing S alone.

### Expired or unusable OpenPGP signing key

Non-dry-run release and snapshot jobs sign and verify a temporary payload before publication (and before the snapshot build). A failure means the imported key or passphrase needs attention; dry-runs do not exercise signing. Maven Central requires OpenPGP signatures; SSH keys used for Git signing are not a substitute.

1. Inspect the failing run and the imported key's public fingerprint, signing capability and expiration. For the current publishing key, the expected fingerprint is `43C18A2E124CF2177A50A71A0C40BB6B2A100CD4`. If expired, an authorized maintainer must run `bash scripts/release/renew_signing_key.sh` from a reviewed, trusted checkout outside Actions, with GnuPG, GitHub CLI, jq and curl installed. Supply `SIGNING_FINGERPRINT`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `GITHUB_REPOSITORY` and a `GH_TOKEN` with repository Secrets write permission through a secure local environment. The helper renews the same primary key and subkeys for two years, proves the exported secret can sign and verify, uploads the renewed public key to `keys.openpgp.org`, and updates `GPG_PRIVATE_KEY`. No dispatchable administrative renewal workflow is retained: branch-controlled renewal code must not receive signing secrets or a secrets-administration token. Update `GPG_PASSPHRASE` if needed. Do not silently replace the publishing identity or print private key material or the passphrase in logs.
2. Keep the release freeze and publication journal refs. Check the failed run and Central's deployment state: a preflight failure before `publication-start` creates no new start checkpoint or upload, but an existing start checkpoint still requires the reconciliation in section 8.6. Remove a start ref only after definitively proving Central accepted no upload, with an expected-R lease.
3. After reconciliation, resume publication of the original version and exact R; do not prepare a new version or blindly redeploy. If the completed checkpoint already exists, follow section 8.6 to finish the tag. Run the snapshot publisher separately as needed.

### 8.7 Dry-run warnings

- Missing deploy secrets in dry-run are expected warnings.
- Fix before production run.

### 8.8 Finding the failure point

Start with the scheduler's Actions graph and **Decision path and outcome** summary to distinguish completed, failed and skipped stages. Assessment jobs retain explicitly selected evidence and results; preparation and publishing retain their phase-specific audit artifacts.

Look for these files first:
- `scheduler-outcome.json` / `scheduler-outcome.md`: the decision path, eligibility blockers, source SHA, version and handoff outcome.
- `context.json`: the release policy, cadence, exact-commit CI evidence and deterministic eligibility decision; the frozen assessment copy also includes diff coverage.
- `release-dossier.md` and `release-assessment-manifest.json`: selected change evidence, explicit omissions and hashes binding the evidence to its source commit.
- `assessment-result.json` / `assessment-summary.md`: the validated Codex outcome, bounded supporting evidence and optional Luna comparison. `codex-decision.json` retains the typed provider result; `luna-result.json` records the optional evaluation's result or availability status.
- `release-plan.json`: the validated recommendation, planned version, source SHA and dry-run flag passed toward handoff.
- `release-audit.json`: workflow-local release metadata.
- `tag-resolution.txt`: resolved latest/reachable/first-parent tag state.
- `artifact-manifest.txt`: exact jars expected for publish/GitHub Release.
- `javadoc-warnings.txt`: Javadoc warning baseline comparison from release artifact/deploy logs; new warnings beyond the tracked `scripts/release/javadoc-warning-baseline.txt` debt fail publish.
- `.agents/logs/full-build-*.log`: release-candidate full build log.
- `snapshot-consumption.json`: exact timestamped parent/core/examples coordinates obtained from cache-busted metadata, publisher and resolved JAR checksums, `metadataError` for a failed fresh metadata fetch, attempts, elapsed time, and `mavenConsumable` result.
- `snapshot-consumption.log`: bounded, redacted Maven consumer attempts used by the post-deploy gate.

If a grouped section fails, inspect the matching artifact before rerunning. Avoid rerunning publish until tag state, artifact state, and Central deployment state are understood.

Snapshot consumers must configure `https://central.sonatype.com/repository/maven-snapshots/` with snapshots enabled. Normal release search and Portal snapshot browsing are not consumability checks; browsing can be unavailable while Maven publishing and resolution continue to work. A manually started `snapshot.yml` run defaults to `dryRun=true` and intentionally skips both deployment and external consumption verification unless `dryRun=false` is selected.

### 8.9 Scheduler assessment or handoff failure

Inspect the first failed or blocked named stage and its structured result before rerunning:

- **Release eligibility:** resolve the fixed blocker codes in `context.json`, such as candidate CI still running, a release freeze or an unfinished prior publication. `aiMode=skip` exercises these checks without model charges.
- **Context preparation:** investigate missing or inconsistent evidence and its source identity. Do not repair a manifest by changing hashes manually; regenerate the evidence from the intended commit.
- **Release assessment:** inspect the official action's failure and the validated assessment result. Confirm the API key, configured model and runner requirements. A refusal, invalid response or unavailable result leaves handoff disabled. Luna comparison failures do not replace or authorize a Codex decision.
- **Release policy and version:** inspect the assessment recommendation, deterministic preconditions and tag collision. Insufficient evidence requires investigation; it is not equivalent to a decision that the changes lack release value.
- **Release handoff:** if the default branch moved after assessment, start a new scheduler run for the new commit. If preparation started and failed, inspect its freeze and publication state before retrying that lifecycle.

The [Release assessment reference](docs/release/reusable-assessment.md) describes runner setup, pinned dependencies, evidence limits and why each retained output exists. The workflow does not retain custom HTTP request/response dumps, headers or provider reasoning transcripts as diagnostic artifacts.

---

## 9. Discussion Posts (Markers and Cleanup)

Non-dry-run release-related workflows post to GitHub Discussions with machine-readable markers:

```html
<!-- ta4j:post-type=<type>;run=<real|dry-run> -->
```

Post types:
- `release-scheduler`
- `publish-release`
- `release-health`

Cleanup rules:
- manual dry-runs do not create, update, or delete discussion comments; their dry-run details remain in workflow summaries and audit artifacts.
- `release-health`: removes prior real health posts before posting the latest real summary.
- `release-scheduler`: posts real scheduler summaries only.
- `publish-release`: posts real publish summaries and keeps historical posts as an audit trail.

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
| `OPENAI_API_KEY` | reusable release assessment | official Codex action authentication and optional Luna Decisions SDK evaluation |
| `GH_TA4J_REPO_TOKEN` | prepare, publish, github-release | PAT for release pushes/release creation |

### 10.2 Optional secrets

| Secret | Used by | Purpose |
|---|---|---|
| `MAVEN_MASTER_PASSPHRASE` | publish, snapshot | optional Maven settings-security |

### 10.3 Variables

| Variable | Used by | Purpose |
|---|---|---|
| `RELEASE_DIRECT_PUSH` | prepare | direct-push mode switch |
| `RELEASE_OWNER` | prepare | release PR assignee and default reviewer candidate |
| `RELEASE_REVIEWERS` | prepare | optional comma/whitespace/newline-separated fallback reviewer users; the PR author is filtered out |
| `RELEASE_REVIEW_TEAMS` | prepare | optional comma/whitespace/newline-separated fallback team slugs for release PR review requests |
| `RELEASE_NOTIFY_USER` | publish, health | discussion mention target |
| `RELEASE_DISCUSSION_NUMBER` | publish | publish summary discussion |
| `RELEASE_SCHEDULER_DISCUSSION_NUMBER` | scheduler, health | scheduler/health discussion |
| `RELEASE_SCHEDULER_ENABLED` | scheduler | enable scheduled release decisions |
| `RELEASE_SCHEDULER_RUNNER` | scheduler | JSON runner label string or array; defaults to `["ubuntu-latest"]` |
| `RELEASE_ASSESSMENT_RUNNER` | reusable release assessment | optional assessment runner override; otherwise use the scheduler runner |
| `RELEASE_ASSESSMENT_MODEL` | reusable release assessment | Codex model override; defaults to `gpt-6.1-sol` |
| `RELEASE_ASSESSMENT_USER` | reusable release assessment | dedicated preprovisioned unprivileged account required for self-hosted Codex assessment; see the assessment reference |
| `RELEASE_LUNA_EVALUATION_ENABLED` | reusable release assessment | set to `true` to enable the additional Luna 6 Decisions comparison; comparison does not authorize releases |
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
| publish GitHub release | no | yes (non-dry-run) |
| release drift auditing | no | yes |

---

## 13. RC Builds

RC versions are supported if they are Maven-valid (for example `0.22.2-rc1`).
Use the same workflows and verification checklist.
