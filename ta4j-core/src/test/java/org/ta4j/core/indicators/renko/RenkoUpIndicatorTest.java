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

public class RenkoUpIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public RenkoUpIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void signalsAfterRequiredNumberOfUpBricks() {
        var series = buildSeries(100d, 100.4d, 100.9d, 101.6d);
        var indicator = new RenkoUpIndicator(new ClosePriceIndicator(series), 0.5d, 2);

        assertThat(indicator.getValue(2)).as("needs at least two bricks").isFalse();
        assertThat(indicator.getValue(3)).as("two bricks formed by index 3").isTrue();
    }

    @Test
    public void resetsAfterBearishBrick() {
        var series = buildSeries(100d, 100.9d, 101.6d, 100.9d, 102.1d);
        var indicator = new RenkoUpIndicator(new ClosePriceIndicator(series), 0.5d, 2);

        assertThat(indicator.getValue(2)).as("uptrend established").isTrue();
        assertThat(indicator.getValue(3)).as("bearish move resets bricks").isFalse();
        assertThat(indicator.getValue(4)).as("new bricks accumulate again").isTrue();
    }

    @Test
    public void countsBrickWhenPriceHitsBoundaryExactly() {
        var series = buildSeries(100d, 100.5d);
        var indicator = new RenkoUpIndicator(new ClosePriceIndicator(series), 0.5d);

        assertThat(indicator.getValue(1)).as("exact boundary move forms a brick").isTrue();
    }

    @Test
    public void updatesFormingBarAfterIntrabarPriceChange() {
        var series = buildSeries(100d, 100.2d);
        var indicator = new RenkoUpIndicator(new ClosePriceIndicator(series), 0.5d);

        var currentIndex = series.getEndIndex();
        assertThat(indicator.getValue(currentIndex)).as("initial move insufficient for brick").isFalse();

        series.getLastBar().addPrice(numFactory.numOf(100.6d));

        assertThat(indicator.getValue(currentIndex)).as("intrabar update forms brick").isTrue();
    }

    @Test
    public void accumulatesMultipleBricksFromSingleBar() {
        var series = buildSeries(100d, 101.6d);
        var indicator = new RenkoUpIndicator(new ClosePriceIndicator(series), 0.5d, 3);

        assertThat(indicator.getValue(1)).as("large move forms three bricks").isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositivePointSize() {
        new RenkoUpIndicator(new ClosePriceIndicator(buildSeries(100d, 100.5d)), 0d, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveBrickCount() {
        new RenkoUpIndicator(new ClosePriceIndicator(buildSeries(100d, 100.5d)), 0.5d, 0);
    }

    private BarSeries buildSeries(double... closes) {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).add();
        }
        return series;
    }
}
