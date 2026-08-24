/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.aggregator.BaseBarSeriesAggregator;
import org.ta4j.core.aggregator.DurationBarAggregator;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BacktestRuntimeReport;
import org.ta4j.core.backtest.StrategyWalkForwardExecutionResult;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.Annualization;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedBooleanIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.strategy.named.NamedStrategy;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.walkforward.WalkForwardRunResult;
import org.ta4j.core.walkforward.WalkForwardRuntimeReport;
import ta4jexamples.rules.RsiThresholdRule;
import ta4jexamples.strategies.DayOfWeekStrategy;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link CliSupport}.
 *
 * <p>
 * The suite covers the bounded helper surface directly so validation, file
 * handling, strategy construction, and JSON/report serialization stay stable
 * without depending only on end-to-end CLI invocation tests.
 * </p>
 */
class CliSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void toJsonPrettyPrintsValues() {
        String json = CliSupport.toJson(Map.of("command", "backtest"));

        assertThat(json).contains("\"command\": \"backtest\"");
    }

    @Test
    void loadSeriesLoadsCsvAndAppliesTimeframeAndDateFilters() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        BarSeries fullSeries = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        BarSeries filteredSeries = CliSupport.loadSeries(dataFile.toString(), "P2D", "2013-02-01", "2013-03-15");

        assertThat(fullSeries.getBarCount()).isGreaterThan(filteredSeries.getBarCount());
        assertThat(filteredSeries.getName()).endsWith("-p2d");
        assertThat(filteredSeries.getFirstBar().getEndTime()).isAfterOrEqualTo(Instant.parse("2013-02-01T00:00:00Z"));
        assertThat(filteredSeries.getLastBar().getEndTime())
                .isBeforeOrEqualTo(Instant.parse("2013-03-15T23:59:59.999999999Z"));
    }

    @Test
    void loadSeriesSlicesSourceBarsBeforeResampling() throws Exception {
        Path dataFile = copyResource("Binance-ETH-USD-PT5M-20230313_20230315.json");
        BarSeries source = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Instant fromDate = Instant.parse("2023-03-13T18:07:00Z");

        int startIndex = source.getBeginIndex();
        while (startIndex <= source.getEndIndex() && source.getBar(startIndex).getEndTime().isBefore(fromDate)) {
            startIndex++;
        }
        BarSeries expected = new BaseBarSeriesAggregator(new DurationBarAggregator(Duration.ofMinutes(15), true))
                .aggregate(source.getSubSeries(startIndex, source.getEndIndex() + 1), "expected");
        BarSeries actual = CliSupport.loadSeries(dataFile.toString(), "PT15M", "2023-03-13T18:07:00Z", null);

        List<Bar> expectedBars = expected.getBarData();
        List<Bar> actualBars = actual.getBarData();
        assertThat(actualBars).hasSameSizeAs(expectedBars);
        for (int index = 0; index < actualBars.size(); index++) {
            assertThat(actualBars.get(index).getVolume()).isEqualTo(expectedBars.get(index).getVolume());
        }
    }

    @Test
    void loadSeriesLoadsJsonFiles() throws Exception {
        Path dataFile = copyResource("Binance-ETH-USD-PT5M-20230313_20230315.json");

        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        assertThat(series.getBarCount()).isGreaterThan(0);
        assertThat(series.getName()).isNotBlank();
    }

    @Test
    void loadSeriesRejectsUnsupportedFormatsAndEmptyResults() throws Exception {
        Path unsupportedFile = tempDir.resolve("bars.txt");
        Files.writeString(unsupportedFile, "unsupported");
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        assertThatThrownBy(() -> CliSupport.loadSeries(unsupportedFile.toString(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported data file format for " + unsupportedFile + ". Use .csv or .json.");
        assertThatThrownBy(() -> CliSupport.loadSeries(dataFile.toString(), null, "2015-01-01", "2015-01-31"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The selected date/timeframe filter produced an empty series.");
    }

    @Test
    void requireFiniteBarsRejectsNonFiniteObservableFields() {
        BaseBarSeries nanClose = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        nanClose.barBuilder().closePrice(Double.NaN).add();

        assertThatThrownBy(() -> CliSupport.requireFiniteBars(nanClose, "test.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Non-finite close value in test.csv at bar 0.");

        BaseBarSeries infiniteVolume = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .build();
        infiniteVolume.barBuilder().closePrice(1d).volume(Double.POSITIVE_INFINITY).add();

        assertThatThrownBy(() -> CliSupport.requireFiniteBars(infiniteVolume, "test.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Non-finite volume value in test.csv at bar 0.");
    }

    @Test
    void buildExecutorConfiguresExecutionAndCostModels() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Strategy strategy = sampleSweepStrategy(series);
        Num amount = CliSupport.resolveAmount(series, "1000", null);

        BacktestExecutor nextOpenExecutor = CliSupport.buildExecutor(series, null, null, null);
        BacktestExecutionResult nextOpenResult = nextOpenExecutor.executeWithRuntimeReport(List.of(strategy), amount,
                strategy.getStartingType());
        TradingRecord nextOpenRecord = nextOpenResult.tradingStatements().getFirst().getTradingRecord();

        BacktestExecutor currentCloseExecutor = CliSupport.buildExecutor(series, "current-close", "0.01", "0.02");
        BacktestExecutionResult currentCloseResult = currentCloseExecutor.executeWithRuntimeReport(List.of(strategy),
                amount, strategy.getStartingType());
        TradingRecord currentCloseRecord = currentCloseResult.tradingStatements().getFirst().getTradingRecord();

        assertThat(nextOpenRecord.getTransactionCostModel()).isInstanceOf(ZeroCostModel.class);
        assertThat(nextOpenRecord.getHoldingCostModel()).isInstanceOf(ZeroCostModel.class);
        assertThat(currentCloseRecord.getTransactionCostModel()).isInstanceOf(LinearTransactionCostModel.class);
        assertThat(currentCloseRecord.getHoldingCostModel()).isInstanceOf(LinearBorrowingCostModel.class);
        assertThat(currentCloseRecord.getPositionCount()).isGreaterThan(0);
        assertThat(nextOpenRecord.getPositionCount()).isEqualTo(currentCloseRecord.getPositionCount());
        assertThat(currentCloseRecord.getPositions().getFirst().getEntry().getIndex())
                .isLessThan(nextOpenRecord.getPositions().getFirst().getEntry().getIndex());
    }

    @Test
    void buildExecutorRejectsUnsupportedExecutionModels() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        assertThatThrownBy(() -> CliSupport.buildExecutor(series, "intrabar", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported execution model. Supported values are next-open and current-close.");
    }

    @Test
    void resolveAmountUsesStakeCapitalAndDefaultOne() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        assertThat(CliSupport.resolveAmount(series, null, null)).hasToString("1");
        assertThat(CliSupport.resolveAmount(series, "500", null)).hasToString("500");
        assertThat(CliSupport.resolveAmount(series, "500", "125")).hasToString("125");
        assertThatThrownBy(() -> CliSupport.resolveAmount(series, "100", "101"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--stake-amount must not exceed --capital.");
    }

    @Test
    void resolveAmountRejectsInvalidSingleCapitalAndStakeValues() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        assertThatThrownBy(() -> CliSupport.resolveAmount(series, "0", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--capital must be greater than zero.");
        assertThatThrownBy(() -> CliSupport.resolveAmount(series, null, "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--stake-amount must be greater than zero.");
        assertThatThrownBy(() -> CliSupport.resolveAmount(series, "abc", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid numeric value for --capital: abc.");
    }

    @Test
    void resolveAmountComparesExactNumValuesBeyondDoublePrecision() {
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance()).build();
        series.barBuilder().closePrice(1d).add();

        // 2^53 and 2^53 + 1 collapse to the same double; only an exact Num
        // comparison detects that the stake exceeds the capital.
        assertThatThrownBy(() -> CliSupport.resolveAmount(series, "9007199254740992", "9007199254740993"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--stake-amount must not exceed --capital.");
        assertThat(CliSupport.resolveAmount(series, "9007199254740993", "9007199254740992"))
                .hasToString("9007199254740992");
    }

    @Test
    void resolveCriteriaRejectsUnboundedMonteCarloIterations() {
        assertThatThrownBy(() -> CliSupport.resolveCriteria(List.of(),
                List.of("{\"type\":\"MonteCarloMaximumDrawdownCriterion\",\"iterations\":100001}"), List.of(),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'iterations' must be between 1 and 100000");
    }

    @Test
    void resolveCriteriaRejectsUnboundedMonteCarloPathBlocks() {
        assertThatThrownBy(() -> CliSupport.resolveCriteria(List.of(), List.of(
                "{\"type\":\"MonteCarloMaximumDrawdownCriterion\",\"iterations\":10000,\"pathBlocks\":1000001}"),
                List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'pathBlocks' must be between 1 and 1000000");
    }

    @Test
    void resolvePositionSizingSupportsFixedBalanceAndKellyModes() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        CliSupport.PositionSizingSpec fixed = CliSupport.resolvePositionSizing(series, null, "1000", "100", null, null,
                null);
        CliSupport.PositionSizingSpec balance = CliSupport.resolvePositionSizing(series, "balance", "1000", null, null,
                null, null);
        CliSupport.PositionSizingSpec kelly = CliSupport.resolvePositionSizing(series, "kelly", "1000", null, "0.6",
                "2", "0.5");

        assertThat(fixed.mode()).isEqualTo("fixed");
        assertThat(balance.mode()).isEqualTo("balance");
        assertThat(kelly.mode()).isEqualTo("kelly");
        assertThat(kelly.kellyCoefficient()).isEqualTo("0.5");
        assertThatThrownBy(() -> CliSupport.resolvePositionSizing(series, "balance", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--capital is required with --position-sizing balance.");
        assertThatThrownBy(() -> CliSupport.resolvePositionSizing(series, "fixed", null, null, "0.6", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--win-probability is only valid with --position-sizing kelly.");
    }

    @Test
    void resolveCriteriaSupportsNamedExpressionsAndFullyQualifiedClasses() {
        List<CliSupport.CriterionSpec> defaults = CliSupport.resolveCriteria(List.of(),
                CliSupport.DEFAULT_BACKTEST_CRITERIA);
        List<CliSupport.CriterionSpec> explicit = CliSupport.resolveCriteria(
                List.of(NetProfitCriterion.class.getName() + "," + SharpeRatioCriterion.class.getName(),
                        NetProfitCriterion.class.getName()),
                CliSupport.DEFAULT_SWEEP_CRITERIA);
        List<CliSupport.CriterionSpec> named = CliSupport.resolveCriteria(List.of("NetProfit,ReturnOverMaxDrawdown"),
                CliSupport.DEFAULT_SWEEP_CRITERIA);

        assertThat(defaults).extracting(CliSupport.CriterionSpec::className)
                .containsExactlyElementsOf(CliSupport.DEFAULT_BACKTEST_CRITERIA);
        assertThat(explicit).extracting(CliSupport.CriterionSpec::className)
                .containsExactly(NetProfitCriterion.class.getName(), SharpeRatioCriterion.class.getName());
        assertThat(named).extracting(CliSupport.CriterionSpec::name)
                .containsExactly("NetProfit", "ReturnOverMaxDrawdown");
        assertThatThrownBy(() -> CliSupport.resolveCriteria(List.of("net-profit"), CliSupport.DEFAULT_SWEEP_CRITERIA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid analysis criterion shorthand: net-profit.");
    }

    @Test
    void resolveCriteriaAcceptsLosslessJsonAndJsonArrayFiles() throws Exception {
        SharpeRatioCriterion tradeSampled = new SharpeRatioCriterion(0.03d, SamplingFrequency.TRADE,
                Annualization.ANNUALIZED, ZoneOffset.UTC);
        Path criteriaFile = tempDir.resolve("criteria.json");
        Files.writeString(criteriaFile, "[" + tradeSampled.toJson() + "," + new NetProfitCriterion().toJson() + "]");

        List<CliSupport.CriterionSpec> criteria = CliSupport.resolveCriteria(List.of(), List.of(tradeSampled.toJson()),
                List.of(criteriaFile.toString()), CliSupport.DEFAULT_SWEEP_CRITERIA);

        assertThat(criteria).hasSize(2);
        assertThat(criteria.getFirst().criterion()).isInstanceOf(SharpeRatioCriterion.class);
        assertThat(criteria.getFirst().json()).contains("TRADE", "0.03");
        assertThat(criteria).extracting(CliSupport.CriterionSpec::className)
                .contains(NetProfitCriterion.class.getName());
    }

    @Test
    void rejectedCriterionInputDoesNotRunStaticInitializer() {
        InitializerProbe.initialized = false;

        assertThatThrownBy(() -> CliSupport.resolveCriteria(List.of(RejectedCriterionProbe.class.getName()),
                CliSupport.DEFAULT_SWEEP_CRITERIA)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid analysis criterion input: " + RejectedCriterionProbe.class.getName() + ".");
        assertThat(InitializerProbe.initialized).isFalse();
    }

    @Test
    void buildStrategySupportsNamedLabelsAndJsonDefinitions() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Strategy labelStrategy = CliSupport.buildStrategy("DayOfWeekStrategy_MONDAY_FRIDAY", null, 12, series);
        Path strategyJsonFile = tempDir.resolve("strategy.json");
        Files.writeString(strategyJsonFile, labelStrategy.toJson());

        Strategy jsonStrategy = CliSupport.buildStrategy("ignored", strategyJsonFile.toString(), 7, series);
        Strategy expressionStrategy = CliSupport.buildStrategy("SMA(7,21)", null, null, series);
        Path versionTwoFile = tempDir.resolve("strategy-v2.json");
        Files.writeString(versionTwoFile, """
                {
                  "version": 2,
                  "name": "v2-sma",
                  "entryRule": {"type": "CrossedUpIndicatorRule", "args": ["SMA(7)", "SMA(21)"]},
                  "exitRule": {"type": "CrossedDownIndicatorRule", "args": ["SMA(7)", "SMA(21)"]}
                }
                """);
        Strategy versionTwoStrategy = CliSupport.buildStrategy(null, versionTwoFile.toString(), null, series);

        assertThat(labelStrategy.getName()).isEqualTo("DayOfWeekStrategy_MONDAY_FRIDAY");
        assertThat(labelStrategy.getUnstableBars()).isEqualTo(12);
        assertThat(jsonStrategy.getName()).isEqualTo(labelStrategy.getName());
        assertThat(jsonStrategy.getUnstableBars()).isEqualTo(7);
        assertThat(expressionStrategy.getName()).isEqualTo("SMA(7,21)");
        assertThat(versionTwoStrategy.getName()).isEqualTo("v2-sma");
    }

    @Test
    void buildStrategySupportsNamedStrategyLabels() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        Strategy namedStrategy = CliSupport.buildStrategy("DayOfWeekStrategy_MONDAY_FRIDAY", null, null, series);

        assertThat(namedStrategy.getName()).isEqualTo("DayOfWeekStrategy_MONDAY_FRIDAY");
        assertThat(namedStrategy.getUnstableBars()).isZero();
    }

    @Test
    void buildStrategyInitializesNamedStrategyRegistryBeforeJsonDeserialization() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Path strategyJsonFile = tempDir.resolve("named-strategy.json");
        Files.writeString(strategyJsonFile,
                "{\"type\":\"NamedStrategy\",\"label\":\"DayOfWeekStrategy_MONDAY_FRIDAY\"}");

        NamedStrategy.unregisterImplementation(DayOfWeekStrategy.class);
        resetNamedStrategyRegistryForTests();

        Strategy strategy = CliSupport.buildStrategy(null, strategyJsonFile.toString(), null, series);

        assertThat(strategy.getName()).isEqualTo("DayOfWeekStrategy_MONDAY_FRIDAY");
        NamedStrategy.initializeRegistry("ta4jexamples.strategies");
    }

    @Test
    void resolveStrategiesSupportsMixedInputsAndCollectsInvalidEntries() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Strategy serializedStrategy = sampleSweepStrategy(series);
        Path strategyJson = tempDir.resolve("strategy.json");
        Path strategiesJsonFile = tempDir.resolve("strategies.json");
        Files.writeString(strategyJson, serializedStrategy.toJson());
        Files.writeString(strategiesJsonFile, "[" + serializedStrategy.toJson() + ",\"invalid\"]");

        CliSupport.ResolvedStrategies resolved = CliSupport.resolveStrategies("DayOfWeekStrategy_MONDAY_FRIDAY",
                strategyJson.toString(), List.of("HourOfDayStrategy_9_17,SMA(7,21),MissingStrategy_VALUE"),
                strategiesJsonFile.toString(), 7, series);

        assertThat(resolved.strategies()).hasSize(5);
        assertThat(resolved.strategies()).extracting(Strategy::getUnstableBars).containsOnly(7);
        assertThat(resolved.invalidStrategies()).contains(
                "--strategies MissingStrategy_VALUE: Invalid strategy shorthand or label 'MissingStrategy_VALUE'. Use a compact expression such as SMA(7,21) or a NamedStrategy label such as DayOfWeekStrategy_MONDAY_FRIDAY.",
                "--strategies-json-file " + strategiesJsonFile
                        + "[1]: Each array element must be a serialized strategy object.");
    }

    @Test
    void resolveStrategiesFailsFastWhenNoValidStrategiesRemain() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        assertThatThrownBy(
                () -> CliSupport.resolveStrategies(null, null, List.of("MissingStrategy_VALUE"), null, null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No valid strategies to run.")
                .hasMessageContaining(
                        "--strategies MissingStrategy_VALUE: Invalid strategy shorthand or label 'MissingStrategy_VALUE'.")
                .hasMessageContaining("Use --strategy, --strategies, --strategy-json-file, or --strategies-json-file.");
    }

    @Test
    void resolveStrategiesPropagatesIoErrorsFromStrategiesJsonFiles() {
        BarSeries series = syntheticSeries(10);
        Path missingJsonFile = tempDir.resolve("missing-strategies.json");

        assertThatThrownBy(
                () -> CliSupport.resolveStrategies(null, null, List.of(), missingJsonFile.toString(), null, series))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("Unable to read strategies JSON from " + missingJsonFile + ".");
    }

    @Test
    void requireBoundedStrategyBatchRejectsOverBudgetBacktests() {
        BarSeries series = syntheticSeries(10_000);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        List<Strategy> batch = new ArrayList<>(10_001);
        for (int i = 0; i < 10_001; i++) {
            batch.add(strategy);
        }

        assertThatThrownBy(() -> CliSupport.requireBoundedStrategyBatch(batch, series.getBarCount()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Strategy batch of 10001 strategies over 10000 bars requires "
                        + "100010000 bar-strategy evaluations; at most 100000000 are supported.");
    }

    @Test
    void requireBoundedStrategyBatchAcceptsAtMostBudgetBatches() {
        BarSeries series = syntheticSeries(10_000);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        List<Strategy> batch = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            batch.add(strategy);
        }

        CliSupport.requireBoundedStrategyBatch(batch, series.getBarCount());
    }

    @Test
    void requireBoundedWalkForwardBatchCountsFoldAndHoldoutTestBars() {
        BarSeries series = syntheticSeries(10_100);
        WalkForwardConfig config = CliSupport.buildWalkForwardConfig(series, "100", "1", "1", "0", "0", "9999", null,
                null, null);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        List<Strategy> batch = new ArrayList<>(4_976);
        for (int i = 0; i < 4_976; i++) {
            batch.add(strategy);
        }

        CliSupport.requireBoundedWalkForwardBatch(List.of(strategy), series, config);
        assertThatThrownBy(() -> CliSupport.requireBoundedWalkForwardBatch(batch, series, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Strategy batch of 4976 strategies over 20100 bars requires "
                        + "100017600 bar-strategy evaluations; at most 100000000 are supported.");
    }

    @Test
    void requireBoundedWalkForwardBatchBoundsActualSplitWork() {
        BarSeries series = syntheticSeries(10_000);
        WalkForwardConfig config = CliSupport.buildWalkForwardConfig(series, "100", "100", "100", "0", "0", "0", null,
                null, null);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        List<Strategy> within = new ArrayList<>(5_025);
        for (int i = 0; i < 5_025; i++) {
            within.add(strategy);
        }
        List<Strategy> over = new ArrayList<>(5_026);
        for (int i = 0; i < 5_026; i++) {
            over.add(strategy);
        }

        CliSupport.requireBoundedWalkForwardBatch(within, series, config);
        assertThatThrownBy(() -> CliSupport.requireBoundedWalkForwardBatch(over, series, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Strategy batch of 5026 strategies over 19900 bars requires "
                        + "100017400 bar-strategy evaluations; at most 100000000 are supported.");
    }

    private static BarSeries syntheticSeries(int barCount) {
        List<Double> data = new ArrayList<>(barCount);
        for (int i = 0; i < barCount; i++) {
            data.add(1d);
        }
        return new MockBarSeriesBuilder().withData(data).build();
    }

    private static void resetNamedStrategyRegistryForTests() {
        try {
            Method reset = NamedStrategy.class.getDeclaredMethod("resetRegistryStateForTests");
            reset.setAccessible(true);
            reset.invoke(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("NamedStrategy test reset seam unavailable", ex);
        }
    }

    @Test
    void buildStrategyRejectsUnknownLabels() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        assertThatThrownBy(() -> CliSupport.buildStrategy("unknown", null, null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid strategy shorthand or label 'unknown'. Use a compact expression such as SMA(7,21) "
                        + "or a NamedStrategy label such as DayOfWeekStrategy_MONDAY_FRIDAY.");
    }

    @Test
    void buildSweepStrategiesBuildsCartesianProductsAndValidatesInputs() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        List<Strategy> strategies = CliSupport.buildSweepStrategies(List.of("slow=40"), List.of("fast=3,5"), 9, series);

        assertThat(strategies).hasSize(2);
        assertThat(strategies).extracting(Strategy::getName)
                .containsExactly("sma-crossover-fast-3-slow-40", "sma-crossover-fast-5-slow-40");
        assertThat(strategies).extracting(Strategy::getUnstableBars).containsOnly(9);
        assertThatThrownBy(() -> CliSupport.buildSweepStrategies(List.of("slow=40"), List.of("fast=3,5", "slow=20,30"),
                null, series)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sweep parameter 'slow' must not appear in both --param and --param-grid");
        assertThatThrownBy(() -> CliSupport.buildSweepStrategies(List.of("slow"), List.of("fast=3,5"), null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid --param value 'slow'. Use key=value.");
        assertThatThrownBy(() -> CliSupport.buildSweepStrategies(List.of(), List.of("fast"), null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid --param-grid value 'fast'. Use key=v1,v2,...");
    }

    @Test
    void resolveIndicatorSupportsInlineAndFileInputs() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        String indicatorJson = new EMAIndicator(new ClosePriceIndicator(series), 5).toJson();
        Path indicatorJsonFile = tempDir.resolve("indicator.json");
        Files.writeString(indicatorJsonFile, indicatorJson);

        CliSupport.ResolvedIndicator inlineIndicator = CliSupport.resolveIndicator(indicatorJson, null, series);
        CliSupport.ResolvedIndicator fileIndicator = CliSupport.resolveIndicator(null, indicatorJsonFile.toString(),
                series);
        CliSupport.ResolvedIndicator expressionIndicator = CliSupport.resolveIndicator("EMA(5)", null, series);

        assertThat(inlineIndicator.typeName()).isEqualTo(EMAIndicator.class.getName());
        assertThat(inlineIndicator.json()).isEqualTo(indicatorJson);
        assertThat(fileIndicator.typeName()).isEqualTo(EMAIndicator.class.getName());
        assertThat(expressionIndicator.typeName()).isEqualTo(EMAIndicator.class.getName());
    }

    @Test
    void buildIndicatorTestStrategySupportsDefaultAndThresholdModes() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        String defaultIndicatorJson = new EMAIndicator(new ClosePriceIndicator(series), 5).toJson();
        String thresholdIndicatorJson = new RSIIndicator(new ClosePriceIndicator(series), 14).toJson();
        Indicator<Num> defaultIndicator = CliSupport.resolveIndicator(defaultIndicatorJson, null, series).indicator();
        Indicator<Num> thresholdIndicator = CliSupport.resolveIndicator(thresholdIndicatorJson, null, series)
                .indicator();

        Strategy defaultStrategy = CliSupport.buildIndicatorTestStrategy(defaultIndicator, null, null, null, null, null,
                series);
        Strategy thresholdStrategy = CliSupport.buildIndicatorTestStrategy(thresholdIndicator, 20, "30", null, null,
                "70", series);

        assertThat(defaultStrategy.getName()).isEqualTo("EMAIndicator-indicator-test");
        assertThat(defaultStrategy.getUnstableBars()).isEqualTo(5);
        assertThat(thresholdStrategy.getName()).isEqualTo("RSIIndicator-indicator-test");
        assertThat(thresholdStrategy.getUnstableBars()).isEqualTo(20);
    }

    @Test
    void buildIndicatorTestStrategyUsesResolvedIndicatorWithoutReReadingInput() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Path indicatorJsonFile = Files.createTempFile("resolved-indicator", ".json");
        try {
            String indicatorJson = new EMAIndicator(new ClosePriceIndicator(series), 5).toJson();
            Files.writeString(indicatorJsonFile, indicatorJson);

            CliSupport.ResolvedIndicator resolvedIndicator = CliSupport.resolveIndicator(null,
                    indicatorJsonFile.toString(), series);
            Files.writeString(indicatorJsonFile, "not-json-anymore");

            Strategy strategy = CliSupport.buildIndicatorTestStrategy(resolvedIndicator.indicator(), null, null, null,
                    null, null, series);
            assertThat(strategy.getName()).isEqualTo("EMAIndicator-indicator-test");
        } finally {
            Files.deleteIfExists(indicatorJsonFile);
        }
    }

    @Test
    void buildIndicatorTestStrategyRejectsInvalidIndicatorAndThresholdInputs() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        String thresholdIndicatorJson = new RSIIndicator(new ClosePriceIndicator(series), 14).toJson();
        Boolean[] booleanValues = new Boolean[series.getBarCount()];
        java.util.Arrays.fill(booleanValues, Boolean.TRUE);
        String booleanIndicatorJson = new FixedBooleanIndicator(series, booleanValues).toJson();

        assertThatThrownBy(() -> CliSupport.resolveIndicator("not-json", null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid indicator shorthand or serialized JSON input.");
        assertThatThrownBy(() -> CliSupport.resolveIndicator(booleanIndicatorJson, null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--indicator must deserialize to an Indicator<Num>.");
        Indicator<Num> thresholdIndicator = CliSupport.resolveIndicator(thresholdIndicatorJson, null, series)
                .indicator();
        assertThatThrownBy(
                () -> CliSupport.buildIndicatorTestStrategy(thresholdIndicator, null, "30", "40", null, null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use only one of --entry-below or --entry-above.");
        assertThatThrownBy(
                () -> CliSupport.buildIndicatorTestStrategy(thresholdIndicator, null, "30", null, null, null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Threshold indicator tests require either --exit-below or --exit-above.");
    }

    @Test
    void buildIndicatorTestStrategyRejectsNonFiniteThresholds() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        String thresholdIndicatorJson = new RSIIndicator(new ClosePriceIndicator(series), 14).toJson();
        Indicator<Num> thresholdIndicator = CliSupport.resolveIndicator(thresholdIndicatorJson, null, series)
                .indicator();

        assertThatThrownBy(
                () -> CliSupport.buildIndicatorTestStrategy(thresholdIndicator, null, "NaN", null, "70", null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid numeric value for --entry-below: NaN.");
        assertThatThrownBy(() -> CliSupport.buildIndicatorTestStrategy(thresholdIndicator, null, null, "Infinity", null,
                "70", series)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid numeric value for --entry-above: Infinity.");
    }

    @Test
    void forecastReportsExposeStateProvenanceAndEmpiricalPriceSupport() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        CliSupport.ForecastRequest stateRequest = new CliSupport.ForecastRequest("change-point", "state", "monte-carlo",
                "none", "auto", null, 3, 25, 40, 42L, "standardized-empirical", "constant", 0.94d, 30, 5, true, 0.90d,
                252, 30, List.of(0.05d, 0.5d, 0.95d));
        CliSupport.ForecastRequest priceRequest = new CliSupport.ForecastRequest("ewma", "price", "monte-carlo", "none",
                "auto", null, 3, 25, 40, 42L, "standardized-empirical", "constant", 0.94d, 30, 5, true, 0.90d, 252, 30,
                List.of(0.05d, 0.5d, 0.95d));

        Map<String, Object> stateReport = CliSupport.buildForecastReport(series, dataFile.toString(), null, null, null,
                stateRequest, null, false);
        Map<String, Object> priceReport = CliSupport.buildForecastReport(series, dataFile.toString(), null, null, null,
                priceRequest, null, false);
        Map<String, Object> stateResult = asMap(stateReport.get("result"));
        Map<String, Object> priceResult = asMap(priceReport.get("result"));

        assertThat(asMap(stateResult.get("state"))).containsEntry("stable", true)
                .containsEntry("representation", "LOG")
                .containsKeys("recentChangeProbability", "mostLikelyRunLength", "topRunLengths");
        assertThat(asMap(stateResult.get("decision"))).containsEntry("index", series.getEndIndex())
                .containsEntry("endTime", series.getLastBar().getEndTime().toString())
                .containsEntry("closePrice", series.getLastBar().getClosePrice().toString());
        Map<String, Object> forecast = asMap(priceResult.get("forecast"));
        assertThat(forecast).containsEntry("stable", true).containsEntry("horizon", 3);
        assertThat(asMap(forecast.get("support"))).containsEntry("type", "empirical").containsEntry("count", 25);
        assertThat(asMap(forecast.get("quantiles"))).containsKeys("0.05", "0.5", "0.95");
    }

    @Test
    void buildRuleTestStrategySupportsNamedRuleLabelsAndJsonDefinitions() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        RsiThresholdRule entryRule = new RsiThresholdRule(series, "BELOW", "14", "30");
        RsiThresholdRule exitRule = new RsiThresholdRule(series, "ABOVE", "14", "70");
        Path entryRuleJsonFile = tempDir.resolve("entry-rule.json");
        Path exitRuleJsonFile = tempDir.resolve("exit-rule.json");
        Files.writeString(entryRuleJsonFile, entryRule.toJson());
        Files.writeString(exitRuleJsonFile, exitRule.toJson());

        Strategy labelStrategy = CliSupport.buildRuleTestStrategy("RsiThresholdRule_BELOW_14_30", null,
                "RsiThresholdRule_ABOVE_14_70", null, 11, series);
        Strategy jsonStrategy = CliSupport.buildRuleTestStrategy(null, entryRuleJsonFile.toString(), null,
                exitRuleJsonFile.toString(), null, series);
        Strategy expressionStrategy = CliSupport.buildRuleTestStrategy("CrossedUp(SMA(7),SMA(21))", null,
                "CrossedDown(SMA(7),SMA(21))", null, null, series);

        assertThat(labelStrategy.getName()).contains("rule-test-RsiThresholdRule_BELOW_14_30");
        assertThat(labelStrategy.getUnstableBars()).isEqualTo(11);
        assertThat(labelStrategy.getEntryRule().getName()).isEqualTo("RsiThresholdRule_BELOW_14_30");
        assertThat(jsonStrategy.getEntryRule().getName()).isEqualTo(entryRule.getName());
        assertThat(jsonStrategy.getExitRule().getName()).isEqualTo(exitRule.getName());
        assertThat(expressionStrategy.getEntryRule().getClass().getSimpleName()).isEqualTo("CrossedUpIndicatorRule");
    }

    @Test
    void buildRuleTestStrategyRejectsMissingAndUnknownRuleInputs() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        assertThatThrownBy(
                () -> CliSupport.buildRuleTestStrategy(null, null, "RsiThresholdRule_ABOVE_14_70", null, null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Provide exactly one of --entry-rule or --entry-rule-json-file. Use --entry-rule or --entry-rule-json-file together with --exit-rule or --exit-rule-json-file.");
        assertThatThrownBy(() -> CliSupport.buildRuleTestStrategy("MissingRule_VALUE", null,
                "RsiThresholdRule_ABOVE_14_70", null, null, series)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Invalid rule shorthand or label 'MissingRule_VALUE'. Use a compact expression such as CrossedUp(SMA(7),SMA(21)) or a NamedRule label such as RsiThresholdRule_BELOW_14_30.");
    }

    @Test
    void buildWalkForwardConfigAndOptionalIntegerParsersApplyOverrides() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        WalkForwardConfig config = CliSupport.buildWalkForwardConfig(series, "120", "40", "20", "3", "2", "10", "5",
                "4", "99");

        assertThat(config.minTrainBars()).isEqualTo(120);
        assertThat(config.testBars()).isEqualTo(40);
        assertThat(config.stepBars()).isEqualTo(20);
        assertThat(config.purgeBars()).isEqualTo(3);
        assertThat(config.embargoBars()).isEqualTo(2);
        assertThat(config.holdoutBars()).isEqualTo(10);
        assertThat(config.primaryHorizonBars()).isEqualTo(5);
        assertThat(config.optimizationTopK()).isEqualTo(4);
        assertThat(config.seed()).isEqualTo(99L);
        assertThat(CliSupport.parseOptionalInteger(null, "unstable-bars")).isNull();
        assertThat(CliSupport.parseOptionalInteger("7", "unstable-bars")).isEqualTo(7);
        assertThatThrownBy(() -> CliSupport.parseOptionalInteger("abc", "unstable-bars"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid integer value for --unstable-bars: abc.");
    }

    @Test
    void parseUnstableBarsRejectsNegativeValues() {
        assertThat(CliSupport.parseUnstableBars(null)).isNull();
        assertThat(CliSupport.parseUnstableBars("7")).isEqualTo(7);
        assertThat(CliSupport.parseUnstableBars("0")).isZero();
        assertThatThrownBy(() -> CliSupport.parseUnstableBars("-1")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--unstable-bars must not be negative: -1.");
        assertThatThrownBy(() -> CliSupport.parseUnstableBars("abc")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid integer value for --unstable-bars: abc.");
    }

    @Test
    void progressAndOutputHelpersWriteExpectedArtifacts() throws Exception {
        StringWriter stderr = new StringWriter();
        PrintWriter err = new PrintWriter(stderr, true);
        Consumer<Integer> progress = CliSupport.progressCallback(true, err, "sweep");

        assertThat(CliSupport.progressCallback(false, err, "sweep")).isNull();
        progress.accept(1);
        progress.accept(2);
        progress.accept(25);
        progress.accept(26);

        assertThat(stderr.toString().lines().toList()).containsExactly("sweep progress: 1", "sweep progress: 25");

        Path outputPath = CliSupport.resolveOutputPath(tempDir.resolve("nested/output.json").toString());
        StringWriter stdout = new StringWriter();
        PrintWriter out = new PrintWriter(stdout, true);

        CliSupport.writeJson("{\"ok\":true}", outputPath, out);
        CliSupport.writeJson("{\"printed\":true}", null, out);

        assertThat(outputPath).exists();
        assertThat(Files.readString(outputPath)).isEqualTo("{\"ok\":true}");
        assertThat(stdout.toString()).contains("{\"printed\":true}");
    }

    @Test
    void invalidStrategyReportingAndRuntimeAggregationStayDeterministic() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Strategy firstStrategy = sampleSweepStrategy(series);
        Strategy secondStrategy = CliSupport.buildStrategy("DayOfWeekStrategy_MONDAY_FRIDAY", null, null, series);
        StringWriter stderr = new StringWriter();
        PrintWriter err = new PrintWriter(stderr, true);
        BacktestRuntimeReport firstRuntime = new BacktestRuntimeReport(Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(2),
                List.of(new BacktestRuntimeReport.StrategyRuntime(firstStrategy, Duration.ofSeconds(2))));
        BacktestRuntimeReport secondRuntime = new BacktestRuntimeReport(Duration.ofSeconds(4), Duration.ofSeconds(4),
                Duration.ofSeconds(4), Duration.ofSeconds(4), Duration.ofSeconds(4),
                List.of(new BacktestRuntimeReport.StrategyRuntime(secondStrategy, Duration.ofSeconds(4))));

        CliSupport.reportInvalidStrategies(List.of("bad first", "bad second"), err);
        BacktestRuntimeReport aggregate = CliSupport.aggregateBacktestRuntimes(List.of(firstRuntime, secondRuntime));

        assertThat(stderr.toString().lines().toList()).containsExactly("Skipping invalid strategy inputs:",
                "- bad first", "- bad second");
        assertThat(aggregate.overallRuntime()).isEqualTo(Duration.ofSeconds(6));
        assertThat(aggregate.minStrategyRuntime()).isEqualTo(Duration.ofSeconds(2));
        assertThat(aggregate.maxStrategyRuntime()).isEqualTo(Duration.ofSeconds(4));
        assertThat(aggregate.averageStrategyRuntime()).isEqualTo(Duration.ofSeconds(3));
        assertThat(aggregate.medianStrategyRuntime()).isEqualTo(Duration.ofSeconds(3));
        assertThat(aggregate.strategyCount()).isEqualTo(2);
    }

    @Test
    void reportHelpersSerializeBacktestAndWalkForwardResultsAndSaveCharts() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Strategy strategy = sampleSweepStrategy(series);
        BacktestExecutor executor = CliSupport.buildExecutor(series, "current-close", "0.01", "0.02");
        Num amount = CliSupport.resolveAmount(series, "1000", null);
        List<CliSupport.CriterionSpec> backtestCriteria = CliSupport.resolveCriteria(
                List.of(NetProfitCriterion.class.getName(), SharpeRatioCriterion.class.getName()),
                CliSupport.DEFAULT_BACKTEST_CRITERIA);
        List<CliSupport.CriterionSpec> walkForwardCriteria = CliSupport.resolveCriteria(
                List.of(GrossReturnCriterion.class.getName()), CliSupport.DEFAULT_WALK_FORWARD_CRITERIA);

        BacktestExecutionResult backtest = executor.executeWithRuntimeReport(List.of(strategy), amount,
                strategy.getStartingType());
        TradingStatement statement = backtest.tradingStatements().getFirst();
        WalkForwardConfig config = CliSupport.buildWalkForwardConfig(series, "120", "40", "20", null, null, "20", null,
                null, null);
        StrategyWalkForwardExecutionResult walkForward = executor.executeWalkForward(strategy, amount, config);
        Path outputPath = CliSupport.resolveOutputPath(tempDir.resolve("artifacts/backtest.json").toString());
        Path chartPath = CliSupport.saveChart(tempDir.resolve("charts/backtest.jpg").toString(), series, statement);
        CliSupport.PositionSizingSpec positionSizing = CliSupport.resolvePositionSizing(series, "fixed", "1000", "1000",
                null, null, null);

        Map<String, Object> metadata = CliSupport.buildCommandMetadata("backtest", series, dataFile.toString(), "1d",
                "2013-01-02", "2013-12-31", "current-close", positionSizing, "0.01", "0.02", "short", backtestCriteria,
                outputPath, chartPath, false);
        Map<String, Object> statementMap = CliSupport.statementToMap(series, statement, backtestCriteria);
        Map<String, Object> runtimeMap = CliSupport.backtestRuntimeToMap(backtest.runtimeReport());
        Map<String, Object> walkForwardMap = CliSupport.walkForwardToMap(series, walkForward, walkForwardCriteria,
                false, null, null);

        assertThat(chartPath).exists();
        assertThat(Files.size(chartPath)).isGreaterThan(0L);
        assertThat(metadata).containsEntry("command", "backtest");
        Map<String, Object> result = asMap(metadata.get("result"));
        assertThat(asMap(result.get("input"))).containsEntry("dataFile", dataFile.toString())
                .containsEntry("seriesName", series.getName())
                .containsEntry("barCount", series.getBarCount())
                .containsKey("seriesSha256");
        assertThat(asMap(result.get("execution"))).containsEntry("executionModel", "current-close")
                .containsEntry("positionSizing", "fixed")
                .containsEntry("capital", "1000")
                .containsEntry("stakeAmount", "1000")
                .containsEntry("commission", "0.01")
                .containsEntry("borrowRate", "0.02");
        assertThat(asMap(asMap(metadata.get("run")).get("artifacts")))
                .containsEntry("outputFile", outputPath.toString())
                .containsEntry("chartFile", chartPath.toString());
        assertThat(statementMap).containsEntry("strategyName", strategy.getName())
                .containsEntry("unstableBars", strategy.getUnstableBars());
        assertThat((List<?>) statementMap.get("criteria")).hasSize(2);
        assertThat(runtimeMap).containsEntry("strategyCount", 1);
        assertThat(asMap(walkForwardMap.get("config"))).containsKey("configHash");
        assertThat(CliSupport.walkForwardRuntimeToMap(walkForward.runtimeReport())).containsEntry("foldCount",
                walkForward.folds().size());
        assertThat((List<?>) walkForwardMap.get("folds")).isNotEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Path copyResource(String resourceName) throws IOException {
        Path target = tempDir.resolve(resourceName);
        try (InputStream inputStream = Objects
                .requireNonNull(getClass().getClassLoader().getResourceAsStream(resourceName))) {
            Files.copy(inputStream, target);
        }
        return target;
    }

    @Test
    void seriesSha256CoversEveryObservableBarField() {
        Instant endTime = Instant.parse("2020-01-02T00:00:00Z");
        String baseline = CliSupport.seriesSha256(seriesWithBar(endTime, Duration.ofDays(1), 7, 50));

        assertThat(CliSupport.seriesSha256(seriesWithBar(endTime, Duration.ofDays(1), 7, 51))).as("amount change")
                .isNotEqualTo(baseline);
        assertThat(CliSupport.seriesSha256(seriesWithBar(endTime, Duration.ofDays(1), 8, 50))).as("trade count change")
                .isNotEqualTo(baseline);
        assertThat(CliSupport.seriesSha256(seriesWithBar(endTime, Duration.ofHours(12), 7, 50)))
                .as("time period change")
                .isNotEqualTo(baseline);
        assertThat(CliSupport.seriesSha256(seriesWithBar(endTime.plusSeconds(3600), Duration.ofDays(1), 7, 50)))
                .as("begin time change")
                .isNotEqualTo(baseline);
    }

    private static BaseBarSeries seriesWithBar(Instant endTime, Duration timePeriod, long trades, double amount) {
        BaseBarSeries series = new BaseBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance()).build();
        NumFactory numFactory = series.numFactory();
        series.addBar(new BaseBar(timePeriod, endTime.minus(timePeriod), endTime, numFactory.numOf(10),
                numFactory.numOf(11), numFactory.numOf(9), numFactory.numOf(10.5), numFactory.numOf(100),
                numFactory.numOf(amount), trades));
        return series;
    }

    private Strategy sampleSweepStrategy(BarSeries series) {
        return CliSupport.buildSweepStrategies(List.of(), List.of("fast=5", "slow=20"), null, series).getFirst();
    }

    @Test
    void requireDistinctArtifactPathsIgnoresInputOnlyCollisions() {
        String shared = tempDir.resolve("shared.csv").toString();
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("--data-file", shared);
        paths.put("--entry-rule-json-file", shared);
        paths.put("--exit-rule-json-file", shared);

        CliSupport.requireDistinctArtifactPaths(paths);
    }

    @Test
    void requireDistinctArtifactPathsStillRejectsWritableCollisions() {
        String shared = tempDir.resolve("shared.csv").toString();
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("--data-file", shared);
        paths.put("--output", shared);

        assertThatThrownBy(() -> CliSupport.requireDistinctArtifactPaths(paths))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--data-file and --output must not refer to the same file");
    }

    @Test
    void walkForwardToMapRecordsFailuresWhenEveryFoldFails() {
        BarSeries series = new MockBarSeriesBuilder().withData(1d, 2d, 3d, 4d, 5d, 6d).build();
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        WalkForwardConfig config = new WalkForwardConfig(2, 1, 1, 0, 0, 0, 1, List.of(1), 1, List.of(1), 7L);
        WalkForwardRuntimeReport runtimeReport = new WalkForwardRuntimeReport(Duration.ZERO, Duration.ZERO,
                Duration.ZERO, Duration.ZERO, Duration.ZERO, List.of());
        WalkForwardRunResult.FoldFailure failure = new WalkForwardRunResult.FoldFailure("fold-1", 0,
                "rule threw during fold", new IllegalStateException("boom"));
        StrategyWalkForwardExecutionResult result = new StrategyWalkForwardExecutionResult(series, strategy, config,
                List.of(), runtimeReport, List.of(failure));

        Map<String, Object> walkForward = CliSupport.walkForwardToMap(series, result, List.of(), false, null, null);

        assertThat(((Number) walkForward.get("failedFoldCount")).intValue()).isEqualTo(1);
        assertThat((List<?>) walkForward.get("folds")).isEmpty();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) walkForward.get("foldFailures");
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).get("foldId")).isEqualTo("fold-1");
        assertThat(failures.get(0).get("message")).isEqualTo("rule threw during fold");
        assertThat(failures.get(0).get("cause")).isEqualTo("boom");
    }

    @Test
    void sameFileDetectsAliasesThroughDanglingSymbolicLinkAncestors(@TempDir Path tempDir) throws IOException {
        boolean linkCreated = true;
        Path target = tempDir.resolve("missing-target");
        Path alias = tempDir.resolve("alias-dir");
        try {
            Files.createSymbolicLink(alias, target);
        } catch (UnsupportedOperationException | IOException unsupported) {
            // Symbolic links require privileges on some platforms (Windows
            // without developer mode); the ancestor-following logic itself is
            // platform independent and is exercised wherever links are legal.
            linkCreated = false;
        }
        assumeTrue(linkCreated, "symbolic links unavailable on this platform");

        assertThat(CliSupport.sameFile(alias.resolve("result.json"), target.resolve("result.json"))).isTrue();
    }

    private static final class InitializerProbe {

        private static boolean initialized;
    }

    private static final class RejectedCriterionProbe {

        static {
            InitializerProbe.initialized = true;
        }
    }
}
