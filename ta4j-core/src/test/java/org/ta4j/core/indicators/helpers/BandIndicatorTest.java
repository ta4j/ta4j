/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BandIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    /**
     * Creates a new BandIndicatorTest instance.
     */
    public BandIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    /**
     * Initializes the test fixtures used by these scenarios.
     */
    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(10).openPrice(10).highPrice(10).lowPrice(10).volume(1).add();
        series.barBuilder().closePrice(12).openPrice(12).highPrice(12).lowPrice(12).volume(1).add();
    }

    /**
     * Verifies that compute upper and lower bands.
     */
    @Test
    public void shouldComputeUpperAndLowerBands() {
        var middle = new ClosePriceIndicator(series);
        var width = new ConstantIndicator<>(series, numFactory.numOf("0.5"));

        var upper = new BandIndicator(middle, width, 2, BandIndicator.BandType.UPPER);
        var lower = new BandIndicator(middle, width, 2, BandIndicator.BandType.LOWER);

        assertNumEquals(11.0, upper.getValue(0));
        assertNumEquals(9.0, lower.getValue(0));
        assertNumEquals(13.0, upper.getValue(1));
        assertNumEquals(11.0, lower.getValue(1));
    }

    /**
     * Verifies that propagate na nfrom inputs.
     */
    @Test
    public void shouldPropagateNaNFromInputs() {
        var middle = new ClosePriceIndicator(series);
        var width = new ConstantIndicator<>(series, NaN.NaN);
        var upper = new BandIndicator(middle, width, 2, BandIndicator.BandType.UPPER);

        assertThat(upper.getValue(0).isNaN()).isTrue();
    }

    /**
     * Verifies that round trip serialize and deserialize.
     */
    @Test
    public void shouldRoundTripSerializeAndDeserialize() {
        var middle = new ClosePriceIndicator(series);
        var width = new ConstantIndicator<>(series, numFactory.numOf(1));
        var upper = new BandIndicator(middle, width, 2, BandIndicator.BandType.UPPER);

        String json = upper.toJson();
        Indicator<?> restored = Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(BandIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(upper.toDescriptor());
        @SuppressWarnings("unchecked")
        Indicator<Num> restoredIndicator = (Indicator<Num>) restored;
        assertThat(restoredIndicator.getValue(1)).isEqualByComparingTo(upper.getValue(1));
    }
}
