# ta4j  [![Build and Test](https://github.com/ta4j/ta4j/actions/workflows/test.yml/badge.svg)](https://github.com/ta4j/ta4j/actions/workflows/test.yml) [![Discord](https://img.shields.io/discord/745552125769023488.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/HX9MbWZ) [![License: MIT](https://img.shields.io/badge/License-MIT-brightgreen.svg)](https://opensource.org/licenses/MIT) ![Maven Central](https://img.shields.io/maven-central/v/org.ta4j/ta4j-parent?color=blue&label=Version) ![JDK](https://img.shields.io/badge/JDK-21%2B-orange)

***Technical Analysis for Java***

![Ta4j main chart](https://raw.githubusercontent.com/ta4j/ta4j-wiki/master/img/ta4j_main_chart.png)

Ta4j turns Java developers into confident quants. With more than 200 indicators, readable APIs, and production-minded tooling, you can explore markets, validate trading ideas, visualize signals, and ship automated bots without leaving the JVM.

---

## What can you build?

- **Backtest trading strategies**: Test "what if" scenarios on historical data before risking real money
- **Paper trading bots**: Run strategies live against market data without placing real orders
- **Research tools**: Analyze market patterns, compare indicators, and explore new trading ideas
- **Automated trading systems**: Deploy production bots that execute trades based on your strategies
- **Market analysis dashboards**: Build visualizations and reports for your trading research

---

## Why Java developers choose Ta4j

- **Pure Java, zero friction**: Works anywhere Java 11+ runs—cloud functions, desktop tools, microservices, or trading bots. No Python bridges or external dependencies.
- **Type-safe and IDE-friendly**: Full Java type system means autocomplete, refactoring, and compile-time checks work perfectly.
- **Huge indicator catalog**: Aroon, ATR, Ichimoku, MACD, RSI, Renko, Heikin-Ashi, and 130+ more ready to plug together.
- **Composable strategies**: Chain rules fluently using familiar Java patterns—no DSLs or configuration files required.
- **Backtesting built-in**: Evaluate risk/reward with realistic trading costs and performance metrics in just a few lines.
- **Production-ready**: Deterministic outputs, JSON serialization for strategies/indicators, and minimal dependencies make it easy to deploy.
- **MIT licensed**: Use it at work, in research, or inside your next trading product without legal concerns.

## Install in seconds

Add Ta4j from Maven Central:

```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.19</version>
</dependency>
```

Prefer living on the edge? Use the snapshot repository and version:

```xml
<repository>
  <id>central-portal-snapshots</id>
  <url>https://central.sonatype.com/repository/maven-snapshots/</url>
</repository>

<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.20-SNAPSHOT</version>
</dependency>
```

Sample applications are also published so you can copy/paste entire flows:

```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-examples</artifactId>
  <version>0.19</version>
</dependency>
```

Like living on the edge? Use the snapshot version of ta4j-examples for the latest experimental/beta features:

```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-examples</artifactId>
  <version>0.20-SNAPSHOT</version>
</dependency>
```

**💡 Tip**: The `ta4j-examples` module includes runnable demos, data loaders, and charting utilities. It's a great way to see Ta4j in action and learn by example.

## Quick start: Your first strategy

Load price data, plug in indicators, and describe when to enter/exit. The API reads like the trading notes you already keep.

**Key concepts:**
- **Indicators**: Calculate values from price data (e.g., moving averages, RSI, MACD)
- **Rules**: Boolean conditions that determine when to enter or exit trades
- **Strategies**: Combine entry and exit rules into a complete trading system
- **BarSeries**: Your price data (OHLCV bars) that everything operates on

```java
import org.ta4j.core.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.*;
import org.ta4j.core.backtest.BarSeriesManager;
import ta4jexamples.loaders.CsvTradesLoader;

// Load historical price data (or use your own data source)
BarSeries series = CsvTradesLoader.loadBitstampSeries();

// Create indicators: calculate moving averages from close prices
ClosePriceIndicator close = new ClosePriceIndicator(series);
EMAIndicator fastEma = new EMAIndicator(close, 12);  // 12-period EMA
EMAIndicator slowEma = new EMAIndicator(close, 26);  // 26-period EMA

// Define entry rule: buy when fast EMA crosses above slow EMA (golden cross)
Rule entry = new CrossedUpIndicatorRule(fastEma, slowEma);

// Define exit rule: sell when price gains 3% OR loses 1.5%
Rule exit = new StopGainRule(close, 3.0)      // take profit at +3%
        .or(new StopLossRule(close, 1.5));    // or cut losses at -1.5%

// Combine rules into a strategy
Strategy strategy = new BaseStrategy("EMA Crossover", entry, exit);

// Run the strategy on historical data
BarSeriesManager manager = new BarSeriesManager(series);
TradingRecord record = manager.run(strategy);

// See the results
System.out.println("Number of trades: " + record.getTradeCount());
System.out.println("Number of positions: " + record.getPositionCount());
```

## Evaluate performance with metrics

Turn ideas into numbers. Add trading costs for realism and measure what matters—returns, risk, drawdowns, and more.

```java
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.cost.LinearTransactionCostModel;
import org.ta4j.core.cost.LinearBorrowingCostModel;

// Run backtest with realistic trading costs
// Transaction cost: 0.1% per trade (typical for crypto exchanges)
// Borrowing cost: 0.01% per period (for margin/short positions)
TradingRecord record = new BarSeriesManager(series,
        new LinearTransactionCostModel(0.001),      // 0.1% fee per trade
        new LinearBorrowingCostModel(0.0001))       // 0.01% borrowing cost
        .run(strategy);

// Calculate performance metrics
System.out.printf("Trades executed: %d%n", record.getTradeCount());
System.out.printf("Net return: %.2f%%%n", 
    new NetReturnCriterion().calculate(series, record).multipliedBy(series.numOf(100)));
System.out.printf("Max drawdown: %.2f%%%n", 
    new MaximumDrawdownCriterion().calculate(series, record).multipliedBy(series.numOf(100)));

// Explore more metrics: Sharpe ratio, win rate, profit factor, etc.
// See the wiki for the full list of available criteria
```

Want to backtest hundreds or even thousands of strategies to find the top performers?

```java
// Generate strategies with varying parameters
List<Strategy> strategies = new ArrayList<>();
for (int fastPeriod = 5; fastPeriod <= 20; fastPeriod += 5) {
    for (int slowPeriod = 20; slowPeriod <= 50; slowPeriod += 10) {
        EMAIndicator fastEma = new EMAIndicator(close, fastPeriod);
        EMAIndicator slowEma = new EMAIndicator(close, slowPeriod);
        Rule entry = new CrossedUpIndicatorRule(fastEma, slowEma);
        Rule exit = new CrossedDownIndicatorRule(fastEma, slowEma);
        strategies.add(new BaseStrategy("EMA(" + fastPeriod + "," + slowPeriod + ")", entry, exit));
    }
}

// Run all strategies with progress tracking
BacktestExecutionResult result = new BacktestExecutor(series)
    .executeWithRuntimeReport(strategies, 
        series.numFactory().numOf(1),  // position size: 1 unit
        Trade.TradeType.BUY,           // long positions (use Trade.TradeType.SELL for shorts)
        ProgressCompletion.loggingWithMemory(); // logs progress with memory stats

// Get top 10 strategies sorted by net profit, then by expectancy (for ties)
// You can sort by any AnalysisCriterion - mix and match to find strategies that meet your goals
List<TradingStatement> topStrategies = result.getTopStrategies(10,
    new NetProfitCriterion(),    // primary sort: highest net profit first
    new ExpectancyCriterion());  // secondary sort: highest expectancy for ties

// Review the winners
topStrategies.forEach(statement -> {
    System.out.printf("Strategy: %s, Net Profit: %.2f, Expectancy: %.2f%n",
        statement.getStrategy().getName(),
        statement.getCriterionScore(new NetProfitCriterion()).orElse(series.numOf(0)),
        statement.getCriterionScore(new ExpectancyCriterion()).orElse(series.numOf(0)));
});
```


## Visualize and share strategies

See your strategies in action. Ta4j includes charting helpers, but you're not locked in—serialize to JSON and use any visualization stack you prefer.

**Built-in Java charting** (using JFreeChart):
```java
import org.ta4j.core.chart.ChartWorkflow;
import org.jfree.chart.JFreeChart;

ChartWorkflow chartWorkflow = new ChartWorkflow();
JFreeChart chart = chartWorkflow.builder()
        .withTitle("EMA Crossover Strategy")
        .withSeries(series)                    // Price bars (candlesticks)
        .withIndicatorOverlay(fastEma)                // Overlay indicators on price chart
        .withIndicatorOverlay(slowEma)
        .withTradingRecordOverlay(record)      // Mark back-tested strategy's entry/exit points on chart
        .withSubChart(new MaximumDrawdownCriterion(), record)  // Add maximum drawdown analysis in a subchart
        .toChart();
chartWorkflow.displayChart(chart);            // displays chart in an interactive Swing window
chartWorkflow.saveChartImage(chart, series, "ema-crossover-strategy", "output/charts");  // Save as output/charts/ema-crossover-strategy.jpg
```

![EMA Crossover Strategy Chart](ta4j-examples/docs/img/ema-crossover-readme.jpg)

The chart above shows candlestick price data with EMA lines overlaid, buy/sell signals marked with arrows, and a performance subchart showing drawdown over time. This is the actual output from the code example above. See the [chart at the top of this README](#ta4j) for another example, or check the [wiki's charting guide](https://ta4j.github.io/ta4j-wiki/Charting.html) for more examples.

**Export to any stack** (Python, TypeScript, etc.):
```java
// Serialize strategies and indicators to JSON
String indicatorJson = fastEma.toJson();
String strategyJson = strategy.toJson();

// Send to your Python/TypeScript service, database, or API
sendToDashboard(indicatorJson, strategyJson);

// Later, restore from JSON (useful for loading saved strategies)
Indicator<?> restoredIndicator = Indicator.fromJson(series, indicatorJson);
Strategy restoredStrategy = Strategy.fromJson(series, strategyJson);
```

## From backtest to live trading

The same strategies you backtest can run live. Ta4j's deterministic calculations make it safe to deploy—test thoroughly, then execute with confidence.

```java
import org.ta4j.core.builder.BaseBarSeriesBuilder;

// Create a live series (starts empty, grows as bars arrive)
BarSeries liveSeries = new BaseBarSeriesBuilder()
        .withName("BTC-USD")
        .build();

// Build your strategy (same code as backtesting!)
Strategy strategy = buildStrategy(liveSeries);

// Main trading loop: check for signals on each new bar
while (true) {
    // Fetch latest bar from your exchange/broker API
    Bar latest = fetchLatestBarFromBroker();  // Your integration here
    liveSeries.addBar(latest);

    int endIndex = liveSeries.getEndIndex();
    
    // Check entry/exit signals (same API as backtesting)
    if (strategy.shouldEnter(endIndex)) {
        placeBuyOrder();  // Your order execution logic
    } else if (strategy.shouldExit(endIndex)) {
        placeSellOrder(); // Your order execution logic
    }
    
    Thread.sleep(60000); // Wait 1 minute (or your bar interval)
}
```

**Why this works:**
- **Same code, different data**: Your strategy logic is identical for backtests and live trading
- **Deterministic**: Same inputs always produce same outputs—critical for testing and debugging
- **Type-safe**: Compile-time checks catch errors before they cost money

## What's next?

**New to technical analysis?**
- Start with the [wiki's Getting Started guide](https://ta4j.github.io/ta4j-wiki/) to learn core concepts
- Explore the [`ta4j-examples`](ta4j-examples) module—each example is runnable and well-commented
- Try modifying the quick start example above: change indicator parameters, add new rules, or test different exit conditions

**Ready to go deeper?**
- Browse [strategy recipes](https://ta4j.github.io/ta4j-wiki/) for Renko bricks, Ichimoku clouds, breakout strategies, and more
- Learn about [portfolio metrics](https://ta4j.github.io/ta4j-wiki/) for multi-asset strategies
- Check out [advanced backtesting patterns](https://ta4j.github.io/ta4j-wiki/) like walk-forward analysis

**Need help?**
- Ask questions on [Discord](https://discord.gg/HX9MbWZ) (active community, quick responses)
- Search or open an [issue](https://github.com/ta4j/ta4j/issues) on GitHub
- Review the [Javadoc](https://ta4j.github.io/ta4j/) for detailed API documentation

## Contributing

- Scan the [roadmap](https://ta4j.github.io/ta4j-wiki/Roadmap-and-Tasks.html) and [how-to-contribute guide](https://ta4j.github.io/ta4j-wiki/How-to-contribute).
- [Fork the repo](http://help.github.com/forking/), open pull requests, and join code discussions on Discord.
- See the [contribution policy](.github/CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md).

## Release & snapshot publishing

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

### Stable releases

For detailed information about the release process, see [RELEASE_PROCESS.md](RELEASE_PROCESS.md).

## Powered by

[![JetBrains logo.](https://resources.jetbrains.com/storage/products/company/brand/logos/jetbrains.svg)](https://jb.gg/OpenSource)

<a href = https://github.com/ta4j/ta4j/graphs/contributors>
  <img src = https://contrib.rocks/image?repo=ta4j/ta4j>
</a>



