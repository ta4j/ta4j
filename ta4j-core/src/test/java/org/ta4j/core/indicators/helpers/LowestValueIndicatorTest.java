/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LowestValueIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public LowestValueIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 2, 4, 3, 1)
                .build();
    }

    @Test
    public void lowestValueIndicatorUsingBarCount5UsingClosePrice() {
        LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals("1.0", lowestValue.getValue(1));
        assertNumEquals("1.0", lowestValue.getValue(2));
        assertNumEquals("1.0", lowestValue.getValue(3));
        assertNumEquals("1.0", lowestValue.getValue(4));
        assertNumEquals("2.0", lowestValue.getValue(5));
        assertNumEquals("3.0", lowestValue.getValue(6));
        assertNumEquals("3.0", lowestValue.getValue(7));
        assertNumEquals("3.0", lowestValue.getValue(8));
        assertNumEquals("3.0", lowestValue.getValue(9));
        assertNumEquals("2.0", lowestValue.getValue(10));
        assertNumEquals("2.0", lowestValue.getValue(11));
        assertNumEquals("2.0", lowestValue.getValue(12));
    }

    @Test
    public void lowestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
        LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals("1.0", lowestValue.getValue(0));
    }

    @Test
    public void lowestValueIndicatorWhenBarCountIsGreaterThanIndex() {
        LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 500);
        assertNumEquals("1.0", lowestValue.getValue(12));
    }

    @Test
    public void outOfOrderEvaluation() {
        LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);
        // Evaluating out-of-order directly at index 10
        assertNumEquals("2.0", lowestValue.getValue(10));
        assertNumEquals("3.0", lowestValue.getValue(6));
    }

    @Test
    public void slidingWindowEdgeCases() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 8, 12, 14, 15, 5).build();
        LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(series), 3);

        assertNumEquals("10.0", lowestValue.getValue(0));
        assertNumEquals("8.0", lowestValue.getValue(1));
        // index 2: [10, 8, 12] -> min = 8 (dropped element index 0 was 10 > prevMin 8)
        assertNumEquals("8.0", lowestValue.getValue(2));
        // index 3: [8, 12, 14] -> min = 8 (dropped element index 0 was 10, remaining
        // window contains 8)
        assertNumEquals("8.0", lowestValue.getValue(3));
        // index 4: [12, 14, 15] -> min = 12 (dropped element index 1 was 8 == prevMin
        // 8, recalculates)
        assertNumEquals("12.0", lowestValue.getValue(4));
        // index 5: [14, 15, 5] -> min = 5 (current 5 < prevMin 12)
        assertNumEquals("5.0", lowestValue.getValue(5));
    }

    @Test
    public void withRemovedBars() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 5, 8, 12, 15).build();
        series.setMaximumBarCount(4);

        LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(series), 3);
        // End index 5, window [3..5] values [8, 12, 15], lowest is 8.0
        assertNumEquals("8.0", lowestValue.getValue(series.getEndIndex()));
    }

    @Test
    public void onlyNaNValues() {
        BaseBarSeries series = new MockBarSeriesBuilder().withName("NaN test").withNumFactory(numFactory).build();
        for (long i = 0; i <= 10000; i++) {
            series.barBuilder().openPrice(NaN).closePrice(NaN).highPrice(NaN).lowPrice(NaN).add();
        }

        LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(series), 5);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertEquals(NaN.toString(), lowestValue.getValue(i).toString());
        }
    }

    @Test
    public void naNValuesInIntervall() {
        BaseBarSeries series = new MockBarSeriesBuilder().withName("NaN test").withNumFactory(numFactory).build();
        for (long i = 0; i <= 10; i++) {
            series.barBuilder().openPrice(NaN).closePrice(NaN).highPrice(NaN).lowPrice(NaN).add();
        }

        var lowestValue = new LowestValueIndicator(new ClosePriceIndicator(series), 2);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            if (i % 2 != 0) {
                assertEquals(series.getBar(i - 1).getClosePrice().toString(), lowestValue.getValue(i).toString());
            } else
                assertEquals(series.getBar(Math.max(0, i - 1)).getClosePrice().toString(),
                        lowestValue.getValue(i).toString());
        }
    }
}
