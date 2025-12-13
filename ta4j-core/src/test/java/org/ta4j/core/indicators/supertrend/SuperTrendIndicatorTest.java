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
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SuperTrendIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public SuperTrendIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void superTrendRecoversWhenUpperBandStartsAsNaN() {
        BarSeries series = buildSeries();
        SuperTrendIndicator indicator = new SuperTrendIndicator(series, 2, 1d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(2);
        assertThat(indicator.getValue(0).isZero()).isTrue();
        assertThat(indicator.getValue(1).isZero()).isTrue();

        assertNumEquals(11.5, indicator.getValue(2));
        assertNumEquals(12.5, indicator.getValue(3));
    }

    @Test
    public void handlesExtremeSmallMultiplier() {
        BarSeries series = buildSeries();
        SuperTrendIndicator indicator = new SuperTrendIndicator(series, 2, 0.01d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(2);
        assertThat(indicator.getValue(0).isZero()).isTrue();
        assertThat(indicator.getValue(1).isZero()).isTrue();

        // After unstable period, should produce valid values
        Num value2 = indicator.getValue(2);
        assertThat(Num.isNaNOrNull(value2)).isFalse();
        assertThat(value2.isZero() || value2.isPositive()).isTrue();
    }

    @Test
    public void handlesExtremeLargeMultiplier() {
        BarSeries series = buildSeries();
        SuperTrendIndicator indicator = new SuperTrendIndicator(series, 2, 100d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(2);
        assertThat(indicator.getValue(0).isZero()).isTrue();
        assertThat(indicator.getValue(1).isZero()).isTrue();

        // After unstable period, should produce valid values
        Num value2 = indicator.getValue(2);
        assertThat(Num.isNaNOrNull(value2)).isFalse();
    }

    @Test
    public void maintainsZeroDuringUnstablePeriod() {
        BarSeries series = buildSeries();
        SuperTrendIndicator indicator = new SuperTrendIndicator(series, 3, 1d);

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
        SuperTrendIndicator indicator = new SuperTrendIndicator(series, 2, 1d);

        // Verify zero during unstable period
        assertThat(indicator.getValue(0).isZero()).isTrue();
        assertThat(indicator.getValue(1).isZero()).isTrue();

        // Verify recovery after unstable period
        Num value2 = indicator.getValue(2);
        assertThat(Num.isNaNOrNull(value2)).isFalse();
        assertThat(value2.isZero() || value2.isPositive()).isTrue();

        // Verify continued stability
        Num value3 = indicator.getValue(3);
        assertThat(Num.isNaNOrNull(value3)).isFalse();
        assertThat(value3.isZero() || value3.isPositive()).isTrue();
    }

    @Test
    public void handlesDifferentBarCounts() {
        BarSeries series = buildSeries();

        // Test with barCount = 1 (minimal unstable period)
        SuperTrendIndicator indicator1 = new SuperTrendIndicator(series, 1, 1d);
        assertThat(indicator1.getCountOfUnstableBars()).isEqualTo(1);
        assertThat(indicator1.getValue(0).isZero()).isTrue();
        Num value1 = indicator1.getValue(1);
        assertThat(Num.isNaNOrNull(value1)).isFalse();

        // Test with barCount = 4 (longer unstable period)
        SuperTrendIndicator indicator4 = new SuperTrendIndicator(series, 4, 1d);
        assertThat(indicator4.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(indicator4.getValue(0).isZero()).isTrue();
        assertThat(indicator4.getValue(1).isZero()).isTrue();
        assertThat(indicator4.getValue(2).isZero()).isTrue();
        assertThat(indicator4.getValue(3).isZero()).isTrue();
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
