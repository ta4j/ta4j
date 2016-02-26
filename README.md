# ta4j [![Build Status](https://travis-ci.org/mdeverdelhan/ta4j.png?branch=master)](https://travis-ci.org/mdeverdelhan/ta4j)

***Technical Analysis For Java***

![Ta4 main chart](https://raw.githubusercontent.com/wiki/mdeverdelhan/ta4j/img/ta4j_main_chart.png)

Ta4j is an open source Java library for [technical analysis](http://en.wikipedia.org/wiki/Technical_analysis). It provides the basic components for creation, evaluation and execution of trading strategies.

### Features

 * [x] 100% Pure Java - works on any Java Platform version 6 or later
 * [x] More than 100 technical indicators (Aroon, ATR, moving averages, parabolic SAR, RSI, etc.)
 * [x] A powerful engine for building custom trading strategies
 * [x] Utilities to run and compare strategies
 * [x] Minimal 3rd party dependencies
 * [x] Simple integration
 * [x] One more thing: it's MIT licensed

### Maven configuration

Ta4j is available on [Maven Central](http://search.maven.org/#search|ga|1|a%3A%22ta4j%22). You just have to add the following dependency in your `pom.xml` file.

```xml
<dependency>
    <groupId>eu.verdelhan</groupId>
    <artifactId>ta4j</artifactId>
    <version>0.8</version>
</dependency>
```

For ***snapshots***, add the following repository to your `pom.xml` file.
```xml
<repository>
    <id>sonatype snapshots</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
</repository>
```
The current snapshot version is `0.9-SNAPSHOT`.


### Getting Help

The [wiki](https://github.com/mdeverdelhan/ta4j/wiki) is the best place to start learning about ta4j.

Of course you can ask anything [via Twitter](http://twitter.com/MarcdeVerdelhan). For more detailed questions, please use the [issues tracker](http://github.com/mdeverdelhan/ta4j/issues).

### Contributing to ta4j

Here are some ways for you to contribute to ta4j:

  * [Create tickets for bugs and new features](http://github.com/mdeverdelhan/ta4j/issues) and comment on the ones that you are interested in.
  * [Fork this repository](http://help.github.com/forking/) and submit pull requests.
  * Consider donating for new feature development. Bitcoin address: `13BMqpqbzJ62LjMWcPGWrTrdocvGqifdJ3`

See also: the [contribution policy](.github/CONTRIBUTING.md).
