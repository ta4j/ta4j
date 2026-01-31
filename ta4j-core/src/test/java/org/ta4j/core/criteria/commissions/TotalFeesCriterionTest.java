/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.commissions;

import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionFill;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TotalFeesCriterionTest extends AbstractCriterionTest {

    public TotalFeesCriterionTest(NumFactory numFactory) {
        super(params -> new TotalFeesCriterion(), numFactory);
    }

    @Test
    public void calculateUsesLiveTradingRecordFees() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new ExecutionFill(Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.recordFill(new ExecutionFill(Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.numOf(0.2), ExecutionSide.SELL, null, null));

        var result = getCriterion().calculate(series, record);

        assertNumEquals(numFactory.numOf(0.3), result);
    }

    @Test
    public void calculateUsesTransactionCostModelForStandardRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120, 130).build();
        var costModel = new FixedTransactionCostModel(1.0);
        var record = new BaseTradingRecord(TradeType.BUY, costModel, new ZeroCostModel());
        Num amount = numFactory.one();

        record.enter(0, series.getBar(0).getClosePrice(), amount);
        record.exit(1, series.getBar(1).getClosePrice(), amount);
        record.enter(2, series.getBar(2).getClosePrice(), amount);

        Num closedFees = record.getPositions().stream().map(costModel::calculate).reduce(numFactory.zero(), Num::plus);
        Num openFees = costModel.calculate(record.getCurrentPosition(), record.getEndIndex(series));
        Num expected = closedFees.plus(openFees);

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void betterThanPrefersLowerFees() {
        var criterion = getCriterion();

        assertTrue(criterion.betterThan(numFactory.one(), numFactory.two()));
        assertFalse(criterion.betterThan(numFactory.two(), numFactory.one()));
    }
}
