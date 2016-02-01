/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2016 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.trackers.ichimoku;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;

public class IchimokuIndicatorTest {

    protected TimeSeries data;
    
    @Before
    public void setUp() {
        
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(44.98, 45.05, 45.17, 44.96));
        ticks.add(new MockTick(45.05, 45.10, 45.15, 44.99));
        ticks.add(new MockTick(45.11, 45.19, 45.32, 45.11));
        ticks.add(new MockTick(45.19, 45.14, 45.25, 45.04));
        ticks.add(new MockTick(45.12, 45.15, 45.20, 45.10));
        ticks.add(new MockTick(45.15, 45.14, 45.20, 45.10));
        ticks.add(new MockTick(45.13, 45.10, 45.16, 45.07));
        ticks.add(new MockTick(45.12, 45.15, 45.22, 45.10));
        ticks.add(new MockTick(45.15, 45.22, 45.27, 45.14));
        ticks.add(new MockTick(45.24, 45.43, 45.45, 45.20));
        ticks.add(new MockTick(45.43, 45.44, 45.50, 45.39));
        ticks.add(new MockTick(45.43, 45.55, 45.60, 45.35));
        ticks.add(new MockTick(45.58, 45.55, 45.61, 45.39));
        ticks.add(new MockTick(45.45, 45.01, 45.55, 44.80));
        ticks.add(new MockTick(45.03, 44.23, 45.04, 44.17));
        ticks.add(new MockTick(44.23, 43.95, 44.29, 43.81));
        ticks.add(new MockTick(43.91, 43.08, 43.99, 43.08));
        ticks.add(new MockTick(43.07, 43.55, 43.65, 43.06));
        ticks.add(new MockTick(43.56, 43.95, 43.99, 43.53));
        ticks.add(new MockTick(43.93, 44.47, 44.58, 43.93));
        data = new MockTimeSeries(ticks);
    }
    
    @Test
    public void ichimoku()
    {
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(data, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(data, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(data, kijunSen, tenkanSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(data, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(data, 5);
        
        assertDecimalEquals(tenkanSen.getValue(3), 67.815);
        assertDecimalEquals(tenkanSen.getValue(4), 67.840);
        assertDecimalEquals(tenkanSen.getValue(5), 67.770);
        assertDecimalEquals(tenkanSen.getValue(6), 67.735);
        assertDecimalEquals(tenkanSen.getValue(7), 67.755);
        assertDecimalEquals(tenkanSen.getValue(8), 67.805);
        assertDecimalEquals(tenkanSen.getValue(16), 66.580);
        assertDecimalEquals(tenkanSen.getValue(17), 65.820);
        assertDecimalEquals(tenkanSen.getValue(18), 65.520);

        assertDecimalEquals(kijunSen.getValue(3), 67.800);
        assertDecimalEquals(kijunSen.getValue(4), 67.800);
        assertDecimalEquals(kijunSen.getValue(5), 67.815);
        assertDecimalEquals(kijunSen.getValue(6), 67.840);
        assertDecimalEquals(kijunSen.getValue(7), 67.770);
        assertDecimalEquals(kijunSen.getValue(16), 67.150);
        assertDecimalEquals(kijunSen.getValue(17), 67.080);
        assertDecimalEquals(kijunSen.getValue(18), 66.570);

        assertDecimalEquals(senkouSpanA.getValue(3), 67.8075);
        assertDecimalEquals(senkouSpanA.getValue(4), 67.8200);
        assertDecimalEquals(senkouSpanA.getValue(5), 67.7925);
        assertDecimalEquals(senkouSpanA.getValue(6), 67.7875);
        assertDecimalEquals(senkouSpanA.getValue(7), 67.7625);
        assertDecimalEquals(senkouSpanA.getValue(16), 66.8650);
        assertDecimalEquals(senkouSpanA.getValue(17), 66.4500);
        assertDecimalEquals(senkouSpanA.getValue(18), 66.0450);

        assertDecimalEquals(senkouSpanB.getValue(3), 67.8000);
        assertDecimalEquals(senkouSpanB.getValue(4), 67.8000);
        assertDecimalEquals(senkouSpanB.getValue(5), 67.8000);
        assertDecimalEquals(senkouSpanB.getValue(6), 67.8000);
        assertDecimalEquals(senkouSpanB.getValue(7), 67.8000);
        assertDecimalEquals(senkouSpanB.getValue(16), 67.1500);
        assertDecimalEquals(senkouSpanB.getValue(17), 67.1400);
        assertDecimalEquals(senkouSpanB.getValue(18), 67.1400);

        assertDecimalEquals(chikouSpan.getValue(5), 45.05);
        assertDecimalEquals(chikouSpan.getValue(6), 45.10);
        assertDecimalEquals(chikouSpan.getValue(7), 45.19);
        assertDecimalEquals(chikouSpan.getValue(8), 45.14);
        assertDecimalEquals(chikouSpan.getValue(19), 44.23);
    }
}
