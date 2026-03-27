/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Tests for {@link IndicatorUtils}.
 */
public class IndicatorUtilsTest {

    @Test
    public void requireSameSeriesReturnsSharedInstance() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        Indicator<Num> highPrice = new HighPriceIndicator(series);
        Indicator<Num> lowPrice = new LowPriceIndicator(series);

        BarSeries resolved = IndicatorUtils.requireSameSeries(closePrice, highPrice, lowPrice);

        assertSame(series, resolved);
    }

    @Test
    public void requireSameSeriesRejectsMismatchedSeries() {
        BarSeries firstSeries = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        BarSeries secondSeries = new MockBarSeriesBuilder().withData(1, 2, 3).build();

        assertThrows(IllegalArgumentException.class, () -> IndicatorUtils
                .requireSameSeries(new ClosePriceIndicator(firstSeries), new HighPriceIndicator(secondSeries)));
    }

    @Test
    public void requireSameSeriesRejectsNullIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();

        assertThrows(NullPointerException.class,
                () -> IndicatorUtils.requireSameSeries(null, new ClosePriceIndicator(series)));
    }

    @Test
    public void requireSameSeriesRejectsIndicatorWithoutSeries() {
        Indicator<Num> withoutSeries = new Indicator<>() {
            @Override
            public Num getValue(int index) {
                return null;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public BarSeries getBarSeries() {
                return null;
            }
        };
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();

        assertThrows(NullPointerException.class,
                () -> IndicatorUtils.requireSameSeries(withoutSeries, new ClosePriceIndicator(series)));
    }

    @Test
    public void requireSameSeriesRejectsSecondIndicatorWithoutSeries() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 11, 12).build();
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        Indicator<Num> withoutSeries = new SerieslessNumIndicator();

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> IndicatorUtils.requireSameSeries(closePrice, withoutSeries));

        assertTrue(exception.getMessage().contains("secondIndicator must reference a bar series"));
    }

    @Test
    public void requireIndicatorReturnsIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        Indicator<Num> closePrice = new ClosePriceIndicator(series);

        Indicator<Num> resolved = IndicatorUtils.requireIndicator(closePrice, "closePrice indicator");

        assertSame(closePrice, resolved);
    }

    @Test
    public void requireIndicatorRejectsNullIndicator() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> IndicatorUtils.requireIndicator(null, "closePrice indicator"));

        assertTrue(exception.getMessage().contains("closePrice indicator must not be null"));
    }

    @Test
    public void isInvalidHandlesNullAndNaN() {
        Num nan = DoubleNumFactory.getInstance().numOf(Double.NaN);
        Num valid = DoubleNumFactory.getInstance().one();

        assertTrue(IndicatorUtils.isInvalid(null));
        assertTrue(IndicatorUtils.isInvalid(nan));
        assertFalse(IndicatorUtils.isInvalid(valid));
    }

    private static final class SerieslessNumIndicator implements Indicator<Num> {

        @Override
        public Num getValue(int index) {
            return null;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return null;
        }
    }
}
