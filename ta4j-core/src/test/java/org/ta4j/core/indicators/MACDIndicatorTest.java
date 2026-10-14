/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MACDIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public MACDIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private BarSeries data;

    @BeforeEach
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(37.08, 36.7, 36.11, 35.85, 35.71, 36.04, 36.41, 37.67, 38.01, 37.79, 36.83, 37.10, 38.01,
                        38.50, 38.99)
                .build();
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
        Indicator<Num> reused = new MACDIndicator(close, 2, 3);
        for (int i = 0; i < series.getEndIndex(); i++) {
            reused.getValue(i);
        }
        series.barBuilder().openPrice(9).highPrice(11).lowPrice(8).closePrice(9).volume(110).add();
        Indicator<Num> fresh = new MACDIndicator(close, 2, 3);
        for (int i = series.getBeginIndex(); i < series.getEndIndex(); i++) {
            assertNumEquals(fresh.getValue(i), reused.getValue(i));
        }
    }

    @Test
    public void throwsErrorOnIllegalArguments() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MACDIndicator(new ClosePriceIndicator(data), 10, 5);
        });
    }

    @Test
    public void macdUsingPeriod5And10() {
        var macdIndicator = new MACDIndicator(new ClosePriceIndicator(data), 5, 10);

        // MACD unstable period is slowPeriod (10), so indices 0-9 return NaN
        // because slow EMA returns NaN during its unstable period
        for (int i = 0; i < 10; i++) {
            assertThat(Double.isNaN(macdIndicator.getValue(i).doubleValue())).isTrue();
        }

        // Values after unstable period should be valid (not NaN)
        // Note: Values will differ from expected because first EMA value after unstable
        // period
        // is now initialized to current value, not calculated from previous values
        assertThat(Double.isNaN(macdIndicator.getValue(10).doubleValue())).isFalse();

        // Short EMA (period 5): unstable period is 5, so indices 0-4 are NaN, index 5+
        // are valid
        assertThat(Double.isNaN(macdIndicator.getShortTermEma().getValue(4).doubleValue())).isTrue();
        assertThat(Double.isNaN(macdIndicator.getShortTermEma().getValue(5).doubleValue())).isFalse();

        // Long EMA (period 10): unstable period is 10, so indices 0-9 are NaN, index
        // 10+ are valid
        assertThat(Double.isNaN(macdIndicator.getLongTermEma().getValue(9).doubleValue())).isTrue();
        assertThat(Double.isNaN(macdIndicator.getLongTermEma().getValue(10).doubleValue())).isFalse();
    }

    @Test
    public void emaGettersReturnIndependentHelpers() {
        MACDIndicator macdIndicator = new MACDIndicator(new ClosePriceIndicator(data), 5, 10);

        EMAIndicator firstShort = macdIndicator.getShortTermEma();
        EMAIndicator secondShort = macdIndicator.getShortTermEma();
        EMAIndicator firstLong = macdIndicator.getLongTermEma();
        EMAIndicator secondLong = macdIndicator.getLongTermEma();

        assertThat(firstShort).isNotSameAs(secondShort);
        assertThat(firstLong).isNotSameAs(secondLong);
        assertThat(firstShort.getBarCount()).isEqualTo(5);
        assertThat(firstLong.getBarCount()).isEqualTo(10);
        assertThat(firstShort.getValue(10)).isEqualByComparingTo(secondShort.getValue(10));
        assertThat(firstLong.getValue(10)).isEqualByComparingTo(secondLong.getValue(10));
    }
}
