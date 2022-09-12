# ta4j  ![Build Status develop](https://github.com/ta4j/ta4j/workflows/Test/badge.svg?branch=develop) ![Build Status master](https://github.com/ta4j/ta4j/workflows/Test/badge.svg?branch=master) [![Discord](https://img.shields.io/discord/745552125769023488.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/HX9MbWZ) [![License: MIT](https://img.shields.io/badge/License-MIT-brightgreen.svg)](https://opensource.org/licenses/MIT) ![Maven Central](https://img.shields.io/maven-central/v/org.ta4j/ta4j-parent?color=blue&label=Version) ![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/org.ta4j/ta4j-parent?label=Snapshot&server=https%3A%2F%2Foss.sonatype.org%2F)


***Technical Analysis For Java***
***Java的技术分析***

![Ta4j main chart](https://raw.githubusercontent.com/ta4j/ta4j-wiki/master/img/ta4j_main_chart.png)

Ta4j is an open source Java library for [technical analysis](http://en.wikipedia.org/wiki/Technical_analysis). It provides the basic components for creation, evaluation and execution of trading strategies.
* Ta4j 是一个用于 [技术分析] (http://en.wikipedia.org/wiki/Technical_analysis) 的开源 Java 库。 它为创建、评估和执行交易策略提供了基本组件。

---

### Features

 * [x] 100% Pure Java - works on any Java Platform version 8 or later
 * [x] More than 130 technical indicators (Aroon, ATR, moving averages, parabolic SAR, RSI, etc.)
 * [x] A powerful engine for building custom trading strategies
 * [x] Utilities to run and compare strategies
 * [x] Minimal 3rd party dependencies
 * [x] Simple integration
 * [x] One more thing: it's MIT licensed
 * 
 * [x] 100% 纯 Java - 适用于任何 Java 平台版本 8 或更高版本
 * [x] 超过 130 种技术指标（Aroon、ATR、移动平均线、抛物线 SAR、RSI 等）
 * [x] 用于构建自定义交易策略的强大引擎
 * [x] 运行和比较策略的实用程序
 * [x] 最小的第 3 方依赖项
 * [x] 简单集成
 * [x] 还有一件事：它是 MIT 许可的

### Maven configuration

Ta4j is available on [Maven Central](http://search.maven.org/#search). You just have to add the following dependency in your `pom.xml` file.
Ta4j 在 [Maven Central](http://search.maven.org/#search) 上可用。 你只需要在你的 `pom.xml` 文件中添加以下依赖项。

```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.14</version>
</dependency>
```

For ***snapshots***, add the following repository to your `pom.xml` file.
对于 ***snapshots***，将以下存储库添加到您的 `pom.xml` 文件中。
```xml
<repository>
    <id>sonatype snapshots</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
</repository>
```
The current ***snapshot version*** is `0.15-SNAPSHOT` from the [develop](https://github.com/ta4j/ta4j/tree/develop) branch.
当前的***快照版本***是来自 [develop](https://github.com/ta4j/ta4j/tree/develop) 分支的 `0.15-SNAPSHOT`。
```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.15-SNAPSHOT</version>
</dependency>
```

You can also download ***example code*** from the maven central repository by adding the following dependency to your pom.xml:
您还可以通过将以下依赖项添加到您的 pom.xml 中，从 maven 中央存储库下载***示例代码***：
```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-examples</artifactId>
  <version>0.14</version>
</dependency>
```
### Getting Help
### 获得帮助
The [wiki](https://ta4j.github.io/ta4j-wiki/) is the best place to start learning about ta4j. For more detailed questions, please use the [issues tracker](https://github.com/ta4j/ta4j/issues).
[wiki](https://ta4j.github.io/ta4j-wiki/) 是开始学习 ta4j 的最佳场所。 有关更详细的问题，请使用 [issues tracker](https://github.com/ta4j/ta4j/issues)。

### Contributing to ta4j
### 为 ta4j 做贡献

Here are some ways for you to contribute to ta4j:
  * Take a look at the [Roadmap items](https://ta4j.github.io/ta4j-wiki/Roadmap-and-Tasks.html)
  * [Fork this repository](http://help.github.com/forking/) and submit pull requests.
  * Take a look at [How to contribute](https://ta4j.github.io/ta4j-wiki/How-to-contribute)
  * 
  * 您可以通过以下方式为 ta4j 做出贡献：
  * 看看[路线图项目](https://ta4j.github.io/ta4j-wiki/Roadmap-and-Tasks.html)
  * [fork this repository](http://help.github.com/forking/) 并提交拉取请求。
  * 看看[如何贡献](https://ta4j.github.io/ta4j-wiki/How-to-contribute)
See also: the [contribution policy](.github/CONTRIBUTING.md) and [Code of Cunduct](CODE_OF_CONDUCT.md)
    另请参阅：[贡献政策](.github/CONTRIBUTING.md) 和 [行为准则](CODE OF CONDUCT.md)