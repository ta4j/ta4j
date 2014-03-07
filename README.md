# ta4j [![Build Status](https://travis-ci.org/mdeverdelhan/ta4j.png?branch=master)](https://travis-ci.org/mdeverdelhan/ta4j)

***Technical Analysis For Java***

Ta4j is an open source Java library for [technical analysis](http://en.wikipedia.org/wiki/Technical_analysis). It provides the basic components for creation, manipulation and evaluation of trading strategies.

## Features

 * More than 40 technical indicators (Aroon, ATR, moving averages, parabolic SAR, RSI, etc.)
 * A powerful engine for building custom trading strategies
 * Utilities to run and compare strategies
 * Simple integration
 * One more thing: it's MIT licensed

***Warning!***

Ta4j uses `double`s under the hood. Small approximations can occur (in indicators notably). This may change in the future.

## Quickstart

At the beginning we just need a time series.

```java
// Getting a time series (from any provider: CSV, web service, etc.)
TimeSeries series = createTimeSeries();
```

#### Using indicators

```java
// Getting the close price of the ticks
double firstClosePrice = series.getTick(0).getClosePrice();
System.out.println("First close price: " + firstClosePrice);
// Or within an indicator:
ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
// Here is the same close price:
System.out.println(firstClosePrice == closePrice.getValue(0)); // equal to firstClosePrice

// Getting the simple moving average (SMA) of the close price over the last 5 ticks
SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
// Here is the 5-ticks-SMA value at the 42nd index
System.out.println("5-ticks-SMA value at the 42nd index: " + shortSma.getValue(42));

// Getting a longer SMA (e.g. over the 30 last ticks)
SMAIndicator longSma = new SMAIndicator(closePrice, 30);
```

Ta4j includes [more than 40 technical indicators](http://github.com/mdeverdelhan/ta4j/tree/master/src/main/java/eu/verdelhan/ta4j/indicators).

#### Building a trading strategy

```java
// Initial strategy:
//  - Buy when 5-ticks SMA crosses over 30-ticks SMA
//  - Sell when 5-ticks SMA crosses under 30-ticks SMA
Strategy ourStrategy = new IndicatorCrossedIndicatorStrategy(shortSma, longSma);

// Cutomizing our strategy...
// We want to buy if the price go below a defined price (e.g $800.00)
ourStrategy = new SupportStrategy(closePrice, ourStrategy, 800d);
// And we want to sell if the price looses more than 3%
ourStrategy = new StopLossStrategy(closePrice, ourStrategy, 3);
// Or if the price earns more than 2%
ourStrategy = new StopGainStrategy(closePrice, ourStrategy, 2);
```

See also:  [Algorithmic trading strategies](http://en.wikipedia.org/wiki/Algorithmic_trading#Strategies)

#### Running our juicy strategy

```java
// Slicing/splitting the series (sub-series will last 1 day each)
RegularSlicer slicer = new RegularSlicer(series, Period.days(1));
System.out.println("Number of slices: " + slicer.getNumberOfSlices());
// Getting the index of the last slice (sub-series)
int lastSliceIndex = slicer.getNumberOfSlices() - 1;

// Running our strategy over the last slice of the series
Runner ourRunner = new HistoryRunner(slicer, ourStrategy);
List<Trade> trades = ourRunner.run(lastSliceIndex);
System.out.println("Number of trades for our strategy: " + trades.size());
```

#### Analyzing our results

```java
// Getting the cash flow of the resulting trades
CashFlow cashFlow = new CashFlow(slicer.getSlice(lastSliceIndex), trades);

// Running a reference strategy (for comparison) in which we buy just once
Runner referenceRunner = new HistoryRunner(slicer, new JustBuyOnceStrategy());
List<Trade> referenceTrades = referenceRunner.run(lastSliceIndex);
System.out.println("Number of trades for reference strategy: " + referenceTrades.size());

// Comparing our strategy to the just-buy-once strategy according to a criterion
TotalProfitCriterion criterion = new TotalProfitCriterion();

// Our strategy is better than a just-buy-once for the last slice
System.out.println("Total profit for our strategy: " + criterion.calculate(slicer.getSlice(lastSliceIndex), trades));
System.out.println("Total profit for reference strategy: " + criterion.calculate(slicer.getSlice(lastSliceIndex), referenceTrades));
```


## To do list

 * github page
 * code coverage
 * release (http://datumedge.blogspot.de/2012/05/publishing-from-github-to-maven-central.html)
 * fixing todos in doc


## About

Ta4j is initially a fork of the [Tail library](http://tail.sourceforge.net/).

#### Donations

Bitcoin address: 13BMqpqbzJ62LjMWcPGWrTrdocvGqifdJ3 
