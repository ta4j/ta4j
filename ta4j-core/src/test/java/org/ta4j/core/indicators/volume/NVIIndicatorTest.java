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
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class NVIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public NVIIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void getValue() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(1355.69).volume(2739.55).add();
        series.barBuilder().closePrice(1325.51).volume(3119.46).add();
        series.barBuilder().closePrice(1335.02).volume(3466.88).add();
        series.barBuilder().closePrice(1313.72).volume(2577.12).add();
        series.barBuilder().closePrice(1319.99).volume(2480.45).add();
        series.barBuilder().closePrice(1331.85).volume(2329.79).add();
        series.barBuilder().closePrice(1329.04).volume(2793.07).add();
        series.barBuilder().closePrice(1362.16).volume(3378.78).add();
        series.barBuilder().closePrice(1365.51).volume(2417.59).add();
        series.barBuilder().closePrice(1374.02).volume(1442.81).add();

        var nvi = new NVIIndicator(series);
        assertNumEquals(1000, nvi.getValue(0));
        assertNumEquals(1000, nvi.getValue(1));
        assertNumEquals(1000, nvi.getValue(2));
        assertNumEquals(984.0452, nvi.getValue(3));
        assertNumEquals(988.7417, nvi.getValue(4));
        assertNumEquals(997.6255, nvi.getValue(5));
        assertNumEquals(997.6255, nvi.getValue(6));
        assertNumEquals(997.6255, nvi.getValue(7));
        assertNumEquals(1000.079, nvi.getValue(8));
        assertNumEquals(1006.3116, nvi.getValue(9));
    }
}
