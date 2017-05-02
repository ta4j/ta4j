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
package eu.verdelhan.ta4j.indicators.trackers.keltner;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;

public class KeltnerChannelUpperIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(11577.43, 11670.75, 11711.47, 11577.35));
        ticks.add(new MockTick(11670.90, 11691.18, 11698.22, 11635.74));
        ticks.add(new MockTick(11688.61, 11722.89, 11742.68, 11652.89));
        ticks.add(new MockTick(11716.93, 11697.31, 11736.74, 11667.46));
        ticks.add(new MockTick(11696.86, 11674.76, 11726.94, 11599.68));
        ticks.add(new MockTick(11672.34, 11637.45, 11677.33, 11573.87));
        ticks.add(new MockTick(11638.51, 11671.88, 11704.12, 11635.48));
        ticks.add(new MockTick(11673.62, 11755.44, 11782.23, 11673.62));
        ticks.add(new MockTick(11753.70, 11731.90, 11757.25, 11700.53));
        ticks.add(new MockTick(11732.13, 11787.38, 11794.15, 11698.83));
        ticks.add(new MockTick(11783.82, 11837.93, 11858.78, 11777.99));
        ticks.add(new MockTick(11834.21, 11825.29, 11861.24, 11798.46));
        ticks.add(new MockTick(11823.70, 11822.80, 11845.16, 11744.77));
        ticks.add(new MockTick(11822.95, 11871.84, 11905.48, 11822.80));
        ticks.add(new MockTick(11873.43, 11980.52, 11982.94, 11867.98));
        ticks.add(new MockTick(11980.52, 11977.19, 11985.97, 11898.74));
        ticks.add(new MockTick(11978.85, 11985.44, 12020.52, 11961.83));
        ticks.add(new MockTick(11985.36, 11989.83, 12019.53, 11971.93));
        ticks.add(new MockTick(11824.39, 11891.93, 11891.93, 11817.88));
        ticks.add(new MockTick(11892.50, 12040.16, 12050.75, 11892.50));
        ticks.add(new MockTick(12038.27, 12041.97, 12057.91, 12018.51));
        ticks.add(new MockTick(12040.68, 12062.26, 12080.54, 11981.05));
        ticks.add(new MockTick(12061.73, 12092.15, 12092.42, 12025.78));
        ticks.add(new MockTick(12092.38, 12161.63, 12188.76, 12092.30));
        ticks.add(new MockTick(12152.70, 12233.15, 12238.79, 12150.05));
        ticks.add(new MockTick(12229.29, 12239.89, 12254.23, 12188.19));
        ticks.add(new MockTick(12239.66, 12229.29, 12239.66, 12156.94));
        ticks.add(new MockTick(12227.78, 12273.26, 12285.94, 12180.48));
        ticks.add(new MockTick(12266.83, 12268.19, 12276.21, 12235.91));
        ticks.add(new MockTick(12266.75, 12226.64, 12267.66, 12193.27));
        ticks.add(new MockTick(12219.79, 12288.17, 12303.16, 12219.79));
        ticks.add(new MockTick(12287.72, 12318.14, 12331.31, 12253.24));
        
        data = new MockTimeSeries(ticks);
    }

    @Test
    public void keltnerChannelUpperIndicatorTest() {
        
        KeltnerChannelMiddleIndicator km = new KeltnerChannelMiddleIndicator(new ClosePriceIndicator(data), 14);
        KeltnerChannelUpperIndicator ku = new KeltnerChannelUpperIndicator(km, Decimal.valueOf(2), 14);

        assertDecimalEquals(ku.getValue(13), 11857.4642);
        assertDecimalEquals(ku.getValue(14), 11896.8619);
        assertDecimalEquals(ku.getValue(15), 11927.1486);
        assertDecimalEquals(ku.getValue(16), 11950.6832);
        assertDecimalEquals(ku.getValue(17), 11970.0729);
        assertDecimalEquals(ku.getValue(18), 11991.4828);
        assertDecimalEquals(ku.getValue(19), 12028.9281);
        assertDecimalEquals(ku.getValue(20), 12045.3761);
        assertDecimalEquals(ku.getValue(21), 12070.6204);
        assertDecimalEquals(ku.getValue(22), 12092.044);
        assertDecimalEquals(ku.getValue(23), 12124.1007);
        assertDecimalEquals(ku.getValue(24), 12160.508);
        assertDecimalEquals(ku.getValue(25), 12189.8451);
        assertDecimalEquals(ku.getValue(26), 12216.1915);
        assertDecimalEquals(ku.getValue(27), 12248.1771);
        assertDecimalEquals(ku.getValue(28), 12266.181);
        assertDecimalEquals(ku.getValue(29), 12280.8623);
    }
}
