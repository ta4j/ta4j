# ta4j [![Build Status](https://travis-ci.org/ta4j/ta4j.svg?branch=master)](https://travis-ci.org/ta4j/ta4j) [![Chat on Riot.im](https://img.shields.io/badge/chat-riot.im-green.svg)](https://riot.im/app/#/room/#ta4j:matrix.org)

***Technical Analysis For Java***

![Ta4 main chart](https://raw.githubusercontent.com/wiki/mdeverdelhan/ta4j/img/ta4j_main_chart.png)

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
  <version>0.11</version>
</dependency>
```

For ***snapshots***, add the following repository to your `pom.xml` file.
```xml
<repository>
    <id>sonatype snapshots</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
</repository>
```
The current snapshot version is `0.12-SNAPSHOT` from the [develop](https://github.com/ta4j/ta4j/tree/develop) branch.
```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.12-SNAPSHOT</version>
</dependency>
```

### Getting Help

Of course you can ask anything [via Twitter](http://twitter.com/MarcdeVerdelhan). For more detailed questions, please use the [issues tracker](http://github.com/mdeverdelhan/ta4j/issues).

### Contributing to ta4j

Here are some ways for you to contribute to ta4j:
  * Take a look at the [Roadmap items](https://github.com/ta4j/ta4j/wiki/Roadmap)
  * [Fork this repository](http://help.github.com/forking/) and submit pull requests.
  * Take a look at [How to contribute](https://github.com/ta4j/ta4j/wiki/How-to-contribute)

See also: the [contribution policy](.github/CONTRIBUTING.md) and [Code of Cunduct](CODE_OF_CONDUCT.md)
