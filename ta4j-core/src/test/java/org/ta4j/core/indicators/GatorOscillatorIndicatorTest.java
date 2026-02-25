/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class GatorOscillatorIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public GatorOscillatorIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 1; i <= 60; i++) {
            final double high = i + ((i % 3 == 0) ? 2 : 1);
            final double low = i - ((i % 4 == 0) ? 2 : 1);
            series.barBuilder().openPrice(i).closePrice(i).highPrice(high).lowPrice(low).volume(20 + i).add();
        }
    }

    @Test
    public void shouldComputeUpperAndLowerHistogramValues() {
        final var median = new MedianPriceIndicator(series);
        final var jaw = new AlligatorIndicator(median, 5, 2);
        final var teeth = new AlligatorIndicator(median, 3, 1);
        final var lips = new AlligatorIndicator(median, 2, 0);
        final var upper = GatorOscillatorIndicator.upper(jaw, teeth, lips);
        final var lower = GatorOscillatorIndicator.lower(jaw, teeth, lips);

        final int expectedUnstableBars = Math.max(
                Math.max(jaw.getCountOfUnstableBars(), teeth.getCountOfUnstableBars()), lips.getCountOfUnstableBars());
        assertThat(upper.getCountOfUnstableBars()).isEqualTo(expectedUnstableBars);
        assertThat(lower.getCountOfUnstableBars()).isEqualTo(expectedUnstableBars);
        assertThat(upper.isUpperHistogram()).isTrue();
        assertThat(lower.isUpperHistogram()).isFalse();

        assertThat(upper.getValue(expectedUnstableBars - 1).isNaN()).isTrue();
        assertThat(lower.getValue(expectedUnstableBars - 1).isNaN()).isTrue();

        for (int i = expectedUnstableBars; i <= series.getEndIndex(); i++) {
            final Num expectedUpper = jaw.getValue(i).minus(teeth.getValue(i)).abs();
            final Num expectedLower = teeth.getValue(i)
                    .minus(lips.getValue(i))
                    .abs()
                    .multipliedBy(numFactory.minusOne());
            assertThat(upper.getValue(i)).isEqualByComparingTo(expectedUpper);
            assertThat(lower.getValue(i)).isEqualByComparingTo(expectedLower);
        }
    }

    @Test
    public void shouldReturnZeroForFlatPriceSeriesAfterWarmup() {
        final BarSeries flatSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 70; i++) {
            flatSeries.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(10).add();
        }

        final var upper = GatorOscillatorIndicator.upper(flatSeries);
        final var lower = GatorOscillatorIndicator.lower(flatSeries);
        final int unstableBars = upper.getCountOfUnstableBars();

        for (int i = unstableBars; i <= flatSeries.getEndIndex(); i++) {
            assertThat(upper.getValue(i)).isEqualByComparingTo(numFactory.zero());
            final Num lowerValue = lower.getValue(i);
            assertThat(lowerValue).isEqualByComparingTo(numFactory.zero());
            assertThat(Double.doubleToRawLongBits(lowerValue.doubleValue()))
                    .isNotEqualTo(Double.doubleToRawLongBits(-0.0d));
        }
    }

    @Test
    public void shouldRejectNullIndicators() {
        final var jaw = new AlligatorIndicator(series, 5, 2);
        final var teeth = new AlligatorIndicator(series, 3, 1);
        final var lips = new AlligatorIndicator(series, 2, 0);

        assertThrows(IllegalArgumentException.class, () -> new GatorOscillatorIndicator(null, teeth, lips, true));
        assertThrows(IllegalArgumentException.class, () -> new GatorOscillatorIndicator(jaw, null, lips, true));
        assertThrows(IllegalArgumentException.class, () -> new GatorOscillatorIndicator(jaw, teeth, null, true));
    }

    @Test
    public void shouldRejectDifferentBarSeries() {
        final BarSeries anotherSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        anotherSeries.barBuilder().openPrice(1).closePrice(1).highPrice(2).lowPrice(0).volume(1).add();

        final var jaw = new AlligatorIndicator(series, 5, 2);
        final var teeth = new AlligatorIndicator(series, 3, 1);
        final var lipsDifferentSeries = new AlligatorIndicator(anotherSeries, 2, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new GatorOscillatorIndicator(jaw, teeth, lipsDifferentSeries, true));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTrip() {
        final var original = new GatorOscillatorIndicator(series, true);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final String json = original.toJson();
        final Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(GatorOscillatorIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final Num expected = original.getValue(i);
            final Num actual = restored.getValue(i);
            if (expected.isNaN()) {
                assertThat(actual.isNaN()).isTrue();
            } else {
                assertThat(actual).isEqualByComparingTo(expected);
            }
        }
    }
}
