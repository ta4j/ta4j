/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.time.Instant;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.junit.Test;

public class InvestedIntervalTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public InvestedIntervalTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void capturesRollingWindowUnderOneReadLease() {
        AtomicBoolean appendBeforeLock = new AtomicBoolean();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.rollingSeriesWithAppendBeforeReadLock(numFactory,
                appendBeforeLock, 1.5d, 2.5d, 3.5d);
        BaseTradingRecord record = new BaseTradingRecord(Trade.buyAt(0, series));
        appendBeforeLock.set(true);

        InvestedInterval intervals = new InvestedInterval(series, record, OpenPositionHandling.MARK_TO_MARKET);

        assertThat(intervals.getValue(2)).isTrue();
    }

    @Test
    public void traversesTheTradingRecordOutsideTheSeriesReadLock() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, lock);
        AtomicBoolean readWhileLocked = new AtomicBoolean();
        AtomicBoolean traversed = new AtomicBoolean();
        // A record guarded by its own lock must never be traversed while the series
        // lock is held, or record and series locks could be taken in both orders.
        BaseTradingRecord record = new BaseTradingRecord() {
            private void probe() {
                traversed.set(true);
                if (lock.getReadHoldCount() > 0) {
                    readWhileLocked.set(true);
                }
            }

            @Override
            public List<Position> getPositions() {
                probe();
                return super.getPositions();
            }

            @Override
            public List<Position> getOpenPositions() {
                probe();
                return super.getOpenPositions();
            }

            @Override
            public Position getCurrentPosition() {
                probe();
                return super.getCurrentPosition();
            }
        };
        record.enter(0, numFactory.one(), numFactory.one());
        record.exit(1, numFactory.two(), numFactory.one());
        record.enter(2, numFactory.three(), numFactory.one());

        InvestedInterval intervals = new InvestedInterval(series, record, OpenPositionHandling.MARK_TO_MARKET);

        assertThat(traversed).isTrue();
        assertThat(readWhileLocked).isFalse();
        assertThat(intervals.getValue(1)).isTrue();
        assertThat(intervals.getValue(3)).isTrue();
    }

    @Test
    public void marksIntervalsForClosedAndOpenPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1).build();
        var tradingRecord = new BaseTradingRecord();
        var price = series.numFactory().numOf(1);
        var amount = series.numFactory().numOf(1);

        tradingRecord.enter(1, price, amount);
        tradingRecord.exit(3, price, amount);
        tradingRecord.enter(4, price, amount);

        var indicator = new InvestedInterval(series, tradingRecord);

        assertThat(indicator.getValue(0)).as("first bar interval").isFalse();
        assertThat(indicator.getValue(1)).as("entry bar interval").isFalse();
        assertThat(indicator.getValue(2)).as("between entry and exit").isTrue();
        assertThat(indicator.getValue(3)).as("exit interval").isTrue();
        assertThat(indicator.getValue(4)).as("open position entry interval").isFalse();
        assertThat(indicator.getValue(5)).as("open position following interval").isTrue();
    }

    @Test
    public void returnsFalseWhenNoPositionsExist() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1).build();
        var tradingRecord = new BaseTradingRecord();

        var indicator = new InvestedInterval(series, tradingRecord);

        assertThat(indicator.getValue(0)).isFalse();
        assertThat(indicator.getValue(1)).isFalse();
        assertThat(indicator.getValue(2)).isFalse();
    }

    @Test
    public void ignoresOpenPositionsWhenConfigured() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1).build();
        var tradingRecord = new BaseTradingRecord();
        var price = series.numFactory().numOf(1);
        var amount = series.numFactory().numOf(1);

        tradingRecord.enter(1, price, amount);
        tradingRecord.exit(3, price, amount);
        tradingRecord.enter(4, price, amount);

        var indicator = new InvestedInterval(series, tradingRecord, OpenPositionHandling.IGNORE);

        assertThat(indicator.getValue(2)).as("between entry and exit").isTrue();
        assertThat(indicator.getValue(3)).as("exit interval").isTrue();
        assertThat(indicator.getValue(4)).as("open position entry interval").isFalse();
        assertThat(indicator.getValue(5)).as("open position following interval").isFalse();
    }

    @Test
    public void handlesEmptySeriesWithoutIntervals() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData().build();
        var tradingRecord = new BaseTradingRecord();

        var indicator = new InvestedInterval(series, tradingRecord);

        assertThat(series.getEndIndex()).isEqualTo(-1);
        assertThat(series.getBarCount()).isEqualTo(0);
        assertThat(indicator.getValue(0)).isFalse();
    }

    @Test
    public void ignoresTradesOutsideAnEmptyLogicalWindow() {
        BarSeries series = ConstrainedSeriesSupport.emptyLogicalSeries("empty-window", numFactory, 100d, 50d);
        Num one = numFactory.one();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, numFactory.numOf(100d), one),
                Trade.sellAt(1, numFactory.numOf(50d), one));

        InvestedInterval indicator = new InvestedInterval(series, tradingRecord);

        assertThat(series.isEmpty()).isTrue();
        assertThat(indicator.getValue(1)).isFalse();
        assertThat(indicator.stream().toList()).isEmpty();
    }

    @Test
    public void respectsNonZeroBeginIndexWhenMarkingIntervals() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 1, 1, 1, 1)
                .withMaxBarCount(2)
                .build();
        var tradingRecord = new BaseTradingRecord();
        var price = series.numFactory().one();
        var amount = series.numFactory().one();

        tradingRecord.enter(0, price, amount);

        var indicator = new InvestedInterval(series, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        int beginIndex = series.getBeginIndex();
        assertThat(beginIndex).isGreaterThan(0);
        assertThat(indicator.getValue(beginIndex)).as("begin index interval").isFalse();
        assertThat(indicator.getValue(beginIndex + 1)).as("first invested interval after begin index").isTrue();

        var ignoreIndicator = new InvestedInterval(series, tradingRecord, OpenPositionHandling.IGNORE);
        assertThat(ignoreIndicator.getValue(beginIndex + 1)).as("ignored open position interval").isFalse();
    }

    @Test
    public void marksIntervalsCompactlyForOffsetSeries() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1).build();
        BarSeries offset = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(10)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(10, offset), Trade.sellAt(12, offset));
        var indicator = new InvestedInterval(offset, tradingRecord);

        assertThat(offset.getBeginIndex()).isEqualTo(10);
        assertThat(indicator.getValue(10)).as("entry interval").isFalse();
        assertThat(indicator.getValue(11)).as("between entry and exit").isTrue();
        assertThat(indicator.getValue(12)).as("exit interval").isTrue();
        assertThat(indicator.getValue(9)).as("below window").isFalse();
    }

    @Test
    public void marksIntervalEndingAtTerminalBarWithoutOverflow() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1).build();
        BarSeries terminal = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(Integer.MAX_VALUE - 1)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(Integer.MAX_VALUE - 1, terminal),
                Trade.sellAt(Integer.MAX_VALUE, terminal));

        var indicator = new InvestedInterval(terminal, tradingRecord);

        assertThat(indicator.getValue(Integer.MAX_VALUE - 1)).as("entry interval").isFalse();
        assertThat(indicator.getValue(Integer.MAX_VALUE)).as("exit interval").isTrue();
    }

    @Test
    public void endsAtTheWindowWhenAnExitLandsAfterIt() {
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("trailing-exit", numFactory, 1, 10d, 20d,
                30d);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, 0, 1, null, null);
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(2, series.getBar(2).getClosePrice(), numFactory.one());

        var indicator = new InvestedInterval(series, tradingRecord);

        assertThat(indicator.stream().toList()).containsExactly(false, true);
        assertThat(indicator.getValue(2)).as("interval after the window").isFalse();
    }

    @Test
    public void intervalsStayAnchoredAfterWindowAdvances() {
        // investedIntervals is materialized against the construction-time
        // window; a later rolling advance of the borrowed series must not
        // rebase the lookup or inherit flags onto never-calculated bars.
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(2);
        rolling.barBuilder().closePrice(30d).add();
        Trade entry = Trade.buyAt(0, rolling);
        rolling.barBuilder().closePrice(40d).add();
        var tradingRecord = new BaseTradingRecord(entry, Trade.sellAt(1, rolling));
        InvestedInterval indicator = new InvestedInterval(rolling, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        assertThat(indicator.getValue(1)).isTrue();

        rolling.barBuilder().closePrice(50d).add();
        rolling.barBuilder().closePrice(60d).add();

        assertThat(indicator.getValue(1)).as("anchored invested interval").isTrue();
        assertThat(indicator.getValue(2)).as("never-calculated bar stays uninvested").isFalse();
        assertThat(indicator.stream().toList()).containsExactly(false, true);
    }

    private BaseTradingRecord boundedRecord(BarSeries series, Integer startIndex, Integer endIndex, int[][] positions) {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, startIndex, endIndex, new ZeroCostModel(),
                new ZeroCostModel());
        Num price = series.numFactory().one();
        for (int[] position : positions) {
            record.enter(position[0], price, price);
            if (position[1] >= 0) {
                record.exit(position[1], price, price);
            }
        }
        return record;
    }

    @Test
    public void dropsPositionsExitingAfterTheRecordEndWhenIgnoringOpenPositions() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1, 1).build();
        BaseTradingRecord record = boundedRecord(series, 0, 3, new int[][] { { 0, 1 }, { 2, 5 } });

        InvestedInterval ignored = new InvestedInterval(series, record, OpenPositionHandling.IGNORE);

        assertThat(ignored.stream().toList()).containsExactly(false, true, false, false, false, false, false);
    }

    @Test
    public void marksPositionsExitingAfterTheRecordEndOnlyThroughTheRecordEnd() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1, 1).build();
        BaseTradingRecord record = boundedRecord(series, 0, 3, new int[][] { { 0, 1 }, { 2, 5 } });

        InvestedInterval marked = new InvestedInterval(series, record, OpenPositionHandling.MARK_TO_MARKET);

        assertThat(marked.stream().toList()).containsExactly(false, true, false, true, false, false, false);
    }

    @Test
    public void boundsOpenPositionsToTheRecordEnd() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1, 1).build();
        BaseTradingRecord record = boundedRecord(series, 0, 3, new int[][] { { 1, -1 } });

        InvestedInterval marked = new InvestedInterval(series, record, OpenPositionHandling.MARK_TO_MARKET);

        assertThat(marked.stream().toList()).containsExactly(false, false, true, true, false, false, false);
    }

    @Test
    public void doesNotMarkBarsBeforeTheRecordStart() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1, 1).build();
        BaseTradingRecord record = boundedRecord(series, 2, null, new int[][] { { 0, 4 } });

        InvestedInterval marked = new InvestedInterval(series, record, OpenPositionHandling.MARK_TO_MARKET);

        boolean[] expected = { false, false, false, true, true, false, false };
        for (int index = 0; index < expected.length; index++) {
            assertThat(marked.getValue(index)).as("interval %d", index).isEqualTo(expected[index]);
        }
    }

    @Test
    public void doesNotMarkRetainedBarsForPositionsClosedBeforeThePrunedBegin() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1).build();
        BaseTradingRecord record = boundedRecord(series, null, null, new int[][] { { 0, 2 }, { 1, 4 } });
        series.setMaximumBarCount(3);

        InvestedInterval marked = new InvestedInterval(series, record, OpenPositionHandling.MARK_TO_MARKET);

        // Retained window [3, 5]: the first position closed before it, the second spans
        // it
        boolean[] expected = { false, true, false };
        for (int index = 0; index < expected.length; index++) {
            assertThat(marked.getValue(3 + index)).as("interval %d", 3 + index).isEqualTo(expected[index]);
        }
    }

    @Test
    public void marksAggregatePartialFuturesPositionThroughFinalBar() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110, 99, 120);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        Trade entry = Trade.fromFills(Trade.TradeType.BUY,
                List.of(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 2, 100, List.of())),
                RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFills(Trade.TradeType.SELL,
                List.of(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, 110, List.of()),
                        FuturesAnalysisTestSupport.fill(contract, -1, ExecutionSide.SELL, 1, 110, List.of())),
                RecordedTradeCostModel.INSTANCE);
        var position = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        var tradingRecord = new AggregatePositionTradingRecord(position);

        var indicator = new InvestedInterval(series, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        assertThat(indicator.getValue(1)).isTrue();
        assertThat(indicator.getValue(2)).isTrue();
        assertThat(indicator.getValue(3)).isTrue();
    }

    @Test
    public void marksOnlyNonzeroExposureForAggregateFuturesPosition() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110, 105, 100, 95, 120, 125);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        Trade entry = Trade.fromFills(Trade.TradeType.BUY,
                List.of(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 100, List.of()),
                        FuturesAnalysisTestSupport.fill(contract, 5, ExecutionSide.BUY, 1, 120, List.of())),
                RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFill(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, 110, List.of()),
                RecordedTradeCostModel.INSTANCE);
        Position position = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        TradingRecord tradingRecord = new AggregatePositionTradingRecord(position);

        InvestedInterval indicator = new InvestedInterval(series, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        assertThat(indicator.getValue(1)).isTrue();
        assertThat(indicator.getValue(2)).isFalse();
        assertThat(indicator.getValue(3)).isFalse();
        assertThat(indicator.getValue(4)).isFalse();
        assertThat(indicator.getValue(5)).isFalse();
        assertThat(indicator.getValue(6)).isTrue();
    }

    @Test
    public void marksPartiallyClosedSpotPositionThroughFinalBar() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 99, 120).build();
        Trade entry = Trade
                .fromFills(
                        Trade.TradeType.BUY, List.of(new TradeFill(0, Instant.EPOCH, numFactory.numOf(100),
                                numFactory.numOf(2), numFactory.zero(), ExecutionSide.BUY, null, null)),
                        RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFills(Trade.TradeType.SELL,
                List.of(new TradeFill(1, Instant.EPOCH.plusSeconds(1), numFactory.numOf(110), numFactory.one(),
                        numFactory.zero(), ExecutionSide.SELL, null, null),
                        new TradeFill(-1, Instant.EPOCH.plusSeconds(2), numFactory.numOf(120), numFactory.one(),
                                numFactory.zero(), ExecutionSide.SELL, null, null)),
                RecordedTradeCostModel.INSTANCE);
        Position position = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        TradingRecord tradingRecord = new AggregatePositionTradingRecord(position);

        InvestedInterval indicator = new InvestedInterval(series, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        assertThat(indicator.getValue(1)).isTrue();
        assertThat(indicator.getValue(2)).isTrue();
        assertThat(indicator.getValue(3)).isTrue();
    }

    @Test
    public void usesLastExecutedExitFillForFullyExitedAggregatePosition() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110, 120, 130, 140, 150, 160, 170, 180);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        Trade entry = Trade.fromFills(Trade.TradeType.BUY,
                List.of(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 2, 100, List.of()),
                        FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100, List.of())),
                RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFills(Trade.TradeType.SELL,
                List.of(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1, 130, List.of()),
                        FuturesAnalysisTestSupport.fill(contract, 5, ExecutionSide.SELL, 1, 150, List.of()),
                        FuturesAnalysisTestSupport.fill(contract, 7, ExecutionSide.SELL, 1, 170, List.of())),
                RecordedTradeCostModel.INSTANCE);
        Position position = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        TradingRecord tradingRecord = new AggregatePositionTradingRecord(position);

        InvestedInterval indicator = new InvestedInterval(series, tradingRecord, OpenPositionHandling.IGNORE);

        assertThat(indicator.getValue(5)).isTrue();
        assertThat(indicator.getValue(6)).isTrue();
        assertThat(indicator.getValue(7)).isTrue();
        assertThat(indicator.getValue(8)).isFalse();
    }

    private static final class AggregatePositionTradingRecord extends BaseTradingRecord {
        private final List<Position> positions;

        private AggregatePositionTradingRecord(Position position) {
            this.positions = List.of(position);
        }

        @Override
        public List<Position> getPositions() {
            return positions;
        }

        @Override
        public Position getCurrentPosition() {
            return positions.getFirst();
        }

        @Override
        public List<Position> getOpenPositions() {
            return List.of();
        }
    }
}
