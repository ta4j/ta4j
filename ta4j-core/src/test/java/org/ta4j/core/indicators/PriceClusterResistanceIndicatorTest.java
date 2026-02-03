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
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.supportresistance.PriceClusterResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.PriceClusterSupportIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PriceClusterResistanceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public PriceClusterResistanceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void supportAndResistanceShouldChooseDifferentClustersWhenFrequenciesTie() {
        BarSeries series = buildSeries(new double[] { 10, 10.1, 10.2, 15, 15.1, 15.2 },
                new double[] { 40, 40, 40, 40, 40, 40 });

        var support = new PriceClusterSupportIndicator(series, 0, numOf(0.3));
        var resistance = new PriceClusterResistanceIndicator(series, 0, numOf(0.3));

        assertThat(support.getClusterIndex(5)).as("support favours lower cluster").isEqualTo(2);

        assertThat(resistance.getClusterIndex(5)).as("resistance favours higher cluster").isEqualTo(5);
        assertThat(support.getValue(5)).isLessThan(resistance.getValue(5));
    }

    @Test
    public void shouldFavorHeavierHighClusterForResistance() {
        BarSeries series = buildSeries(new double[] { 10, 10, 15 }, new double[] { 20, 20, 200 });

        var resistance = new PriceClusterResistanceIndicator(series, 0, numOf(0.5));

        assertThat(resistance.getValue(2)).as("resistance favours heavier upper cluster")
                .isEqualByComparingTo(numOf(15));
        assertThat(resistance.getClusterIndex(2)).isEqualTo(2);
    }

    private BarSeries buildSeries(double[] closes, double[] volumes) {
        var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        BarSeries barSeries = builder.build();
        for (int i = 0; i < closes.length; i++) {
            barSeries.barBuilder()
                    .closePrice(closes[i])
                    .openPrice(closes[i])
                    .highPrice(closes[i])
                    .lowPrice(closes[i])
                    .volume(volumes[i])
                    .add();
        }
        return barSeries;
    }
}
