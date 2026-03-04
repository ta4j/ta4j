/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.FixedRule;

/**
 * Demonstrates parity-oriented backtests using different {@link TradingRecord}
 * implementations.
 */
public class TradingRecordParityBacktest {

    private static final Logger LOG = LogManager.getLogger(TradingRecordParityBacktest.class);

    public static void main(String[] args) {
        BarSeries series = createSeries();
        Strategy strategy = new BaseStrategy(new FixedRule(1, 4), new FixedRule(2, 5));

        BarSeriesManager manager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());
        TradingRecord baseRecord = manager.run(strategy, TradeType.BUY, series.numFactory().one(), 0,
                series.getEndIndex());

        LiveTradingRecord providedLiveRecord = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), 0, series.getEndIndex());
        TradingRecord explicitLiveRun = manager.run(strategy, providedLiveRecord, series.numFactory().one(), 0,
                series.getEndIndex());
        assertEquivalent(baseRecord, explicitLiveRun, "provided LiveTradingRecord");

        BarSeriesManager liveDefaultManager = new BarSeriesManager(series, new ZeroCostModel(), new ZeroCostModel(),
                new TradeOnCurrentCloseModel(),
                (tradeType, startIndex, endIndex, txCost, holdCost) -> new LiveTradingRecord(tradeType,
                        ExecutionMatchPolicy.FIFO, txCost, holdCost, startIndex, endIndex));
        TradingRecord defaultLiveRun = liveDefaultManager.run(strategy, TradeType.BUY, series.numFactory().one(), 0,
                series.getEndIndex());
        assertEquivalent(baseRecord, defaultLiveRun, "factory-configured LiveTradingRecord");

        LOG.info("Parity checks passed for both live-backed backtest flows.");
    }

    private static BarSeries createSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("parity-series").build();
        addBar(series, 1, 10d);
        addBar(series, 2, 20d);
        addBar(series, 3, 30d);
        addBar(series, 4, 15d);
        addBar(series, 5, 25d);
        addBar(series, 6, 35d);
        return series;
    }

    private static void addBar(BarSeries series, int day, double closePrice) {
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.of(2025, 1, day, 12, 0, 0, 0, ZoneOffset.UTC).toInstant())
                .openPrice(closePrice)
                .highPrice(closePrice)
                .lowPrice(closePrice)
                .closePrice(closePrice)
                .volume(1000d + day)
                .add();
    }

    private static void assertEquivalent(TradingRecord expected, TradingRecord actual, String runLabel) {
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

    private static void assertTrade(Trade expected, Trade actual, String label) {
        require(expected.getType() == actual.getType(), () -> label + ": type mismatch");
        require(expected.getIndex() == actual.getIndex(), () -> label + ": index mismatch");
        assertNum(expected.getPricePerAsset(), actual.getPricePerAsset(), label + ": price mismatch");
        assertNum(expected.getAmount(), actual.getAmount(), label + ": amount mismatch");
    }

    private static void assertNum(Num expected, Num actual, String message) {
        if (expected == null || actual == null) {
            require(expected == actual, () -> message);
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
