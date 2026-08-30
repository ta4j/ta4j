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
import org.ta4j.core.num.Num;
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
    public void scoresArePinnedToMultiplicativeReturns() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 104.5, 114.95, 100, 95)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));
        AnalysisCriterion cpk = getCriterion(0.9, 1.15);
        Num expected = cpk.calculate(series, tradingRecord);

        ReturnRepresentation original = ReturnRepresentationPolicy.getDefaultRepresentation();
        try {
            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.DECIMAL);
            assertNumEquals(expected, cpk.calculate(series, tradingRecord));
        } finally {
            ReturnRepresentationPolicy.setDefaultRepresentation(original);
        }
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
    public void extremeGrossReturnsScoreZero() {
        // Both gross returns equal MAX / 1e-300: DoubleNum overflows the
        // return, the mean becomes non-finite and the criterion scores zero;
        // DecimalNum stays finite with zero variance (also zero).
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1e-300, Double.MAX_VALUE, 1e-300, Double.MAX_VALUE)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(0.9, 1.15);
        assertNumEquals(0, cpk.calculate(series, tradingRecord));
    }

    @Test
    public void wideFiniteDispersionDoesNotOverflowSquaring() {
        // Gross returns 1e200 and 2e200 with limits 0 and 3e200: population
        // sigma is 5e199 and Cpk is 1, but each naive squared deviation
        // overflows DoubleNum; the normalized computation must not collapse.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1e200, 2, 4e200).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(0, 3e200);
        assertNumEquals(1, cpk.calculate(series, tradingRecord));
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
    public void meanOverflowDoesNotCollapseCapability() {
        // Gross returns 1e308 and 1.4e308 with limits 0 and 1.7e308: Cpk is
        // 5/6, but the naive mean sum overflows DoubleNum to infinity; the
        // normalized mean recomputation must preserve the score.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1e308, 1, 1.4e308).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(0, 1.7e308);
        assertNumEquals(numOf(5).dividedBy(numOf(6)), cpk.calculate(series, tradingRecord), 1e-9);
    }

    @Test
    public void threeSigmaOverflowKeepsFiniteCapability() {
        // Gross returns 1e307 and 1.7e308 with limits 0 and 1.79e308: mean
        // 9e307 and sigma 8e307 are finite, but 3 * sigma overflows DoubleNum;
        // the overflow-safe per-ratio scaling must keep Cpk at 89/240 instead
        // of collapsing both ratios to zero.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1e307, 1, 1.7e308).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(0, 1.79e308);
        assertNumEquals(numOf(89).dividedBy(numOf(240)), cpk.calculate(series, tradingRecord), 1e-6);
    }

    @Test
    public void subUnitSigmaKeepsFiniteCapability() {
        // Gross returns 0.5 and 1.5 with a one-sided lower limit of
        // -Double.MAX_VALUE: mean 1 and sigma 0.5 push the numerator to MAX,
        // so dividing by sigma first overflows even though the final Cpk
        // (MAX / 1.5) is representable; dividing by three first keeps it
        // finite.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 0.5, 1, 1.5).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(-Double.MAX_VALUE);
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        Num expected = numFactory.numOf(Double.MAX_VALUE)
                .dividedBy(numFactory.numOf(3))
                .dividedBy(numFactory.numOf(0.5));
        assertNumEquals(expected, capability, expected.doubleValue() * 1e-9);
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
