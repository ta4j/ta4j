/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BacktestRuntimeReport;
import org.ta4j.core.backtest.StrategyWalkForwardExecutionResult;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedBooleanIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.walkforward.WalkForwardConfig;
import ta4jexamples.rules.RsiThresholdRule;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void resolveCriteriaUsesDefaultsDeduplicatesClassNamesAndRejectsAliases() {
        List<CliSupport.CriterionSpec> defaults = CliSupport.resolveCriteria(List.of(),
                CliSupport.DEFAULT_BACKTEST_CRITERIA);
        List<CliSupport.CriterionSpec> explicit = CliSupport.resolveCriteria(
                List.of(NetProfitCriterion.class.getName() + "," + SharpeRatioCriterion.class.getName(),
                        NetProfitCriterion.class.getName()),
                CliSupport.DEFAULT_SWEEP_CRITERIA);

        assertThat(defaults).extracting(CliSupport.CriterionSpec::className)
                .containsExactlyElementsOf(CliSupport.DEFAULT_BACKTEST_CRITERIA);
        assertThat(explicit).extracting(CliSupport.CriterionSpec::className)
                .containsExactly(NetProfitCriterion.class.getName(), SharpeRatioCriterion.class.getName());
        assertThatThrownBy(() -> CliSupport.resolveCriteria(List.of("net-profit"), CliSupport.DEFAULT_SWEEP_CRITERIA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Criteria aliases are no longer supported. Use a fully qualified AnalysisCriterion class name such as "
                                + NetProfitCriterion.class.getName() + ".");
    }

    @Test
    void buildStrategySupportsNamedLabelsAndJsonDefinitions() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Strategy labelStrategy = CliSupport.buildStrategy("DayOfWeekStrategy_MONDAY_FRIDAY", null, 12, series);
        Path strategyJsonFile = tempDir.resolve("strategy.json");
        Files.writeString(strategyJsonFile, labelStrategy.toJson());

        Strategy jsonStrategy = CliSupport.buildStrategy("ignored", strategyJsonFile.toString(), 7, series);

        assertThat(labelStrategy.getName()).isEqualTo("DayOfWeekStrategy_MONDAY_FRIDAY");
        assertThat(labelStrategy.getUnstableBars()).isEqualTo(12);
        assertThat(jsonStrategy.getName()).isEqualTo(labelStrategy.getName());
        assertThat(jsonStrategy.getUnstableBars()).isEqualTo(7);
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
    void resolveStrategiesSupportsMixedInputsAndCollectsInvalidEntries() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Strategy serializedStrategy = sampleSweepStrategy(series);
        Path strategyJson = tempDir.resolve("strategy.json");
        Path strategiesJsonFile = tempDir.resolve("strategies.json");
        Files.writeString(strategyJson, serializedStrategy.toJson());
        Files.writeString(strategiesJsonFile, "[" + serializedStrategy.toJson() + ",\"invalid\"]");

        CliSupport.ResolvedStrategies resolved = CliSupport.resolveStrategies("DayOfWeekStrategy_MONDAY_FRIDAY",
                strategyJson.toString(), List.of("HourOfDayStrategy_9_17,MissingStrategy_VALUE"),
                strategiesJsonFile.toString(), 7, series);

        assertThat(resolved.strategies()).hasSize(4);
        assertThat(resolved.strategies()).extracting(Strategy::getUnstableBars).containsOnly(7);
        assertThat(resolved.invalidStrategies()).contains(
                "--strategies MissingStrategy_VALUE: Unknown strategy label 'MissingStrategy_VALUE'. Use a NamedStrategy label such as DayOfWeekStrategy_MONDAY_FRIDAY.",
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
                        "--strategies MissingStrategy_VALUE: Unknown strategy label 'MissingStrategy_VALUE'.")
                .hasMessageContaining("Use --strategy, --strategies, --strategy-json-file, or --strategies-json-file.");
    }

    @Test
    void buildStrategyRejectsUnknownLabels() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        assertThatThrownBy(() -> CliSupport.buildStrategy("unknown", null, null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown strategy label 'unknown'. Use a NamedStrategy label such as "
                        + "DayOfWeekStrategy_MONDAY_FRIDAY.");
    }

    @Test
    void buildSweepStrategiesBuildsCartesianProductsAndValidatesInputs() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);

        List<Strategy> strategies = CliSupport.buildSweepStrategies(List.of("slow=40"),
                List.of("fast=3,5", "slow=20,30"), 9, series);

        assertThat(strategies).hasSize(4);
        assertThat(strategies).extracting(Strategy::getName)
                .containsExactly("sma-crossover-fast-3-slow-20", "sma-crossover-fast-3-slow-30",
                        "sma-crossover-fast-5-slow-20", "sma-crossover-fast-5-slow-30");
        assertThat(strategies).extracting(Strategy::getUnstableBars).containsOnly(9);
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

        assertThat(inlineIndicator.typeName()).isEqualTo(EMAIndicator.class.getName());
        assertThat(inlineIndicator.json()).isEqualTo(indicatorJson);
        assertThat(fileIndicator.typeName()).isEqualTo(EMAIndicator.class.getName());
    }

    @Test
    void buildIndicatorTestStrategySupportsDefaultAndThresholdModes() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        String defaultIndicatorJson = new EMAIndicator(new ClosePriceIndicator(series), 5).toJson();
        String thresholdIndicatorJson = new RSIIndicator(new ClosePriceIndicator(series), 14).toJson();

        Strategy defaultStrategy = CliSupport.buildIndicatorTestStrategy(defaultIndicatorJson, null, null, null, null,
                null, null, series);
        Strategy thresholdStrategy = CliSupport.buildIndicatorTestStrategy(thresholdIndicatorJson, null, 20, "30", null,
                null, "70", series);

        assertThat(defaultStrategy.getName()).isEqualTo("EMAIndicator-indicator-test");
        assertThat(defaultStrategy.getUnstableBars()).isEqualTo(5);
        assertThat(thresholdStrategy.getName()).isEqualTo("RSIIndicator-indicator-test");
        assertThat(thresholdStrategy.getUnstableBars()).isEqualTo(20);
    }

    @Test
    void buildIndicatorTestStrategyRejectsInvalidIndicatorAndThresholdInputs() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        String thresholdIndicatorJson = new RSIIndicator(new ClosePriceIndicator(series), 14).toJson();
        Boolean[] booleanValues = new Boolean[series.getBarCount()];
        java.util.Arrays.fill(booleanValues, Boolean.TRUE);
        String booleanIndicatorJson = new FixedBooleanIndicator(series, booleanValues).toJson();

        assertThatThrownBy(
                () -> CliSupport.buildIndicatorTestStrategy("not-json", null, null, null, null, null, null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid serialized indicator input.");
        assertThatThrownBy(() -> CliSupport.resolveIndicator(booleanIndicatorJson, null, series))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--indicator must deserialize to an Indicator<Num>.");
        assertThatThrownBy(() -> CliSupport.buildIndicatorTestStrategy(thresholdIndicatorJson, null, null, "30", "40",
                null, null, series)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use only one of --entry-below or --entry-above.");
        assertThatThrownBy(() -> CliSupport.buildIndicatorTestStrategy(thresholdIndicatorJson, null, null, "30", null,
                null, null, series)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Threshold indicator tests require either --exit-below or --exit-above.");
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

        assertThat(labelStrategy.getName()).contains("rule-test-RsiThresholdRule_BELOW_14_30");
        assertThat(labelStrategy.getUnstableBars()).isEqualTo(11);
        assertThat(labelStrategy.getEntryRule().getName()).isEqualTo("RsiThresholdRule_BELOW_14_30");
        assertThat(jsonStrategy.getEntryRule().getName()).isEqualTo(entryRule.getName());
        assertThat(jsonStrategy.getExitRule().getName()).isEqualTo(exitRule.getName());
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
                        "Unknown rule label 'MissingRule_VALUE'. Use a NamedRule label such as RsiThresholdRule_BELOW_14_30.");
    }

    @Test
    void buildWalkForwardConfigAndOptionalIntegerParsersApplyOverrides() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        CliArguments arguments = CliArguments.parse(new String[] { "walk-forward", "--min-train-bars", "120",
                "--test-bars", "40", "--step-bars", "20", "--purge-bars", "3", "--embargo-bars", "2", "--holdout-bars",
                "10", "--primary-horizon-bars", "5", "--optimization-top-k", "4", "--seed", "99" });

        WalkForwardConfig config = CliSupport.buildWalkForwardConfig(series, arguments);

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
        WalkForwardConfig config = CliSupport.buildWalkForwardConfig(series,
                CliArguments.parse(new String[] { "walk-forward", "--min-train-bars", "120", "--test-bars", "40",
                        "--step-bars", "20", "--holdout-bars", "20" }));
        StrategyWalkForwardExecutionResult walkForward = executor.executeWalkForward(strategy, amount, config);
        Path outputPath = CliSupport.resolveOutputPath(tempDir.resolve("artifacts/backtest.json").toString());
        Path chartPath = CliSupport.saveChart(tempDir.resolve("charts/backtest.jpg").toString(), series, statement);

        Map<String, Object> metadata = CliSupport.buildCommandMetadata("backtest", series, dataFile.toString(), "1d",
                "2013-01-02", "2013-12-31", "current-close", "1000", "1000", "0.01", "0.02", backtestCriteria,
                outputPath, chartPath);
        Map<String, Object> statementMap = CliSupport.statementToMap(series, statement, backtestCriteria);
        Map<String, Object> runtimeMap = CliSupport.backtestRuntimeToMap(backtest.runtimeReport());
        Map<String, Object> walkForwardMap = CliSupport.walkForwardToMap(series, walkForward, walkForwardCriteria);

        assertThat(chartPath).exists();
        assertThat(Files.size(chartPath)).isGreaterThan(0L);
        assertThat(metadata).containsEntry("command", "backtest");
        assertThat(asMap(metadata.get("input")))
                .containsEntry("dataFile", dataFile.toAbsolutePath().normalize().toString())
                .containsEntry("seriesName", series.getName())
                .containsEntry("barCount", series.getBarCount());
        assertThat(asMap(metadata.get("execution"))).containsEntry("executionModel", "current-close")
                .containsEntry("capital", "1000")
                .containsEntry("stakeAmount", "1000")
                .containsEntry("commission", "0.01")
                .containsEntry("borrowRate", "0.02");
        assertThat(asMap(metadata.get("artifacts"))).containsEntry("outputFile", outputPath.toString())
                .containsEntry("chartFile", chartPath.toString());
        assertThat(statementMap).containsEntry("strategyName", strategy.getName())
                .containsEntry("unstableBars", strategy.getUnstableBars());
        assertThat(asMap(statementMap.get("criteria")).keySet())
                .containsAll(Set.of(NetProfitCriterion.class.getName(), SharpeRatioCriterion.class.getName()));
        assertThat(runtimeMap).containsEntry("strategyCount", 1);
        assertThat(asMap(walkForwardMap.get("config"))).containsKey("configHash");
        assertThat(asMap(walkForwardMap.get("runtime"))).containsEntry("foldCount", walkForward.folds().size());
        assertThat((List<?>) walkForwardMap.get("folds")).isNotEmpty();
        assertThat(asMap(walkForwardMap.get("criteria"))).containsKey(GrossReturnCriterion.class.getName());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Path copyResource(String resourceName) throws IOException {
        Path target = tempDir.resolve(resourceName);
        try (var inputStream = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resourceName))) {
            Files.copy(inputStream, target);
        }
        return target;
    }

    private Strategy sampleSweepStrategy(BarSeries series) {
        return CliSupport.buildSweepStrategies(List.of(), List.of("fast=5", "slow=20"), null, series).getFirst();
    }
}
