/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.alwaysInvested;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.buildDailySeries;

import java.time.Instant;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OmegaRatioCriterionTest extends AbstractCriterionTest {

    public OmegaRatioCriterionTest(NumFactory numFactory) {
        super(params -> new OmegaRatioCriterion((double) params[0]), numFactory);
    }

    @Test
    public void calculatesExpectedValueForMixedReturnsTradingRecord() {
        double[] closes = new double[] { 100d, 120d, 90d, 99d };
        BarSeries series = buildSeries("omega_mixed", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceOmega(returnsFromCloses(closes), 0d);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void calculatesExpectedValueForCustomThreshold() {
        double threshold = 0.05d;
        double[] closes = new double[] { 100d, 120d, 90d, 99d };
        BarSeries series = buildSeries("omega_threshold", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        OmegaRatioCriterion criterion = new OmegaRatioCriterion(threshold);
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceOmega(returnsFromCloses(closes), threshold);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void returnsNaNWhenTradingRecordHasOnlyUpsideReturns() {
        BarSeries series = buildSeries("omega_positive_only", new double[] { 100d, 110d, 121d });
        TradingRecord tradingRecord = alwaysInvested(series);

        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);
        Num actual = criterion.calculate(series, tradingRecord);

        assertTrue(actual.isNaN());
    }

    @Test
    public void returnsZeroWhenTradingRecordHasOnlyDownsideReturns() {
        BarSeries series = buildSeries("omega_negative_only", new double[] { 100d, 90d, 81d });
        TradingRecord tradingRecord = alwaysInvested(series);

        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);
        Num actual = criterion.calculate(series, tradingRecord);

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void returnsZeroWhenTradingRecordHasNoPositions() {
        BarSeries series = buildSeries("omega_no_positions", new double[] { 100d, 120d, 90d, 99d });
        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);

        Num actual = criterion.calculate(series, new BaseTradingRecord());

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void returnsZeroWhenThereAreNoReturnObservations() {
        BarSeries series = buildSeries("omega_one_bar", new double[] { 100d });
        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);

        Num actual = criterion.calculate(series, new BaseTradingRecord());

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void returnsZeroWhenTradingRecordIsNull() {
        BarSeries series = buildSeries("omega_null_record", new double[] { 100d, 110d });
        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);

        Num actual = criterion.calculate(series, (TradingRecord) null);

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void betterThanUsesHigherValuesAsBetter() {
        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);

        assertTrue(criterion.betterThan(numFactory.one(), numFactory.zero()));
        assertFalse(criterion.betterThan(numFactory.zero(), numFactory.one()));
    }

    private BarSeries buildSeries(String name, double[] closes) {
        return buildDailySeries(getBarSeries(name), closes, Instant.parse("2024-01-01T00:00:00Z"));
    }

    private double[] returnsFromCloses(double[] closes) {
        double[] returns = new double[Math.max(closes.length - 1, 0)];
        for (int i = 1; i < closes.length; i++) {
            returns[i - 1] = (closes[i] / closes[i - 1]) - 1d;
        }
        return returns;
    }

    private double referenceOmega(double[] returns, double threshold) {
        double upsideExcess = 0d;
        double downsideShortfall = 0d;

        for (double value : returns) {
            double excess = value - threshold;
            if (excess > 0d) {
                upsideExcess += excess;
            } else if (excess < 0d) {
                downsideShortfall += -excess;
            }
        }

        if (downsideShortfall == 0d) {
            return upsideExcess == 0d ? 0d : Double.NaN;
        }
        return upsideExcess / downsideShortfall;
    }
}
