# Ta4j Release Process

This document explains how the Ta4j release workflow works end-to-end, including
developer responsibilities, changelog practices, how snapshots are published,
and how new stable releases are produced and published to Maven Central.
Automated release gating and scheduling lives in `.github/workflows/release-scheduler.yml`, which evaluates changes, calls GitHub Models (with a short explanation of the decision), and dispatches the main release workflow when criteria are met (or when manually requested with `dryRun`).

The process is intentionally simple: GitHub Actions performs all version
management, deployment, and tagging. Contributors only maintain documentation
(changelog + README).

---

## Quick Start

**For a typical release:**

1. Update `CHANGELOG.md` with changes under the `Unreleased` section
2. Run `scripts/prepare-release.sh 0.20.0` (use `major.minor.patch` format)
3. Commit and push: `git add CHANGELOG.md README.md release/ && git commit -m "Prepare release 0.20.0" && git push`
4. Trigger the release workflow manually or wait for the automated scheduler

**To test the release process without publishing:**

1. Follow steps 1-3 above
2. Go to **GitHub → Actions → Publish Release to Maven Central → Run Workflow**
3. Set `dryRun` to `true` and provide the version (e.g., `0.20.0`)

**Prerequisites:**

- Local tools: `git`, `perl`, `python3` (for `prepare-release.sh`)
- Repository secrets configured (see [Required Resources](#required-resources))
- Write access to the repository
- Releases must be triggered from the `master` branch

---

## Overview

Ta4j uses five coordinated components:

1. **prepare-release.sh**  
   Updates CHANGELOG and README, and generates `release/<version>.md` notes.
   Does *not* bump versions or perform SCM operations.
   - **Input**: Version in `major.minor.patch` format (e.g., `0.20.0`)
   - **Output**: Updated `CHANGELOG.md`, `README.md`, and `release/<version>.md`

2. **Release Scheduler (`release-scheduler.yml`)**  
   - Triggered on a 14-day cron or manual dispatch (`dryRun` supported).  
   - Collects diff + Unreleased changelog, sanitizes them, and asks GitHub Models for a SemVer bump with a 1–2 sentence rationale.  
   - Requires `GH_MODELS_TOKEN` (PAT authorized for GitHub Models). If missing, it short-circuits.  
   - Computes the next tag (no leading `v`), basing the bump on the higher of the current POM base or the last default-branch tag to avoid double-bumping snapshots.  
   - Always dispatches `release.yml`, passing through `releaseVersion`, `nextVersion` (optional), the AI reasoning, and `dryRun`.  
   - Emits a decision summary (gate, token, AI verdict, bump, version, reason, dryRun).
   - For major releases, requires manual approval via the `major-release` environment.

3. **GitHub Actions Release Workflow (`release.yml`)**  
   - Sets the release version in the POM using the Maven Versions Plugin.  
   - Commits the version and release notes.  
   - Creates a release tag (skipped on `dryRun=true`).  
   - Verifies the branch is still fast-forwardable, then builds, signs, and deploys to Maven Central (skipped on `dryRun=true`).  
   - Bumps to the next snapshot with the Maven Versions Plugin and commits it (skipped on `dryRun=true`).  
   - Pushes only the tag (after a successful deploy) and opens a pull request (`release/<version>` → `master`) containing both the release commit and the next-snapshot bump. No direct push to `master` is performed.  
   - **Inputs**: `releaseVersion` (e.g., `0.20.0`, no leading `v`), `nextVersion` (optional), `dryRun` (boolean).
   - **Version formats supported**: `major.minor.patch` (e.g., `0.20.0`)

4. **GitHub Release Workflow (`github-release.yml`)**  
   - Automatically triggered when a release tag is pushed.
   - Reads `release/<version>.md` for release notes.
   - Builds all module artifacts (JARs, sources, javadoc).
   - Creates a GitHub Release with artifacts attached, preserving the markdown formatting of `release/<version>.md` via `body_path`.
   - **Note**: This runs automatically after `release.yml` creates a tag.

5. **Snapshot Workflow (`snapshot.yml`)**  
   Automatically publishes snapshot builds to Central on every push to `master`.

---

## Required Resources

The release process depends on several GitHub repository secrets and configurations that must be set up before releases can be published. These are configured in **GitHub → Settings → Secrets and variables → Actions**.

### Required Repository Secrets

The following secrets are **mandatory** for the release workflows to function:

| Secret Name | Used By | Purpose |
|------------|---------|---------|
| `MAVEN_CENTRAL_TOKEN_USER` | `release.yml`, `snapshot.yml` | Maven Central authentication username (Sonatype token username) |
| `MAVEN_CENTRAL_TOKEN_PASS` | `release.yml`, `snapshot.yml` | Maven Central authentication password (Sonatype token password) |
| `GPG_PRIVATE_KEY` | `release.yml`, `snapshot.yml` | GPG private key (ASCII-armored) for signing artifacts before publishing to Maven Central |
| `GPG_PASSPHRASE` | `release.yml`, `snapshot.yml` | Passphrase for the GPG private key |
| `GH_MODELS_TOKEN` | `release-scheduler.yml` | GitHub Models API token (PAT authorized for GitHub Models) for AI-powered semantic versioning decisions |

### Optional Repository Secrets

| Secret Name | Used By | Purpose |
|------------|---------|---------|
| `MAVEN_MASTER_PASSPHRASE` | `release.yml`, `snapshot.yml` | Optional Maven master password for `settings-security.xml` encryption. If not set, the workflow skips creating this file. |

### Required GitHub Environment

For **major releases**, a protected environment must be configured:

- **Environment Name**: `major-release`
- **Location**: **GitHub → Settings → Environments**
- **Purpose**: Requires manual approval before major version releases are published
- **Configuration**: Add required reviewers who can approve/reject major releases

The `release-scheduler.yml` workflow will pause at the approval step for major releases until an authorized reviewer approves the release in the GitHub Actions UI.

### Secret Verification

The `release.yml` workflow includes a verification step that checks for all required secrets before proceeding. If any required secrets are missing:
- In **dry-run mode**: The workflow continues with warnings
- In **normal mode**: The workflow fails immediately with an error listing missing secrets

### Setting Up Secrets

1. **Maven Central Credentials**: Create a token in [Sonatype Central](https://central.sonatype.com/) with appropriate permissions for publishing to the `org.ta4j` group.
2. **GPG Key**: Generate a GPG key pair and export the private key (ASCII-armored) for signing artifacts. The public key must be published to a keyserver.
3. **GitHub Tokens**: Create Personal Access Tokens (PATs) with appropriate scopes:
   - `GH_MODELS_TOKEN`: Requires authorization for GitHub Models API access (used by the scheduler)

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

**Prerequisites:**
- Ensure you're on the `master` branch and up to date: `git checkout master && git pull`
- Verify you have `git`, `perl`, and `python3` installed locally

**Update CHANGELOG.md:**
- Add all changes to the `Unreleased` section under appropriate categories (Added, Changed, Fixed, etc.)
- Follow the [Keep a Changelog](https://keepachangelog.com/) format

**Run the preparation script:**

```bash
scripts/prepare-release.sh <version>
```

**Version format:** Use `major.minor.patch` format (e.g., `0.20.0`).

Example:

```bash
scripts/prepare-release.sh 0.20.0
```

**What the script does:**

- Rolls forward the `Unreleased` entries into a `## 0.20.0 (YYYY-MM-DD)` section with today's date
- Resets `Unreleased` to contain a placeholder (`- _No changes yet._`)
- Validates the README sentinel blocks (`TA4J_VERSION_BLOCK:*`) and fails fast if any are missing
- Updates version snippets in `README.md` (Maven dependency examples)
- Generates a standalone release notes file: `release/0.20.0.md`

**Important:** The script requires `major.minor.patch` (e.g., `0.20.0`). The release workflow now validates patch-formatted release/next versions before running `prepare-release.sh`.

### **2. Commit the updated documentation**

```
git add CHANGELOG.md README.md release/
git commit -m "Prepare release 0.20.0"
git push
```

### **3. Trigger (or wait for) the Release Workflow**

#### **Option A: Manual Trigger**

1. Go to **GitHub → Actions → Publish Release to Maven Central → Run Workflow**
2. Select the `master` branch (releases must be from `master`)
3. Provide workflow inputs:
   - **releaseVersion** — e.g., `0.20.0` (no leading `v`)
     - Must be `major.minor.patch` to satisfy `prepare-release.sh`
     - If blank, auto-detects from current POM version (must be a SNAPSHOT)
   - **nextVersion** — e.g., `0.20.1-SNAPSHOT` (optional; auto-computed if blank)
     - Auto-increments: `0.20.0` → `0.20.1-SNAPSHOT`
   - **dryRun** — Set to `true` to validate without tagging/pushing/deploying
     - Recommended for first-time releases or testing changes
4. Click **Run workflow**

#### **Option B: Automated Scheduler**

The scheduler (`release-scheduler.yml`) runs every 14 days and:
- Analyzes changes since the last release
- Uses AI to determine the appropriate version bump (patch/minor/major)
- Automatically dispatches `release.yml` with the computed version
- For major releases, waits for manual approval via the `major-release` environment

You can also trigger the scheduler manually:
- Go to **GitHub → Actions → AI Semantic Release Scheduler → Run Workflow**
- Optionally set `dryRun` to `true` to test

#### **What the Release Workflow Does**

The `release.yml` workflow executes these steps in order:

1. **Verification**: Checks for required secrets (fails if missing, warns in dry-run)
2. **Version Detection**: Determines release and next snapshot versions
3. **Release Notes**: Runs `prepare-release.sh` to ensure release notes are up to date
4. **Version Bump**: Updates POM to release version using the Maven Versions Plugin (skipped in dry-run)
5. **Commit & Tag**: Commits version change (with release notes) and creates release tag (skipped in dry-run)
6. **Safety Check**: Verifies branch hasn't advanced (prevents race conditions)
7. **Build & Deploy**: Builds, signs, and deploys to Maven Central (skipped in dry-run)
8. **Next Snapshot**: Bumps to next snapshot version with the Maven Versions Plugin and commits (skipped in dry-run)
9. **Publish & PR**: Pushes the release tag only (after successful deploy) and opens a pull request `release/<version> -> master` containing the release commit and the next-snapshot bump (skipped in dry-run)

**After the tag is created and pushed**, the `github-release.yml` workflow automatically:
- Creates a GitHub Release using `release/<version>.md` as the description
- Attaches all built artifacts (JARs, sources, javadoc) to the release

**No local Maven commands are required** — everything happens in GitHub Actions.

---

## Snapshot Publishing

Every push to `master` automatically triggers the snapshot workflow (`snapshot.yml`), which:

1. Builds and tests the project
2. Signs artifacts with GPG
3. Deploys to Maven Central Snapshots repository

**Snapshot repository:**
```
https://central.sonatype.com/repository/maven-snapshots/
```

**Key points:**
- Snapshots use the `sign-snapshots` Maven profile (not the full `production-release` profile)
- No manual intervention required — happens automatically on every push to `master`
- Snapshot versions end with `-SNAPSHOT` (e.g., `0.21.0-SNAPSHOT`)
- Users can depend on snapshots using the snapshot repository URL in their `pom.xml`

---

## Verifying a Release

After triggering a release, verify it completed successfully:

### **1. Check Workflow Status**

- Go to **GitHub → Actions** and find the workflow run
- Verify all steps completed successfully (green checkmarks)
- In dry-run mode, check that validation passed

### **2. Verify Git State**

```bash
git fetch --tags
git tag | grep <version>  # e.g., git tag | grep 0.20.0
```

The release tag should exist (unless in dry-run mode).

### **3. Check Maven Central**

Releases appear on Maven Central after a few minutes:
- Search: https://central.sonatype.com/search?q=org.ta4j
- Direct: https://repo1.maven.org/maven2/org/ta4j/

**Note:** It may take 10-30 minutes for artifacts to sync and become searchable.

### **4. Verify GitHub Release**

- Go to **GitHub → Releases**
- The release should appear with:
  - Correct version tag
  - Release notes from `release/<version>.md`
  - Attached artifacts (JARs, sources, javadoc)

### **5. Check Next Snapshot Version**

```bash
git checkout master
git pull
# Check pom.xml - version should be the next snapshot (e.g., 0.21.0-SNAPSHOT)
```
Also verify the release PR (`release/<version>`) is open and contains both the release commit and the next-snapshot bump; merge when ready.

---

## Troubleshooting

### **Workflow Fails with "Missing Required Secrets"**

**Problem:** One or more required secrets are not configured.

**Solution:**
1. Go to **GitHub → Settings → Secrets and variables → Actions**
2. Verify all required secrets are present (see [Required Resources](#required-resources))
3. Check secret names match exactly (case-sensitive)
4. Re-run the workflow

### **Workflow Fails During Maven Central Deployment**

**Problem:** Authentication or signing issues.

**Solutions:**
- Verify `MAVEN_CENTRAL_TOKEN_USER` and `MAVEN_CENTRAL_TOKEN_PASS` are correct
- Check that the GPG key is valid and the public key is published to a keyserver
- Verify `GPG_PASSPHRASE` matches the GPG key
- Check Sonatype Central for error messages or pending requests

### **Tag Already Exists Error**

**Problem:** The release tag already exists in the repository.

**Solutions:**
- If the previous release succeeded, you may be trying to re-release the same version
- Use a new version number
- If the previous release failed partway through, you may need to:
  1. Delete the tag: `git push origin --delete <version>` (if safe to do so)
  2. Or use a patch version increment (e.g., `0.20.1` instead of `0.20.0`)

### **Release Notes File Not Found**

**Problem:** `github-release.yml` fails because `release/<version>.md` doesn't exist.

**Solution:**
- Ensure you ran `scripts/prepare-release.sh <version>` before triggering the release
- Verify the file exists: `ls release/<version>.md`
- Commit and push the file before triggering the release workflow

### **Branch Advanced During Release**

**Problem:** Workflow fails with "Remote branch advanced while the release workflow was running."

**Solution:**
- This is a safety check to prevent race conditions
- Ensure no one pushes to `master` while a release is in progress
- Re-run the workflow after resolving any conflicts

### **Version Format Errors**

**Problem:** Workflow fails with version format validation errors.

**Solutions:**
- Use `major.minor.patch` (e.g., `0.20.0`) format
- Do not include `-SNAPSHOT` in the release version
- Ensure `nextVersion` ends with `-SNAPSHOT` if provided manually
- Do not include a leading `v` prefix

### **Major Release Approval Not Working**

**Problem:** Major release workflow is stuck waiting for approval.

**Solution:**
1. Verify the `major-release` environment exists: **GitHub → Settings → Environments**
2. Ensure you're listed as a required reviewer
3. Go to the workflow run in **GitHub → Actions**
4. Click **Review deployments** and approve the `major-release` environment

### **Dry-Run Shows Warnings but Continues**

**Expected behavior:** In dry-run mode, missing secrets show warnings but the workflow continues for validation purposes. This is normal and allows you to test the workflow structure without all secrets configured.

---

## Common Scenarios

### **Scenario 1: First Release After Setup**

1. Verify all secrets are configured (see [Required Resources](#required-resources))
2. Test with dry-run: Trigger `release.yml` with `dryRun=true`
3. Review workflow logs to ensure everything validates
4. Run a real release with `dryRun=false`

### **Scenario 2: Hotfix Release (Patch Version)**

1. Create a hotfix branch from the release tag: `git checkout -b hotfix/0.20.1 0.20.0`
2. Make fixes and merge to `master`
3. Update `CHANGELOG.md` under `Unreleased`
4. Run `scripts/prepare-release.sh 0.20.1` (use patch version)
5. Commit and push
6. Trigger release with `releaseVersion=0.20.1`

### **Scenario 3: Testing Release Process**

1. Use dry-run mode: Set `dryRun=true` in workflow inputs
2. Review all workflow steps in the Actions UI
3. Check that version detection, validation, and preparation steps succeed
4. Verify no actual tags or deployments occur
5. Once confident, run with `dryRun=false`

### **Scenario 4: Automated Release via Scheduler**

1. Ensure `GH_MODELS_TOKEN` is configured
2. Wait for the 14-day cron schedule, or trigger manually
3. Review the scheduler's AI decision in the workflow logs
4. For major releases, approve via the `major-release` environment
5. Monitor the dispatched `release.yml` workflow

---

## Reproducibility Notes

- All release artifacts are signed with GPG keys stored in GitHub Secrets.
- The release workflow performs full builds via Maven with Central Publishing.
- Releases are deterministic when built from a tag.
- The same tag will always produce the same artifacts.

---

## Cutting RC ("Release Candidate") Builds

If RC support is later enabled:

- RCs should be named `0.20.0-rc1`, `0.20.0-rc2`, etc.
- You would run the same workflow but provide the RC version.
- The workflow supports any version format that Maven accepts.

(Currently RCs are not required, but the system is compatible.)

---

## Summary

The release process ensures:

- **Minimal burden on contributors** — Only documentation maintenance required
- **100% reproducible builds** — Deterministic artifacts from tags
- **Fully automated publishing** — No manual Maven commands needed
- **Stable, predictable release versioning** — AI-assisted semantic versioning with human oversight for major releases

**The only manual requirement is keeping the CHANGELOG and README accurate.**

For questions or issues, check the workflow logs in **GitHub → Actions** or review the workflow files in `.github/workflows/`.
