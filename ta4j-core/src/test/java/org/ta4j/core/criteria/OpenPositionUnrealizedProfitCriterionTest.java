/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.LiveTrade;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.LiveTradingRecord;
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
    public void calculateForLiveTradingRecordLong() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.two(), numFactory.numOf(0.5), ExecutionSide.BUY, null, null));

        Num expected = numFactory.numOf(110)
                .multipliedBy(numFactory.two())
                .minus(numFactory.hundred().multipliedBy(numFactory.two()))
                .minus(numFactory.numOf(0.5));

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateForLiveTradingRecordShort() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90).build();
        var record = new LiveTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.2), ExecutionSide.SELL, null, null));

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
}
