/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.supertrend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SuperTrendLowerBandIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public SuperTrendLowerBandIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void respectsAtrUnstablePeriodAndRecovers() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 1d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(atrIndicator.getCountOfUnstableBars());
        assertThat(indicator.getValue(0).isZero()).isTrue();
        assertThat(indicator.getValue(1).isZero()).isTrue();

        assertNumEquals(11.5, indicator.getValue(2));
        assertNumEquals(12.5, indicator.getValue(3));
    }

    @Test
    public void handlesExtremeSmallMultiplier() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 0.01d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(atrIndicator.getCountOfUnstableBars());
        assertThat(indicator.getValue(0).isZero()).isTrue();
        assertThat(indicator.getValue(1).isZero()).isTrue();

        // With very small multiplier, band should be close to median price
        Num value2 = indicator.getValue(2);
        assertThat(Num.isNaNOrNull(value2)).isFalse();
        assertThat(value2.isPositive()).isTrue();
    }

    @Test
    public void handlesExtremeLargeMultiplier() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 100d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(atrIndicator.getCountOfUnstableBars());
        assertThat(indicator.getValue(0).isZero()).isTrue();
        assertThat(indicator.getValue(1).isZero()).isTrue();

        // With very large multiplier, band should be much lower than median price
        // (could even be negative, but should not be NaN)
        Num value2 = indicator.getValue(2);
        assertThat(Num.isNaNOrNull(value2)).isFalse();
    }

    @Test
    public void maintainsZeroDuringUnstablePeriod() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 3);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 1d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(3);
        // All unstable period values should be zero
        assertThat(indicator.getValue(0).isZero()).isTrue();
        assertThat(indicator.getValue(1).isZero()).isTrue();
        assertThat(indicator.getValue(2).isZero()).isTrue();

        // After unstable period, should recover
        Num value3 = indicator.getValue(3);
        assertThat(Num.isNaNOrNull(value3)).isFalse();
    }

    @Test
    public void recoversGracefullyAfterNaNPeriod() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 1d);

        // Verify zero during unstable period
        assertThat(indicator.getValue(0).isZero()).isTrue();
        assertThat(indicator.getValue(1).isZero()).isTrue();

        // Verify recovery after unstable period
        Num value2 = indicator.getValue(2);
        assertThat(Num.isNaNOrNull(value2)).isFalse();
        assertThat(value2.isPositive()).isTrue();

        // Verify continued stability
        Num value3 = indicator.getValue(3);
        assertThat(Num.isNaNOrNull(value3)).isFalse();
        assertThat(value3.isPositive()).isTrue();
    }

    private BarSeries buildSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10).closePrice(11).highPrice(12).lowPrice(10).add();
        series.barBuilder().openPrice(12).closePrice(13).highPrice(14).lowPrice(11).add();
        series.barBuilder().openPrice(14).closePrice(15).highPrice(16).lowPrice(13).add();
        series.barBuilder().openPrice(16).closePrice(16).highPrice(18).lowPrice(14).add();
        return series;
    }
}
