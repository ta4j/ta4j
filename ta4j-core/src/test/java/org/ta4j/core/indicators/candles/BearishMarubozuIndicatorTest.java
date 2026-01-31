/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BearishMarubozuIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public BearishMarubozuIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void detectsBearishMarubozuWithDefaultThresholds() {
        final var series = buildSeriesWithLastBar(15.5, 12.0, 15.6, 11.9);
        final var indicator = new BearishMarubozuIndicator(series);

        assertThat(indicator.getValue(5)).as("bearish marubozu detected").isTrue();
    }

    @Test
    public void doesNotTriggerForBullishCandle() {
        final var series = buildSeriesWithLastBar(12.0, 15.5, 15.6, 11.9);
        final var indicator = new BearishMarubozuIndicator(series);

        assertThat(indicator.getValue(5)).as("bullish candle is not bearish marubozu").isFalse();
    }

    @Test
    public void doesNotTriggerWhenBodyIsNotLongEnough() {
        final var series = buildSeriesWithLastBar(12.3, 12.0, 12.35, 11.95);
        final var indicator = new BearishMarubozuIndicator(series);

        assertThat(indicator.getValue(5)).as("body must be longer than average").isFalse();
    }

    @Test
    public void respectsCustomBodyRatio() {
        final var series = buildSeriesWithLastBar(15.5, 12.0, 15.6, 11.9);
        final var indicator = new BearishMarubozuIndicator(series, 5, 2d, 0.05d, 0.05d);

        assertThat(indicator.getValue(5)).as("body comfortably exceeds custom ratio").isTrue();
    }

    private BarSeries buildSeriesWithLastBar(final double openPrice, final double closePrice, final double highPrice,
            final double lowPrice) {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            final double base = 10d + i;
            series.barBuilder().openPrice(base).closePrice(base + 0.4d).highPrice(base + 0.4d).lowPrice(base).add();
        }
        series.barBuilder().openPrice(openPrice).closePrice(closePrice).highPrice(highPrice).lowPrice(lowPrice).add();
        return series;
    }
}
