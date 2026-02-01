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
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OpenPositionCostBasisCriterionTest extends AbstractCriterionTest {

    public OpenPositionCostBasisCriterionTest(NumFactory numFactory) {
        super(params -> new OpenPositionCostBasisCriterion(), numFactory);
    }

    @Test
    public void calculateUsesLiveTradingRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.numOf(0.2), ExecutionSide.BUY, null, null));

        Num expected = numFactory.hundred()
                .plus(numFactory.numOf(110))
                .plus(numFactory.numOf(0.1))
                .plus(numFactory.numOf(0.2));
        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateUsesCurrentPositionForStandardRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var costModel = new FixedTransactionCostModel(1.5);
        var record = new BaseTradingRecord(TradeType.BUY, costModel, new ZeroCostModel());

        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        Num expected = series.getBar(0)
                .getClosePrice()
                .multipliedBy(numFactory.one())
                .plus(costModel.calculate(series.getBar(0).getClosePrice(), numFactory.one()));

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void returnsZeroWhenNoOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());

        var result = getCriterion().calculate(series, record);

        assertNumEquals(numFactory.zero(), result);
    }

    @Test
    public void betterThanPrefersLowerCostBasis() {
        var criterion = getCriterion();

        assertTrue(criterion.betterThan(numFactory.one(), numFactory.two()));
        assertFalse(criterion.betterThan(numFactory.two(), numFactory.one()));
    }
}
