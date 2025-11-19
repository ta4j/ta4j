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

### Serialization utilities

The core module ships with component serializers that convert indicators and strategies into JSON payloads backed by
`ComponentDescriptor`. Indicators can call `Indicator#toJson()` / `Indicator#fromJson(BarSeries, String)` to persist their
configuration, while strategies can now rely on `Strategy#toJson()` / `Strategy#fromJson(BarSeries, String)` for
round-tripping entry and exit rules alongside metadata. These helpers make it easier to store and exchange model parameters
without hand-rolling JSON glue.

### Maven configuration

Ta4j is available on [Maven Central](http://search.maven.org/#search). You just have to add the following dependency in your `pom.xml` file.

```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.19</version>
</dependency>
```

For ***snapshots***, add the following repository to your `pom.xml` file.
```xml
<repository>
    <id>sonatype snapshots</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
</repository>
```
The current ***snapshot version*** is `0.20-SNAPSHOT` from the [develop](https://github.com/ta4j/ta4j/tree/develop) branch.
```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.20-SNAPSHOT</version>
</dependency>
```

You can also download ***example code*** from the maven central repository by adding the following dependency to your pom.xml:
```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-examples</artifactId>
  <version>0.19</version>
</dependency>
```

or for the bleeding edge:

```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-examples</artifactId>
  <version>0.20-SNAPSHOT</version>
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

## Release & Snapshot Publishing

Ta4j uses automated workflows for publishing both snapshot and stable releases.

### Snapshots

Every push to `master` triggers a snapshot deployment:

```
mvn deploy
```

Snapshots are available at:

```
https://central.sonatype.com/repository/maven-snapshots/
```

### Stable Releases

Releases are performed in two phases:

#### 1. Prepare the release notes

```
scripts/prepare-release.sh <version>
```

This script:

- Moves the `Unreleased` changelog section into a new versioned section
- Resets `Unreleased`
- Updates README version references
- Generates `release/<version>.md`

#### 2. Trigger the GitHub release workflow

From GitHub:

**Actions → Publish Release to Maven Central → Run workflow**

Provide:

- `releaseVersion` (e.g. `0.20`)
- `nextVersion` (e.g. `0.21-SNAPSHOT`)

The workflow automatically:

- Updates project version
- Creates a tag
- Deploys artifacts to Maven Central
- Bumps next snapshot
- Pushes changes
- Creates a GitHub Release with the generated notes


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



