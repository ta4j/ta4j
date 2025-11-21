# Ta4j Release Process

This document explains how the Ta4j release workflow works end-to-end, including
developer responsibilities, changelog practices, how snapshots are published,
and how new stable releases are produced and published to Maven Central.

The process is intentionally simple: GitHub Actions performs all version
management, deployment, and tagging. Contributors only maintain documentation
(changelog + README).

---

## Overview

Ta4j uses three coordinated components:

1. **prepare-release.sh**  
   A helper script that updates CHANGELOG and README, and generates release notes.
   It does *not* bump versions or perform SCM operations.

2. **GitHub Actions Release Workflow (`release.yml`)**  
   Handles:
   - setting the release version in the POM  
   - committing the version  
   - creating a release tag  
   - deploying artifacts to Maven Central  
   - bumping the next snapshot version  
   - committing and pushing those changes  

3. **Snapshot Workflow (`snapshot.yml`)**  
   Automatically publishes snapshot builds to Central on every push to `master`.

---

## Responsibilities Summary

| Task | Contributor | GitHub Actions |
|------|-------------|----------------|
| Edit CHANGELOG.md | ✔ | ❌ |
| Update README version references | ✔ | ❌ |
| Generate release notes | ✔ (prepare script) | ❌ |
| Bump release version | ❌ | ✔ |
| Tag release | ❌ | ✔ |
| Deploy to Maven Central | ❌ | ✔ |
| Bump to next snapshot | ❌ | ✔ |
| Publish snapshots | ❌ | ✔ |

---

## Release Workflow (Step-by-Step)

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

### **3. Trigger the Release Workflow**

Go to:

**GitHub → Actions → Publish Release to Maven Central → Run Workflow**

Provide:

- **releaseVersion** — e.g., `0.20`
- **nextVersion** — e.g., `0.21-SNAPSHOT`

The workflow will:

- update POM version to the release version  
- commit and tag the release  
- build, sign, and deploy to Maven Central  
- bump to next snapshot version  
- commit and push the snapshot POM  
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
