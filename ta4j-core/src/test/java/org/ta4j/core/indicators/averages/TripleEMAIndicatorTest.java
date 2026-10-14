/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.BarSeries;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TripleEMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ClosePriceIndicator closePrice;

    public TripleEMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        var data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0.73, 0.72, 0.86, 0.72, 0.62, 0.76, 0.84, 0.69, 0.65, 0.71, 0.53, 0.73, 0.77, 0.67, 0.68,
                        0.69, 0.7, 0.72, 0.74, 0.71)
                .build();
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void cachedHistoricalValuesReanchorAfterPruning() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        double[] prices = { 10, 12, 11, 15, 13, 16, 12, 17, 14, 18 };
        for (int i = 0; i < prices.length; i++) {
            double price = prices[i];
            series.barBuilder()
                    .openPrice(price)
                    .highPrice(price + i % 3 + 1)
                    .lowPrice(price - i % 2 - 1)
                    .closePrice(price)
                    .volume(100 + i)
                    .add();
        }
        series.setMaximumBarCount(10);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Indicator<Num> reused = new TripleEMAIndicator(close, 2);
        for (int i = 0; i < series.getEndIndex(); i++) {
            reused.getValue(i);
        }
        series.barBuilder().openPrice(9).highPrice(11).lowPrice(8).closePrice(9).volume(110).add();
        Indicator<Num> fresh = new TripleEMAIndicator(close, 2);
        for (int i = series.getBeginIndex(); i < series.getEndIndex(); i++) {
            assertNumEquals(fresh.getValue(i), reused.getValue(i));
        }
    }

    @Test
    public void tripleEMAUsingBarCount5UsingClosePrice() {
        var tripleEma = new TripleEMAIndicator(closePrice, 5);

        int unstableBars = tripleEma.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(tripleEma.getValue(i))).isTrue();
        }

        for (int i = unstableBars; i < closePrice.getBarSeries().getBarCount(); i++) {
            assertThat(Num.isNaNOrNull(tripleEma.getValue(i))).isFalse();
        }
    }
}
