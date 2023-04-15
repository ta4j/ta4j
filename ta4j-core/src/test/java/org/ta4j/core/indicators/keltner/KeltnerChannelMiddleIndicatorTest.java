/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.indicators.keltner;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class KeltnerChannelMiddleIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public KeltnerChannelMiddleIndicatorTest(Function<Number, Num> function) {
        super(function);
    }

    @Before
    public void setUp() {

        List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(11577.43, 11670.75, 11711.47, 11577.35, numFunction));
        bars.add(new MockBar(11670.90, 11691.18, 11698.22, 11635.74, numFunction));
        bars.add(new MockBar(11688.61, 11722.89, 11742.68, 11652.89, numFunction));
        bars.add(new MockBar(11716.93, 11697.31, 11736.74, 11667.46, numFunction));
        bars.add(new MockBar(11696.86, 11674.76, 11726.94, 11599.68, numFunction));
        bars.add(new MockBar(11672.34, 11637.45, 11677.33, 11573.87, numFunction));
        bars.add(new MockBar(11638.51, 11671.88, 11704.12, 11635.48, numFunction));
        bars.add(new MockBar(11673.62, 11755.44, 11782.23, 11673.62, numFunction));
        bars.add(new MockBar(11753.70, 11731.90, 11757.25, 11700.53, numFunction));
        bars.add(new MockBar(11732.13, 11787.38, 11794.15, 11698.83, numFunction));
        bars.add(new MockBar(11783.82, 11837.93, 11858.78, 11777.99, numFunction));
        bars.add(new MockBar(11834.21, 11825.29, 11861.24, 11798.46, numFunction));
        bars.add(new MockBar(11823.70, 11822.80, 11845.16, 11744.77, numFunction));
        bars.add(new MockBar(11822.95, 11871.84, 11905.48, 11822.80, numFunction));
        bars.add(new MockBar(11873.43, 11980.52, 11982.94, 11867.98, numFunction));
        bars.add(new MockBar(11980.52, 11977.19, 11985.97, 11898.74, numFunction));
        bars.add(new MockBar(11978.85, 11985.44, 12020.52, 11961.83, numFunction));
        bars.add(new MockBar(11985.36, 11989.83, 12019.53, 11971.93, numFunction));
        bars.add(new MockBar(11824.39, 11891.93, 11891.93, 11817.88, numFunction));
        bars.add(new MockBar(11892.50, 12040.16, 12050.75, 11892.50, numFunction));
        bars.add(new MockBar(12038.27, 12041.97, 12057.91, 12018.51, numFunction));
        bars.add(new MockBar(12040.68, 12062.26, 12080.54, 11981.05, numFunction));
        bars.add(new MockBar(12061.73, 12092.15, 12092.42, 12025.78, numFunction));
        bars.add(new MockBar(12092.38, 12161.63, 12188.76, 12092.30, numFunction));
        bars.add(new MockBar(12152.70, 12233.15, 12238.79, 12150.05, numFunction));
        bars.add(new MockBar(12229.29, 12239.89, 12254.23, 12188.19, numFunction));
        bars.add(new MockBar(12239.66, 12229.29, 12239.66, 12156.94, numFunction));
        bars.add(new MockBar(12227.78, 12273.26, 12285.94, 12180.48, numFunction));
        bars.add(new MockBar(12266.83, 12268.19, 12276.21, 12235.91, numFunction));
        bars.add(new MockBar(12266.75, 12226.64, 12267.66, 12193.27, numFunction));
        bars.add(new MockBar(12219.79, 12288.17, 12303.16, 12219.79, numFunction));
        bars.add(new MockBar(12287.72, 12318.14, 12331.31, 12253.24, numFunction));
        bars.add(new MockBar(12389.74, 12212.79, 12389.82, 12176.31, numFunction));

        data = new MockBarSeries(bars);
    }

    @Test
    public void keltnerChannelMiddleIndicatorTest() {
        KeltnerChannelMiddleIndicator km = new KeltnerChannelMiddleIndicator(new ClosePriceIndicator(data), 14);

        assertNumEquals(11764.23, km.getValue(13));
        assertNumEquals(11793.0687, km.getValue(14));
        assertNumEquals(11817.6182, km.getValue(15));
        assertNumEquals(11839.9944, km.getValue(16));
        assertNumEquals(11859.9725, km.getValue(17));
        assertNumEquals(11864.2335, km.getValue(18));
        assertNumEquals(11887.6903, km.getValue(19));
        assertNumEquals(11908.2609, km.getValue(20));
        assertNumEquals(11928.7941, km.getValue(21));
        assertNumEquals(11950.5749, km.getValue(22));
        assertNumEquals(11978.7156, km.getValue(23));
        assertNumEquals(12012.6402, km.getValue(24));
        assertNumEquals(12042.9401, km.getValue(25));
        assertNumEquals(12067.7868, km.getValue(26));
        assertNumEquals(12095.1832, km.getValue(27));
        assertNumEquals(12118.2508, km.getValue(28));
        assertNumEquals(12132.7027, km.getValue(29));
    }
}
