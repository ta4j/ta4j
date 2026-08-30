/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class ProcessCapabilityCriterionTest extends AbstractCriterionTest {

    public ProcessCapabilityCriterionTest(NumFactory numFactory) {
        super(params -> {
            if (params.length == 1) {
                return new ProcessCapabilityCriterion((Number) params[0]);
            }
            return new ProcessCapabilityCriterion((Number) params[0], (Number) params[1]);
        }, numFactory);
    }

    @Test
    public void calculatesCpkOverClosedPositions() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 104.5, 114.95, 100, 95)
                .build();
        // Gross returns 1.10, 1.10, 0.95: mu = 1.05, sigma^2 = 0.005.
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));

        AnalysisCriterion cpk = getCriterion(0.9, 1.15);
        assertNumEquals(0.1 / (3 * Math.sqrt(0.005)), cpk.calculate(series, tradingRecord));

        AnalysisCriterion oneSided = getCriterion(0.9);
        assertNumEquals(0.15 / (3 * Math.sqrt(0.005)), oneSided.calculate(series, tradingRecord));
    }

    @Test
    public void returnsZeroWhenGrossReturnsHaveNoVariance() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 100)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));

        AnalysisCriterion cpk = getCriterion(0.9, 1.15);
        assertNumEquals(0, cpk.calculate(series, tradingRecord));
    }

    @Test
    public void returnsZeroWithoutClosedPositions() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121, 133.1).build();

        AnalysisCriterion cpk = getCriterion(0.9, 1.15);
        assertNumEquals(0, cpk.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void higherCpkIsBetter() {
        AnalysisCriterion cpk = getCriterion(0.9, 1.15);

        assertTrue(cpk.betterThan(numOf(1.5), numOf(1.0)));
    }

    @Test
    public void rejectsInvalidSpecificationLimits() {
        assertThrows(NullPointerException.class, () -> new ProcessCapabilityCriterion(null));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCapabilityCriterion(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCapabilityCriterion(1.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCapabilityCriterion(1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCapabilityCriterion(1.0, 0.9));
    }
}
