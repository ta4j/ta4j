/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class HighestValueIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public HighestValueIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2)
                .build();
    }

    @Test
    public void highestValueUsingBarCount5UsingClosePrice() {
        var highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals("4.0", highestValue.getValue(4));
        assertNumEquals("4.0", highestValue.getValue(5));
        assertNumEquals("5.0", highestValue.getValue(6));
        assertNumEquals("6.0", highestValue.getValue(7));
        assertNumEquals("6.0", highestValue.getValue(8));
        assertNumEquals("6.0", highestValue.getValue(9));
        assertNumEquals("6.0", highestValue.getValue(10));
        assertNumEquals("6.0", highestValue.getValue(11));
        assertNumEquals("4.0", highestValue.getValue(12));
    }

    @Test
    public void firstHighestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
        var highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals("1.0", highestValue.getValue(0));
    }

    @Test
    public void highestValueIndicatorWhenBarCountIsGreaterThanIndex() {
        var highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 500);
        assertNumEquals("6.0", highestValue.getValue(12));
    }

    @Test
    public void outOfOrderEvaluation() {
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
        // Evaluating out-of-order directly at index 10
        assertNumEquals("6.0", highestValue.getValue(10));
        assertNumEquals("5.0", highestValue.getValue(6));
    }

    @Test
    public void slidingWindowEdgeCases() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(5, 12, 8, 6, 4, 20).build();
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 3);

        assertNumEquals("5.0", highestValue.getValue(0));
        assertNumEquals("12.0", highestValue.getValue(1));
        // index 2: [5, 12, 8] -> max = 12
        assertNumEquals("12.0", highestValue.getValue(2));
        // index 3: [12, 8, 6] -> max = 12 (dropped element index 0 was 5 < prevMax 12)
        assertNumEquals("12.0", highestValue.getValue(3));
        // index 4: [8, 6, 4] -> max = 8 (dropped element index 1 was 12 == prevMax 12,
        // recalculates)
        assertNumEquals("8.0", highestValue.getValue(4));
        // index 5: [6, 4, 20] -> max = 20 (current 20 > prevMax 8)
        assertNumEquals("20.0", highestValue.getValue(5));
    }

    @Test
    public void withRemovedBars() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 5, 8, 12, 15).build();
        series.setMaximumBarCount(4);

        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 3);
        assertNumEquals("15.0", highestValue.getValue(series.getEndIndex()));
    }

    @Test
    public void onlyNaNValues() {
        var series = new MockBarSeriesBuilder().withName("NaN test").build();
        var now = Instant.now();
        for (long i = 0; i <= 10000; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(NaN)
                    .closePrice(NaN)
                    .highPrice(NaN)
                    .lowPrice(NaN)
                    .volume(NaN)
                    .add();
        }

        var highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 5);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertEquals(NaN.toString(), highestValue.getValue(i).toString());
        }
    }

    @Test
    public void naNValuesInIntervall() {
        var series = new MockBarSeriesBuilder().withName("NaN test").build();
        var now = Instant.now();
        for (long i = 0; i <= 10; i++) { // (0, NaN, 2, NaN, 3, NaN, 4, NaN, 5, ...)
            Num closePrice = i % 2 == 0 ? series.numFactory().numOf(i) : NaN;
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(NaN)
                    .closePrice(closePrice)
                    .highPrice(NaN)
                    .lowPrice(NaN)
                    .volume(NaN)
                    .add();
        }

        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 2);

        // index is the biggest of (index, index-1)
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            if (i % 2 != 0) // current is NaN take the previous as highest
                assertEquals(series.getBar(i - 1).getClosePrice().toString(), highestValue.getValue(i).toString());
            else // current is not NaN but previous, take the current
                assertEquals(series.getBar(i).getClosePrice().toString(), highestValue.getValue(i).toString());
        }
    }
}
