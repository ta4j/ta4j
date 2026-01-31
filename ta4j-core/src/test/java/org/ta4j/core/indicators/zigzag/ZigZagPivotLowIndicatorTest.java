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

public class ZigZagPivotLowIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Boolean> {

    private BarSeries series;
    private final Num reversalThreshold = numOf(5.0);

    public ZigZagPivotLowIndicatorTest(NumFactory numFactory) {
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
        final ZigZagPivotLowIndicator indicator = new ZigZagPivotLowIndicator(stateIndicator);

        assertThat(indicator.getValue(0)).isFalse();
    }

    @Test
    public void shouldReturnTrueWhenSwingLowIsConfirmed() {
        // Price: 100, 95, 90 (low), 92, 97 (reversal >= 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(95).add();
        series.barBuilder().closePrice(90).add(); // low
        series.barBuilder().closePrice(92).add();
        series.barBuilder().closePrice(97).add(); // reversal: 97 - 90 = 7 >= 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final ZigZagPivotLowIndicator indicator = new ZigZagPivotLowIndicator(stateIndicator);

        assertThat(indicator.getValue(0)).isFalse();
        assertThat(indicator.getValue(1)).isFalse();
        assertThat(indicator.getValue(2)).isFalse();
        assertThat(indicator.getValue(3)).isFalse();
        assertThat(indicator.getValue(4)).isTrue(); // Swing low confirmed at index 4
    }

    @Test
    public void shouldReturnFalseWhenNoNewSwingLow() {
        // Price: 100, 95, 90 (low), 92, 97 (reversal), 96, 95
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(95).add();
        series.barBuilder().closePrice(90).add();
        series.barBuilder().closePrice(92).add();
        series.barBuilder().closePrice(97).add(); // Swing low confirmed
        series.barBuilder().closePrice(96).add();
        series.barBuilder().closePrice(95).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final ZigZagPivotLowIndicator indicator = new ZigZagPivotLowIndicator(stateIndicator);

        assertThat(indicator.getValue(4)).isTrue(); // Swing low confirmed
        assertThat(indicator.getValue(5)).isFalse(); // No new swing low
        assertThat(indicator.getValue(6)).isFalse(); // No new swing low
    }

    @Test
    public void shouldReturnTrueForMultipleSwingLows() {
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
        final ZigZagPivotLowIndicator indicator = new ZigZagPivotLowIndicator(stateIndicator);

        assertThat(indicator.getValue(2)).isTrue(); // First swing low confirmed
        assertThat(indicator.getValue(3)).isFalse(); // Swing high confirmed, not low
        assertThat(indicator.getValue(5)).isTrue(); // Second swing low confirmed
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
        final ZigZagPivotLowIndicator indicator = new ZigZagPivotLowIndicator(stateIndicator);

        assertThat(indicator.getLatestSwingLowIndex(0)).isEqualTo(-1);
        assertThat(indicator.getLatestSwingLowIndex(1)).isEqualTo(-1);
        assertThat(indicator.getLatestSwingLowIndex(2)).isEqualTo(1);
        assertThat(indicator.getLatestSwingLowIndex(2)).isEqualTo(1); // Still 1
    }

    @Test
    public void shouldReturnFalseWhenReversalThresholdNotMet() {
        // Price: 100, 90 (low), 92, 93 (reversal: 93 - 90 = 3 < 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(90).add();
        series.barBuilder().closePrice(92).add();
        series.barBuilder().closePrice(93).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final ZigZagPivotLowIndicator indicator = new ZigZagPivotLowIndicator(stateIndicator);

        assertThat(indicator.getValue(3)).isFalse(); // No swing low confirmed
        assertThat(indicator.getLatestSwingLowIndex(3)).isEqualTo(-1);
    }

    @Test
    public void shouldReturnZeroUnstableBars() {
        series.barBuilder().closePrice(100).add();
        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator stateIndicator = new ZigZagStateIndicator(price, threshold);
        final ZigZagPivotLowIndicator indicator = new ZigZagPivotLowIndicator(stateIndicator);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(0);
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
        final ZigZagPivotLowIndicator original = new ZigZagPivotLowIndicator(stateIndicator);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final String json = original.toJson();
        final Indicator<Boolean> restored = (Indicator<Boolean>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(ZigZagPivotLowIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());

        // Verify the restored indicator produces the same values
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(original.getValue(i));
        }
    }
}
