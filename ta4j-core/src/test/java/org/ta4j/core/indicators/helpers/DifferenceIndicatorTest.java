/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DifferenceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private DifferenceIndicator priceChange;

    private BarSeries barSeries;

    public DifferenceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        priceChange = new DifferenceIndicator(new ClosePriceIndicator(barSeries));
    }

    @Test
    public void indicatorShouldRetrieveBarDifference() {
        assertThat(priceChange.getValue(0).isNaN()).isTrue();
        for (int i = 1; i < 10; i++) {
            Num previousBarClosePrice = barSeries.getBar(i - 1).getClosePrice();
            Num currentBarClosePrice = barSeries.getBar(i).getClosePrice();
            assertEquals(priceChange.getValue(i), currentBarClosePrice.minus(previousBarClosePrice));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void deprecatedClosePriceDifferenceUsesClosePrices() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int close : new int[] { 10, 12, 12, 7 }) {
            series.barBuilder().openPrice(50).highPrice(100).lowPrice(0).closePrice(close).add();
        }
        ClosePriceDifferenceIndicator difference = new ClosePriceDifferenceIndicator(series);

        assertThat(difference.getCountOfUnstableBars()).isEqualTo(1);
        assertThat(difference.getValue(0).isNaN()).isTrue();
        assertThat(difference.getValue(1)).isEqualByComparingTo(numOf(2));
        assertThat(difference.getValue(2)).isEqualByComparingTo(numFactory.zero());
        assertThat(difference.getValue(3)).isEqualByComparingTo(numOf(-5));
    }

    @Test
    public void returnsNaNInUnstableRegionAfterRemoval() {
        BarSeries removed = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4)
                .withMaxBarCount(3)
                .build();
        DifferenceIndicator diff = new DifferenceIndicator(new ClosePriceIndicator(removed));

        // beginIndex = 1; relative index 0 is still within the unstable region
        assertThat(diff.getValue(1).isNaN()).isTrue();
        assertEquals(removed.getBar(2).getClosePrice().minus(removed.getBar(1).getClosePrice()), diff.getValue(2));
    }
}
