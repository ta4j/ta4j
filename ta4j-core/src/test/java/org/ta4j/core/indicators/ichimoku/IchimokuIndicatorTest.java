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
package org.ta4j.core.indicators.ichimoku;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class IchimokuIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    protected BarSeries data;

    public IchimokuIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        data.barBuilder().openPrice(44.98).closePrice(45.05).highPrice(45.17).lowPrice(44.96).add();
        data.barBuilder().openPrice(45.05).closePrice(45.10).highPrice(45.15).lowPrice(44.99).add();
        data.barBuilder().openPrice(45.11).closePrice(45.19).highPrice(45.32).lowPrice(45.11).add();
        data.barBuilder().openPrice(45.19).closePrice(45.14).highPrice(45.25).lowPrice(45.04).add();
        data.barBuilder().openPrice(45.12).closePrice(45.15).highPrice(45.20).lowPrice(45.10).add();
        data.barBuilder().openPrice(45.15).closePrice(45.14).highPrice(45.20).lowPrice(45.10).add();
        data.barBuilder().openPrice(45.13).closePrice(45.10).highPrice(45.16).lowPrice(45.07).add();
        data.barBuilder().openPrice(45.12).closePrice(45.15).highPrice(45.22).lowPrice(45.10).add();
        data.barBuilder().openPrice(45.15).closePrice(45.22).highPrice(45.27).lowPrice(45.14).add();
        data.barBuilder().openPrice(45.24).closePrice(45.43).highPrice(45.45).lowPrice(45.20).add();
        data.barBuilder().openPrice(45.43).closePrice(45.44).highPrice(45.50).lowPrice(45.39).add();
        data.barBuilder().openPrice(45.43).closePrice(45.55).highPrice(45.60).lowPrice(45.35).add();
        data.barBuilder().openPrice(45.58).closePrice(45.55).highPrice(45.61).lowPrice(45.39).add();
        data.barBuilder().openPrice(45.45).closePrice(45.01).highPrice(45.55).lowPrice(44.80).add();
        data.barBuilder().openPrice(45.03).closePrice(44.23).highPrice(45.04).lowPrice(44.17).add();
        data.barBuilder().openPrice(44.23).closePrice(43.95).highPrice(44.29).lowPrice(43.81).add();
        data.barBuilder().openPrice(43.91).closePrice(43.08).highPrice(43.99).lowPrice(43.08).add();
        data.barBuilder().openPrice(43.07).closePrice(43.55).highPrice(43.65).lowPrice(43.06).add();
        data.barBuilder().openPrice(43.56).closePrice(43.95).highPrice(43.99).lowPrice(43.53).add();
        data.barBuilder().openPrice(43.93).closePrice(44.47).highPrice(44.58).lowPrice(43.93).add();
    }

    @Test
    public void ichimoku() {
        var tenkanSen = new IchimokuTenkanSenIndicator(data, 3);
        var kijunSen = new IchimokuKijunSenIndicator(data, 5);
        var senkouSpanA = new IchimokuSenkouSpanAIndicator(data, tenkanSen, kijunSen, 5);
        var senkouSpanB = new IchimokuSenkouSpanBIndicator(data, 9, 5);
        final int chikouSpanTimeDelay = 5;
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(data, chikouSpanTimeDelay);

        assertNumEquals(45.155, tenkanSen.getValue(3));
        assertNumEquals(45.18, tenkanSen.getValue(4));
        assertNumEquals(45.145, tenkanSen.getValue(5));
        assertNumEquals(45.135, tenkanSen.getValue(6));
        assertNumEquals(45.145, tenkanSen.getValue(7));
        assertNumEquals(45.17, tenkanSen.getValue(8));
        assertNumEquals(44.06, tenkanSen.getValue(16));
        assertNumEquals(43.675, tenkanSen.getValue(17));
        assertNumEquals(43.525, tenkanSen.getValue(18));

        assertNumEquals(45.14, kijunSen.getValue(3));
        assertNumEquals(45.14, kijunSen.getValue(4));
        assertNumEquals(45.155, kijunSen.getValue(5));
        assertNumEquals(45.18, kijunSen.getValue(6));
        assertNumEquals(45.145, kijunSen.getValue(7));
        assertNumEquals(45.17, kijunSen.getValue(8));
        assertNumEquals(44.345, kijunSen.getValue(16));
        assertNumEquals(44.305, kijunSen.getValue(17));
        assertNumEquals(44.05, kijunSen.getValue(18));

        assertNumEquals(NaN.NaN, senkouSpanA.getValue(3));
        assertNumEquals(45.065, senkouSpanA.getValue(4));
        assertNumEquals(45.1475, senkouSpanA.getValue(7));
        assertNumEquals(45.16, senkouSpanA.getValue(8));
        assertNumEquals(45.15, senkouSpanA.getValue(9));
        assertNumEquals(45.1575, senkouSpanA.getValue(10));
        assertNumEquals(45.4275, senkouSpanA.getValue(16));
        assertNumEquals(45.205, senkouSpanA.getValue(17));
        assertNumEquals(44.89, senkouSpanA.getValue(18));

        assertNumEquals(NaN.NaN, senkouSpanB.getValue(3));
        assertNumEquals(45.065, senkouSpanB.getValue(4));
        assertNumEquals(45.065, senkouSpanB.getValue(5));
        assertNumEquals(45.14, senkouSpanB.getValue(6));
        assertNumEquals(45.14, senkouSpanB.getValue(7));
        assertNumEquals(45.22, senkouSpanB.getValue(13));
        assertNumEquals(45.34, senkouSpanB.getValue(16));
        assertNumEquals(45.205, senkouSpanB.getValue(17));
        assertNumEquals(44.89, senkouSpanB.getValue(18));

        assertNumEquals(data.getBar(chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(0));
        assertNumEquals(data.getBar(1 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(1));
        assertNumEquals(data.getBar(2 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(2));
        assertNumEquals(data.getBar(3 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(3));
        assertNumEquals(data.getBar(4 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(4));
        assertNumEquals(data.getBar(5 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(5));
        assertNumEquals(data.getBar(6 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(6));
        assertNumEquals(data.getBar(7 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(7));
        assertNumEquals(data.getBar(8 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(8));
        assertNumEquals(data.getBar(9 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(9));
        assertNumEquals(data.getBar(10 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(10));
        assertNumEquals(data.getBar(11 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(11));
        assertNumEquals(data.getBar(12 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(12));
        assertNumEquals(data.getBar(13 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(13));
        assertNumEquals(data.getBar(14 + chikouSpanTimeDelay).getClosePrice(), chikouSpan.getValue(14));
        assertNumEquals(NaN.NaN, chikouSpan.getValue(15));
        assertNumEquals(NaN.NaN, chikouSpan.getValue(16));
        assertNumEquals(NaN.NaN, chikouSpan.getValue(17));
        assertNumEquals(NaN.NaN, chikouSpan.getValue(18));
        assertNumEquals(NaN.NaN, chikouSpan.getValue(19));
    }
}
