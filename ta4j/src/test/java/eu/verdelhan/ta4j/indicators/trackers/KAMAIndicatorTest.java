/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.trackers;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;

import org.junit.Before;
import org.junit.Test;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;

/**
 * The Class KAMAIndicatorTest.
 * 
 * @see http://stockcharts.com/school/data/media/chart_school/technical_indicators_and_overlays/kaufman_s_adaptive_moving_average/cs-kama.xls
 */
public class KAMAIndicatorTest {

    private TimeSeries data;
    
    
    @Before
    public void setUp() {

        data = new MockTimeSeries(
                110.46, 109.80, 110.17, 109.82, 110.15,
                109.31, 109.05, 107.94, 107.76, 109.24,
                109.40, 108.50, 107.96, 108.55, 108.85,
                110.44, 109.89, 110.70, 110.79, 110.22,
                110.00, 109.27, 106.69, 107.07, 107.92,
                107.95, 107.70, 107.97, 106.09, 106.03,
                107.65, 109.54, 110.26, 110.38, 111.94,
                113.59, 113.98, 113.91, 112.62, 112.20,
                111.10, 110.18, 111.13, 111.55, 112.08,
                111.95, 111.60, 111.39, 112.25
            
        );
    }
    
    @Test
    public void kama() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(data);
        KAMAIndicator kama = new KAMAIndicator(closePrice, 10, 2, 30);
        
        assertDecimalEquals(kama.getValue(9), 109.2400);
        assertDecimalEquals(kama.getValue(10), 109.2449);
        assertDecimalEquals(kama.getValue(11), 109.2165);
        assertDecimalEquals(kama.getValue(12), 109.1173);
        assertDecimalEquals(kama.getValue(13), 109.0981);
        assertDecimalEquals(kama.getValue(14), 109.0894);
        assertDecimalEquals(kama.getValue(15), 109.1240);
        assertDecimalEquals(kama.getValue(16), 109.1376);
        assertDecimalEquals(kama.getValue(17), 109.2769);
        assertDecimalEquals(kama.getValue(18), 109.4365);
        assertDecimalEquals(kama.getValue(19), 109.4569);
        assertDecimalEquals(kama.getValue(20), 109.4651);
        assertDecimalEquals(kama.getValue(21), 109.4612);
        assertDecimalEquals(kama.getValue(22), 109.3904);
        assertDecimalEquals(kama.getValue(23), 109.3165);
        assertDecimalEquals(kama.getValue(24), 109.2924);
        assertDecimalEquals(kama.getValue(25), 109.1836);
        assertDecimalEquals(kama.getValue(26), 109.0778);
        assertDecimalEquals(kama.getValue(27), 108.9498);
        assertDecimalEquals(kama.getValue(28), 108.4230);
        assertDecimalEquals(kama.getValue(29), 108.0157);
        assertDecimalEquals(kama.getValue(30), 107.9967);
        assertDecimalEquals(kama.getValue(31), 108.0069);
        assertDecimalEquals(kama.getValue(32), 108.2596);
        assertDecimalEquals(kama.getValue(33), 108.4818);
        assertDecimalEquals(kama.getValue(34), 108.9119);
        assertDecimalEquals(kama.getValue(35), 109.6734);
        assertDecimalEquals(kama.getValue(36), 110.4947);
        assertDecimalEquals(kama.getValue(37), 111.1077);
        assertDecimalEquals(kama.getValue(38), 111.4622);
        assertDecimalEquals(kama.getValue(39), 111.6092);
        assertDecimalEquals(kama.getValue(40), 111.5663);
        assertDecimalEquals(kama.getValue(41), 111.5491);
        assertDecimalEquals(kama.getValue(42), 111.5425);
        assertDecimalEquals(kama.getValue(43), 111.5426);
        assertDecimalEquals(kama.getValue(44), 111.5457);
        assertDecimalEquals(kama.getValue(45), 111.5658);
        assertDecimalEquals(kama.getValue(46), 111.5688);
        assertDecimalEquals(kama.getValue(47), 111.5522);
        assertDecimalEquals(kama.getValue(48), 111.5595);
    }
}
