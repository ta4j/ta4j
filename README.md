# ta4j  [![Build and Test](https://github.com/ta4j/ta4j/actions/workflows/test.yml/badge.svg)](https://github.com/ta4j/ta4j/actions/workflows/test.yml) [![Discord](https://img.shields.io/discord/745552125769023488.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/HX9MbWZ) [![License: MIT](https://img.shields.io/badge/License-MIT-brightgreen.svg)](https://opensource.org/licenses/MIT) ![Maven Central](https://img.shields.io/maven-central/v/org.ta4j/ta4j-parent?color=blue&label=Version) ![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/org.ta4j/ta4j-parent?label=Snapshot&server=https%3A%2F%2Foss.sonatype.org%2F)


***Technical Analysis For Java***

![Ta4j main chart](https://raw.githubusercontent.com/ta4j/ta4j-wiki/master/img/ta4j_main_chart.png)

Ta4j is an open source Java library for [technical analysis](http://en.wikipedia.org/wiki/Technical_analysis). It provides the basic components for creation, evaluation and execution of trading strategies.

---

### Features

 * [x] 100% Pure Java - works on any Java Platform version 11 or later
 * [x] More than 130 technical indicators (Aroon, ATR, moving averages, parabolic SAR, RSI, etc.)
 * [x] A powerful engine for building custom trading strategies
 * [x] Utilities to run and compare strategies
 * [x] Minimal 3rd party dependencies
 * [x] Simple integration
 * [x] One more thing: it's MIT licensed

### Maven configuration

Ta4j is available on [Maven Central](http://search.maven.org/#search). You just have to add the following dependency in your `pom.xml` file.

```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.18</version>
</dependency>
```

For ***snapshots***, add the following repository to your `pom.xml` file.
```xml
<repository>
    <id>sonatype snapshots</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
</repository>
```
The current ***snapshot version*** is `0.19-SNAPSHOT` from the [develop](https://github.com/ta4j/ta4j/tree/develop) branch.
```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.19-SNAPSHOT</version>
</dependency>
```

You can also download ***example code*** from the maven central repository by adding the following dependency to your pom.xml:
```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-examples</artifactId>
  <version>0.18</version>
</dependency>
```
### Getting Help
The [wiki](https://ta4j.github.io/ta4j-wiki/) is the best place to start learning about ta4j. For more detailed questions, please use the [issues tracker](https://github.com/ta4j/ta4j/issues).

### Contributing to ta4j

Here are some ways for you to contribute to ta4j:
  * Take a look at the [Roadmap items](https://ta4j.github.io/ta4j-wiki/Roadmap-and-Tasks.html)
  * [Fork this repository](http://help.github.com/forking/) and submit pull requests.
  * Take a look at [How to contribute](https://ta4j.github.io/ta4j-wiki/How-to-contribute)

See also: the [contribution policy](.github/CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md)

&nbsp;
&nbsp;

### Preparing a release

Maintainers can automate the mundane parts of the release process with
`scripts/prepare-release.sh`. The script coordinates Maven's version bump,
updates documentation snippets, rolls the changelog forward, and stages the
resulting changes so they are ready to review and commit.

```
./scripts/prepare-release.sh
```

By default the script derives the release version from `project.version` in the
root `pom.xml`, strips the `-SNAPSHOT` suffix, and increments the final numeric
segment to determine the next development snapshot. You can override either
value when cutting special releases:

```
./scripts/prepare-release.sh --release-version 0.19.1 --next-version 0.20
```

Run the script from a clean working tree. It stages the updated POM files,
documentation, and changelog entries automatically (skipping this step when you
pass `--dry-run`) so you can create the release commit (and follow-up snapshot
bump) with confidence.

To preview a release (no file writes) run:

```
./scripts/prepare-release.sh release --dry-run
```

Example output of a dry run:

```
Preparing release:
  Release version: 1.5.0
  Next version:    1.6.0-SNAPSHOT
  Mode:            DRY-RUN (no changes applied)

[DRY-RUN] Would set Maven version to 1.5.0 and update changelog/README.
[DRY-RUN] Snapshot bump to 1.6.0-SNAPSHOT is handled separately by CI workflow.
[DRY-RUN] Would update CHANGELOG.md: move 'Unreleased' to '1.5.0' section.
[DRY-RUN] Would update README.md version references.

release_version=1.5.0
next_version=1.6.0-SNAPSHOT
release_notes_file=release/release-notes.md

```

&nbsp;

### Automated releases

The `Release` workflow (`.github/workflows/release.yml`) automates version bumps, tagging, and publishing to OSSRH/GitHub. It can
be triggered manually from the **Actions** tab using the optional `release_version` and `next_version` inputs. When the inputs
are omitted the workflow derives the release number from the root `pom.xml` and calculates the next snapshot version
automatically.

To execute the workflow successfully, configure the following repository secrets:

| Secret | Purpose |
| --- | --- |
| `OSSRH_USERNAME` / `OSSRH_PASSWORD` | Credentials used by Maven to authenticate with Sonatype OSSRH. |
| `OSSRH_GPG_PRIVATE_KEY` | ASCII-armored private key for signing the deployed artifacts. |
| `OSSRH_GPG_PASSPHRASE` | Passphrase for the signing key; also forwarded as `MAVEN_GPG_PASSPHRASE`. |
| `RELEASE_GIT_NAME` *(optional)* | Overrides the Git author name used for release commits. Defaults to `github.actor`. |
| `RELEASE_GIT_EMAIL` *(optional)* | Overrides the Git author email for release commits. Defaults to `<actor>@users.noreply.github.com`. |

Grant the workflow permission to push to the protected `master` branch and to create `release/*` branches and tags. If branch
protections are enforced, make sure "Allow GitHub Actions to bypass branch protections" is enabled for `master` and that the
`release/*` pattern permits direct pushes from the `github-actions[bot]` account.

&nbsp;
&nbsp;

### Powered by

[![JetBrains logo.](https://resources.jetbrains.com/storage/products/company/brand/logos/jetbrains.svg)](https://jb.gg/OpenSource)

&nbsp;
&nbsp;
&nbsp;
&nbsp;

<a href = https://github.com/ta4j/ta4j/graphs/contributors>
  <img src = https://contrib.rocks/image?repo=ta4j/ta4j>
</a>

