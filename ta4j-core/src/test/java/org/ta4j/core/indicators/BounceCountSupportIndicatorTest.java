/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.supportresistance.BounceCountSupportIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BounceCountSupportIndicatorTest extends AbstractIndicatorTest<BounceCountSupportIndicator, Num> {

    public BounceCountSupportIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldPreferLowerSupportWhenBounceCountsTie() {
        BarSeries series = buildSeries(7.0, 6.0, 7.0, 5.0, 7.0);
        var indicator = new BounceCountSupportIndicator(series, numOf(0));

        assertThat(indicator.getValue(4)).as("ties should favour lower support bucket").isEqualByComparingTo(numOf(5));
        assertThat(indicator.getBounceIndex(4)).isEqualTo(3);
    }

    @Test
    public void shouldGroupBouncesWithinBucketSize() {
        BarSeries series = buildSeries(10.0, 10.6, 10.9, 10.4, 10.8, 10.2, 10.6);
        var indicator = new BounceCountSupportIndicator(series, numOf(0.5));

        assertThat(indicator.getValue(6).doubleValue()).as("support average within bucket")
                .isCloseTo(10.3, within(1e-6));
        assertThat(indicator.getBounceIndex(6)).isEqualTo(5);
    }

    @Test
    public void shouldPreventBucketDriftFromAbsorbingDistantBounces() {
        BarSeries series = buildSeries(12.0, 10.0, 12.0, 11.0, 12.0, 11.5, 12.0, 11.8, 12.0);
        var indicator = new BounceCountSupportIndicator(series, numOf(1.0));

        assertThat(indicator.getValue(8)).isEqualByComparingTo(numOf(10.5));
        assertThat(indicator.getBounceIndex(8)).isEqualTo(3);
    }

    @Test
    public void shouldCountBounceWhenWindowStartsAfterTrend() {
        BarSeries series = buildSeries(7.0, 6.0, 5.0, 6.0);
        var indicator = new BounceCountSupportIndicator(new ClosePriceIndicator(series), 2, numOf(0));

        assertThat(indicator.getValue(3)).as("bounce within truncated window").isEqualByComparingTo(numOf(5));
        assertThat(indicator.getBounceIndex(3)).isEqualTo(2);
    }

    @Test
    public void shouldIgnoreBouncesOutsideWindow() {
        BarSeries series = buildSeries(7.0, 6.0, 7.0, 8.0);
        var indicator = new BounceCountSupportIndicator(new ClosePriceIndicator(series), 2, numOf(0.1));

        assertThat(indicator.getValue(3)).isEqualByComparingTo(NaN);
        assertThat(indicator.getBounceIndex(3)).isEqualTo(-1);
    }

    @Test
    public void shouldReturnNaNWhenNoBounceExists() {
        BarSeries series = buildSeries(1.0, 2.0, 3.0, 4.0);
        var indicator = new BounceCountSupportIndicator(series, numOf(0.1));

        assertThat(indicator.getValue(3)).isEqualByComparingTo(NaN);
        assertThat(indicator.getBounceIndex(3)).isEqualTo(-1);
    }

    @Test
    public void shouldRoundTripSerializeAndDeserialize() {
        BarSeries series = buildSeries(7.0, 6.0, 7.0, 5.0, 7.0);
        var indicator = new BounceCountSupportIndicator(series, numOf(0.1));

        String json = indicator.toJson();
        Indicator<?> restored = Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(BounceCountSupportIndicator.class);
        var restoredIndicator = (BounceCountSupportIndicator) restored;
        assertThat(restoredIndicator.toDescriptor()).isEqualTo(indicator.toDescriptor());
        int index = series.getEndIndex();
        assertThat(restoredIndicator.getValue(index)).isEqualByComparingTo(indicator.getValue(index));
        assertThat(restoredIndicator.getBounceIndex(index)).isEqualTo(indicator.getBounceIndex(index));
    }

    @Test
    public void unstableBarsIncludeInputWarmupAndLookback() {
        BarSeries series = buildSeries(3.0, 2.0, 3.0, 2.0, 3.0, 2.0, 3.0);
        MockIndicator price = new MockIndicator(series, 2, numOf(3), numOf(2), numOf(3), numOf(2), numOf(3), numOf(2),
                numOf(3));
        BounceCountSupportIndicator indicator = new BounceCountSupportIndicator(price, 3, numOf(0.1));

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(indicator.getValue(3).isNaN()).isTrue();
        assertThat(indicator.getValue(4).isNaN()).isFalse();
    }

    private BarSeries buildSeries(double... closes) {
        MockBarSeriesBuilder builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        BarSeries series = builder.build();
        for (double close : closes) {
            series.barBuilder().closePrice(close).openPrice(close).highPrice(close).lowPrice(close).volume(1).add();
        }
        return series;
    }
}
