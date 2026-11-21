/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.renko;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.NaN;
import org.ta4j.core.indicators.CachedIndicator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RenkoXIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public RenkoXIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void detectsThreeBricksInEitherDirection() {
        var series = buildSeries(100d, 100.6d, 101.2d, 101.8d, 101.2d, 100.6d, 100.0d, 99.4d);
        var indicator = new RenkoXIndicator(new ClosePriceIndicator(series), 0.5d);

        assertThat(indicator.getValue(3)).as("three bullish bricks trigger signal").isTrue();
        assertThat(indicator.getValue(6)).as("three bearish bricks trigger signal").isTrue();
        assertThat(indicator.getValue(4)).as("lull between moves keeps signal active").isTrue();
    }

    @Test
    public void respectsCustomThreshold() {
        var series = buildSeries(100d, 100.6d, 101.2d, 101.8d, 102.4d);
        var indicator = new RenkoXIndicator(new ClosePriceIndicator(series), 0.5d, 4);

        assertThat(indicator.getValue(3)).as("needs four bricks").isFalse();
        assertThat(indicator.getValue(4)).as("four bricks reached").isTrue();
    }

    @Test
    public void countsBricksAtExactBoundaries() {
        var series = buildSeries(100d, 100.5d, 101.0d, 101.5d);
        var indicator = new RenkoXIndicator(new ClosePriceIndicator(series), 0.5d, 3);

        assertThat(indicator.getValue(3)).as("three boundary bricks trigger signal").isTrue();
    }

    @Test
    public void detectsMultipleBricksFromSingleMove() {
        var series = buildSeries(100d, 101.6d, 99.7d);
        var indicator = new RenkoXIndicator(new ClosePriceIndicator(series), 0.5d, 3);

        assertThat(indicator.getValue(1)).as("large bullish move forms bricks").isTrue();
        assertThat(indicator.getValue(2)).as("subsequent bearish move forms bricks").isTrue();
    }

    @Test
    public void rejectsNonPositivePointSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RenkoXIndicator(new ClosePriceIndicator(buildSeries(100d, 100.6d)), 0d, 3);
        });
    }

    @Test
    public void rejectsNonPositiveBrickCount() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RenkoXIndicator(new ClosePriceIndicator(buildSeries(100d, 100.6d)), 0.5d, 0);
        });
    }

    @Test
    public void transientPriceIsNotCommittedToTheBrickState() {
        BarSeries series = buildSeries(1d, 2d, 3d);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        AtomicBoolean sourceFailing = new AtomicBoolean(true);
        CachedIndicator<Num> flaky = new CachedIndicator<>(series) {
            @Override
            protected Num calculate(int index) {
                if (index == 1 && sourceFailing.get()) {
                    markResultTransient();
                    return NaN.NaN;
                }
                return close.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        var indicator = new RenkoXIndicator(flaky, 1d, 1);

        assertThat(indicator.getValue(1)).isFalse();
        sourceFailing.set(false);

        assertThat(indicator.getValue(1)).isTrue();
    }

    private BarSeries buildSeries(double... closes) {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).add();
        }
        return series;
    }
}
