# ta4j [![Build Status](https://img.shields.io/travis/mdeverdelhan/ta4j.svg)](https://travis-ci.org/mdeverdelhan/ta4j) [![Chat on Riot.im](https://img.shields.io/badge/chat-riot.im-green.svg)](https://riot.im/app/#/room/#ta4j:matrix.org)

***Technical Analysis For Java***

![Ta4 main chart](https://raw.githubusercontent.com/wiki/mdeverdelhan/ta4j/img/ta4j_main_chart.png)

Ta4j is an open source Java library for [technical analysis](http://en.wikipedia.org/wiki/Technical_analysis). It provides the basic components for creation, evaluation and execution of trading strategies.

---

**Important note:** _Ta4j was initially developed by Marc de Verdelhan [mdeverdelhan](https://github.com/mdeverdelhan). He decided to give up the project, since lack of time. See [#192](https://github.com/mdeverdelhan/ta4j/issues/192). Many, many thanks for this great work and he made it possible with his work and his time to provide us such a great solution ta4j.
So there was a decision to fork the project and to pull it below the top level organisation ta4j. Also the corresponding domain was registered and will be contributed to the ta4j organisation, which hopefully will evolve from the contributors._

* The new corresponding webpage http://ta4j.org will also be initialized in the near future.<br>

* **The maintenance of ta4j will be continued in this repository**

---

### Features

 * [x] 100% Pure Java - works on any Java Platform version 8 or later
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
    <version>0.9</version>
</dependency>
```

For ***snapshots***, add the following repository to your `pom.xml` file.
```xml
<repository>
    <id>sonatype snapshots</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
</repository>
```
The current snapshot version is `0.10-SNAPSHOT`.


### Getting Help

The [wiki](https://github.com/mdeverdelhan/ta4j/wiki) is the best place to start learning about ta4j.

Of course you can ask anything [via Twitter](http://twitter.com/MarcdeVerdelhan). For more detailed questions, please use the [issues tracker](http://github.com/mdeverdelhan/ta4j/issues).

### Contributing to ta4j

Here are some ways for you to contribute to ta4j:

  * [Create tickets for bugs and new features](http://github.com/mdeverdelhan/ta4j/issues) and comment on the ones that you are interested in.
  * [Fork this repository](http://help.github.com/forking/) and submit pull requests.
  * Consider donating for new feature development. Bitcoin address: `13BMqpqbzJ62LjMWcPGWrTrdocvGqifdJ3`

See also: the [contribution policy](.github/CONTRIBUTING.md).
