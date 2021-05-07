# ta4j  ![Build Status develop](https://github.com/ta4j/ta4j/workflows/Test/badge.svg?branch=develop) ![Build Status master](https://github.com/ta4j/ta4j/workflows/Test/badge.svg?branch=master) [![Discord](https://img.shields.io/discord/745552125769023488.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/HX9MbWZ) [![License: MIT](https://img.shields.io/badge/License-MIT-brightgreen.svg)](https://opensource.org/licenses/MIT) ![Maven Central](https://img.shields.io/maven-central/v/org.ta4j/ta4j-parent?color=blue&label=Version) ![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/org.ta4j/ta4j-parent?label=Snapshot&server=https%3A%2F%2Foss.sonatype.org%2F)


***Technical Analysis For Java***

![Ta4j main chart](https://raw.githubusercontent.com/ta4j/ta4j-wiki/master/img/ta4j_main_chart.png)

Ta4j is an open source Java library for [technical analysis](http://en.wikipedia.org/wiki/Technical_analysis). It provides the basic components for creation, evaluation and execution of trading strategies.

---

### Features

 * [x] 100% Pure Java - works on any Java Platform version 8 or later
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
  <version>0.14</version>
</dependency>
```

For ***snapshots***, add the following repository to your `pom.xml` file.
```xml
<repository>
    <id>sonatype snapshots</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
</repository>
```
The current ***snapshot version*** is `0.15-SNAPSHOT` from the [develop](https://github.com/ta4j/ta4j/tree/develop) branch.
```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.15-SNAPSHOT</version>
</dependency>
```

You can also download ***example code*** from the maven central repository by adding the following dependency to your pom.xml:
```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-examples</artifactId>
  <version>0.14</version>
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
