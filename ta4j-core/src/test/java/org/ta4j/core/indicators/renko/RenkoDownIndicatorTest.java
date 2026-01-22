/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.renko;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RenkoDownIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public RenkoDownIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void signalsAfterRequiredNumberOfDownBricks() {
        var series = buildSeries(100d, 100.2d, 99.4d, 98.7d);
        var indicator = new RenkoDownIndicator(new ClosePriceIndicator(series), 0.5d, 2);

        assertThat(indicator.getValue(2)).as("needs at least two bricks").isFalse();
        assertThat(indicator.getValue(3)).as("two bricks formed by index 3").isTrue();
    }

    @Test
    public void resetsAfterBullishBrick() {
        var series = buildSeries(100d, 99.4d, 98.7d, 99.6d, 98.0d);
        var indicator = new RenkoDownIndicator(new ClosePriceIndicator(series), 0.5d, 2);

        assertThat(indicator.getValue(2)).as("downtrend established").isTrue();
        assertThat(indicator.getValue(3)).as("bullish move resets bricks").isFalse();
        assertThat(indicator.getValue(4)).as("new bricks accumulate again").isTrue();
    }

    @Test
    public void countsBrickWhenPriceHitsBoundaryExactly() {
        var series = buildSeries(100d, 99.5d);
        var indicator = new RenkoDownIndicator(new ClosePriceIndicator(series), 0.5d);

        assertThat(indicator.getValue(1)).as("exact boundary move forms a brick").isTrue();
    }

    @Test
    public void accumulatesMultipleBricksFromSingleBar() {
        var series = buildSeries(100d, 98.2d);
        var indicator = new RenkoDownIndicator(new ClosePriceIndicator(series), 0.5d, 3);

        assertThat(indicator.getValue(1)).as("large move forms three bricks").isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositivePointSize() {
        new RenkoDownIndicator(new ClosePriceIndicator(buildSeries(100d, 99.4d)), 0d, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveBrickCount() {
        new RenkoDownIndicator(new ClosePriceIndicator(buildSeries(100d, 99.4d)), 0.5d, 0);
    }

    private BarSeries buildSeries(double... closes) {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).add();
        }
        return series;
    }
}
