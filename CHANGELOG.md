Changelog for `ta4j`, roughly following [keepachangelog.com](http://keepachangelog.com/en/1.0.0/) from version 0.9 onwards.


## unreleased (`develop` branch)

- **BREAKING**: Tick has been renamed to **Bar**

### Fixed
- **ATRIndicator**: fixed calculations
- **PlusDI, MinusDI, ADX**: fixed calculations
- **LinearTransactionCostCriterion**: fixed calculations, added xls file and unit tests
- **FisherIndicator**: fixed calculations

### Changed
- **TotalProfitCriterion**: If not `NaN` the criterion uses the price of the `Order` and not just the close price of underlying `TimeSeries`
- **Order**: Now constructors and static `sell/buyAt(..)` functions need a price and amount parameter to satisfy correct be
behaviour of criterions (entry/exit prices can differ from corresponding close prices in `Order`)
- **JustOnceRule**: now it is possible to add another rule so that this rule is satisfied if the inner rule is satisfied for the first time
- **MeanDeviationIndicator**: moved to statistics package
- **Decimal**: use `BigDecimal::valueof` instead of instantiating a new BigDecimal for double, int and long
    - now `Decimal` extends `Number` 
- **Strategy:** can now have a optional parameter "name".
- **Tick:** `Tick` has been renamed to **`Bar`** for a more appropriate description of the price movement over a set period of time.
- **MMAIndicator**: restructured and moved from `helpers` to `indicators` package
- **AverageTrueRangeIndicator**: renamed to **ATRIndicator**
- **AverageDirectionalMovementDownIndicator**: renamed to **ADXIndicator**
-  **ADXIndicator**: added new two argument constructor
- **DirectionalMovementPlusIndicator** and **DirectionalMovementPlusIndicator**: renamed to **PlusDIIndicator** and **MinusDIIndicator**
- **XlsTestsUtils**: rewritten to provide getSeries(), getIndicator(), getFinalCriterionValue(), and getTradingRecord() in support of XLSCriterionTest and XLSIndicatorTest.
- **IndicatorFactory**: made generic and renamed createIndicator() to getIndicator()
- **RSIIndicatorTest**: example showing usage of new generic unit testing of indicators
- **LinearTransactionCostCriterionTest**: example showing usage of new generic unit testing of criteria

## Added
- **ConvergenceDivergenceIndicator**: new Indicator for positive/negative convergence and divergence.
- **BooleanTransformIndicator**: new indicator to transform any decimal indicator to a boolean indicator by using logical operations.
- **DecimalTransformIndicator**: new indicator to transforms any indicator by using common math operations.
- **Decimal**: added functions `Decimal valueOf(BigDecimal)` and `BigDecimal getDelegate()`
- **AbstractEMAIndicator**: new abstract indicator for ema based indicators like MMAIndicator
- **PearsonCorrelationIndicator**: new statistic indicator with pearson correlation
- **TimeSeries**: new method `getSubSeries(int, int)` to create a sub series of the TimeSeries that stores bars exclusively between `startIndex` and `endIndex` parameters
- **IIIIndicator**: Intraday Intensity Index
- **CriterionFactory**: new functional interface to support CriterionTest
- **IndicatorTest**: new class for storing an indicator factory, allows for generic calls like getIndicator(D data, P... params) after the factory is set once in the constructor call.  Facilitates standardization across unit tests.
- **CriterionTest**: new class for storing a criterion factory, allows for generic calls like getCriterion(P... params) after the factory is set once in the constructor call.  Facilitates standardization across unit tests.
- **ExternalIndicatorTest**: new interface for fetching indicators and time series from external sources
- **ExternalCriterionTest**: new interface for fetching criteria, trading records, and time series from external sources
- **XLSIndicatorTest**: new class implementing ExternalIndicatorTest for XLS files, for use in XLS unit tests
- **XLSCriterionTest**: new class implementing ExternalCriterionTest for XLS files, for use in XLS unit tests

## Removed
- **TraillingStopLossIndicator**: no need for this as indicator. No further calculations possible after price falls below stop loss. Use `StopLossRule` or `DifferenceIndicator`

## Deprecated
- **BaseTimeSeries**: Constructor: `BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex)` use `getSubSeries(int, int)`
- **Decimal**: Method `toDouble()` use `doubleValue()`

## 0.10 (released October 30, 2017)

### VERY Important note!!!!

with the release 0.10 we have changed the previous java package definition to org.ta4j or to be more specific to org.ta4j.core (the new organisation). You have to reorganize all your refernces to the new packages!
In eclipse you can do this easily by selecting your sources and run "Organize imports"
_Changed ownership of the ta4j repository_: from mdeverdelhan/ta4j (stopped the maintenance) to ta4j/ta4j (new organization)

### Fixed
- **ParabolicSarIndicator**: wrong calculation fixed
- **KAMAIndicator**: stack overflow bug fixed
- **AroonUpIndicator and AroonDownIndicator**: wrong calculations fixed and can handle NaN values now

### Changed
- **BREAKING**: **new package structure**: change eu.verdelhan.ta4j to org.ta4j.ta4j-core
- **new package adx**: new location of AverageDirectionalMovementIndicator and DMI+/DMI-
- **Ownership of the ta4j repository**: from mdeverdelhan/ta4j (stopped the maintenance) to ta4j/ta4j (new organization)
- **ParabolicSarIndicator**: old constructor removed (there was no need for time frame parameter after big fix). Three new constructors for default and custom parameters.
- **HighestValueIndicator and LowestValueIndicator:** ignore also NaN values if they are at the current index


## Added
- **AroonOscillatorIndicator**: new indicator based on AroonUp/DownIndicator
- **AroonUpIndicator** and **AroonDownIndicator**: New constructor with parameter for custom indicator for min price and max price calculation
- **ROCVIndicator**: rate of Change of Volume
- **DirectionalMovementPlusIndicator**: new indicator for Directional Movement System (DMI+)
- **DirectionalMovementDownIndicator**: new indicator for Directional Movement System (DMI-)
- **ChaikinOscillatorIndicator**: new indicator.
- **InSlopeRule**: new rule that is satisfied if the slope of two indicators are within a boundary
- **IsEqualRule**: new rule that is satisfied if two indicators are equal
- **AroonUpIndicator** and **AroonDownIndicator**: new constructor with parameter for custom indicator for min price and max price calculation
- **Pivot Point Indicators Package**: new package with Indicators for calculating standard, Fibonacci and DeMark pivot points and reversals
    - **PivotPointIndicator**: new indicator for calculating the standard pivot point
        - **StandardReversalIndicator**: new indicator for calculating the standard reversals (R3,R2,R1,S1,S2,S3)
        - **FibonacciReversalIndicator**: new indicator for calculating the Fibonacci reversals (R3,R2,R1,S1,S2,S3)
    - **DeMarkPivotPointIndicator**: new indicator for calculating the DeMark pivot point
        - **DeMarkReversalIndicator**: new indicator for calculating the DeMark resistance and the DeMark support
- **IsFallingRule**: new rule that is satisfied if indicator strictly decreases within the timeFrame.
- **IsRisingRule**: new rule that is satisfied if indicator strictly increases within the timeFrame.
- **IsLowestRule**: new rule that is satisfied if indicator is the lowest within the timeFrame.
- **IsHighestRule**: new rule that is satisfied if indicator is the highest within the timeFrame.

## 0.9 (released September 7, 2017)
  - **BREAKING** drops Java 7 support
  - use `java.time` instead of `java.util.Date`
  * Added interfaces for some API basic objects
  * Cleaned whole API
  * Reordered indicators
  * Added PreviousValueIndicator
  * Fixed #162 - Added amount field into Tick constructor
  * Fixed #183 - addTrade bad calculation
  * Fixed #153, #170 - Updated StopGainRule and StopLossRule for short trades
  * Removed dependency to Joda-time
  * Dropped Java 6 and Java 7 compatibility
  * Fixed #120 - ZLEMAIndicator StackOverflowError
  * Added stochastic RSI indicator
  * Added smoothed RSI indicator
  * Fixed examples
  * Fixed #81 - Tick uses Period of 24H when it possibly means 1 Day
  * Fixed #80 - TimeSeries always iterates over all the data
  * Removed the `timePeriod` field in time series
  * Fixed #102 - RSIIndicator returns NaN when rsi == 100
  * Added periodical growth rate indicator
  * Fixed #105 - Strange calculation with Ichimoku Indicator
  * Added Random Walk Index (high/low) indicators
  * Improved performance for Williams %R indicator
  * Moved mock indicators to regular scope (renamed in Fixed*Indicator)

## 0.8 (released February 25, 2016)

  * Fixed StackOverflowErrors on recursive indicators (see #60 and #68)
  * Fixed #74 - Question on backtesting strategies with indicators calculated with enough ticks
  * Added Chande Momentum Oscillator indicator
  * Added cumulated losses/gains indicators
  * Added Range Action Verification Index indicator
  * Added MVWAP indicator
  * Added VWAP indicator
  * Added Chandelier exit indicators
  * Improved Decimal performances
  * Added Fisher indicator
  * Added KAMA indicator
  * Added Detrended Price Oscillator
  * Added Ichimoku clouds indicators
  * Added statistical indicators: Simple linear regression, Correlation coefficient, Variance, Covariance, Standard error
  * Moved standard deviation
  * Added Bollinger BandWidth and %B indicator
  * Added Keltner channel indicators
  * Added Ulcer Index and Mass Index indicators
  * Added a trailing stop-loss indicator
  * Added Coppock Curve indicator
  * Added sum indicator
  * Added candle indicators: Real body, Upper/Lower shadow, Doji, 3 black crows, 3 white soldiers, Bullish/Bearish Harami, Bullish/Bearish Engulfing
  * Added absolute indicator
  * Added Hull Moving Average indicator
  * Updated Bollinger Bands (variable multiplier, see #53) 
  * Fixed #39 - Possible update for TimeSeries.run()
  * Added Chaikin Money Flow indicator
  * Improved volume indicator
  * Added Close Location Value indicator
  * Added Positive Volume Index and Negative Volume Index indicators
  * Added zero-lag EMA indicator

## 0.7 (released May 21, 2015)

  * Fixed #35 - Fix max drawdown criterion
  * Improved documentation: user's guide & contributor's guidelines
  * Fixed #37 - Update Tick.toString method
  * Fixed #36 - Missing 'Period timePeriod' in full Tick constructor
  * Updated examples
  * Improved analysis criteria (to use actual entry/exit prices instead of close prices)
  * Added price and amount to `Order`
  * Added helpers for order creation
  * Renamed `Operation` to `Order`
  * Added a record/history of a trading session (`TradingRecord`)
  * Moved the trading logic from strategies to rules
  * Refactored trade operations
  * Added a difference indicator
  * Small other API changes

## 0.6 (released February 5, 2015)

  * Added `NaN` to Decimals
  * Renamed `TADecimal` to `Decimal`
  * Fixed #24 - Error in standard deviation calculation
  * Added moving time series (& cache: #25)
  * Refactored time series and ticks
  * Added entry-pass filter and exit-pass filter strategies
  * Replaced `JustBuyOnceStrategy` and `CombinedBuyAndSellStrategy` by `JustEnterOnceStrategy` and `CombinedEntryAndExitStrategy` respectively
  * Added examples
  * Added operation type helpers
  * Added strategy execution traces through SLF4J
  * Removed `.summarize(...)` methods and `Decision` (analysis)
  * Improved performance of some indicators and strategies
  * Generalized cache to all indicators (#20)
  * Removed AssertJ dependency
  * Fixed #16 - Division by zero in updated WalkForward example

## 0.5 (released October 22, 2014)

  * Switched doubles for TADecimals (BigDecimals) in indicators
  * Semantic improvement for IndicatorOverIndicatorStrategy
  * Fixed #11 - UnknownFormatConversionException when using toString() for 4 strategies 
  * Added a maximum value starter strategy
  * Added linear transaction cost (analysis criterion)
  * Removed evaluators (replaced by `.chooseBest(...)` and `.betterThan(...)` methods)
  * Added triple EMA indicator
  * Added double EMA indicator
  * Removed slicers (replaced by `.split(...)` methods)
  * Removed runner (replaced by `.run(...)` methods)
  * Added more tests
  * Removed `ConstrainedTimeSeries` (replaced by `.subseries(...)` methods)
  * Added/refactored examples (including walk-forward and candlestick chart)

## 0.4 (released May 28, 2014)

  * Fixed #2 - Tests failing in JDK8
  * Added indicators: Mean deviation, Commodity channel index, Percentage price oscillator (tests)
  * Added distance between indicator and constant
  * Added opposite strategy
  * Removed some runners
  * Added strategy runs on whole series
  * Refactored slicers
  * Removed log4j dependency
  * Added examples

## 0.3 (released March 11, 2014)

  * First public release
  * 100% Pure Java - works on any Java Platform version 6 or later
  * More than 40 technical indicators (Aroon, ATR, moving averages, parabolic SAR, RSI, etc.)
  * A powerful engine for building custom trading strategies
  * Utilities to run and compare strategies
  * Minimal 3rd party dependencies
  * MIT license
