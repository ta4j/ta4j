/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.Trade;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OpenPositionUnrealizedProfitCriterionTest extends AbstractCriterionTest {

    public OpenPositionUnrealizedProfitCriterionTest(NumFactory numFactory) {
        super(params -> new OpenPositionUnrealizedProfitCriterion(), numFactory);
    }

    @Test
    public void includesPositionClosedAfterLogicalEnd() {
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("unrealized-end", numFactory, 1, 10d, 20d,
                30d);
        BaseTradingRecord record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));

        assertNumEquals(10d, getCriterion().calculate(series, record));
    }

    @Test
    public void calculateForBaseTradingRecordLong() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.two(),
                numFactory.numOf(0.5), ExecutionSide.BUY, null, null));

        Num expected = numFactory.numOf(110)
                .multipliedBy(numFactory.two())
                .minus(numFactory.hundred().multipliedBy(numFactory.two()))
                .minus(numFactory.numOf(0.5));

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateForBaseTradingRecordShort() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90).build();
        var record = new BaseTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.numOf(0.2), ExecutionSide.SELL, null, null));

        Num expected = numFactory.hundred().minus(numFactory.numOf(90)).minus(numFactory.numOf(0.2));

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateForStandardRecordOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120).build();
        var record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());

        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        Num expected = numFactory.numOf(120).minus(numFactory.hundred());

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void returnsZeroWhenNoOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120).build();
        var record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());

        var result = getCriterion().calculate(series, record);

        assertNumEquals(numFactory.zero(), result);
    }

    @Test
    public void betterThanPrefersHigherProfit() {
        var criterion = getCriterion();

        assertTrue(criterion.betterThan(numFactory.two(), numFactory.one()));
        assertFalse(criterion.betterThan(numFactory.one(), numFactory.two()));
    }

    @Test
    public void matchesFreshSeriesAcrossWindowShapesAndPositionBoundaries() {
        var criterion = new OpenPositionUnrealizedProfitCriterion();
        for (ConstrainedSeriesSupport.CriterionWindowFixture fixture : ConstrainedSeriesSupport
                .criterionWindowFixtures(numFactory)) {
            Num actual = criterion.calculate(fixture.series(), fixture.tradingRecord());
            Num expected = criterion.calculate(fixture.equivalentSeries(), fixture.equivalentRecord());
            assertEquals(fixture.name(), expected.doubleValue(), actual.doubleValue(), 1e-10);
            if (fixture.position() != null && fixture.equivalentPosition() != null) {
                Num actualPosition = criterion.calculate(fixture.series(), fixture.position());
                Num expectedPosition = criterion.calculate(fixture.equivalentSeries(), fixture.equivalentPosition());
                assertNumEquals(expectedPosition, actualPosition, 1e-10);
            }
        }
    }

    @Test
    public void sumsEveryClosedLotStillOpenAtLogicalEnd() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord(false);

        assertEquals(2, record.getPositions().size());
        assertTrue(record.getOpenPositions().isEmpty());
        assertNumEquals(numFactory.numOf(30), getCriterion().calculate(series, record));
    }

    @Test
    public void includesHistoricalLotsWhenCurrentLotStartsAfterLogicalEnd() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord(true);

        assertEquals(2, record.getPositions().size());
        assertEquals(1, record.getOpenPositions().size());
        assertEquals(7, record.getCurrentPosition().getEntry().getIndex());
        assertNumEquals(numFactory.numOf(30), getCriterion().calculate(series, record));
    }

    private BarSeries multiLotSeries() {
        return ConstrainedSeriesSupport.trailingConstrainedSeries("unrealized-multi-lot", numFactory, 5, 100d, 110d,
                110d, 110d, 110d, 120d, 120d, 120d, 130d, 130d, 130d, 130d);
    }

    private BaseTradingRecord multiLotRecord(boolean addLaterOpenLot) {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(new BaseTrade(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), numFactory.zero(),
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(1, Instant.EPOCH.plusSeconds(1), numFactory.numOf(110), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        if (addLaterOpenLot) {
            record.operate(new BaseTrade(7, Instant.EPOCH.plusSeconds(7), numFactory.numOf(120), numFactory.one(),
                    numFactory.zero(), ExecutionSide.BUY, null, null));
        }
        record.operate(new BaseTrade(10, Instant.EPOCH.plusSeconds(10), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, null, null));
        record.operate(new BaseTrade(11, Instant.EPOCH.plusSeconds(11), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, null, null));
        return record;
    }
}
