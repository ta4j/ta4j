/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.zigzag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RecentZigZagSwingLowIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;
    private final Num reversalThreshold = numOf(5.0);

    public RecentZigZagSwingLowIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
    }

    @Test
    public void shouldReturnNaNWhenNoSwingLowConfirmed() {
        // Price: 100, 98, 97
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(98).add();
        series.barBuilder().closePrice(97).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingLowIndicator indicator = new RecentZigZagSwingLowIndicator(stateIndicator, price);

        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(1).isNaN()).isTrue();
        assertThat(indicator.getValue(2).isNaN()).isTrue();
        assertThat(indicator.getLatestSwingLowIndex(2)).isEqualTo(-1);
    }

    @Test
    public void shouldReturnMostRecentSwingLowPrice() {
        // Price: 100, 95, 90 (low), 92, 97 (reversal >= 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(95).add();
        series.barBuilder().closePrice(90).add(); // low
        series.barBuilder().closePrice(92).add();
        series.barBuilder().closePrice(97).add(); // reversal: 97 - 90 = 7 >= 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingLowIndicator indicator = new RecentZigZagSwingLowIndicator(stateIndicator, price);

        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(1).isNaN()).isTrue();
        assertThat(indicator.getValue(2).isNaN()).isTrue();
        assertThat(indicator.getValue(3).isNaN()).isTrue();
        assertThat(indicator.getValue(4)).isEqualByComparingTo(numOf(90));
        assertThat(indicator.getLatestSwingLowIndex(4)).isEqualTo(2);
    }

    @Test
    public void shouldTrackLatestSwingLowThroughMultipleSwingPoints() {
        // Price: 100, 90 (low1), 95, 105, 85 (low2), 90
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(90).add(); // low1
        series.barBuilder().closePrice(95).add(); // reversal: 95 - 90 = 5 >= 5
        series.barBuilder().closePrice(105).add(); // reversal: 105 - 95 = 10 >= 5
        series.barBuilder().closePrice(85).add(); // low2
        series.barBuilder().closePrice(90).add(); // reversal: 90 - 85 = 5 >= 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingLowIndicator indicator = new RecentZigZagSwingLowIndicator(stateIndicator, price);

        assertThat(indicator.getValue(2)).isEqualByComparingTo(numOf(90)); // First swing low
        assertThat(indicator.getLatestSwingLowIndex(2)).isEqualTo(1);
        assertThat(indicator.getValue(3)).isEqualByComparingTo(numOf(90)); // Still first swing low
        assertThat(indicator.getValue(5)).isEqualByComparingTo(numOf(85)); // Second swing low
        assertThat(indicator.getLatestSwingLowIndex(5)).isEqualTo(4);
    }

    @Test
    public void shouldWorkWithLowPriceIndicator() {
        // Low prices: 100, 95, 90, 92, 97
        series.barBuilder().lowPrice(100).closePrice(100).add();
        series.barBuilder().lowPrice(95).closePrice(95).add();
        series.barBuilder().lowPrice(90).closePrice(90).add();
        series.barBuilder().lowPrice(92).closePrice(92).add();
        series.barBuilder().lowPrice(97).closePrice(97).add();

        final Indicator<Num> price = new LowPriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingLowIndicator indicator = new RecentZigZagSwingLowIndicator(stateIndicator, price);

        assertThat(indicator.getValue(4)).isEqualByComparingTo(numOf(90));
        assertThat(indicator.getLatestSwingLowIndex(4)).isEqualTo(2);
    }

    @Test
    public void shouldReturnLatestSwingLowIndex() {
        // Price: 100, 90 (low), 95
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(90).add(); // low
        series.barBuilder().closePrice(95).add(); // reversal

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingLowIndicator indicator = new RecentZigZagSwingLowIndicator(stateIndicator, price);

        assertThat(indicator.getLatestSwingLowIndex(0)).isEqualTo(-1);
        assertThat(indicator.getLatestSwingLowIndex(1)).isEqualTo(-1);
        assertThat(indicator.getLatestSwingLowIndex(2)).isEqualTo(1);
    }

    @Test
    public void shouldReturnZeroUnstableBars() {
        series.barBuilder().closePrice(100).add();
        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingLowIndicator indicator = new RecentZigZagSwingLowIndicator(stateIndicator, price);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(0);
    }

    @Test
    public void shouldHandleMultipleSwingLowsCorrectly() {
        // Price: 100, 90 (low1), 95, 80 (low2), 85, 70 (low3), 75
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(90).add(); // low1
        series.barBuilder().closePrice(95).add(); // reversal
        series.barBuilder().closePrice(80).add(); // low2
        series.barBuilder().closePrice(85).add(); // reversal
        series.barBuilder().closePrice(70).add(); // low3
        series.barBuilder().closePrice(75).add(); // reversal

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingLowIndicator indicator = new RecentZigZagSwingLowIndicator(stateIndicator, price);

        assertThat(indicator.getValue(2)).isEqualByComparingTo(numOf(90)); // First swing low
        assertThat(indicator.getValue(4)).isEqualByComparingTo(numOf(80)); // Second swing low
        assertThat(indicator.getValue(6)).isEqualByComparingTo(numOf(70)); // Third swing low
        assertThat(indicator.getLatestSwingLowIndex(6)).isEqualTo(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRoundTripSerializeAndDeserialize() {
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(95).add();
        series.barBuilder().closePrice(90).add();
        series.barBuilder().closePrice(92).add();
        series.barBuilder().closePrice(97).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingLowIndicator original = new RecentZigZagSwingLowIndicator(stateIndicator, price);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final String json = original.toJson();
        final Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(RecentZigZagSwingLowIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());

        // Verify the restored indicator produces the same values
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualByComparingTo(original.getValue(i));
        }
    }
}
