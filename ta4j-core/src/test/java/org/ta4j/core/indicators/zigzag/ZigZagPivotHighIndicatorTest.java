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
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ZigZagPivotHighIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Boolean> {

    private BarSeries series;
    private final Num reversalThreshold = numOf(5.0);

    public ZigZagPivotHighIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
    }

    @Test
    public void shouldReturnFalseAtFirstBar() {
        series.barBuilder().closePrice(100).add();
        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final ZigZagPivotHighIndicator indicator = new ZigZagPivotHighIndicator(stateIndicator);

        assertThat(indicator.getValue(0)).isFalse();
    }

    @Test
    public void shouldReturnTrueWhenSwingHighIsConfirmed() {
        // Price: 100, 105, 110 (high), 108, 103 (reversal >= 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();
        series.barBuilder().closePrice(110).add(); // high
        series.barBuilder().closePrice(108).add();
        series.barBuilder().closePrice(103).add(); // reversal: 110 - 103 = 7 >= 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final ZigZagPivotHighIndicator indicator = new ZigZagPivotHighIndicator(stateIndicator);

        assertThat(indicator.getValue(0)).isFalse();
        assertThat(indicator.getValue(1)).isFalse();
        assertThat(indicator.getValue(2)).isFalse();
        assertThat(indicator.getValue(3)).isFalse();
        assertThat(indicator.getValue(4)).isTrue(); // Swing high confirmed at index 4
    }

    @Test
    public void shouldReturnFalseWhenNoNewSwingHigh() {
        // Price: 100, 105, 110 (high), 108, 103 (reversal), 104, 105
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();
        series.barBuilder().closePrice(110).add();
        series.barBuilder().closePrice(108).add();
        series.barBuilder().closePrice(103).add(); // Swing high confirmed
        series.barBuilder().closePrice(104).add();
        series.barBuilder().closePrice(105).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final ZigZagPivotHighIndicator indicator = new ZigZagPivotHighIndicator(stateIndicator);

        assertThat(indicator.getValue(4)).isTrue(); // Swing high confirmed
        assertThat(indicator.getValue(5)).isFalse(); // No new swing high
        assertThat(indicator.getValue(6)).isFalse(); // No new swing high
    }

    @Test
    public void shouldReturnTrueForMultipleSwingHighs() {
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
        final ZigZagPivotHighIndicator indicator = new ZigZagPivotHighIndicator(stateIndicator);

        assertThat(indicator.getValue(2)).isTrue(); // First swing high confirmed
        assertThat(indicator.getValue(3)).isFalse(); // Swing low confirmed, not high
        assertThat(indicator.getValue(5)).isTrue(); // Second swing high confirmed
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
        final ZigZagPivotHighIndicator indicator = new ZigZagPivotHighIndicator(stateIndicator);

        assertThat(indicator.getLatestSwingHighIndex(0)).isEqualTo(-1);
        assertThat(indicator.getLatestSwingHighIndex(1)).isEqualTo(-1);
        assertThat(indicator.getLatestSwingHighIndex(2)).isEqualTo(1);
        assertThat(indicator.getLatestSwingHighIndex(2)).isEqualTo(1); // Still 1
    }

    @Test
    public void shouldReturnFalseWhenReversalThresholdNotMet() {
        // Price: 100, 110 (high), 108, 107 (reversal: 110 - 107 = 3 < 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(110).add();
        series.barBuilder().closePrice(108).add();
        series.barBuilder().closePrice(107).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final ZigZagPivotHighIndicator indicator = new ZigZagPivotHighIndicator(stateIndicator);

        assertThat(indicator.getValue(3)).isFalse(); // No swing high confirmed
        assertThat(indicator.getLatestSwingHighIndex(3)).isEqualTo(-1);
    }

    @Test
    public void shouldReturnZeroUnstableBars() {
        series.barBuilder().closePrice(100).add();
        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final ZigZagPivotHighIndicator indicator = new ZigZagPivotHighIndicator(stateIndicator);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(0);
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
        final ZigZagPivotHighIndicator original = new ZigZagPivotHighIndicator(stateIndicator);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final String json = original.toJson();
        final Indicator<Boolean> restored = (Indicator<Boolean>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(ZigZagPivotHighIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());

        // Verify the restored indicator produces the same values
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(original.getValue(i));
        }
    }
}
