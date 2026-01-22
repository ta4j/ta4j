/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private final ExternalIndicatorTest xls;

    public SMAIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new SMAIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "SMA.xls", 6, numFactory);
    }

    private BarSeries data;

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2)
                .build();
    }

    public Instant getNextEndTime() {
        var lastBar = data.getLastBar();
        return lastBar == null ? null : lastBar.getEndTime().plus(lastBar.getTimePeriod());
    }

    @Test
    public void ensureCountOfUnstableBarsAddsToCountOfUnstableBarsOfPreviousIndicator() {
        var closePrice = new ClosePriceIndicator(data);
        var firstSMAindicator = new SMAIndicator(closePrice, 2);
        var secondSMAindicator = new SMAIndicator(firstSMAindicator, 3);

        assertEquals(1, firstSMAindicator.getCountOfUnstableBars());
        assertNumEquals(1.5, firstSMAindicator.getValue(1));
        assertNumEquals(2.5, firstSMAindicator.getValue(2));
        assertNumEquals(3.5, firstSMAindicator.getValue(3));
        assertNumEquals(3.5, firstSMAindicator.getValue(4));
        assertNumEquals(3.5, firstSMAindicator.getValue(5));
        assertNumEquals(4.5, firstSMAindicator.getValue(6));
        assertNumEquals(4.5, firstSMAindicator.getValue(7));
        assertNumEquals(3.5, firstSMAindicator.getValue(8));
        assertNumEquals(3, firstSMAindicator.getValue(9));
        assertNumEquals(3.5, firstSMAindicator.getValue(10));
        assertNumEquals(3.5, firstSMAindicator.getValue(11));
        assertNumEquals(2.5, firstSMAindicator.getValue(12));

        assertEquals(3, secondSMAindicator.getCountOfUnstableBars());
        assertNumEquals(2.5, secondSMAindicator.getValue(3));
        assertNumEquals((2.5 + 3.5 + 3.5) / 3, secondSMAindicator.getValue(4));
        assertNumEquals(3.5, secondSMAindicator.getValue(5));
        assertNumEquals((3.5 + 3.5 + 4.5) / 3, secondSMAindicator.getValue(6));
        assertNumEquals((3.5 + 4.5 + 4.5) / 3, secondSMAindicator.getValue(7));
        assertNumEquals((4.5 + 4.5 + 3.5) / 3, secondSMAindicator.getValue(8));
        assertNumEquals((4.5 + 3.5 + 3) / 3, secondSMAindicator.getValue(9));
        assertNumEquals((3.5 + 3 + 3.5) / 3, secondSMAindicator.getValue(10));
        assertNumEquals((3 + 3.5 + 3.5) / 3, secondSMAindicator.getValue(11));
        assertNumEquals((3.5 + 3.5 + 2.5) / 3, secondSMAindicator.getValue(12));
    }

    @Test
    public void usingBarCount3UsingClosePrice() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 3);

        assertNumEquals(1, indicator.getValue(0));
        assertNumEquals(1.5, indicator.getValue(1));
        assertNumEquals(2, indicator.getValue(2));
        assertNumEquals(3, indicator.getValue(3));
        assertNumEquals(10d / 3, indicator.getValue(4));
        assertNumEquals(11d / 3, indicator.getValue(5));
        assertNumEquals(4, indicator.getValue(6));
        assertNumEquals(13d / 3, indicator.getValue(7));
        assertNumEquals(4, indicator.getValue(8));
        assertNumEquals(10d / 3, indicator.getValue(9));
        assertNumEquals(10d / 3, indicator.getValue(10));
        assertNumEquals(10d / 3, indicator.getValue(11));
        assertNumEquals(3, indicator.getValue(12));
    }

    @Test
    public void usingBarCount3UsingClosePriceMovingSerie() {
        data.setMaximumBarCount(13);
        data.barBuilder().closePrice(5.).endTime(getNextEndTime()).add();

        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 3);

        // unstable bars skipped, unpredictable results
        assertNumEquals((3d + 4d + 3d) / 3, indicator.getValue(data.getBeginIndex() + 3));
        assertNumEquals((4d + 3d + 4d) / 3, indicator.getValue(data.getBeginIndex() + 4));
        assertNumEquals((3d + 4d + 5d) / 3, indicator.getValue(data.getBeginIndex() + 5));
        assertNumEquals((4d + 5d + 4d) / 3, indicator.getValue(data.getBeginIndex() + 6));
        assertNumEquals((3d + 2d + 5d) / 3, indicator.getValue(data.getBeginIndex() + 12));
    }

    @Test
    public void whenBarCountIs1ResultShouldBeIndicatorValue() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        for (int i = 0; i < data.getBarCount(); i++) {
            assertEquals(data.getBar(i).getClosePrice(), indicator.getValue(i));
        }
    }

    @Test
    public void externalData() throws Exception {
        Indicator<Num> xlsClose = new ClosePriceIndicator(xls.getSeries());
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(xlsClose, 1);
        assertIndicatorEquals(xls.getIndicator(1), actualIndicator);
        assertEquals(329.0, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsClose, 3);
        assertIndicatorEquals(xls.getIndicator(3), actualIndicator);
        assertEquals(326.6333, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsClose, 13);
        assertIndicatorEquals(xls.getIndicator(13), actualIndicator);
        assertEquals(327.7846, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

}
