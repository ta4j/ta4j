/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class KSTIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private MockBarSeries data;

    public KSTIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {

        data = new MockBarSeries(numFunction, 1344.78, 1357.98, 1355.69, 1325.51, 1335.02, 1313.72, 1319.99, 1331.85,
                1329.04, 1362.16, 1365.51, 1374.02, 1367.58, 1354.68, 1352.46, 1341.47, 1341.45, 1334.76, 1356.78,
                1353.64, 1363.67, 1372.78, 1376.51, 1362.66, 1350.52, 1338.31, 1337.89, 1360.02, 1385.97, 1385.30,
                1379.32, 1375.32, 1365.00, 1390.99, 1394.23, 1401.35, 1402.22, 1402.80, 1405.87, 1404.11, 1403.93,
                1405.53, 1415.51, 1418.16, 1418.13, 1413.17, 1413.49, 1402.08, 1411.13, 1410.44);
    }

    @Test
    public void KSTIndicator() {
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(data);
        KSTIndicator kstIndicator = new KSTIndicator(closePriceIndicator);
        assertNumEquals(36.597637, kstIndicator.getValue(44));
        assertNumEquals(37.228478, kstIndicator.getValue(45));
        assertNumEquals(38.381911, kstIndicator.getValue(46));
        assertNumEquals(38.783888, kstIndicator.getValue(47));
        assertNumEquals(37.543147, kstIndicator.getValue(48));
        assertNumEquals(36.253502, kstIndicator.getValue(49));
    }

}
