/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.SlippageExecutionModel;
import org.ta4j.core.backtest.TradeExecutionModel;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.FixedRule;

/**
 * Example demonstrating execution engine parity across different execution
 * models.
 *
 * This backtest asserts that strategies yield identical results when run
 * through:
 * <ul>
 * <li>{@link BarSeriesManager} (standard simulation)</li>
 * <li>{@link BacktestExecutor} (batch/parallel simulation)</li>
 * <li>Manual iterative loops (mimicking live/paper trading)</li>
 * </ul>
 * It ensures the default {@link BaseTradingRecord} acts as a unified record
 * model.
 */
public class TradingRecordParityBacktest {

    private static final Logger LOG = LogManager.getLogger(TradingRecordParityBacktest.class);

    public static void main(String[] args) {
        BarSeries series = createSeries();
        Strategy strategy = createStrategy();
        Num slippageRatio = series.numFactory().numOf(0.01);

        TradingRecord nextOpenRecord = runWithExecutionModel(series, strategy, new TradeOnNextOpenModel());
        TradingRecord currentCloseRecord = runWithExecutionModel(series, strategy, new TradeOnCurrentCloseModel());
        TradingRecord slippageRecord = runWithExecutionModel(series, strategy,
                new SlippageExecutionModel(slippageRatio, TradeExecutionModel.PriceSource.CURRENT_CLOSE));

        logExecutionComparison("Next-open fills", nextOpenRecord);
        logExecutionComparison("Current-close fills", currentCloseRecord);
        logExecutionComparison("Current-close fills with 1% slippage", slippageRecord);

        TradingRecord providedRecord = runWithProvidedRecord(series, strategy, new TradeOnCurrentCloseModel());
        assertEquivalent(currentCloseRecord, providedRecord, "provided BaseTradingRecord");

        TradingRecord factoryConfiguredRecord = runWithFactoryConfiguredRecord(series, strategy,
                new TradeOnCurrentCloseModel());
        assertEquivalent(currentCloseRecord, factoryConfiguredRecord, "factory-configured BaseTradingRecord");

        LOG.info("Execution-model comparison and record-parity checks passed.");
    }

    static Strategy createStrategy() {
        return new BaseStrategy("Single-entry timing demo", new FixedRule(1), new FixedRule(3));
    }

    static BarSeries createSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("parity-series").build();
        addBar(series, 1, 100d, 100d);
        addBar(series, 2, 102d, 104d);
        addBar(series, 3, 109d, 111d);
        addBar(series, 4, 107d, 103d);
        addBar(series, 5, 99d, 101d);
        addBar(series, 6, 105d, 107d);
        return series;
    }

    static TradingRecord runWithExecutionModel(BarSeries series, Strategy strategy,
            TradeExecutionModel tradeExecutionModel) {
        BarSeriesManager manager = new BarSeriesManager(series, new ZeroCostModel(), new ZeroCostModel(),
                tradeExecutionModel);
        return manager.run(strategy, TradeType.BUY, series.numFactory().one(), 0, series.getEndIndex());
    }

    static TradingRecord runWithProvidedRecord(BarSeries series, Strategy strategy,
            TradeExecutionModel tradeExecutionModel) {
        BarSeriesManager manager = new BarSeriesManager(series, new ZeroCostModel(), new ZeroCostModel(),
                tradeExecutionModel);
        BaseTradingRecord providedRecord = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), 0, series.getEndIndex());
        return manager.run(strategy, providedRecord, series.numFactory().one(), 0, series.getEndIndex());
    }

    static TradingRecord runWithFactoryConfiguredRecord(BarSeries series, Strategy strategy,
            TradeExecutionModel tradeExecutionModel) {
        BarSeriesManager configuredManager = new BarSeriesManager(series, new ZeroCostModel(), new ZeroCostModel(),
                tradeExecutionModel,
                (tradeType, startIndex, endIndex, txCost, holdCost) -> new BaseTradingRecord(tradeType,
                        ExecutionMatchPolicy.FIFO, txCost, holdCost, startIndex, endIndex));
        return configuredManager.run(strategy, TradeType.BUY, series.numFactory().one(), 0, series.getEndIndex());
    }

    private static void addBar(BarSeries series, int day, double openPrice, double closePrice) {
        double highPrice = Math.max(openPrice, closePrice);
        double lowPrice = Math.min(openPrice, closePrice);
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.of(2025, 1, day, 12, 0, 0, 0, ZoneOffset.UTC).toInstant())
                .openPrice(openPrice)
                .highPrice(highPrice)
                .lowPrice(lowPrice)
                .closePrice(closePrice)
                .volume(1000d + day)
                .add();
    }

    static void assertEquivalent(TradingRecord expected, TradingRecord actual, String runLabel) {
        require(expected.getTrades().size() == actual.getTrades().size(), () -> runLabel + ": trade count mismatch");
        require(expected.getPositions().size() == actual.getPositions().size(),
                () -> runLabel + ": position count mismatch");
        require(expected.isClosed() == actual.isClosed(), () -> runLabel + ": close-state mismatch");

        for (int i = 0; i < expected.getTrades().size(); i++) {
            Trade left = expected.getTrades().get(i);
            Trade right = actual.getTrades().get(i);
            assertTrade(left, right, runLabel + ": trade[" + i + "]");
        }

        for (int i = 0; i < expected.getPositions().size(); i++) {
            Position left = expected.getPositions().get(i);
            Position right = actual.getPositions().get(i);
            assertTrade(left.getEntry(), right.getEntry(), runLabel + ": position[" + i + "].entry");
            if (left.getExit() != null || right.getExit() != null) {
                require(left.getExit() != null && right.getExit() != null, () -> runLabel + ": exit mismatch");
                assertTrade(left.getExit(), right.getExit(), runLabel + ": position[" + i + "].exit");
            }
        }
    }

    private static void logExecutionComparison(String label, TradingRecord tradingRecord) {
        Position position = tradingRecord.getPositions().get(0);
        LOG.info("{} -> entry={} @ {}, exit={} @ {}, gross profit={}", label, position.getEntry().getIndex(),
                position.getEntry().getPricePerAsset(), position.getExit().getIndex(),
                position.getExit().getPricePerAsset(), position.getGrossProfit());
    }

    private static void assertTrade(Trade expected, Trade actual, String label) {
        require(expected.getType() == actual.getType(), () -> label + ": type mismatch");
        require(expected.getIndex() == actual.getIndex(), () -> label + ": index mismatch");
        assertNum(expected.getPricePerAsset(), actual.getPricePerAsset(), label + ": price mismatch");
        assertNum(expected.getAmount(), actual.getAmount(), label + ": amount mismatch");
    }

    private static void assertNum(Num expected, Num actual, String message) {
        if (expected == null || actual == null) {
            require(Objects.equals(expected, actual), () -> message);
            return;
        }
        require(expected.isEqual(actual), () -> message + " expected=" + expected + ", actual=" + actual);
    }

    private static void require(boolean condition, Supplier<String> messageSupplier) {
        if (!condition) {
            throw new IllegalStateException(messageSupplier.get());
        }
    }
}
