/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.ichimoku;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.ArrayList;
import java.util.List;

import static org.ta4j.core.TATestsUtils.assertNumEquals;

public class IchimokuIndicatorTest {

    protected TimeSeries data;

    @Before
    public void setUp() {

        List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(44.98, 45.05, 45.17, 44.96));
        bars.add(new MockBar(45.05, 45.10, 45.15, 44.99));
        bars.add(new MockBar(45.11, 45.19, 45.32, 45.11));
        bars.add(new MockBar(45.19, 45.14, 45.25, 45.04));
        bars.add(new MockBar(45.12, 45.15, 45.20, 45.10));
        bars.add(new MockBar(45.15, 45.14, 45.20, 45.10));
        bars.add(new MockBar(45.13, 45.10, 45.16, 45.07));
        bars.add(new MockBar(45.12, 45.15, 45.22, 45.10));
        bars.add(new MockBar(45.15, 45.22, 45.27, 45.14));
        bars.add(new MockBar(45.24, 45.43, 45.45, 45.20));
        bars.add(new MockBar(45.43, 45.44, 45.50, 45.39));
        bars.add(new MockBar(45.43, 45.55, 45.60, 45.35));
        bars.add(new MockBar(45.58, 45.55, 45.61, 45.39));
        bars.add(new MockBar(45.45, 45.01, 45.55, 44.80));
        bars.add(new MockBar(45.03, 44.23, 45.04, 44.17));
        bars.add(new MockBar(44.23, 43.95, 44.29, 43.81));
        bars.add(new MockBar(43.91, 43.08, 43.99, 43.08));
        bars.add(new MockBar(43.07, 43.55, 43.65, 43.06));
        bars.add(new MockBar(43.56, 43.95, 43.99, 43.53));
        bars.add(new MockBar(43.93, 44.47, 44.58, 43.93));
        data = new MockTimeSeries(bars);
    }

    @Test
    public void ichimoku() {
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(data, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(data, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(data, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(data, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(data, 5);

        assertNumEquals(tenkanSen.getValue(3), 45.155);
        assertNumEquals(tenkanSen.getValue(4), 45.18);
        assertNumEquals(tenkanSen.getValue(5), 45.145);
        assertNumEquals(tenkanSen.getValue(6), 45.135);
        assertNumEquals(tenkanSen.getValue(7), 45.145);
        assertNumEquals(tenkanSen.getValue(8), 45.17);
        assertNumEquals(tenkanSen.getValue(16), 44.06);
        assertNumEquals(tenkanSen.getValue(17), 43.675);
        assertNumEquals(tenkanSen.getValue(18), 43.525);

        assertNumEquals(kijunSen.getValue(3), 45.14);
        assertNumEquals(kijunSen.getValue(4), 45.14);
        assertNumEquals(kijunSen.getValue(5), 45.155);
        assertNumEquals(kijunSen.getValue(6), 45.18);
        assertNumEquals(kijunSen.getValue(7), 45.145);
        assertNumEquals(kijunSen.getValue(8), 45.17);
        assertNumEquals(kijunSen.getValue(16), 44.345);
        assertNumEquals(kijunSen.getValue(17), 44.305);
        assertNumEquals(kijunSen.getValue(18), 44.05);

        assertNumEquals(senkouSpanA.getValue(3), 45.1475);
        assertNumEquals(senkouSpanA.getValue(4), 45.16);
        assertNumEquals(senkouSpanA.getValue(5), 45.15);
        assertNumEquals(senkouSpanA.getValue(6), 45.1575);
        assertNumEquals(senkouSpanA.getValue(7), 45.145);
        assertNumEquals(senkouSpanA.getValue(8), 45.17);
        assertNumEquals(senkouSpanA.getValue(16), 44.2025);
        assertNumEquals(senkouSpanA.getValue(17), 43.99);
        assertNumEquals(senkouSpanA.getValue(18), 43.7875);

        assertNumEquals(senkouSpanB.getValue(3), 45.14);
        assertNumEquals(senkouSpanB.getValue(4), 45.14);
        assertNumEquals(senkouSpanB.getValue(5), 45.14);
        assertNumEquals(senkouSpanB.getValue(6), 45.14);
        assertNumEquals(senkouSpanB.getValue(7), 45.14);
        assertNumEquals(senkouSpanB.getValue(8), 45.14);
        assertNumEquals(senkouSpanB.getValue(16), 44.345);
        assertNumEquals(senkouSpanB.getValue(17), 44.335);
        assertNumEquals(senkouSpanB.getValue(18), 44.335);

        assertNumEquals(chikouSpan.getValue(5), 45.05);
        assertNumEquals(chikouSpan.getValue(6), 45.10);
        assertNumEquals(chikouSpan.getValue(7), 45.19);
        assertNumEquals(chikouSpan.getValue(8), 45.14);
        assertNumEquals(chikouSpan.getValue(19), 44.23);
    }
}
