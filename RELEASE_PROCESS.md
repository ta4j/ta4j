# Ta4j Release Process

This document explains how the Ta4j release workflow works end-to-end, including
developer responsibilities, changelog practices, how snapshots are published,
and how new stable releases are produced and published to Maven Central.
Automated release gating and scheduling lives in `.github/workflows/release-scheduler.yml`, which evaluates changes, calls GitHub Models, and dispatches the main release workflow when criteria are met (or when manually requested with `dryRun`).

The process is intentionally simple: GitHub Actions performs all version
management, deployment, and tagging. Contributors only maintain documentation
(changelog + README).

---

## Overview

Ta4j uses four coordinated components:

1. **prepare-release.sh**  
   Updates CHANGELOG and README, and generates `release/<version>.md` notes.
   Does *not* bump versions or perform SCM operations.

2. **Release Scheduler (`release-scheduler.yml`)**  
   - Triggered on a 14-day cron or manual dispatch (`dryRun` supported).  
   - Collects diff + Unreleased changelog, sanitizes them, and asks GitHub Models for a SemVer bump.  
   - Requires `GH_MODELS_TOKEN` (PAT authorized for GitHub Models). If missing, it short-circuits.  
   - Computes the next tag (no leading `v`).  
   - Always dispatches `release.yml`, passing through `releaseVersion`, `nextVersion` (optional), and `dryRun`.  
   - Emits a decision summary (gate, token, AI verdict, bump, version, dryRun).

3. **GitHub Actions Release Workflow (`release.yml`)**  
   - Sets the release version in the POM.  
   - Commits the version.  
   - Creates a release tag (skipped on `dryRun=true`).  
   - Builds, signs, and deploys to Maven Central (skipped on `dryRun=true`).  
   - Bumps the next snapshot version and commits/pushes it (skipped on `dryRun=true`).  
   Inputs: `releaseVersion` (e.g., `0.20.0`, no `v`), `nextVersion` (optional), `dryRun` (boolean).

4. **Snapshot Workflow (`snapshot.yml`)**  
   Automatically publishes snapshot builds to Central on every push to `master`.

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

## Release Workflow (Step-by-Step)

You can reach the release workflow two ways:
- **Automated**: `release-scheduler.yml` runs on a 14-day cadence (or manual dispatch) and dispatches `release.yml`, passing along `releaseVersion`, optional `nextVersion`, and `dryRun`.
- **Manual**: You can run `release.yml` directly via **GitHub → Actions → Publish Release to Maven Central → Run Workflow**.

### **1. Prepare documentation locally**

Run the helper:

```
scripts/prepare-release.sh <version>
```

Example:

```
scripts/prepare-release.sh 0.20
```

This will:

- roll forward the `Unreleased` entries into a `## 0.20 (YYYY-MM-DD)` section  
- reset `Unreleased` to contain a placeholder  
- update version snippets in README  
- generate a standalone file:  
  ```
  release/0.20.md
  ```

### **2. Commit the updated documentation**

```
git add CHANGELOG.md README.md release/
git commit -m "Prepare release 0.20"
git push
```

### **3. Trigger (or wait for) the Release Workflow**

If running manually, go to **GitHub → Actions → Publish Release to Maven Central → Run Workflow** and provide:

- **releaseVersion** — e.g., `0.20.0` (no leading `v`)
- **nextVersion** — e.g., `0.21.0-SNAPSHOT` (optional; auto-computed if blank)
- **dryRun** — `true` to validate without tagging/pushing/deploying

If using the scheduler, it will supply `releaseVersion` (no `v`) and pass through `dryRun` from its trigger.

The release workflow will:

- update POM version to the release version  
- commit and tag the release (skipped on `dryRun=true`)  
- build, sign, and deploy to Maven Central (skipped on `dryRun=true`)  
- bump to next snapshot version  
- commit and push the snapshot POM (skipped on `dryRun=true`)  
- automatically publish a GitHub Release (via release notes automation)

No local Maven commands are required.

---

## Snapshot Publishing

Every push to `master` runs the snapshot workflow:

```
mvn -B deploy
```

Publishing goes to:

```
https://central.sonatype.com/repository/maven-snapshots/
```

Snapshots do **not** use the release profile and are deployed automatically.

---

## Reproducibility Notes

- All release artifacts are signed with GPG keys stored in GitHub Secrets.
- The release workflow performs full builds via Maven with Central Publishing.
- Releases are deterministic when built from a tag.

---

## Cutting RC (“Release Candidate”) Builds

If RC support is later enabled:

- RCs should be named `0.20-rc1`, `0.20-rc2`, etc.
- You would run the same workflow but provide the RC version.

(Currently RCs are not required, but the system is compatible.)

---

## Summary

The release process ensures:

- minimal burden on contributors  
- 100% reproducible builds  
- fully automated publishing  
- stable, predictable release versioning  

The only manual requirement is keeping the CHANGELOG and README accurate.
