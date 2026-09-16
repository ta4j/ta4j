/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class InvestedIntervalTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public InvestedIntervalTest(NumFactory numFactory) {
        super(numFactory);
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
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110, 120, 130, 140, 150, 160);
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

        assertThat(indicator.getValue(3)).isTrue();
        assertThat(indicator.getValue(4)).isTrue();
        assertThat(indicator.getValue(5)).isTrue();
        assertThat(indicator.getValue(6)).isFalse();
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
