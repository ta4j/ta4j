/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.statistics.SinglePrecisionNumFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.num.DecimalNumFactory;
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
        ProcessCapabilityCriterion cpk = new ProcessCapabilityCriterion(0.9, 1.15);
        assertEquals(ReturnRepresentation.MULTIPLICATIVE, cpk.getReturnRepresentation().orElseThrow());
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
        // decimal-space mean recovery must preserve the score.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1e308, 1, 1.4e308).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(0, 1.7e308);
        assertNumEquals(numOf(5).dividedBy(numOf(6)), cpk.calculate(series, tradingRecord), 1e-9);
    }

    @Test
    public void adjacentMaximumReturnsRecoverOverflowingMeanInDecimal() {
        // The two finite long returns are adjacent doubles whose sum overflows.
        // Averaging after normalizing each return by MAX rounds both normalized
        // values to a mean of 1 and yields the wrong Cpk (about 4.25e15). For two
        // observations, population sigma is half their absolute difference, so
        // (x1 + x2) / (3 * |x1 - x2|) independently gives approximately 6.00e15.
        double upperReturn = Double.MAX_VALUE;
        double lowerReturn = Math.nextDown(upperReturn);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, upperReturn, 1, lowerReturn)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        Num capability = getCriterion(0).calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        if (numFactory instanceof DoubleNumFactory) {
            BigDecimal upper = BigDecimal.valueOf(upperReturn);
            BigDecimal lower = BigDecimal.valueOf(lowerReturn);
            BigDecimal expected = upper.add(lower)
                    .divide(upper.subtract(lower).abs().multiply(BigDecimal.valueOf(3)), MathContext.DECIMAL128);
            double tolerance = expected.doubleValue() * 1e-12;
            assertEquals(expected.doubleValue(), capability.doubleValue(), tolerance);
            assertTrue(capability.isGreaterThan(numOf(5.9e15)));
            assertTrue(capability.isLessThan(numOf(6.1e15)));
        } else {
            // Default DecimalNum precision narrows both input doubles to the same
            // value before criterion evaluation, so its honest result remains the
            // zero-dispersion convention rather than entering mean recovery.
            assertTrue(capability.isZero());
        }
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
    public void limitDistanceOverflowKeepsFiniteCapability() {
        // Gross returns 5e307 and 1.5e308 with a one-sided lower limit of
        // -1e308: mean 1e308 and sigma 5e307 make mean - lsl overflow to
        // +Infinity even though the true Cpk is 4/3; the scale-aware
        // per-ratio form must keep the capability finite.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 5e307, 1, 1.5e308).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(-1e308);
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        assertNumEquals(numFactory.numOf(4).dividedBy(numFactory.numOf(3)), capability, 1e-9);
    }

    @Test
    public void limitOverflowKeepsFiniteCapability() {
        // Gross returns 1e307 and 1.7e308 with a one-sided lower limit of
        // -1e400: DoubleNum converts the limit itself to -Infinity before any
        // subtraction, collapsing the finite Cpk (about 4.17e91) to
        // +Infinity; the raw limit must be scaled against the deviation scale
        // before narrowing.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1e307, 1, 1.7e308).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(new BigDecimal("-1e400"));
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        assertTrue(capability.isGreaterThan(numOf(4e91)));
        assertTrue(capability.isLessThan(numOf(4.2e91)));
    }

    @Test
    public void twoSidedLimitOverflowKeepsFiniteCapability() {
        // With both limits outside the double range, the upper side is the
        // binding one and both must be normalized against the deviation scale
        // before narrowing: Cpk = (1e400 - 9e307) / (1.7e308 * 3 * sigma)
        // is about 4.17e91, not infinity.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1e307, 1, 1.7e308).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(new BigDecimal("-1e400"), new BigDecimal("1e400"));
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        assertTrue(capability.isGreaterThan(numOf(4e91)));
        assertTrue(capability.isLessThan(numOf(4.2e91)));
    }

    @Test
    public void unrepresentableLimitScalesThroughFullDenominator() {
        // Gross returns 1 and -1 with a lower limit of -2e308: the limit
        // overflows DoubleNum, and scaling it by the deviation scale alone
        // still overflows even though the true Cpk (2e308 / 3, about
        // 6.67e307) is representable; the raw limit must be divided by the
        // complete 3 * sigma denominator before narrowing.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 1, 2).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(3, series));

        AnalysisCriterion cpk = getCriterion(new BigDecimal("-2e308"));
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        assertTrue(capability.isGreaterThan(numOf(6.6e307)));
        assertTrue(capability.isLessThan(numOf(6.7e307)));
    }

    @Test
    public void subUnitScaleDistanceOverflowFallsBackToProductDenominator() {
        // Gross returns 0.4 and -0.4 with a lower limit of 1.7e308: the
        // finite distance divides to -4.25e308 through the sub-unit deviation
        // scale alone (overflow) even though the full Cpk (-1.7e308 / 1.2)
        // is representable; the fallback must divide once by the scale * 3 *
        // scaledSigma product.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1.4, 0.5, 0.7).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(3, series));

        AnalysisCriterion cpk = getCriterion(1.7e308);
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        assertTrue(capability.isLessThan(numOf(-1.4e308)));
        assertTrue(capability.isGreaterThan(numOf(-1.5e308)));
    }

    @Test
    public void hugeOpposingReturnsKeepFiniteCapability() {
        // Three long positions earning MAX and one short position losing
        // (2 - MAX) give a mean near MAX / 2 and a population sigma of
        // sqrt(3) / 2 * MAX. Subtracting the mean from the losing return
        // overflows DoubleNum (|2 - MAX - MAX / 2| = 1.5 * MAX), so the
        // deviation pass must scale before subtracting; the true Cpk with
        // limits 0 and MAX is 1 / (3 * sqrt(3)).
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, Double.MAX_VALUE, 1, Double.MAX_VALUE, 1, Double.MAX_VALUE, 1, Double.MAX_VALUE)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series),
                Trade.sellAt(6, series), Trade.buyAt(7, series));

        AnalysisCriterion cpk = getCriterion(0, Double.MAX_VALUE);
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        assertNumEquals(numFactory.numOf(1).dividedBy(numFactory.numOf(3).multipliedBy(numFactory.numOf(Math.sqrt(3)))),
                capability, 1e-9);
    }

    @Test
    public void closeSpecificationDistanceKeepsPositiveCapability() {
        // Three gross returns at 3.0452032506296296e261 and one at the USL
        // 3.04520325062963e261: the two differ by only one double ulp
        // (4.37e245), so dividing each operand by three before subtracting
        // rounds both to the same value and collapses the upper capability to
        // zero even though the distance is positive and representable.
        // Same-sign operands must be subtracted before scaling. DoubleNum
        // keeps the one-ulp distance and must score a positive capability;
        // DecimalNum's default 16-digit precision rounds both prices to the
        // same value, so its honest score is the zero-dispersion zero.
        double mean = 3.0452032506296296e261;
        double usl = 3.04520325062963e261;
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, mean, 1, mean, 1, mean, 1, usl)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series),
                Trade.buyAt(6, series), Trade.sellAt(7, series));

        AnalysisCriterion cpk = getCriterion(0, usl);
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        if (numFactory instanceof DoubleNumFactory) {
            assertTrue(capability.isPositive());
        } else {
            assertTrue(capability.isZero());
        }
    }

    @Test
    public void subnormalDispersionKeepsFinitePositiveCapability() {
        // Four MIN_VALUE gross returns and one 2 * MIN_VALUE return: the mean
        // (1.2 * MIN_VALUE) and deviation scale are subnormal, so the raw
        // population sigma underflows to zero under DoubleNum when
        // materialized and any ratio through it becomes NaN. The normalized
        // domain must keep the capability finite and positive; the exact
        // value differs by factory (DoubleNum rounds the mean down to
        // MIN_VALUE, DecimalNum keeps 1.2 * MIN_VALUE), so only finiteness
        // and a sensible positive bound are asserted.
        double min = Double.MIN_VALUE;
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, min, 1, min, 1, min, 1, min, 1, 2 * min)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series),
                Trade.buyAt(6, series), Trade.sellAt(7, series), Trade.buyAt(8, series), Trade.sellAt(9, series));

        AnalysisCriterion cpk = getCriterion(0);
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        assertTrue(capability.isPositive());
        assertTrue(capability.isLessThanOrEqual(numFactory.numOf(10)));
    }

    @Test
    public void grossReturnRatioOverflowKeepsRepresentableCapability() {
        // Entry 1e-300 and exit 1e300 / 2e300 produce gross returns 1e600 and
        // 2e600: both ratios overflow DoubleNum to infinity, but the mean
        // (1.5e600), dispersion (0.5e600) and one-sided Cpk against LSL 0
        // (1) are all representable. The decimal-space recovery from the raw
        // prices must preserve the score instead of zeroing it. DecimalNum
        // keeps the ratios finite, so its honest score is the same 1.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1e-300, 1e300, 1e-300, 2e300)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(0);
        assertNumEquals(1, cpk.calculate(series, tradingRecord));
    }

    @Test
    public void mixedDirectionOverflowingRatiosPreserveCancellationResidual() {
        // The exact multiplicative returns are 1e309 and 2 - 1e309. Their
        // mean is 1 and sigma is approximately 1e309, so lower-only Cpk is
        // approximately 1 / 3e309. Decimal recovery must retain the fixed
        // short-return base through cancellation before narrowing to DoubleNum.
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory)
                .withData(0.1, 1e308, 0.1, 1e308)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(3, series));

        Num capability = new ProcessCapabilityCriterion(0).calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        assertTrue(capability.isPositive());
        assertNumEquals("3.333333333333333333333333333333333E-310", capability);
    }

    @Test
    public void grossReturnRatioUnderflowKeepsRepresentableCapability() {
        // Entry 1e300 and exits 1e-308 / 2e-308 produce long gross returns
        // 1e-608 and 2e-608. DoubleNum rounds both ratios to zero, but their
        // one-sided Cpk against LSL 0 remains 1. DecimalNum keeps the ratios
        // finite and yields the same score.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1e300, 1e-308, 1e300, 2e-308)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(0);
        assertNumEquals(1, cpk.calculate(series, tradingRecord));
    }

    @Test
    public void shortGrossReturnRatioUnderflowKeepsRepresentableCapability() {
        // Short entries 1e300 covered at 1e-308 / 2e-308 produce returns
        // 2 - 1e-608 and 2 - 2e-608. DoubleNum rounds both to 2, but their
        // one-sided Cpk against USL 2 remains 1. DecimalNum preserves the tiny
        // ratios and yields the same score.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1e300, 1e-308, 1e300, 2e-308)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(3, series));

        AnalysisCriterion cpk = getCriterion(0, 2);
        assertNumEquals(1, cpk.calculate(series, tradingRecord));
    }

    @Test
    public void shortGrossReturnRatioOverflowKeepsRepresentableCapability() {
        // Short entries at 1e-300 covered at 1e300 / 2e300 produce
        // multiplicative returns 2 - 1e600 and 2 - 2e600. Both overflow
        // DoubleNum, while the one-sided Cpk against LSL -3e600 remains 1.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1e-300, 1e300, 1e-300, 2e300)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(3, series));

        AnalysisCriterion cpk = getCriterion(new BigDecimal("-3e600"));
        assertNumEquals(1, cpk.calculate(series, tradingRecord));
    }

    @Test
    public void floatGrossReturnRatioOverflowKeepsRepresentableCapability() {
        NumFactory floatFactory = SinglePrecisionNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatFactory)
                .withData(1e-30, 1e30, 1e-30, 2e30)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        Num capability = new ProcessCapabilityCriterion(0).calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        assertNumEquals(1, capability);
    }

    @Test
    public void rejectsInvalidSpecificationLimits() {
        assertThrows(NullPointerException.class, () -> new ProcessCapabilityCriterion(null));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCapabilityCriterion(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCapabilityCriterion(1.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCapabilityCriterion(1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCapabilityCriterion(1.0, 0.9));
    }

    @Test
    public void limitJustBeyondDoubleRangeKeepsPositiveCapability() {
        // Gross returns Double.MAX_VALUE and the next double below it with
        // limits 0 and 1.79769313486231581e308: the USL overflows DoubleNum
        // by about one ulp of the return scale, and dividing the limit and
        // the mean by the same deviation scale rounds both quotients to the
        // same double, collapsing the positive capability to zero; the raw
        // decimal mean-to-limit distance must be narrowed once against the
        // complete 3-sigma denominator instead. DecimalNum rounds both
        // returns to the same 16-digit value, so its honest score is the
        // zero-dispersion zero.
        double max = Double.MAX_VALUE;
        BigDecimal usl = new BigDecimal("1.79769313486231581E308");
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, max, 1, Math.nextDown(max))
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(0, usl);
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        if (numFactory instanceof DoubleNumFactory) {
            assertTrue(capability.isPositive());
            // The true Cpk is about 0.26 (a unit-of-one-ulp distance over a
            // unit-of-one-ulp dispersion); bound it loosely to catch wildly
            // wrong decimal-space narrowing without replicating internals.
            assertTrue(capability.isGreaterThan(numOf(0.05)));
            assertTrue(capability.isLessThan(numOf(2)));
        } else {
            assertNumEquals(0, capability);
        }
    }

    @Test
    public void meanSummationIsOrderStableAcrossRecords() {
        // Gross returns 1e16, 1 and -1e16: a naive left-to-right sum absorbs
        // the unit return (1e16 + 1 = 1e16) and cancels to zero, collapsing
        // the capability against a one-sided LSL of 0 to zero; the
        // compensated summation must recover the unit return and keep the
        // score positive and identical regardless of the trade order.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 1e16, 1, 1, 1, -1e16)
                .build();
        TradingRecord ascending = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));
        TradingRecord descending = new BaseTradingRecord(Trade.buyAt(4, series), Trade.sellAt(5, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(0, series), Trade.sellAt(1, series));

        AnalysisCriterion cpk = getCriterion(0);
        Num ascendingCapability = cpk.calculate(series, ascending);
        Num descendingCapability = cpk.calculate(series, descending);

        assertTrue(ascendingCapability.isPositive());
        assertNumEquals(ascendingCapability, descendingCapability);
    }

    @Test
    public void scaledVarianceSummationIsOrderStableAcrossRecords() {
        // Precision-2 DecimalNum retains the ten 0.01 squared deviations
        // only when they precede the two unit deviations under naive
        // accumulation. Both records represent the same returns, so Cpk must
        // be identical regardless of their position order.
        NumFactory precisionTwo = DecimalNumFactory.getInstance(2);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(precisionTwo)
                .withData(1, 10, 1, -10, 1, 1, 1, -1, 1, 1, 1, -1, 1, 1, 1, -1, 1, 1, 1, -1, 1, 1, 1, -1)
                .build();
        TradingRecord largeFirst = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series),
                Trade.buyAt(6, series), Trade.sellAt(7, series), Trade.buyAt(8, series), Trade.sellAt(9, series),
                Trade.buyAt(10, series), Trade.sellAt(11, series), Trade.buyAt(12, series), Trade.sellAt(13, series),
                Trade.buyAt(14, series), Trade.sellAt(15, series), Trade.buyAt(16, series), Trade.sellAt(17, series),
                Trade.buyAt(18, series), Trade.sellAt(19, series), Trade.buyAt(20, series), Trade.sellAt(21, series),
                Trade.buyAt(22, series), Trade.sellAt(23, series));
        TradingRecord smallFirst = new BaseTradingRecord(Trade.buyAt(4, series), Trade.sellAt(5, series),
                Trade.buyAt(6, series), Trade.sellAt(7, series), Trade.buyAt(8, series), Trade.sellAt(9, series),
                Trade.buyAt(10, series), Trade.sellAt(11, series), Trade.buyAt(12, series), Trade.sellAt(13, series),
                Trade.buyAt(14, series), Trade.sellAt(15, series), Trade.buyAt(16, series), Trade.sellAt(17, series),
                Trade.buyAt(18, series), Trade.sellAt(19, series), Trade.buyAt(20, series), Trade.sellAt(21, series),
                Trade.buyAt(22, series), Trade.sellAt(23, series), Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = new ProcessCapabilityCriterion(-1);

        Num largeFirstCapability = cpk.calculate(series, largeFirst);
        Num smallFirstCapability = cpk.calculate(series, smallFirst);

        assertNumEquals(largeFirstCapability, smallFirstCapability);
    }

    @Test
    public void coarseFactoryRoundingKeepsPositiveSpecificationGap() {
        // A precision-2 DecimalNum factory rounds the retained LSL 0.999
        // onto the mean 1.0, collapsing the positive capability (about
        // 0.0033) to zero: the raw limit must feed the distance before the
        // factory narrows it. The two-sided case exercises the USL mirror.
        NumFactory precisionTwo = DecimalNumFactory.getInstance(2);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(precisionTwo).withData(1, 0.9, 1, 1.1).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion lowerOnly = getCriterion(new BigDecimal("0.999"));
        Num lowerCapability = lowerOnly.calculate(series, tradingRecord);
        assertTrue(Num.isFinite(lowerCapability));
        assertTrue(lowerCapability.isPositive());
        assertTrue(lowerCapability.isLessThan(precisionTwo.numOf(0.1)));
        assertTrue(lowerCapability.isGreaterThan(precisionTwo.numOf(0.001)));

        AnalysisCriterion twoSided = getCriterion(0.9, new BigDecimal("1.001"));
        Num twoSidedCapability = twoSided.calculate(series, tradingRecord);
        assertTrue(Num.isFinite(twoSidedCapability));
        assertTrue(twoSidedCapability.isPositive());
        assertTrue(twoSidedCapability.isLessThan(precisionTwo.numOf(0.1)));
    }

    @Test
    public void nonzeroRoundedLimitDistanceKeepsRawCapability() {
        // A precision-2 DecimalNum factory rounds the retained LSL 0.944 to
        // 0.94 without reaching the mean 1.0: the rounded distance 0.06
        // scores Cpk about 0.20 while the raw 0.056 distance scores about
        // 0.19. The finite nonzero branch must recover the raw distance too,
        // not only the zero-gap branch. The USL 1.056 mirrors the case.
        NumFactory precisionTwo = DecimalNumFactory.getInstance(2);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(precisionTwo).withData(1, 0.9, 1, 1.1).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion lowerOnly = getCriterion(new BigDecimal("0.944"));
        Num lowerCapability = lowerOnly.calculate(series, tradingRecord);
        assertNumEquals(0.19, lowerCapability);

        AnalysisCriterion twoSided = getCriterion(0.9, new BigDecimal("1.056"));
        Num twoSidedCapability = twoSided.calculate(series, tradingRecord);
        assertNumEquals(0.19, twoSidedCapability);
    }

    @Test
    public void rawLimitRecoveryRetainsDecimalFactoryPrecision() {
        MathContext context = new MathContext(50, RoundingMode.HALF_UP);
        NumFactory precisionFifty = DecimalNumFactory.getInstance(context);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(precisionFifty).withData(1, 0.9, 1, 1.1).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        Num lowerCapability = new ProcessCapabilityCriterion(
                new BigDecimal("0.944444444444444444444444444444444444444444444444444444"))
                .calculate(series, tradingRecord);
        assertTrue(Num.isFinite(lowerCapability));
        assertEquals(context.getPrecision(), lowerCapability.bigDecimalValue().precision());

        Num upperCapability = new ProcessCapabilityCriterion(new BigDecimal("0.9"),
                new BigDecimal("1.055555555555555555555555555555555555555555555555555555"))
                .calculate(series, tradingRecord);
        assertTrue(Num.isFinite(upperCapability));
        assertEquals(context.getPrecision(), upperCapability.bigDecimalValue().precision());
    }

    @Test
    public void oversizedDecimalMeanWithRoundedLimitKeepsPositiveCapability() {
        // A DecimalNum factory preserves means beyond the double range:
        // gross returns 1e400 and 1.2e400 have mean 1.1e400, and a retained
        // LSL written just below that mean rounds onto it at the factory
        // precision. The zero-gap recovery must stay in decimal space; the
        // former doubleValue() conversion produced Infinity and
        // BigDecimal.valueOf(Infinity) threw instead of returning Cpk.
        NumFactory large = DecimalNumFactory.getInstance();
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(large).build();
        series.addBar(new MockBarBuilder(large).closePrice(large.one()).build());
        series.addBar(new MockBarBuilder(large).closePrice(large.numOf(new BigDecimal("1e400"))).build());
        series.addBar(new MockBarBuilder(large).closePrice(large.one()).build());
        series.addBar(new MockBarBuilder(large).closePrice(large.numOf(new BigDecimal("1.2e400"))).build());
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        AnalysisCriterion cpk = getCriterion(new BigDecimal("1.09999999999999999e400"));
        Num capability = cpk.calculate(series, tradingRecord);

        assertTrue(Num.isFinite(capability));
        assertTrue(capability.isPositive());
        assertTrue(capability.isLessThan(large.numOf(1e-10)));
        assertTrue(capability.isGreaterThan(large.numOf(1e-30)));
    }
}
