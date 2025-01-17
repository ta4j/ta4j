/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.averages;

import static org.ta4j.core.TestUtils.*;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.CsvTestUtils;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VWMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public VWMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void vwmaIndicatorTest() {

        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withName("VWMA test").build();
        series.barBuilder().closePrice(101).volume(1847).add();
        series.barBuilder().closePrice(100).volume(1290).add();
        series.barBuilder().closePrice(104).volume(1856).add();
        series.barBuilder().closePrice(102).volume(1993).add();
        series.barBuilder().closePrice(100).volume(1942).add();
        series.barBuilder().closePrice(99).volume(1893).add();
        series.barBuilder().closePrice(98).volume(1813).add();
        series.barBuilder().closePrice(100).volume(1024).add();
        series.barBuilder().closePrice(100).volume(1289).add();
        series.barBuilder().closePrice(102).volume(1006).add();
        series.barBuilder().closePrice(100).volume(1992).add();
        series.barBuilder().closePrice(97).volume(1180).add();
        series.barBuilder().closePrice(102).volume(1268).add();
        series.barBuilder().closePrice(97).volume(1934).add();
        series.barBuilder().closePrice(104).volume(1585).add();
        series.barBuilder().closePrice(99).volume(1727).add();
        series.barBuilder().closePrice(102).volume(1884).add();
        series.barBuilder().closePrice(98).volume(1134).add();
        series.barBuilder().closePrice(100).volume(1701).add();
        series.barBuilder().closePrice(101).volume(1134).add();
        series.barBuilder().closePrice(102).volume(1675).add();
        series.barBuilder().closePrice(99).volume(1166).add();
        series.barBuilder().closePrice(99).volume(1220).add();
        series.barBuilder().closePrice(101).volume(1854).add();
        series.barBuilder().closePrice(96).volume(1779).add();
        series.barBuilder().closePrice(98).volume(1970).add();
        series.barBuilder().closePrice(99).volume(1579).add();
        series.barBuilder().closePrice(99).volume(1515).add();
        series.barBuilder().closePrice(100).volume(1732).add();
        series.barBuilder().closePrice(103).volume(1234).add();
        series.barBuilder().closePrice(100).volume(1651).add();
        series.barBuilder().closePrice(100).volume(1457).add();
        series.barBuilder().closePrice(102).volume(1990).add();
        series.barBuilder().closePrice(99).volume(1707).add();
        series.barBuilder().closePrice(98).volume(1937).add();
        series.barBuilder().closePrice(104).volume(1537).add();
        series.barBuilder().closePrice(100).volume(1864).add();
        series.barBuilder().closePrice(101).volume(1616).add();
        series.barBuilder().closePrice(99).volume(1712).add();
        series.barBuilder().closePrice(102).volume(1906).add();
        series.barBuilder().closePrice(100).volume(1287).add();
        series.barBuilder().closePrice(96).volume(1518).add();
        series.barBuilder().closePrice(102).volume(1362).add();
        series.barBuilder().closePrice(100).volume(1104).add();
        series.barBuilder().closePrice(102).volume(1948).add();
        series.barBuilder().closePrice(102).volume(1064).add();
        series.barBuilder().closePrice(98).volume(1108).add();
        series.barBuilder().closePrice(102).volume(1933).add();
        series.barBuilder().closePrice(97).volume(1717).add();
        series.barBuilder().closePrice(102).volume(1808).add();
        series.barBuilder().closePrice(101).volume(1623).add();

        VWMAIndicator vwma = new VWMAIndicator(new ClosePriceIndicator(series), 5);

        int j = 10;
        assertNumEquals(99.77344188658058, vwma.getValue(j++));
        assertNumEquals(99.76459713449391, vwma.getValue(j++));
        assertNumEquals(100.14966592427618, vwma.getValue(j++));
        assertNumEquals(99.35040650406505, vwma.getValue(j++));
        assertNumEquals(99.94144993089584, vwma.getValue(j++));
        assertNumEquals(99.71497270600467, vwma.getValue(j++));
        assertNumEquals(100.60907358894976, vwma.getValue(j++));
        assertNumEquals(100.03763310745403, vwma.getValue(j++));
        assertNumEquals(100.76117544515004, vwma.getValue(j++));
        assertNumEquals(100.11965699208443, vwma.getValue(j++));
        assertNumEquals(100.79489904357067, vwma.getValue(j++));
        assertNumEquals(100.15418502202643, vwma.getValue(j++));
        assertNumEquals(100.3042343387471, vwma.getValue(j++));
        assertNumEquals(100.56064690026955, vwma.getValue(j++));

    }

    @Test
    public void vwmaIndicatorTest2() {

        MockIndicator mock = CsvTestUtils.getCsvFile(VWMAIndicatorTest.class, "VWMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        VWMAIndicator ma = new VWMAIndicator(new ClosePriceIndicator(barSeries), 10);

        for (int i = 10; i < barSeries.getBarCount(); i++) {

            Num expected = mock.getValue(i);
            Num value = ma.getValue(i);

            assertNumEquals(expected.doubleValue(), value);
        }
    }

}
