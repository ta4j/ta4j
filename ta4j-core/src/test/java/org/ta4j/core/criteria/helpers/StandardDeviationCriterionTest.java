/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class StandardDeviationCriterionTest extends AbstractCriterionTest {

    public StandardDeviationCriterionTest(NumFactory numFactory) {
        super(params -> params.length == 2
                ? new StandardDeviationCriterion((AnalysisCriterion) params[0], (boolean) params[1])
                : new StandardDeviationCriterion((AnalysisCriterion) params[0]), numFactory);
    }

    @Test
    public void calculateStandardDeviationPnL() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series, series.numFactory().one()),
                Trade.sellAt(2, series, series.numFactory().one()), Trade.buyAt(3, series, series.numFactory().one()),
                Trade.sellAt(5, series, series.numFactory().one()));

        AnalysisCriterion criterion = getCriterion(new NetProfitLossCriterion());
        assertNumEquals(2.5, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void betterThanWithLessIsBetter() {
        AnalysisCriterion criterion = getCriterion(new NetProfitLossCriterion(), true);
        assertFalse(criterion.betterThan(numOf(5000), numOf(4500)));
        assertTrue(criterion.betterThan(numOf(4500), numOf(5000)));
    }

    @Test
    public void betterThanWithLessIsNotBetter() {
        AnalysisCriterion criterion = getCriterion(new NetProfitLossCriterion());
        assertTrue(criterion.betterThan(numOf(5000), numOf(4500)));
        assertFalse(criterion.betterThan(numOf(4500), numOf(5000)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory,
                getCriterion(new NetProfitLossCriterion()), 0);
    }

}
