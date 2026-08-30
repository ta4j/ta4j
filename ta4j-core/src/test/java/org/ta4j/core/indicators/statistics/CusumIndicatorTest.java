/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CusumIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;
    private MockIndicator source;
    private CusumIndicator cusum;

    public CusumIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        source = new MockIndicator(data, 0, numOf(0.010), numOf(0.010), numOf(-0.100));
        cusum = new CusumIndicator(source, 0, 0.005, 3.0, 0.5);
    }

    @Test
    public void winsorizedRecursionMatchesFormula() {
        // mu0 = 0, k = 0.005, clipFactor = 3, scaleDecay = 0.5:
        // S = [0, 0, 0.045] with the -0.100 increment clipped to 3 * 0.015.
        assertNumEquals(0, cusum.getValue(0));
        assertNumEquals(0, cusum.getValue(1));
        assertNumEquals(0.045, cusum.getValue(2));
    }

    @Test
    public void nonFiniteInputCarriesPreviousValue() {
        MockIndicator gapped = new MockIndicator(data, 0, numOf(0.010), NaN.NaN, numOf(-0.100));
        CusumIndicator gappedCusum = new CusumIndicator(gapped, 0, 0.005);

        // The gap carries the previous CUSUM value and deviation scale forward.
        assertNumEquals(0, gappedCusum.getValue(0));
        assertNumEquals(0, gappedCusum.getValue(1));
        assertNumEquals(0.045, gappedCusum.getValue(2));
    }

    @Test
    public void firstOutlierAfterOnTargetRunIsFullyDamped() {
        // All raw increments so far were exactly zero, so the deviation scale is
        // zero when the first outlier arrives: the winsorization bound is zero
        // and the outlier is fully damped while bootstrapping the scale.
        MockIndicator bootstrap = new MockIndicator(data, 0, numOf(0), numOf(-100), numOf(-100));
        CusumIndicator bootstrapCusum = new CusumIndicator(bootstrap, 0, 0, 3.0, 0.5);

        assertNumEquals(0, bootstrapCusum.getValue(0));
        assertNumEquals(0, bootstrapCusum.getValue(1));
        assertNumEquals(100, bootstrapCusum.getValue(2));
    }

    @Test
    public void propagatesSourceUnstableBars() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        MockIndicator unstable = new MockIndicator(series, 2, numOf(0.010), numOf(0.010), numOf(0.010), numOf(0.010));
        CusumIndicator unstableCusum = new CusumIndicator(unstable, 0, 0.005);

        assertEquals(2, unstableCusum.getCountOfUnstableBars());
        assertTrue(unstableCusum.getValue(0).isNaN());
        assertTrue(unstableCusum.getValue(1).isNaN());
        assertFalse(unstableCusum.getValue(2).isNaN());
    }

    @Test
    public void killSwitchFlipsWhenCusumCrossesControlLimit() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        MockIndicator drift = new MockIndicator(series, 0, numOf(0.01), numOf(0.01), numOf(0.01), numOf(-0.02),
                numOf(-0.02), numOf(-0.02));
        CusumIndicator driftCusum = new CusumIndicator(drift, 0, 0, 3.0, 0.5);
        FixedIndicator<Num> limit = new FixedIndicator<>(series, numOf(0.05), numOf(0.05), numOf(0.05), numOf(0.05),
                numOf(0.05), numOf(0.05));
        Rule killSwitch = NumericIndicator.of(driftCusum).isLessThan(limit);
        TradingRecord record = new BaseTradingRecord();

        // S = [0, 0, 0, 0.02, 0.04, 0.06]: the switch opens only once S >= 0.05.
        for (int i = 0; i < 5; i++) {
            assertTrue(killSwitch.isSatisfied(i, record));
        }
        assertFalse(killSwitch.isSatisfied(5, record));
    }

    @Test
    public void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new CusumIndicator(source, 0, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new CusumIndicator(source, 0, 0.005, 0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CusumIndicator(source, 0, 0.005, 3.0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new CusumIndicator(source, Double.NaN, 0.005));
        assertThrows(IllegalArgumentException.class, () -> new CusumIndicator(source, Double.POSITIVE_INFINITY, 0.005));
        assertThrows(NullPointerException.class, () -> new CusumIndicator(null, 0, 0.005));
    }

    @Test
    public void saturatesOverflowingDeviationAndAccumulator() {
        // Deviations and accumulations that overflow the numeric representation
        // (a DoubleNum series jumping between opposite extremes) must saturate
        // at the largest finite magnitude instead of leaking infinity.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(-Double.MAX_VALUE, Double.MAX_VALUE / 2)
                .build();
        CusumIndicator extreme = new CusumIndicator(
                new MockIndicator(series, 0, numOf(-Double.MAX_VALUE), numOf(Double.MAX_VALUE / 2)), Double.MAX_VALUE,
                0, 3.0, 0.5);

        assertTrue(Num.isFinite(extreme.getValue(0)));
        assertTrue(extreme.getValue(0).isGreaterThanOrEqual(numOf(Double.MAX_VALUE)));
        assertTrue(Num.isFinite(extreme.getValue(1)));
        assertTrue(extreme.getValue(1).isGreaterThanOrEqual(numOf(Double.MAX_VALUE)));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        return List.of(serializationFixture(series, new CusumIndicator(close, 0, 0.005), stableIndexes(series)));
    }
}
