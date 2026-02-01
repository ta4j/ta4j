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
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RecentZigZagSwingHighIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;
    private final Num reversalThreshold = numOf(5.0);

    public RecentZigZagSwingHighIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
    }

    @Test
    public void shouldReturnNaNWhenNoSwingHighConfirmed() {
        // Price: 100, 102, 103
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(102).add();
        series.barBuilder().closePrice(103).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingHighIndicator indicator = new RecentZigZagSwingHighIndicator(stateIndicator, price);

        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(1).isNaN()).isTrue();
        assertThat(indicator.getValue(2).isNaN()).isTrue();
        assertThat(indicator.getLatestSwingHighIndex(2)).isEqualTo(-1);
    }

    @Test
    public void shouldReturnMostRecentSwingHighPrice() {
        // Price: 100, 105, 110 (high), 108, 103 (reversal >= 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();
        series.barBuilder().closePrice(110).add(); // high
        series.barBuilder().closePrice(108).add();
        series.barBuilder().closePrice(103).add(); // reversal: 110 - 103 = 7 >= 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingHighIndicator indicator = new RecentZigZagSwingHighIndicator(stateIndicator, price);

        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(1).isNaN()).isTrue();
        assertThat(indicator.getValue(2).isNaN()).isTrue();
        assertThat(indicator.getValue(3).isNaN()).isTrue();
        assertThat(indicator.getValue(4)).isEqualByComparingTo(numOf(110));
        assertThat(indicator.getLatestSwingHighIndex(4)).isEqualTo(2);
    }

    @Test
    public void shouldTrackLatestSwingHighThroughMultipleSwingPoints() {
        // Price: 100, 110 (high1), 105, 95, 115 (high2), 110
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(110).add(); // high1
        series.barBuilder().closePrice(105).add(); // reversal: 110 - 105 = 5 >= 5
        series.barBuilder().closePrice(95).add(); // reversal: 105 - 95 = 10 >= 5
        series.barBuilder().closePrice(115).add(); // high2
        series.barBuilder().closePrice(110).add(); // reversal: 115 - 110 = 5 >= 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingHighIndicator indicator = new RecentZigZagSwingHighIndicator(stateIndicator, price);

        assertThat(indicator.getValue(2)).isEqualByComparingTo(numOf(110)); // First swing high
        assertThat(indicator.getLatestSwingHighIndex(2)).isEqualTo(1);
        assertThat(indicator.getValue(3)).isEqualByComparingTo(numOf(110)); // Still first swing high
        assertThat(indicator.getValue(5)).isEqualByComparingTo(numOf(115)); // Second swing high
        assertThat(indicator.getLatestSwingHighIndex(5)).isEqualTo(4);
    }

    @Test
    public void shouldWorkWithHighPriceIndicator() {
        // High prices: 100, 105, 110, 108, 103
        series.barBuilder().highPrice(100).closePrice(100).add();
        series.barBuilder().highPrice(105).closePrice(105).add();
        series.barBuilder().highPrice(110).closePrice(110).add();
        series.barBuilder().highPrice(108).closePrice(108).add();
        series.barBuilder().highPrice(103).closePrice(103).add();

        final Indicator<Num> price = new HighPriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingHighIndicator indicator = new RecentZigZagSwingHighIndicator(stateIndicator, price);

        assertThat(indicator.getValue(4)).isEqualByComparingTo(numOf(110));
        assertThat(indicator.getLatestSwingHighIndex(4)).isEqualTo(2);
    }

    @Test
    public void shouldReturnLatestSwingHighIndex() {
        // Price: 100, 110 (high), 105
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(110).add(); // high
        series.barBuilder().closePrice(105).add(); // reversal

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingHighIndicator indicator = new RecentZigZagSwingHighIndicator(stateIndicator, price);

        assertThat(indicator.getLatestSwingHighIndex(0)).isEqualTo(-1);
        assertThat(indicator.getLatestSwingHighIndex(1)).isEqualTo(-1);
        assertThat(indicator.getLatestSwingHighIndex(2)).isEqualTo(1);
    }

    @Test
    public void shouldReturnZeroUnstableBars() {
        series.barBuilder().closePrice(100).add();
        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingHighIndicator indicator = new RecentZigZagSwingHighIndicator(stateIndicator, price);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(0);
    }

    @Test
    public void shouldHandleMultipleSwingHighsCorrectly() {
        // Price: 100, 110 (high1), 105, 120 (high2), 115, 125 (high3), 120
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(110).add(); // high1
        series.barBuilder().closePrice(105).add(); // reversal
        series.barBuilder().closePrice(120).add(); // high2
        series.barBuilder().closePrice(115).add(); // reversal
        series.barBuilder().closePrice(125).add(); // high3
        series.barBuilder().closePrice(120).add(); // reversal

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingHighIndicator indicator = new RecentZigZagSwingHighIndicator(stateIndicator, price);

        assertThat(indicator.getValue(2)).isEqualByComparingTo(numOf(110)); // First swing high
        assertThat(indicator.getValue(4)).isEqualByComparingTo(numOf(120)); // Second swing high
        assertThat(indicator.getValue(6)).isEqualByComparingTo(numOf(125)); // Third swing high
        assertThat(indicator.getLatestSwingHighIndex(6)).isEqualTo(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRoundTripSerializeAndDeserialize() {
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();
        series.barBuilder().closePrice(110).add();
        series.barBuilder().closePrice(108).add();
        series.barBuilder().closePrice(103).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final RecentZigZagSwingHighIndicator original = new RecentZigZagSwingHighIndicator(stateIndicator, price);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final String json = original.toJson();
        final Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(RecentZigZagSwingHighIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());

        // Verify the restored indicator produces the same values
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualByComparingTo(original.getValue(i));
        }
    }
}
