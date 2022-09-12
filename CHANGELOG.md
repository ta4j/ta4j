Changelog for `ta4j`, roughly following [keepachangelog.com](http://keepachangelog.com/en/1.0.0/) from version 0.9 onwards.
ta4j 的变更日志，从 0.9 版开始大致遵循 keepachangelog.com。

## 0.14 (released April 25, 2021)
## 0.14（2021 年 4 月 25 日发布）

### Breaking
- **Breaking:** **Changed order of parameters for addTrade in `BaseBarSeries` to match abstract and description
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
- ### 打破
- **中断：** **更改了 `BaseBarSeries` 中 addTrade 的参数顺序以匹配摘要和描述
- **中断：** **`PrecisionNum`** 重命名为 **`DecimalNum`**
- **突破：** **`AverageProfitableTradesCriterion`** 重命名为 **`WinningTradesRatioCriterion`**
- **打破：** **`AverageProfitCriterion`** 重命名为 **`AverageReturnPerBarCriterion`**
- **中断：** **`BuyAndHoldCriterion`** 重命名为 **`BuyAndHoldReturnCriterion`**
- **中断：** **`RewardRiskRatioCriterion`** 重命名为 **`ReturnOverMaxDrawdownCriterion`**
- **突破：** **`ProfitLossCriterion`** 移至 PnL-Package
- **突破：** **`ProfitLossPercentageCriterion`** 移至 PnL-Package
- **打破：** **`TotalProfitCriterion`** 重命名为 **`GrossReturnCriterion`** 并移至 PnL-Package。
- **Breaking:** **`TotalProfit2Criterion`** 重命名为 **`GrossProfitCriterion`** 并移至 PnL-Package。
- **Breaking:** **`TotalLossCriterion`** 重命名为 **`NetLossCriterion`** 并移至 PnL-Package。
- **打破：** 包“tradereports”重命名为“reports”
- **中断：** **`NumberOfTradesCriterion`** 重命名为 **`NumberOfPositionsCriterion`**
- **中断：** **`NumberOfLosingTradesCriterion`** 重命名为 **`NumberOfLosingPositionsCriterion`**
- **打破：** **`NumberOfWinningTradesCriterion`** 重命名为 **`NumberOfWinningPositionsCriterion`**
- **中断：** **`NumberOfBreakEvenTradesCriterion`** 重命名为 **`NumberOfBreakEvenPositionsCriterion`**
- **突破：** **`WinningTradesRatioCriterion`** 重命名为 **`WinningPositionsRatioCriterion`**
- **突破：** **`TradeStatsReport`** 重命名为 **`PositionStatsReport`**
- **中断：** **`TradeStatsReportGenerator`** 重命名为 **`PositionStatsReportGenerator`**
- **Breaking:** **`TradeOpenedMinimumBarCountRule`** 重命名为 **`OpenedPositionMinimumBarCountRule`**
- **Breaking:** **`Trade.class`** 重命名为 **`Position.class`**
- **Breaking:** **`Order.class`** 重命名为 **`Trade.class`**
- **打破：** 包“tradereports”重命名为“reports”
- **打破：**包“交易/规则”重命名为“规则”
- **中断：**从所有指标中删除可序列化
- **突破：** Bar#trades：将类型从 int 更改为 long


### Fixed
- **Fixed `Trade`**: problem with profit calculations on short trades.
- **Fixed `TotalLossCriterion`**: problem with profit calculations on short trades.
- **Fixed `BarSeriesBuilder`**: removed the Serializable interface
- **Fixed `ParabolicSarIndicator`**: problem with calculating in special cases
- **Fixed `BaseTimeSeries`**: can now be serialized
- **Fixed `ProfitLossPercentageCriterion`**: use entryPrice#getValue() instead of entryPrice#getPricePerAsset()

###固定的
- **修复了“交易”**：空头交易的利润计算问题。
- **修复了`TotalLossCriterion`**：空头交易的利润计算问题。
- **Fixed `BarSeriesBuilder`**: 移除 Serializable 接口
- **修复了`ParabolicSarIndicator`**：特殊情况下的计算问题
- **固定`BaseTimeSeries`**：现在可以序列化
- **固定 `ProfitLossPercentageCriterion`**：使用 entryPrice#getValue() 而不是 entryPrice#getPricePerAsset()

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
- ### 改变了
- **贸易**：改变了创建数字的方式。
- **WinningTradesRatioCriterion**（以前的 AverageProfitableTradesCriterion）：更改为使用 Trade 的 getProfit() 计算交易利润。
- **BuyAndHoldReturnCriterion**（以前的 BuyAndHoldCriterion）：更改为使用 Trade 的 getProfit() 计算贸易利润。
- **ExpectedShortfallCriterion**：删除了不必要的原始装箱。
- **NumberOfBreakEvenTradesCriterion**：更改为使用 Trade 的 getProfit() 计算贸易利润。
- **NumberOfLosingTradesCriterion**：更改为使用 Trade 的 getProfit() 计算交易利润。
- **NumberOfWinningTradesCriterion**：更改为使用 Trade 的 getProfit() 计算贸易利润。
- **ProfitLossPercentageCriterion**：更改为使用 Trade 的进入和退出价格计算贸易利润。
- **TotalLossCriterion**：更改为使用 Trade 的 getProfit() 计算贸易利润。
- **TotalReturnCriterion**（以前的 TotalProfitCriterion）：更改为使用 Trade 的 getProfit() 计算贸易利润。
- **WMAIndicator**：降低 WMAIndicator 实现的复杂性

### Removed/Deprecated
- **MultiplierIndicator**: replaced by TransformIndicator.
- **AbsoluteIndicator**: replaced by TransformIndicator.
- ### 已删除/已弃用
- **MultiplierIndicator**：替换为 TransformIndicator。
- **AbsoluteIndicator**：替换为 TransformIndicator。

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
###添加
- **增强** gitignore 的改进
- **增强功能** 添加了 TradeOpenedMinimumBarCountRule - 指定已开交易的最小柱数的规则。
- **增强功能** 添加了 DateTimeIndicator 一个新的日期指示器。
- **增强** 添加了 DayOfWeekRule 以指定一周中的哪几天进行交易。
- **增强**为时间范围内的交易添加了 TimeRangeRule。
- **增强**将 floor() 和 ceil() 添加到 Num.class
- **增强** 在 CrossedDownIndicatorRule 中添加了 getter getLow() 和 getUp()
- **增强功能** 添加了 BarSeriesUtils：BarSeries 方法的常用助手和快捷方式。
- **增强**对 PreviousValueIndicator 的改进：更具描述性的 toString() 方法，验证之前的第 n 个柱
- **增强**添加了百分比成交量振荡器指标，PVOIndicator。
- **增强**添加了与移动平均线指标的距离，DistanceFromMAIndicator。
- **增强**添加了 Know Sure Thing 指标，KSTIndicator。
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
- 
- PreviousValueIndicator 的构造函数
- :tada: **Enhancement** 在交易中添加了 getGrossProfit() 和 getGrossProfit(BarSeries)。
- :tada: **Enhancement** 在订单中添加了 getPricePerAsset(BarSeries)。
- :tada: **Enhancement** 将 convertBarSeries(BarSeries, conversionFunction) 添加到 BarSeriesUtils。
- :tada: **Enhancement** 添加了 UnstableIndicator。
- :tada: **增强**添加了 Chainrule。
- :tada: **增强**添加了 BarSeriesUtils#sortBars。
- :tada: **增强**添加了 BarSeriesUtils#addBars。
- :tada: **Enhancement** 添加了 Num.negate() 来否定 Num 值。
- :tada: **Enhancement** 添加了 **`GrossLossCriterion.class`**。
- :tada: **Enhancement** 添加**`NetProfitCriterion.class`**。
- :tada: **Enhancement** 在 AnalysisCriterion 中添加了带有参数 tradeType 的 chooseBest() 方法。
- :tada: **Enhancement** 添加了 **`AverageLossCriterion.class`**。
- :tada: **Enhancement** 添加了 **`AverageProfitCriterion.class`**。
- :tada: **Enhancement** 添加了 **`ProfitLossRatioCriterion.class`**。
- :tada: **Enhancement** 添加**`ExpectancyCriterion.class`**。
- :tada: **Enhancement** 添加**`ConsecutiveWinningPositionsCriterion.class`**。
- :tada: **Enhancement** 添加**`LosingPositionsRatioCriterion.class`**
- :tada: **增强**添加了 Position#hasProfit。
- :tada: **增强**添加了 Position#hasLoss。
- :tada: **增强**在 MACD 指标中暴露了两个 EMA


## 0.13 (released November 5, 2019)
## 0.13（2019 年 11 月 5 日发布）

### Breaking
- **Breaking** Refactored from Max/Min to High/Low in Bar class
- **Breaking** Removed redundant constructors from BaseBar class
- **Breaking** Renamed `TimeSeries` to `BarSeries`
### 打破
- **Breaking** 在 Bar 类中从 Max/Min 重构为 High/Low
- **打破**从 BaseBar 类中删除了多余的构造函数
- **Breaking** 将 `TimeSeries` 重命名为 `BarSeries`

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
###固定的
- **修复了 `BaseBarSeries`**：getSubList 的问题，用于指定 `maximumBarCount` 的系列。
- **固定返回 `BigDecimal` 而不是**中的 `Number`：`PrecisionNum.getDelegate`。
- **修复了**中的`java.lang.ClassCastException`：`PrecisionNum.equals()`。
- **修复了**中的`java.lang.ClassCastException`：`DoubleNum.equals()`。
- **修复了 `java.lang.NullPointerException` in**: `NumberOfBarsCriterion.calculate(TimeSeries, Trade)` 用于打开的交易。
- **修复了 `java.lang.NullPointerException` in**: `AverageProfitableTradesCriterion.calculate(TimeSeries, Trade)` 用于打开的交易。
- **StopGainRule**：现在可以正确处理卖单的止损
- **StopLossRule**：现在可以正确处理卖单的止损
- **ProfitLossCriterion**：固定为空头交易正常工作
- **PivotPointIndicator**：如果第一个柱不在同一时期，则修复可能的 npe
- **`IchimokuChikouSpanIndicator`**：固定计算 - 应用了正确的公式。
- **CloseLocationValueIndicator**：固定特殊情况，如果高价 == 低价则返回零而不是 NaN

### Changed
- **PrecisionNum**: improve performance for methods isZero/isPositive/isPositiveOrZero/isNegative/isNegativeOrZero.
- **BaseTimeSeriesBuilder** moved from inner class to own class
- **TrailingStopLossRule** added ability to look back the last x bars for calculating the trailing stop loss
### 改变了
- **PrecisionNum**：提高方法 isZero/isPositive/isPositiveOrZero/isNegative/isNegativeOrZero 的性能。
- **BaseTimeSeriesBuilder** 从内部类移动到自己的类
- **TrailingStopLossRule** 增加了回顾最后 x 个柱以计算追踪止损的能力

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

### 添加
- **增强**在 AroonOscillatorIndicator 中为 AroonDownIndicator 和 AroonUpIndicator 添加了 getter
- **增强** 在 BaseBar 中为 BigDecimal、Double 和 String 值添加了通用构造函数
- **增强**在 BaseBar 中添加了带有交易属性的构造函数
- **增强功能** 添加了 BaseBarBuilder 和 ConvertibleBaseBarBuilder - BaseBar 构建器类
- **增强** 添加了 BarAggregator 和 TimeSeriesAggregator 以允许聚合条形图和时间序列
- **增强**添加了 LWMA 线性加权移动平均线指标
- **增强**实施的交易成本模型（线性交易和借贷成本模型）
- **增强**实施的风险价值分析标准
- **增强**已实施预期短缺分析标准
- **Enhancement** 实施 Returns 类以分析回报率的时间序列。支持对数和算术返回
- **增强**通过在回测时提交一系列数字，实现了一种为多种策略找到最佳结果的方法
- **增强** 实施 NumberOfBreakEvenTradesCriterion 以计算盈亏平衡交易
- **增强** 实施 NumberOfLosingTradesCriterion 以计算亏损交易
- **增强**实施 NumberOfWinningTradesCriterion 以计算获胜交易
- **增强**实施 NumberOfWinningTradesCriterion 以计算获胜交易
- **增强** 实施了用于计算您交易的总绩效百分比的利润损失百分比标准
- **增强**实施 TotalProfit2Criterion 以计算您的交易总利润
- **增强**实施 TotalLossCriterion 以计算您的交易总损失
- **增强**向 ta4j-examples 添加了基于 ADX 指标的策略
- **增强** TrailingStopLossRule：增加了计算 TrailingStopLossRule 的可能性，也适用于开盘价、最高价、最低价。添加了吸气剂
for currentStopLossLimitActivation
- **Enhancement** Add constructors with parameters to allow custom implementation of ReportGenerators in BacktestExecutor
- **Enhancement** Added license checker goal on CI's pipeline
- **Enhancement** Added source format checker goal on CI's pipeline
对于 currentStopLossLimitActivation
- **增强**添加带参数的构造函数以允许在 BacktestExecutor 中自定义实现 ReportGenerators
- **增强**在 CI 的管道上添加了许可证检查器目标
- **增强**在 CI 的管道上添加了源格式检查器目标

### Removed/Deprecated
### 已删除/已弃用

## 0.12 (released September 10, 2018)
## 0.12（2018 年 9 月 10 日发布）

### Breaking:
   - `Decimal` class has been replaced by new `Num` interface. Enables using `Double`, `BigDecimal` and custom data types for calculations.
   - Big changes in `TimeSeries` and `BaseTimeSeries`. Multiple new `addBar(..)` functions in `TimeSeries` allow to add data directly to the series
### 破坏：
    - `Decimal` 类已被新的 `Num` 接口取代。 允许使用 `Double`、`BigDecimal` 和自定义数据类型进行计算。
    - `TimeSeries` 和 `BaseTimeSeries` 的重大变化。 `TimeSeries` 中的多个新 `addBar(..)` 函数允许将数据直接添加到系列中

### Fixed
- **TradingBotOnMovingTimeSeries**: fixed calculations and ArithmeticException Overflow
- **Fixed wrong indexing in**: `Indicator.toDouble()`.
- **PrecisionNum.sqrt()**: using DecimalFormat.parse().
- **RandomWalk[High|Low]Indicator**: fixed formula (max/min of formula with n iterating from 2 to barCount)
### 固定的
- **TradingBotOnMovingTimeSeries**：固定计算和 ArithmeticException 溢出
- **修复了**中的错误索引：`Indicator.toDouble()`。
- **PrecisionNum.sqrt()**：使用 DecimalFormat.parse()。
- **RandomWalk[High|Low]Indicator**：固定公式（公式的最大值/最小值，n 从 2 迭代到 barCount）

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
### 改变了
- **所有指标**：`Decimal` 替换为`Num`。
- **所有标准**：修改为使用“Num”的计算。
- **AbstractIndicator**：新的 `AbstractIndicator#numOf(Number n)` 函数作为删除的 `Decimal.valueOf(double|int|..)` 的对应项
- **时间序列 |条形图**：将条形数据添加到 `TimeSeries` 的首选方式是通过新的 `TimeSeries#addBar(time,open,high,..)` 函数直接添加到序列中。它确保使用该系列的正确“Num”实现
- **XlsTestsUtils**：现在处理数据行之间有一天或多天的 xls（每天、每周、每月等）。还要处理 xls #DIV/0！计算单元格（导入为 NaN.NaN）
- **CachedIndicator**：不缓存最后一根柱以支持实时指标
- **时间序列 | Bar **：添加了新的 `#addPrice(price)` 函数，将价格添加到（最后一个）bar。
- 参数 **timeFrame** 重命名为 **barCount**。
- **各种规则**：添加了提供“数字”参数的构造函数
- **AroonUpIndicator**：从构造函数中删除了多余的 TimeSeries 调用
- **AroonDownIndicator**：从构造函数中删除了多余的 TimeSeries 调用
- **BaseTimeSeries**：将 setDefaultFunction() 添加到 SeriesBuilder，用于为该 SeriesBuilder 构建的所有新 TimeSeries 设置默认 Num 类型函数，更新了 BuildTimeSeries 示例
- **<various>CriterionTest**：从显式构造函数调用更改为“AbstractCriterionTest.getCriterion()”调用。
- **ChopIndicator**：透明修复
- **StochasticRSIIndicator**：注释和参数名称更改以减少混淆
- **ConvergenceDivergenceIndicator**：删除未使用的方法
- **ChopIndicatorTest**：拼写，TODO：添加更好的测试
- **各种指标**：删除双重数学运算，将 `Math.sqrt(double)` 更改为 `Num.sqrt()`，其他小的改进
- **RandomWalk[High|Low]Indicator**：重命名为`RWI[High|Low]Indicator`

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
###添加
- **BaseTimeSeries.SeriesBuilder**：简化 BaseTimeSeries 的创建。
- **Num**：已删除的“十进制”类的提取接口
- **DoubleNum**：`Num` 实现以支持基于 `double` 原语的计算
- **BigDecimalNum**：`BaseTimeSeries` 的默认 `Num` 实现
- **DifferencePercentageIndicator**：获取与上一个值的百分比差异的新指标
- **PrecisionNum**: `Num` 实现以支持任意精度
- **TestUtils**：删除了置换参数的便捷方法，修复了所有单元测试
- **TestUtils**：添加参数化抽象测试类以允许使用 `DoubleNum` 和 `BigDecimalNum` 进行两次测试
- **ChopIndicator** 新的市场波动（低波动性）通用指标，以及相关的“ChopIndicatorTest”JUnit 测试和“CandlestickChartWithChopIndicator”示例
- **BollingerBandWidthIndicator**：添加了缺少的构造函数文档。
- **BollingerBandsLowerIndicator**：添加了缺少的构造函数文档。
- **BollingerBandsMiddleIndicator**：添加了缺少的构造函数文档。
- **TrailingStopLossRule**：达到追踪止损时满足的新规则
- **Num**：添加了 Num sqrt(int) 和 Num sqrt()
- **pom.xml**：添加了对生成 ta4j-core OSGi 工件的支持。
- 
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
### 已删除/已弃用
- **十进制**：_removed_。 替换为 `Num` 接口
- **TimeSeries#addBar(Bar bar)**: _deprecated_。 使用`TimeSeries#addBar(Time, open, high, low, ...)`
- **BaseTimeSeries**：_Constructor_`BaseTimeSeries（TimeSeries defaultSeries，int seriesBeginIndex，int seriesEndIndex）`_removed_。 改用`TimeSeries.getSubseries(int i, int i)`
- **FisherIndicator**：删除了注释的构造函数。
- **TestUtils**：删除了置换参数的便捷方法，修复了所有单元测试
- **BaseTimeSeries**：_Constructor_`BaseTimeSeries（TimeSeries defaultSeries，int seriesBeginIndex，int seriesEndIndex）`_removed_。 改用`TimeSeries.getSubseries(int i, int i)`
- **BigDecimalNum**：_removed_。 替换为 `PrecisionNum`
- **AbstractCriterionTest**：删除了构造函数“AbstractCriterionTest(Function<Number, Num)”。 使用 `AbstractCriterionTest(CriterionFactory, Function<Number, Num>)`。
- **<various>Indicator**：删除了多余的`private TimeSeries`

## 0.11 (released January 25, 2018)
## 0.11（2018 年 1 月 25 日发布）

- **BREAKING**: Tick has been renamed to **Bar**
- **BREAKING**：Tick 已重命名为 **Bar**
- 
### Fixed
- **ATRIndicator**: fixed calculations
- **PlusDI, MinusDI, ADX**: fixed calculations
- **LinearTransactionCostCriterion**: fixed calculations, added xls file and unit tests
- **FisherIndicator**: fixed calculations
- **ConvergenceDivergenceIndicator**: fixed NPE of optional "minStrenght"-property
### 固定的
- **ATRIndicator**：固定计算
- **PlusDI、MinusDI、ADX**：固定计算
- **LinearTransactionCostCriterion**：固定计算，添加 xls 文件和单元测试
- **FisherIndicator**：固定计算
- **ConvergenceDivergenceIndicator**：固定 NPE 的可选“minStrenght”-property
- 
### Changed
- **TotalProfitCriterion**: If not `NaN` the criterion uses the price of the `Order` and not just the close price of underlying `TimeSeries`
- **Order**: Now constructors and static `sell/buyAt(..)` functions need a price and amount parameter to satisfy correct be behaviour of criterions (entry/exit prices can differ from corresponding close prices in `Order`)
- **JustOnceRule**: now it is possible to add another rule so that this rule is satisfied if the inner rule is satisfied for the first time
- **MeanDeviationIndicator**: moved to statistics package
- **Decimal**: use `BigDecimal::valueof` instead of instantiating a new BigDecimal for double, int and long
    - now `Decimal` extends `Number`
- **Strategy:** can now have a optional parameter "name".
- **Tick:** `Tick` has been renamed to **`Bar`** for a more appropriate description of the price movement over a set period of time.
- **MMAIndicator**: restructured and moved from `helpers` to `indicators` package
- **AverageTrueRangeIndicator**: renamed to **ATRIndicator**
- **AverageDirectionalMovementDownIndicator**: renamed to **ADXIndicator**
- **ADXIndicator**: added new two argument constructor
- **DirectionalMovementPlusIndicator** and **DirectionalMovementPlusIndicator**: renamed to **PlusDIIndicator** and **MinusDIIndicator**
- **XlsTestsUtils**: rewritten to provide getSeries(), getIndicator(), getFinalCriterionValue(), and getTradingRecord() in support of XLSCriterionTest and XLSIndicatorTest.
- **IndicatorFactory**: made generic and renamed createIndicator() to getIndicator()
- **RSIIndicatorTest**: example showing usage of new generic unit testing of indicators
- **LinearTransactionCostCriterionTest**: example showing usage of new generic unit testing of criteria
- ### 改变了
- **TotalProfitCriterion**：如果不是 `NaN`，则标准使用 `Order` 的价格，而不仅仅是底层 `TimeSeries` 的收盘价
- **Order**：现在构造函数和静态 `sell/buyAt(..)` 函数需要价格和数量参数来满足标准的正确行为（进入/退出价格可能与 `Order` 中的相应收盘价不同）
- **JustOnceRule**：现在可以添加另一个规则，以便在第一次满足内部规则时满足此规则
- **MeanDeviationIndicator**：移至统计包
- **Decimal**：使用 `BigDecimal::valueof` 而不是为 double、int 和 long 实例化新的 BigDecimal
    - 现在 `Decimal` 扩展了 `Number`
- **策略：**现在可以有一个可选参数“名称”。
- **Tick：** `Tick` 已重命名为 **`Bar`**，以便更恰当地描述一段时间内的价格变动。
- **MMAIndicator**：重组并从 `helpers` 转移到 `indicators` 包
- **AverageTrueRangeIndicator**：重命名为**ATRIndicator**
- **AverageDirectionalMovementDownIndicator**：重命名为**ADXIndicator**
- **ADXIndicator**：添加了新的两个参数构造函数
- **DirectionalMovementPlusIndicator** 和 **DirectionalMovementPlusIndicator**：重命名为 **PlusDIIndicator** 和 **MinusDIIndicator**
- **XlsTestsUtils**：重写以提供 getSeries()、getIndicator()、getFinalCriterionValue() 和 getTradingRecord() 以支持 XLSCriterionTest 和 XLSIndicatorTest。
- **IndicatorFactory**：通用并将 createIndicator() 重命名为 getIndicator()
- **RSIIndicatorTest**：显示使用新的指标通用单元测试的示例
- **LinearTransactionCostCriterionTest**：显示使用标准的新通用单元测试的示例

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
##添加
- **ConvergenceDivergenceIndicator**：正/负收敛和发散的新指标。
- **BooleanTransformIndicator**：通过使用逻辑运算将任何十进制指标转换为布尔指标的新指标。
- **DecimalTransformIndicator**：新指标通过使用常见的数学运算转换任何指标。
- **十进制**：添加了函数`Decimal valueOf(BigDecimal)`和`BigDecimal getDelegate()`
- **AbstractEMAIndicator**：基于 ema 的指标的新抽象指标，如 MMAIndicator
- **PearsonCorrelationIndicator**：具有皮尔逊相关性的新统计指标
- **TimeSeries**：新方法 `getSubSeries(int, int)` 创建 TimeSeries 的子系列，专门存储在 `startIndex` 和 `endIndex` 参数之间的柱线
- **IIIIndicator**：盘中强度指数
- **CriterionFactory**：支持 CriterionTest 的新功能接口
- **IndicatorTest**：用于存储指标工厂的新类，允许在构造函数调用中设置一次工厂后进行通用调用，如 getIndicator(D data, P... params)。促进跨单元测试的标准化。
- **CriterionTest**：用于存储标准工厂的新类，允许在构造函数调用中设置一次工厂后进行通用调用，如 getCriterion(P... params)。促进跨单元测试的标准化。
- **ExternalIndicatorTest**：用于从外部来源获取指标和时间序列的新接口
- **ExternalCriterionTest**：用于从外部来源获取标准、交易记录和时间序列的新界面
- **XLSIndicatorTest**：为 XLS 文件实现 ExternalIndicatorTest 的新类，用于 XLS 单元测试
- **XLSCriterionTest**：为 XLS 文件实现 ExternalCriterionTest 的新类，用于 XLS 单元测试
## Removed
- **TraillingStopLossIndicator**: no need for this as indicator. No further calculations possible after price falls below stop loss. Use `StopLossRule` or `DifferenceIndicator`
## 已移除
- **TraillingStopLossIndicator**：不需要将此作为指标。 价格跌破止损后无法进行进一步计算。 使用 `StopLossRule` 或 `DifferenceIndicator`
- 
## Deprecated
- **BaseTimeSeries**: Constructor: `BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex)` use `getSubSeries(int, int)`
- **Decimal**: Method `toDouble()` use `doubleValue()`
## 已弃用
- **BaseTimeSeries**：构造函数：`BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex)` 使用`getSubSeries(int, int)`
- **十进制**：方法 `toDouble()` 使用 `doubleValue()`

## 0.10 (released October 30, 2017)
## 0.10（2017 年 10 月 30 日发布）

### VERY Important note!!!!
### 非常重要的注意事项！！！！

with the release 0.10 we have changed the previous java package definition to org.ta4j or to be more specific to org.ta4j.core (the new organisation). You have to reorganize all your refernces to the new packages!
In eclipse you can do this easily by selecting your sources and run "Organize imports"
_Changed ownership of the ta4j repository_: from mdeverdelhan/ta4j (stopped the maintenance) to ta4j/ta4j (new organization)
在 0.10 版本中，我们将以前的 java 包定义更改为 org.ta4j 或更具体为 org.ta4j.core（新组织）。 您必须重新组织对新软件包的所有引用！
在 Eclipse 中，您可以通过选择源并运行“组织导入”轻松完成此操作
_更改了 ta4j 存储库的所有权_：从 mdeverdelhan/ta4j（停止维护）到 ta4j/ta4j（新组织）

### Fixed
- **ParabolicSarIndicator**: wrong calculation fixed
- **KAMAIndicator**: stack overflow bug fixed
- **AroonUpIndicator and AroonDownIndicator**: wrong calculations fixed and can handle NaN values now
### 固定的
- **ParabolicSarIndicator**：错误的计算已修复
- **KAMAIndicator**：堆栈溢出错误已修复
- **AroonUpIndicator 和 AroonDownIndicator**：错误计算已修复，现在可以处理 NaN 值

### Changed
- **BREAKING**: **new package structure**: change eu.verdelhan.ta4j to org.ta4j.ta4j-core
- **new package adx**: new location of AverageDirectionalMovementIndicator and DMI+/DMI-
- **Ownership of the ta4j repository**: from mdeverdelhan/ta4j (stopped the maintenance) to ta4j/ta4j (new organization)
- **ParabolicSarIndicator**: old constructor removed (there was no need for time frame parameter after big fix). Three new constructors for default and custom parameters.
- **HighestValueIndicator and LowestValueIndicator:** ignore also NaN values if they are at the current index
### 改变了
- **BREAKING**：**新包结构**：将 eu.verdelhan.ta4j 更改为 org.ta4j.ta4j-core
- **新包 adx**：AverageDirectionalMovementIndicator 和 DMI+/DMI- 的新位置
- **ta4j 存储库的所有权**：从 mdeverdelhan/ta4j（停止维护）到 ta4j/ta4j（新组织）
- **ParabolicSarIndicator**：旧的构造函数被移除（大修复后不需要时间框架参数）。 默认和自定义参数的三个新构造函数。
- **HighestValueIndicator 和 LowestValueIndicator：** 如果它们位于当前索引处，则也忽略 NaN 值


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
##添加
- **AroonOscillatorIndicator**：基于 AroonUp/DownIndicator 的新指标
- **AroonUpIndicator** 和 **AroonDownIndicator**：带有参数的新构造函数，用于计算最低价格和最高价格的自定义指标
- **ROCVIndicator**：交易量变化率
- **DirectionalMovementPlusIndicator**：定向运动系统的新指标 (DMI+)
- **DirectionalMovementDownIndicator**：定向运动系统的新指标 (DMI-)
- **ChaikinOscillatorIndicator**：新指标。
- **InSlopeRule**：如果两个指标的斜率在边界内，则满足新规则
- **IsEqualRule**：如果两个指标相等则满足的新规则
- **AroonUpIndicator** 和 **AroonDownIndicator**：带有参数的新构造函数，用于计算最低价格和最高价格的自定义指标
- **枢轴点指标包**：带有用于计算标准、斐波那契和德马克枢轴点和反转指标的新软件包
    - **PivotPointIndicator**：用于计算标准枢轴点的新指标
        - **StandardReversalIndicator**：用于计算标准反转的新指标（R3、R2、R1、S1、S2、S3）
        - **FibonacciReversalIndicator**：用于计算斐波那契反转的新指标（R3、R2、R1、S1、S2、S3）
    - **DeMarkPivotPointIndicator**：用于计算 DeMark 枢轴点的新指标
        - **DeMarkReversalIndicator**：用于计算 DeMark 阻力和 DeMark 支撑的新指标
- **IsFallingRule**：如果指标在时间范围内严格下降，则满足新规则。
- **IsRisingRule**：如果指标在时间范围内严格增加，则满足新规则。
- **IsLowestRule**：如果指标在时间范围内最低，则满足新规则。
- **IsHighestRule**：如果指标在时间帧内最高，则满足新规则。

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
## 0.9（2017 年 9 月 7 日发布）
- **BREAKING** 放弃 Java 7 支持
- 使用 `java.time` 而不是 `java.util.Date`
* 为部分 API 基础对象添加接口
* 清理整个 API
* 重新排序的指标
* 添加了 PreviousValueIndicator
* 修复 #162 - 在 Tick 构造函数中添加了金额字段
* 修正 #183 - addTrade 错误计算
* 已修复 #153、#170 - 更新了空头交易的 StopGainRule 和 StopLossRule
* 移除对 Joda-time 的依赖
* 删除了 Java 6 和 Java 7 的兼容性
* 修正 #120 - ZLEMAIndicator StackOverflowError
* 添加随机 RSI 指标
* 添加了平滑 RSI 指标
* 固定示例
* 固定 #81 - 当它可能意味着 1 天时，Tick 使用 24H 周期
* 固定 #80 - TimeSeries 总是遍历所有数据
* 移除时间序列中的 `timePeriod` 字段
* 已修复 #102 - 当 rsi == 100 时，RSIIndicator 返回 NaN
* 增加了周期性增长率指标
* 修正 #105 - Ichimoku 指标的奇怪计算
* 添加了随机游走指数（高/低）指标
* 改进了 Williams %R 指标的性能
* 将模拟指标移至常规范围（在 Fixed*Indicator 中重命名）
* 
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
## 0.8（2016 年 2 月 25 日发布）

* 修复了递归指标上的 StackOverflowErrors（参见 #60 和 #68）
* 修复了 #74 - 关于使用足够刻度计算指标的回测策略的问题
* 添加了 Chande 动量振荡器指标
* 添加了累计损失/收益指标
* 添加范围行动验证指数指标
* 添加了 MVWAP 指标
* 添加了 VWAP 指标
* 添加了枝形吊灯退出指标
  *改进的十进制性能
* 添加费希尔指标
* 添加了 KAMA 指标
* 添加了去趋势价格振荡器
* 添加 Ichimoku 云指标
* 新增统计指标：简单线性回归、相关系数、方差、协方差、标准误
* 移动标准差
* 添加了布林带宽度和 %B 指标
* 添加了 Keltner 通道指标
* 添加了溃疡指数和质量指数指标
* 添加了追踪止损指标
* 添加了 Coppock 曲线指标
* 添加总和指标
* 添加蜡烛指标：实体、上/下影线、十字星、3 黑乌鸦、3 白士兵、看涨/看跌孕线、看涨/看跌吞没
* 添加绝对指标
* 添加了赫尔移动平均线指标
* 更新布林带（可变乘数，见 #53）
* 修正 #39 - TimeSeries.run() 的可能更新
* 添加了 Chaikin 资金流向指标
  *改进的音量指示器
* 添加关闭位置值指示符
* 增加了正成交量指数和负成交量指数指标
* 添加零滞后 EMA 指标

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
## 0.7（2015 年 5 月 21 日发布）

* 修正 #35 - 修正最大回撤标准
* 改进的文档：用户指南和贡献者指南
* 修正 #37 - 更新 Tick.toString 方法
* 修复 #36 - 在完整的 Tick 构造函数中缺少“Period timePeriod”
* 更新示例
* 改进的分析标准（使用实际进入/退出价格而不是收盘价）
* 在“订单”中添加了价格和金额
* 添加了创建订单的助手
* 将“操作”重命名为“订单”
* 添加了交易时段的记录/历史记录（`TradingRecord`）
* 将交易逻辑从策略转移到规则
* 重构贸易操作
* 添加了差异指示器
* 其他小的 API 更改

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
## 0.6（2015 年 2 月 5 日发布）

* 将 `NaN` 添加到小数
* 将 `TADecimal` 重命名为 `Decimal`
* 修正 #24 - 标准差计算错误
* 添加移动时间序列（& 缓存：#25）
* 重构时间序列和刻度
* 添加进入通过过滤器和退出通过过滤器策略
* 将 `JustBuyOnceStrategy` 和 `CombinedBuyAndSellStrategy` 分别替换为 `JustEnterOnceStrategy` 和 `CombinedEntryAndExitStrategy`
* 添加示例
* 添加操作类型助手
* 通过 SLF4J 添加策略执行跟踪
* 删除了 `.summarize(...)` 方法和 `Decision`（分析）
* 改进了一些指标和策略的性能
* 所有指标的通用缓存（#20）
* 移除 AssertJ 依赖
* 修复 #16 - 在更新的 WalkForward 示例中除以零

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
## 0.5（2014 年 10 月 22 日发布）

* 指标中的 TADecimals (BigDecimals) 切换双打
* IndicatorOverIndicatorStrategy 的语义改进
* 修复 #11 - 将 toString() 用于 4 种策略时出现 UnknownFormatConversionException
* 增加了一个最大值启动策略
* 增加了线性交易成本（分析标准）
* 移除评估器（由 `.chooseBest(...)` 和 `.betterThan(...)` 方法取代）
* 添加了三重 EMA 指标
* 添加了双 EMA 指标
* 移除切片器（由 `.split(...)` 方法取代）
* 移除跑步者（由 `.run(...)` 方法取代）
* 添加了更多测试
* 移除 `ConstrainedTimeSeries`（由 `.subseries(...)` 方法取代）
* 添加/重构示例（包括前进和烛台图）

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
## 0.4（2014 年 5 月 28 日发布）

* 修正 #2 - JDK8 中的测试失败
* 添加指标：平均偏差、商品通道指数、百分比价格振荡器（测试）
* 增加了指标和常数之间的距离
* 添加了相反的策略
  *删除了一些跑步者
* 添加策略在整个系列上运行
* 重构切片器
* 删除 log4j 依赖
* 添加示例

## 0.3 (released March 11, 2014)

  * First public release
  * 100% Pure Java - works on any Java Platform version 6 or later
  * More than 40 technical indicators (Aroon, ATR, moving averages, parabolic SAR, RSI, etc.)
  * A powerful engine for building custom trading strategies
  * Utilities to run and compare strategies
  * Minimal 3rd party dependencies
  * MIT license
## 0.3（2014 年 3 月 11 日发布）

* 首次公开发布
* 100% 纯 Java - 适用于任何 Java 平台版本 6 或更高版本
* 超过 40 种技术指标（Aroon、ATR、移动平均线、抛物线 SAR、RSI 等）
* 用于构建自定义交易策略的强大引擎
* 运行和比较策略的实用程序
* 最小的第 3 方依赖
* 麻省理工学院许可证