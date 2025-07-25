Changelog for `ta4j`, roughly following [keepachangelog.com](http://keepachangelog.com/en/1.0.0/) from version 0.9 onwards.

## 0.19

### Breaking
- Refactored `ProfitLossCriterion`, `ProfitCriterion`, `LossCriterion`, `AverageProfitCriterion`, `AverageLossCriterion`, `ReturnCriterion`, `ProfitLossRatioCriterion` and `ProfitLossPercentageCriterion` criteria into their net and gross concrete classes
- [#1266](https://github.com/ta4j/ta4j/issues/1266) Consolidated BinaryOperation, UnaryOperation, TransformIndicator and CombineIndicator

### Fixed
- Updated Github test workflow to cache dependencies for quicker builds
- Updated test status badge on README
- Fixed EnterAndHoldCriterion to keep track of transaction and hold costs
- Clarify PnL criterion comments about trading costs
- Refactor ProfitLossPercentageCriterion to calculate aggregated return
- Fixed strict rules of `ConvergenceDivergenceIndicator`
- Fixed calculation of `ReturnOverMaxDrawdownCriterion`
- swapped parameter naming in  `BaseBarSeries#addTrade(final Number tradeVolume, final Number tradePrice)`
- Aggregation of amount and trades in `VolumeBarBuilder` and `TickBarBuilder`

### Changed
- Use `NetReturnCriterion` in `AverageReturnPerBarCriterion`, `EnterAndHoldCriterion` and `ReturnOverMaxDrawdownCriterion` to avoid optimistic bias of `GrossReturnCriterion`
- `ReturnOverMaxDrawdownCriterion` now returns 0 instead of `NaN` for strategies that never operate, and returns the net profit instead of `NaN` for strategies with no drawdown
- Changed snapshot distribution to Maven Central after OSSRH end-of-life

### Removed/Deprecated
- TransformIndicator and CombineIndicator

### Added
- Bars can now be built by `beginTime` instead of `endTime`


## 0.18 (released May 15, 2025)

### Breaking
- Updated project Java JDK from 11 > 21
- Updated Github workflows to use JDK 21
- Extracted NumFactory as source of numbers with defined precision
- Replaced `ZonedDateTime` with `Instant`
- Renamed `FixedDecimalIndicator` with `FixedNumIndicator`
- Moved `BaseBarBuilder` and `BaseBarBuilderFactory` to `bars`-package and renamed to `TimeBarBuilder` and `TimeBarBuilderFactory`
- Renamed `BaseBarConvertibleBuilderTest` to `BaseBarSeriesBuilderTest`
- Renamed  `Indicator.getUnstableBars` to  `Indicator.getCountOfUnstableBars`
- Moved `indicators/AbstractEMAIndicator` to `indicators/averages`-package
- Moved `indicators/DoubleEMAIndicator` to `indicators/averages`-package
- Moved `indicators/EMAIndicator` to `indicators/averages`-package
- Moved `indicators/HMAIndicator` to `indicators/averages`-package
- Moved `indicators/KAMAIndicator` to `indicators/averages`-package
- Moved `indicators/LWMAIndicator` to `indicators/averages`-package
- Moved `indicators/MMAIndicator` to `indicators/averages`-package
- Moved `indicators/SMAIndicator` to `indicators/averages`-package
- Moved `indicators/TripleEMAIndicator` to `indicators/averages`-package
- Moved `indicators/WMAIndicator` to `indicators/averages`-package
- Moved `indicators/ZLEMAIndicator` to `indicators/averages`-package
- Implemented sharing of `MathContext` in `DecimalNum`. For creating numbers, `NumFactory` implementations are the preferred way.

### Fixed
- Fixed `BaseBar.toString()` to avoid `NullPointerException` if any of its property is null
- Fixed `SMAIndicatorTest` to set the endTime of the next bar correctly
- Fixed `SMAIndicatorMovingSeriesTest` to set the endTime of the next bar correctly
- Use UTC TimeZone for `AroonOscillatorIndicatorTest`, `PivotPointIndicatorTest`
- Fixed `MockBarBuilder` to use `Instant.now` for beginTime
- Fixed `RecentSwingHighIndicatorTest` to create bars consistently
- Fixed `LSMAIndicator` to fix lsma calculation for incorrect values
- Fixed `RSIIndicator` getCountOfUnstableBars to return barCount value instead of 0 
- Fixed `RSIIndicator` calculate to return NaN during unstable period

### Changed
- Updated **jfreechart** dependency in **ta4j-examples** project from 1.5.3 to 1.5.5 to resolve [CVE-2023-52070](https://ossindex.sonatype.org/vulnerability/CVE-2023-6481?component-type=maven&component-name=ch.qos.logback%2Flogback-core)
- Updated **logback-classic** 1.4.12 > 1.5.6 to resolve [CVE-2023-6481](https://ossindex.sonatype.org/vulnerability/CVE-2023-6481?component-type=maven&component-name=ch.qos.logback%2Flogback-core)
- Cleaned code by using new java syntax `text blocks`
- Faster test execution by using `String.lines()` instead of `String` concatenation
- Improve Javadoc for `DecimalNum`and `DoubleNum`
- Allowed JUnit5 for new tests. Old remain as is.
- Updated `StochasticOscillatorKIndicator` constructor to use generic params
- Updated `StochasticRSIIndicator` to use `StochasticOscillatorKIndicator` instead of duplicating the logic
- Updated `TestUtils` assertIndicatorEquals and assertIndicatorNotEquals to handle NaN values

### Removed/Deprecated


### Added
- added `HeikinAshiBarAggregator`: Heikin-Ashi bar aggregator implementation
- added `HeikinAshiBarBuilder`: Heikin-Ashi bar builder implementation
- added `Bar.getZonedBeginTime`: the bar's begin time usable as ZonedDateTime
- added `Bar.getZonedEndTime`: the bar's end time usable as ZonedDateTime
- added `Bar.getSystemZonedBeginTime`: the bar's begin time converted to system time zone
- added `Bar.getSystemZonedEndTime`: the bar's end time converted to system time zone
- added `BarSeries.getSeriesPeriodDescriptionInSystemTimeZone`: with times printed in system's default time zone
- added `KRIIndicator`
- Added constructor with `amount` for  `EnterAndHoldCriterion`
- Added constructor with `amount` for  `VersusEnterAndHoldCriterion`
- Added `TickBarBuilder` to `bars`-package to aggregate bars after a fixed number of ticks
- Added `VolumeBarBuilder` to `bars`-package to  aggregate bars after a fixed number of contracts (volume)
- Added `TickBarBuilder` to `bars`-package
- Added `VolumeBarBuilder` to `bars`-package
- Added `Indicator.isStable`: is `true` if the indicator no longer produces incorrect values due to insufficient data
- Added `WildersMAIndicator` to `indicators.averages`-package: Wilder's moving average indicator
- Added `DMAIndicator` to `indicators.averages`-package: Displaced Moving Average (DMA) indicator
- Added `EDMAIndicator` to `indicators.averages`-package: Exponential Displaced Moving Average (EDMA) indicator
- Added `JMAIndicator` to `indicators.averages`-package: Jurik Moving Average (JMA) indicator
- Added `TMAIndicator` to `indicators.averages`-package: Trangular Moving Average (TMA) indicator
- Added `ATMAIndicator` to `indicators.averages`-package: Asymmetric Trangular Moving Average (TMA) indicator
- Added `MCGinleyMAIndicator` to `indicators.averages`-package: McGinley Moving Average (McGinleyMA) indicator
- Added `SMMAIndicator` to `indicators.averages`-package: Smoothed Moving Average (SMMA) indicator
- Added `SGMAIndicator` to `indicators.averages`-package: Savitzky-Golay Moving Average (SGMA) indicator
- Added `LSMAIndicator` to `indicators.averages`-package: Least Squares Moving Average (LSMA) indicator
- Added `KiJunV2Indicator` to `indicators.averages`-package: Kihon Moving Average (KiJunV2) indicator
- Added `VIDYAIndicator` to `indicators.averages`-package: Chande’s Variable Index Dynamic Moving Average (VIDYA) indicator
- Added `VWMAIndicator` to `indicators.averages`-package: Volume Weighted Moving Average (VWMA) indicator
- added `AverageIndicator`

## 0.17 (released September 9, 2024)

### Breaking
- Renamed **SMAIndicatorMovingSerieTest** to **SMAIndicatorMovingSeriesTest**

### Fixed
- Fixed **ta4jexamples** project still pointing to old (0.16) version of **ta4j-core**
- Fixed **SMAIndicatorMovingSeriesTest** test flakiness where on fast enough build machines the mock bars are created with the exact same end time
- Fixed NaN in **DXIndicator, MinusDIIndicator, PlusDIIndicator** if there is no trend
- Fixed look ahead bias in **RecentSwingHighIndicator** and **RecentSwingLowIndicator**

### Changed
- Implemented inner cache for **SMAIndicator**
- **BooleanTransformIndicator** remove enum constraint in favor of more flexible `Predicate`
- **EnterAndHoldReturnCriterion** replaced by `EnterAndHoldCriterion` to calculate the "enter and hold"-strategy of any criteria.
- **ATRIndicator** re-use tr by passing it as a constructor param when initializing averageTrueRangeIndicator

### Removed/Deprecated

### Added
- Added signal line and histogram to **MACDIndicator**
- Added getTransactionCostModel, getHoldingCostModel, getTrades in **TradingRecord**
- Added `Num.bigDecimalValue(DoubleNum)` to convert Num to a BigDecimal
- Added **AverageTrueRangeTrailingStopLossRule**
- Added **AverageTrueRangeStopLossRule**
- Added **AverageTrueRangeStopGainRule**
- Added **SqueezeProIndicator**
- Added **RecentSwingHighIndicator**
- Added **RecentSwingLowIndicator**
- Added **KalmanFilterIndicator**
- Added **HammerIndicator**
- Added **InvertedHammerIndicator**
- Added **HangingManIndicator**
- Added **ShootingStarIndicator**
- Added **DownTrendIndicator**
- Added **UpTrendIndicator**

## 0.16 (released May 15, 2024)

### Breaking
- **Upgraded to Java 11**
- **VersusBuyAndHoldCriterion** renamed to **`VersusEnterAndHoldCriterion`**
- **BarSeries** constructors use any instance of Num instead of Num-Function
- **GrossReturnCriterion** renamed to **`ReturnCriterion`**
- **NetProfitCriterion** and **GrossProfitCriterion** replaced by **`ProfitCriterion`**
- **NetLossCriterion** and **GrossLossCriterion** replaced by **`LossCriterion`**
- **LosingPositionsRatioCriterion** replaced by **`PositionsRatioCriterion`**
- **WinningPositionsRatioCriterion** replaced by **`PositionsRatioCriterion`**
- **Strategy#unstablePeriod** renamed to **`Strategy#unstableBars*`**
- **DateTimeIndicator** moved to package **`indicators/helpers`**
- **UnstableIndicator** moved to package **`indicators/helpers`**
- **ConvertableBaseBarBuilder** renamed to **`BaseBarConvertableBuilder`**
- **BarSeriesManager** updated to use **`TradeOnNextOpenModel`** by default, which opens new trades at index `t + 1` at the open price.
  - For strategies require the previous behaviour, i.e. trades seconds or minutes before the closing prices, **`TradeOnCurerentCloseModel`** can be passed to **BarSeriesManager**
    - For example:
      - `BarSeriesManager manager = new BarSeriesManager(barSeries, new TradeOnCurrentCloseModel())`
      - `BarSeriesManager manager = new BarSeriesManager(barSeries, transactionCostModel, holdingCostModel, tradeExecutionModel)`
- **BarSeriesManager** and **BacktestExecutor** moved to package **`backtest`**
- **BarSeries#getBeginIndex()** method returns correct begin index for bar series with max bar count

### Fixed
- **Fixed** **SuperTrendIndicator** fixed calculation when close price is the same as the previous Super Trend indicator value
- **Fixed** **ParabolicSarIndicator** fixed calculation for sporadic indices
- **ExpectancyCriterion** fixed calculation
- catch NumberFormatException if `DecimalNum.valueOf(Number)` is `NaN`
- **ProfitCriterion** fixed excludeCosts functionality as it was reversed
- **LossCriterion** fixed excludeCosts functionality as it was reversed
- **PerformanceReportGenerator** fixed netProfit and netLoss calculations to include costs
- **DifferencePercentageIndicator** fixed re-calculate instance variable on every iteration
- **ThreeWhiteSoldiersIndicator** fixed eliminated instance variable holding possible wrong value
- **ThreeBlackCrowsIndicator** fixed eliminated instance variable holding possible wrong value
- **TrailingStopLossRule** removed instance variable `currentStopLossLimitActivation` because it may not be always the correct (last) value
- sets `ClosePriceDifferenceIndicator#getUnstableBars` = `1`
- sets `ClosePriceRatioIndicator#getUnstableBars` = `1`
- sets `ConvergenceDivergenceIndicator#getUnstableBars` = `barCount`
- sets `GainIndicator#getUnstableBars` = `1`
- sets `HighestValueIndicator#getUnstableBars` = `barCount`
- sets `LossIndicator#getUnstableBars` = `1`
- sets `LowestValueIndicator#getUnstableBars` = `barCount`
- sets `TRIndicator#getUnstableBars` = `1`
- sets `PreviousValueIndicator#getUnstableBars` = `n` (= the n-th previous index)
- **PreviousValueIndicator** returns `NaN` if the (n-th) previous value of an indicator does not exist, i.e. if the (n-th) previous is below the first available index. 
- **EnterAndHoldReturnCriterion** fixes exception thrown when bar series was empty
- **BaseBarSeries** fixed `UnsupportedOperationException` when creating a bar series that is based on an unmodifiable collection
- **Num** implements Serializable

### Changed
- **BarSeriesManager** consider finishIndex when running backtest
- **BarSeriesManager** add **`holdingTransaction`**
- **BacktestExecutor** evaluates strategies in parallel when possible
- **CachedIndicator** synchronize on getValue()
- **BaseBar** defaults to **`DecimalNum`** type in all constructors
- **TRIndicator** improved calculation
- **WMAIndicator** improved calculation
- **KSTIndicator** improved calculation
- **RSIIndicator** simplify calculation
- **FisherIndicator** improved calculation
- **DoubleEMAIndicator** improved calculation
- **CMOIndicator** improved calculation
- **PearsonCorrelationIndicator** improved calculation
- **PivotPoint**-Indicators improved calculations
- **ValueAtRiskCriterion** improved calculation
- **ExpectedShortfallCriterion** improved calculation
- **SqnCriterion** improved calculation
- **NumberOfBreakEvenPositionsCriterion** shorten code
- **AverageReturnPerBarCriterion** improved calculation
- **ZLEMAIndicator** improved calculation
- **InPipeRule** improved calculation
- **SumIndicator** improved calculation
- updated pom.xml: slf4j-api to 2.0.7
- updated pom.xml: org.apache.poi to 5.2.3
- updated pom.xml: maven-jar-plugin to 3.3.0
- add `final` to properties where possible
- improved javadoc
- **SuperTrendIndicator**,**SuperTrendUpperIndicator**,**SuperTrendLowerIndicator**: optimized calculation
- **SuperTrendIndicator**, **SuperTrendLowerBandIndicator**, **SuperTrendUpperBandIndicator**: `multiplier` changed to from `Integer` to `Double`
- add missing `@Override` annotation
- **RecursiveCachedIndicator**: simplified code
- **LossIndicator**: optimize calculation
- **GainIndicator**: improved calculation
- **PriceVariationIndicator** renamed to **ClosePriceRatioIndicator** for consistency with new **ClosePriceDifferenceIndicator**
- made **UnaryOperation** and **BinaryOperation** public 

### Removed/Deprecated
- removed **Serializable** from `CostModel`
- removed `@Deprecated Bar#addTrade(double tradeVolume, double tradePrice, Function<Number, Num> numFunction)`; use `Bar#addTrade(Num tradeVolume, Num tradePrice)` instead.
- removed `@Deprecated Bar#addTrade(String tradeVolume, String tradePrice, Function<Number, Num> numFunction)`; use `Bar#addTrade(Num tradeVolume, Num tradePrice)` instead.
- removed `DecimalNum.valueOf(DecimalNum)`
- delete `.travis.yml` as this project is managed by "Github actions"

### Added
- added `TradingRecord.getStartIndex()` and `TradingRecord.getEndIndex()` to track start and end of the recording
- added **SuperTrendIndicator**
- added **SuperTrendUpperBandIndicator**
- added **SuperTrendLowerBandIndicator**
- added **Donchian Channel indicators (Upper, Lower, and Middle)**
- added `Indicator.getUnstableBars()`
- added `TransformIndicator.pow()`
- added `BarSeriesManager.getHoldingCostModel()` and `BarSeriesManager.getTransactionCostModel()`  to allow extending BarSeriesManager and reimplementing `run()`
- added `MovingAverageCrossOverRangeBacktest.java` and `ETH-USD-PT5M-2023-3-13_2023-3-15.json` test data file to demonstrate parallel strategy evaluation
- added javadoc improvements for percentage criteria
- added "lessIsBetter"-property for **AverageCriterion**
- added "lessIsBetter"-property for **RelativeStandardDeviation**
- added "lessIsBetter"-property for **StandardDeviationCriterion**
- added "lessIsBetter"-property for **StandardErrorCriterion**
- added "lessIsBetter"-property for **VarianceCriterion**
- added "lessIsBetter"-property for **NumberOfPositionsCriterion**
- added "addBase"-property for **ReturnCriterion** to include or exclude the base percentage of 1
- added **RelativeVolumeStandardDeviationIndicator**
- added **MoneyFlowIndexIndicator**
- added **IntraDayMomentumIndexIndicator**
- added **ClosePriceDifferenceIndicator**
- added **TimeSegmentedVolumeIndicator**
- added `DecimalNum.valueOf(DoubleNum)` to convert a DoubleNum to a DecimalNum.
- added `DoubleNum.valueOf(DecimalNum)` to convert a DecimalNum to a DoubleNum.
- added "TradeExecutionModel" to modify trade execution during backtesting
- added **NumIndicator** to calculate any `Num`-value for a `Bar`
- added **RunningTotalIndicator** to calculate a cumulative sum for a period.

### Fixed
- **Fixed** **CashFlow** fixed calculation with custom startIndex and endIndex
- **Fixed** **Returns** fixed calculation with custom startIndex and endIndex
- **Fixed** **ExpectedShortfallCriterion** fixed calculation with custom startIndex and endIndex
- **Fixed** **MaximumDrawDownCriterion** fixed calculation with custom startIndex and endIndex
- **Fixed** **EnterAndHoldReturnCriterion** fixed calculation with custom startIndex and endIndex
- **Fixed** **VersusEnterAndHoldCriterion** fixed calculation with custom startIndex and endIndex
- **Fixed** **BarSeriesManager** consider finishIndex when running backtest

## 0.15 (released September 11, 2022)

### Breaking
- **NumberOfConsecutiveWinningPositions** renamed to **`NumberOfConsecutivePositions`**
- **DifferencePercentage** renamed to **`DifferencePercentageIndicator`**
- **BuyAndHoldCriterion** renamed to **`EnterAndHoldCriterion`**
- **DXIndicator** moved to adx-package
- **PlusDMIndicator** moved to adx-package
- **MinusDMIndicator** moved to adx-package
- `analysis/criterion`-package moved to root
- `cost`-package moved to `analysis/cost`-package
- **AroonXXX** indicators moved to aroon package

### Fixed
- **LosingPositionsRatioCriterion** correct betterThan
- **VersusBuyAndHoldCriterionTest** NaN-Error.
- **Fixed** **`ChaikinOscillatorIndicatorTest`**
- **DecimalNum#remainder()** adds NaN-check
- **Fixed** **ParabolicSarIndicatorTest** fixed openPrice always 0 and highPrice lower than lowPrice
- **UlcerIndexIndicator** using the max price of current period instead of the highest value of last n bars
- **DurationBarAggregator** fixed aggregation of bars with gaps


### Changed
- **KeltnerChannelMiddleIndicator** changed superclass to AbstractIndicator; add GetBarCount() and toString()
- **KeltnerChannelUpperIndicator** add constructor to accept pre-constructed ATR; add GetBarCount() and toString()
- **KeltnerChannelLowerIndicator** add constructor to accept pre-constructed ATR; add GetBarCount() and toString()
- **BarSeriesManager** removed empty args constructor
- **Open|High|Low|Close** do not cache price values anymore
- **DifferenceIndicator(i1,i2)** replaced by the more flexible CombineIndicator.minus(i1,i2)
- **DoubleNum** replace redundant `toString()` call in `DoubleNum.valueOf(Number i)` with `i.doubleValue()`
- **ZeroCostModel** now extends from `FixedTransactionCostModel`

### Removed/Deprecated
- **Num** removed Serializable
- **PriceIndicator** removed

### Added
- **NumericIndicator** new class providing a fluent and lightweight api for indicator creation
- **AroonFacade**, **BollingerBandFacade**, **KeltnerChannelFacade** new classes providing a facade for indicator groups by using lightweight `NumericIndicators`
- **AbstractEMAIndicator** added getBarCount() to support future enhancements
- **ATRIndicator** "uncached" by changing superclass to AbstractIndicator; added constructor to accept TRIndicator and getter for same; added toString(); added getBarCount() to support future enhancements
- :tada: **Enhancement** added possibility to use CostModels when backtesting with the BacktestExecutor
- :tada: **Enhancement** added Num#zero, Num#one, Num#hundred
- :tada: **Enhancement** added possibility to use CostModels when backtesting with the BacktestExecutor
- :tada: **Enhancement** added Indicator#stream() method
- :tada: **Enhancement** added a new CombineIndicator, which can combine the values of two Num Indicators with a given combine-function
- **Example** added a json serialization and deserialization example of BarSeries using google-gson library
- **EnterAndHoldCriterion** added constructor with TradeType to begin with buy or sell
- :tada: **Enhancement** added Position#getStartingType() method
- :tada: **Enhancement** added **`SqnCriterion`**
- :tada: **Enhancement** added **`StandardDeviationCriterion`**
- :tada: **Enhancement** added **`RelativeStandardDeviationCriterion`**
- :tada: **Enhancement** added **`StandardErrorCriterion`**
- :tada: **Enhancement** added **`VarianceCriterion`**
- :tada: **Enhancement** added **`AverageCriterion`**
- :tada: **Enhancement** added javadoc for all rules to make clear which rule makes use of a TradingRecord
- **Enhancement** prevent Object[] allocation for varargs log.trace and log.debug calls by wrapping them in `if` blocks
- :tada: **Enhancement** added **`FixedTransactionCostModel`**
- :tada: **Enhancement** added **`AnalysisCriterion.PositionFilter`** to handle both sides within one Criterion.

## 0.14 (released April 25, 2021)

### Breaking
- **Breaking:** **`PrecisionNum`** renamed to **`DecimalNum`**
- **Breaking:** **`AverageProfitableTradesCriterion`** renamed to **`WinningTradesRatioCriterion`**
- **Breaking:** **`AverageProfitCriterion`** renamed to **`AverageReturnPerBarCriterion`**
- **Breaking:** **`BuyAndHoldCriterion`** renamed to **`BuyAndHoldReturnCriterion`**
- **Breaking:** **`RewardRiskRatioCriterion`** renamed to **`ReturnOverMaxDrawdownCriterion`**
- **Breaking:** **`ProfitLossCriterion`** moved to PnL-Package
- **Breaking:** **`ProfitLossPercentageCriterion`** moved to PnL-Package
- **Breaking:** **`TotalProfitCriterion`** renamed to **`GrossReturnCriterion`** and moved to PnL-Package.
- **Breaking:** **`TotalProfit2Criterion`** renamed to **`GrossProfitCriterion`** and moved to PnL-Package.
- **Breaking:** **`TotalLossCriterion`** renamed to **`NetLossCriterion`** and moved to PnL-Package.
- **Breaking:** package "tradereports" renamed to "reports"
- **Breaking:** **`NumberOfTradesCriterion`** renamed to **`NumberOfPositionsCriterion`**
- **Breaking:** **`NumberOfLosingTradesCriterion`** renamed to **`NumberOfLosingPositionsCriterion`**
- **Breaking:** **`NumberOfWinningTradesCriterion`** renamed to **`NumberOfWinningPositionsCriterion`**
- **Breaking:** **`NumberOfBreakEvenTradesCriterion`** renamed to **`NumberOfBreakEvenPositionsCriterion`**
- **Breaking:** **`WinningTradesRatioCriterion`** renamed to **`WinningPositionsRatioCriterion`**
- **Breaking:** **`TradeStatsReport`** renamed to **`PositionStatsReport`**
- **Breaking:** **`TradeStatsReportGenerator`** renamed to **`PositionStatsReportGenerator`**
- **Breaking:** **`TradeOpenedMinimumBarCountRule`** renamed to **`OpenedPositionMinimumBarCountRule`**
- **Breaking:** **`Trade.class`** renamed to **`Position.class`**
- **Breaking:** **`Order.class`** renamed to **`Trade.class`**
- **Breaking:** package "tradereports" renamed to "reports"
- **Breaking:** package "trading/rules" renamed to "rules"
- **Breaking:** remove Serializable from all indicators
- **Breaking:** Bar#trades: changed type from int to long


### Fixed
- **Fixed `Trade`**: problem with profit calculations on short trades.
- **Fixed `TotalLossCriterion`**: problem with profit calculations on short trades.
- **Fixed `BarSeriesBuilder`**: removed the Serializable interface
- **Fixed `ParabolicSarIndicator`**: problem with calculating in special cases
- **Fixed `BaseTimeSeries`**: can now be serialized
- **Fixed `ProfitLossPercentageCriterion`**: use entryPrice#getValue() instead of entryPrice#getPricePerAsset()

### Changed
- **Trade**: Changed the way Nums are created.
- **WinningTradesRatioCriterion** (previously AverageProfitableTradesCriterion): Changed to calculate trade profits using Trade's getProfit().
- **BuyAndHoldReturnCriterion** (previously BuyAndHoldCriterion): Changed to calculate trade profits using Trade's getProfit().
- **ExpectedShortfallCriterion**: Removed unnecessary primitive boxing.
- **NumberOfBreakEvenTradesCriterion**: Changed to calculate trade profits using Trade's getProfit().
- **NumberOfLosingTradesCriterion**: Changed to calculate trade profits using Trade's getProfit().
- **NumberOfWinningTradesCriterion**: Changed to calculate trade profits using Trade's getProfit().
- **ProfitLossPercentageCriterion**: Changed to calculate trade profits using Trade's entry and exit prices.
- **TotalLossCriterion**: Changed to calculate trade profits using Trade's getProfit().
- **TotalReturnCriterion** (previously TotalProfitCriterion): Changed to calculate trade profits using Trade's getProfit().
- **WMAIndicator**: reduced complexity of WMAIndicator implementation

### Removed/Deprecated
- **MultiplierIndicator**: replaced by TransformIndicator.
- **AbsoluteIndicator**: replaced by TransformIndicator.

### Added
- **Enhancement** Improvements on gitignore
- **Enhancement** Added TradeOpenedMinimumBarCountRule - rule to specify minimum bar count for opened trade.
- **Enhancement** Added DateTimeIndicator a new Indicator for dates.
- **Enhancement** Added DayOfWeekRule for specifying days of the week to trade.
- **Enhancement** Added TimeRangeRule for trading within time ranges.
- **Enhancement** Added floor() and ceil() to Num.class
- **Enhancement** Added getters getLow() and getUp() in CrossedDownIndicatorRule
- **Enhancement** Added BarSeriesUtils: common helpers and shortcuts for BarSeries methods.
- **Enhancement** Improvements for PreviousValueIndicator: more descriptive toString() method, validation of n-th previous bars in
- **Enhancement** Added Percentage Volume Oscillator Indicator, PVOIndicator.
- **Enhancement** Added Distance From Moving Average Indicator, DistanceFromMAIndicator.
- **Enhancement** Added Know Sure Thing Indicator, KSTIndicator.
 constructor of PreviousValueIndicator
- :tada: **Enhancement** added getGrossProfit() and getGrossProfit(BarSeries) on Trade.
- :tada: **Enhancement** added getPricePerAsset(BarSeries) on Order.
- :tada: **Enhancement** added convertBarSeries(BarSeries, conversionFunction) to BarSeriesUtils.
- :tada: **Enhancement** added UnstableIndicator.
- :tada: **Enhancement** added Chainrule.
- :tada: **Enhancement** added BarSeriesUtils#sortBars.
- :tada: **Enhancement** added BarSeriesUtils#addBars.
- :tada: **Enhancement** added Num.negate() to negate a Num value.
- :tada: **Enhancement** added **`GrossLossCriterion.class`**.
- :tada: **Enhancement** added **`NetProfitCriterion.class`**.
- :tada: **Enhancement** added chooseBest() method with parameter tradeType in AnalysisCriterion.
- :tada: **Enhancement** added **`AverageLossCriterion.class`**.
- :tada: **Enhancement** added **`AverageProfitCriterion.class`**.
- :tada: **Enhancement** added **`ProfitLossRatioCriterion.class`**.
- :tada: **Enhancement** added **`ExpectancyCriterion.class`**.
- :tada: **Enhancement** added **`ConsecutiveWinningPositionsCriterion.class`**.
- :tada: **Enhancement** added **`LosingPositionsRatioCriterion.class`**
- :tada: **Enhancement** added Position#hasProfit.
- :tada: **Enhancement** added Position#hasLoss.
- :tada: **Enhancement** exposed both EMAs in MACD indicator


## 0.13 (released November 5, 2019)

### Breaking
- **Breaking** Refactored from Max/Min to High/Low in Bar class
- **Breaking** Removed redundant constructors from BaseBar class
- **Breaking** Renamed `TimeSeries` to `BarSeries`

### Fixed
- **Fixed `BaseBarSeries`**: problem with getSubList for series with specified `maximumBarCount`.
- **Fixed return `BigDecimal` instead of `Number` in**: `PrecisionNum.getDelegate`.
- **Fixed `java.lang.ClassCastException` in**: `PrecisionNum.equals()`.
- **Fixed `java.lang.ClassCastException` in**: `DoubleNum.equals()`.
- **Fixed `java.lang.NullPointerException` in**: `NumberOfBarsCriterion.calculate(TimeSeries, Trade)` for opened trade.
- **Fixed `java.lang.NullPointerException` in**: `AverageProfitableTradesCriterion.calculate(TimeSeries, Trade)` for opened trade.
- **StopGainRule**: now correctly handles stops for sell orders
- **StopLossRule**: now correctly handles stops for sell orders
- **ProfitLossCriterion**: fixed to work properly for short trades
- **PivotPointIndicator**: fixed possible npe if first bar is not in same period
- **`IchimokuChikouSpanIndicator`**: fixed calculations - applied correct formula.
- **CloseLocationValueIndicator**: fixed special case, return zero instead of NaN if high price == low price

### Changed
- **PrecisionNum**: improve performance for methods isZero/isPositive/isPositiveOrZero/isNegative/isNegativeOrZero.
- **BaseTimeSeriesBuilder** moved from inner class to own class
- **TrailingStopLossRule** added ability to look back the last x bars for calculating the trailing stop loss

### Added
- **Enhancement** Added getters for AroonDownIndicator and AroonUpIndicator in AroonOscillatorIndicator
- **Enhancement** Added common constructors in BaseBar for BigDecimal, Double and String values
- **Enhancement** Added constructor in BaseBar with trades property
- **Enhancement** Added BaseBarBuilder and ConvertibleBaseBarBuilder - BaseBar builder classes
- **Enhancement** Added BarAggregator and TimeSeriesAggregator to allow aggregates bars and time series
- **Enhancement** Added LWMA Linearly Weighted Moving Average Indicator
- **Enhancement** Implemented trading cost models (linear transaction and borrowing cost models)
- **Enhancement** Implemented Value at Risk Analysis Criterion
- **Enhancement** Implemented Expected Shortfall Analysis Criterion
- **Enhancement** Implemented Returns class to analyze the time series of return rates. Supports logarithmic and arithmetic returns
- **Enhancement** Implemented a way to find the best result for multiple strategies by submitting a range of numbers while backtesting
- **Enhancement** Implemented NumberOfBreakEvenTradesCriterion for counting break even trades
- **Enhancement** Implemented NumberOfLosingTradesCriterion for counting losing trades
- **Enhancement** Implemented NumberOfWinningTradesCriterion for counting winning trades
- **Enhancement** Implemented NumberOfWinningTradesCriterion for counting winning trades
- **Enhancement** Implemented ProfitLossPercentageCriterion for calculating the total performance percentage of your trades
- **Enhancement** Implemented TotalProfit2Criterion for calculating the total profit of your trades
- **Enhancement** Implemented TotalLossCriterion for calculating the total loss of your trades
- **Enhancement** Added ADX indicator based strategy to ta4j-examples
- **Enhancement** TrailingStopLossRule: added possibility of calculations of TrailingStopLossRule also for open, high, low price. Added getter
for currentStopLossLimitActivation
- **Enhancement** Add constructors with parameters to allow custom implementation of ReportGenerators in BacktestExecutor
- **Enhancement** Added license checker goal on CI's pipeline
- **Enhancement** Added source format checker goal on CI's pipeline

### Removed/Deprecated

## 0.12 (released September 10, 2018)

### Breaking:
   - `Decimal` class has been replaced by new `Num` interface. Enables using `Double`, `BigDecimal` and custom data types for calculations.
   - Big changes in `TimeSeries` and `BaseTimeSeries`. Multiple new `addBar(..)` functions in `TimeSeries` allow to add data directly to the series


### Fixed
- **TradingBotOnMovingTimeSeries**: fixed calculations and ArithmeticException Overflow
- **Fixed wrong indexing in**: `Indicator.toDouble()`.
- **PrecisionNum.sqrt()**: using DecimalFormat.parse().
- **RandomWalk[High|Low]Indicator**: fixed formula (max/min of formula with n iterating from 2 to barCount)

### Changed
- **ALL INDICATORS**: `Decimal` replaced by `Num`.
- **ALL CRITERION**: Calculations modified to use `Num`.
- **AbstractIndicator**: new `AbstractIndicator#numOf(Number n)` function as counterpart of dropped `Decimal.valueOf(double|int|..)`
- **TimeSeries | Bar**: preferred way to add bar data to a `TimeSeries` is directly to the series via new `TimeSeries#addBar(time,open,high,..)` functions. It ensures to use the correct `Num` implementation of the series
- **XlsTestsUtils**: now processes xls with one or more days between data rows (daily, weekly, monthly, etc).  Also handle xls #DIV/0! calculated cells (imported as NaN.NaN)
- **CachedIndicator**: Last bar is not cached to support real time indicators
- **TimeSeries | Bar **: added new `#addPrice(price)` function that adds price to (last) bar.
- Parameter **timeFrame** renamed to **barCount**.
- **Various Rules**: added constructor that provides `Number` parameters
- **AroonUpIndicator**: redundant TimeSeries call was removed from constructor
- **AroonDownIndicator**: redundant TimeSeries call was removed from constructor
- **BaseTimeSeries**: added setDefaultFunction() to SeriesBuilder for setting the default Num type function for all new TimeSeries built by that SeriesBuilder, updated BuildTimeSeries example
- **<various>CriterionTest**: changed from explicit constructor calls to `AbstractCriterionTest.getCriterion()` calls.
- **ChopIndicator**: transparent fixes
- **StochasticRSIIndicator**: comments and params names changes to reduce confusion
- **ConvergenceDivergenceIndicator**: remove unused method
- **ChopIndicatorTest**: spelling, TODO: add better tests
- **Various Indicators**: remove double math operations, change `Math.sqrt(double)` to `Num.sqrt()`, other small improvements
- **RandomWalk[High|Low]Indicator**: renamed to `RWI[High|Low]Indicator`

### Added
- **BaseTimeSeries.SeriesBuilder**: simplifies creation of BaseTimeSeries.
- **Num**: Extracted interface of dropped `Decimal` class
- **DoubleNum**: `Num` implementation to support calculations based on `double` primitive
- **BigDecimalNum**: Default `Num` implementation of `BaseTimeSeries`
- **DifferencePercentageIndicator**: New indicator to get the difference in percentage from last value
- **PrecisionNum**: `Num` implementation to support arbitrary precision
- **TestUtils**: removed convenience methods for permuted parameters, fixed all unit tests
- **TestUtils**: added parameterized abstract test classes to allow two test runs with `DoubleNum` and `BigDecimalNum`
- **ChopIndicator** new common indicator of market choppiness (low volatility), and related 'ChopIndicatorTest' JUnit test and 'CandlestickChartWithChopIndicator' example
- **BollingerBandWidthIndicator**: added missing constructor documentation.
- **BollingerBandsLowerIndicator**: added missing constructor documentation.
- **BollingerBandsMiddleIndicator**: added missing constructor documentation.
- **TrailingStopLossRule**: new rule that is satisfied if trailing stop loss is reached
- **Num**: added Num sqrt(int) and Num sqrt()
- **pom.xml**: added support to generate ta4j-core OSGi artifact.

### Removed/Deprecated
- **Decimal**: _removed_. Replaced by `Num` interface
- **TimeSeries#addBar(Bar bar)**: _deprecated_. Use `TimeSeries#addBar(Time, open, high, low, ...)`
- **BaseTimeSeries**: _Constructor_ `BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex)` _removed_. Use `TimeSeries.getSubseries(int i, int i)` instead
- **FisherIndicator**: commented constructor removed.
- **TestUtils**: removed convenience methods for permuted parameters, fixed all unit tests
- **BaseTimeSeries**: _Constructor_ `BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex)` _removed_. Use `TimeSeries.getSubseries(int i, int i)` instead
- **BigDecimalNum**: _removed_.  Replaced by `PrecisionNum`
- **AbstractCriterionTest**: removed constructor `AbstractCriterionTest(Function<Number, Num)`.  Use `AbstractCriterionTest(CriterionFactory, Function<Number, Num>)`.
- **<various>Indicator**: removed redundant `private TimeSeries`

## 0.11 (released January 25, 2018)

- **BREAKING**: Tick has been renamed to **Bar**

### Fixed
- **ATRIndicator**: fixed calculations
- **PlusDI, MinusDI, ADX**: fixed calculations
- **LinearTransactionCostCriterion**: fixed calculations, added xls file and unit tests
- **FisherIndicator**: fixed calculations
- **ConvergenceDivergenceIndicator**: fixed NPE of optional "minStrenght"-property

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

with the release 0.10 we have changed the previous java package definition to org.ta4j or to be more specific to org.ta4j.core (the new organisation). You have to reorganize all your references to the new packages!
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
