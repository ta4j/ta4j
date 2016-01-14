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
package eu.verdelhan.ta4j.indicators.trackers.keltner;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;

public class KeltnerMiddleIndicatorTest {

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
    	ticks.add(new MockTick(12389.74, 12212.79, 12389.82, 12176.31));
    	ticks.add(new MockTick(12211.81, 12105.78, 12221.12, 12063.43));
    	ticks.add(new MockTick(12104.56, 12068.50, 12129.62, 11983.17));
    	ticks.add(new MockTick(12060.93, 12130.45, 12151.03, 12060.93));
    	ticks.add(new MockTick(12130.45, 12226.34, 12235.04, 12130.15));
    	ticks.add(new MockTick(12226.49, 12058.02, 12261.38, 12054.99));
    	ticks.add(new MockTick(12057.34, 12066.80, 12115.12, 12018.63));
    	ticks.add(new MockTick(12068.01, 12258.20, 12283.10, 12068.01));
    	ticks.add(new MockTick(12171.09, 12090.03, 12243.44, 12041.60));
    	ticks.add(new MockTick(12085.87, 12214.38, 12251.20, 12072.21));
    	ticks.add(new MockTick(12211.16, 12213.09, 12257.82, 12156.60));
    	ticks.add(new MockTick(12211.43, 12023.89, 12211.43, 11974.39));
    	ticks.add(new MockTick(11976.96, 12044.40, 12087.01, 11936.32));
    	ticks.add(new MockTick(12042.13, 11993.16, 12042.13, 11897.31));
    	ticks.add(new MockTick(11988.69, 11891.21, 11988.69, 11696.25));
    	ticks.add(new MockTick(11854.20, 11613.30, 11856.70, 11555.48));
    	ticks.add(new MockTick(11614.89, 11774.59, 11800.54, 11614.82));
    	ticks.add(new MockTick(11777.23, 11858.52, 11927.09, 11777.23));
    	ticks.add(new MockTick(11860.11, 12036.53, 12078.30, 11860.11));
    	ticks.add(new MockTick(12036.37, 12018.63, 12050.98, 12002.85));
    	ticks.add(new MockTick(12018.40, 12086.02, 12116.14, 11972.61));
    	ticks.add(new MockTick(12087.54, 12170.56, 12191.18, 12087.54));
    	ticks.add(new MockTick(12170.71, 12220.59, 12259.79, 12170.71));
    	ticks.add(new MockTick(12221.19, 12197.88, 12272.92, 12197.88));
    	ticks.add(new MockTick(12194.48, 12279.01, 12285.41, 12173.51));
    	ticks.add(new MockTick(12280.07, 12350.61, 12383.46, 12280.07));
    	ticks.add(new MockTick(12350.76, 12319.73, 12381.68, 12319.01));
    	ticks.add(new MockTick(12321.02, 12376.72, 12419.71, 12321.02));
    	ticks.add(new MockTick(12374.60, 12400.03, 12407.41, 12369.15));
    	ticks.add(new MockTick(12402.08, 12393.90, 12438.14, 12353.34));
    	ticks.add(new MockTick(12386.66, 12426.75, 12450.93, 12386.66));
    	ticks.add(new MockTick(12426.45, 12409.49, 12440.56, 12328.36));
    	ticks.add(new MockTick(11945.33, 11952.97, 12011.66, 11917.78));
    	ticks.add(new MockTick(11951.38, 12076.11, 12120.80, 11951.38));
    	ticks.add(new MockTick(12075.12, 11897.27, 12075.20, 11862.53));
    	ticks.add(new MockTick(11896.13, 11961.52, 11990.02, 11875.77));
    	ticks.add(new MockTick(11962.66, 12004.36, 12072.89, 11962.51));
    	ticks.add(new MockTick(12081.33, 12190.01, 12217.33, 12081.18));
    	ticks.add(new MockTick(12189.71, 12109.67, 12207.99, 12105.85));
    	ticks.add(new MockTick(12108.35, 12050.00, 12108.73, 11874.94));
    	ticks.add(new MockTick(12049.24, 11934.58, 12057.19, 11925.42));
    	ticks.add(new MockTick(11934.66, 12043.56, 12098.81, 11934.05));
    	ticks.add(new MockTick(12042.28, 12188.69, 12190.43, 12042.28));
    	ticks.add(new MockTick(12187.63, 12261.42, 12284.39, 12175.86));
    	ticks.add(new MockTick(12262.25, 12414.34, 12427.09, 12262.10));
    	ticks.add(new MockTick(12412.07, 12582.77, 12596.13, 12404.08));
    	ticks.add(new MockTick(12583.00, 12569.87, 12601.80, 12540.58));
    	ticks.add(new MockTick(12562.47, 12626.02, 12643.24, 12539.21));
    	ticks.add(new MockTick(12627.23, 12719.49, 12753.89, 12627.23));
    	ticks.add(new MockTick(12717.90, 12657.20, 12717.90, 12567.41));
    	ticks.add(new MockTick(12655.62, 12505.76, 12655.84, 12470.30));
    	ticks.add(new MockTick(12505.54, 12446.88, 12570.58, 12446.88));
    	ticks.add(new MockTick(12447.33, 12491.61, 12611.04, 12447.33));
    	ticks.add(new MockTick(12491.53, 12437.12, 12581.98, 12414.41));
    	ticks.add(new MockTick(12437.12, 12479.73, 12504.82, 12406.09));
    	ticks.add(new MockTick(12475.11, 12385.16, 12475.26, 12296.23));
    	ticks.add(new MockTick(12386.03, 12587.42, 12607.56, 12385.96));
    	ticks.add(new MockTick(12583.68, 12571.91, 12603.51, 12546.56));
    	ticks.add(new MockTick(12567.07, 12724.41, 12751.43, 12566.61));
    	ticks.add(new MockTick(12724.71, 12681.16, 12740.87, 12644.19));
    	ticks.add(new MockTick(12679.72, 12592.80, 12679.95, 12536.19));
    	ticks.add(new MockTick(12592.12, 12501.30, 12593.40, 12489.04));
    	ticks.add(new MockTick(12498.42, 12302.55, 12498.65, 12289.69));
    	ticks.add(new MockTick(12301.72, 12240.11, 12384.90, 12226.83));
    	ticks.add(new MockTick(12239.36, 12143.24, 12243.07, 12083.45));
    	ticks.add(new MockTick(12144.22, 12132.49, 12282.42, 11998.08));
    	ticks.add(new MockTick(12129.77, 11866.62, 12130.30, 11865.56));
    	ticks.add(new MockTick(11863.74, 11896.44, 11904.91, 11700.34));
    	ticks.add(new MockTick(11893.86, 11383.68, 11893.94, 11372.14));
    	ticks.add(new MockTick(11383.98, 11444.61, 11555.41, 11139.00));
    	ticks.add(new MockTick(11433.93, 10809.85, 11434.09, 10809.85));
    	ticks.add(new MockTick(10810.91, 11239.77, 11244.01, 10604.07));
    	ticks.add(new MockTick(11228.00, 10719.94, 11228.00, 10686.49));
    	ticks.add(new MockTick(10729.85, 11143.31, 11278.90, 10729.85));
    	ticks.add(new MockTick(11143.46, 11269.02, 11346.67, 11142.18));
    	ticks.add(new MockTick(11269.85, 11482.90, 11484.60, 11269.85));
    	ticks.add(new MockTick(11480.48, 11405.93, 11488.01, 11292.63));
    	ticks.add(new MockTick(11392.01, 11410.21, 11529.67, 11322.30));
    	ticks.add(new MockTick(11406.27, 10990.58, 11406.50, 10881.60));
    	ticks.add(new MockTick(10989.75, 10817.65, 11086.40, 10801.41));
    	ticks.add(new MockTick(10820.37, 10854.65, 11020.55, 10820.37));
    	ticks.add(new MockTick(10854.58, 11176.76, 11176.84, 10854.43));
    	ticks.add(new MockTick(11175.78, 11320.71, 11331.57, 11113.04));
    	ticks.add(new MockTick(11321.02, 11149.82, 11406.39, 11106.76));
    	ticks.add(new MockTick(11145.20, 11284.54, 11326.43, 10929.20));
    	ticks.add(new MockTick(11286.65, 11539.25, 11541.78, 11286.58));
    	ticks.add(new MockTick(11532.13, 11559.95, 11630.07, 11429.39));
    	ticks.add(new MockTick(11560.48, 11613.53, 11712.60, 11528.08));
    	ticks.add(new MockTick(11613.30, 11493.57, 11716.84, 11488.46));
    	ticks.add(new MockTick(11492.06, 11240.26, 11492.14, 11211.35));
    	ticks.add(new MockTick(11237.31, 11139.30, 11237.46, 10932.53));
    	ticks.add(new MockTick(11137.63, 11414.86, 11414.86, 11137.63));
    	ticks.add(new MockTick(11414.86, 11295.81, 11477.30, 11283.74));
    	ticks.add(new MockTick(11294.60, 10992.13, 11294.83, 10935.64));
    	ticks.add(new MockTick(10990.01, 11061.12, 11062.03, 10824.76));
    	ticks.add(new MockTick(11054.99, 11105.85, 11140.85, 10987.18));
    	ticks.add(new MockTick(11106.83, 11246.73, 11386.78, 10993.84));
    	ticks.add(new MockTick(11247.72, 11433.18, 11433.40, 11247.49));
    	ticks.add(new MockTick(11433.71, 11509.09, 11532.47, 11407.41));
    	ticks.add(new MockTick(11506.67, 11401.01, 11506.82, 11255.25));
    	ticks.add(new MockTick(11401.47, 11408.66, 11550.22, 11373.92));
    	ticks.add(new MockTick(11408.58, 11124.84, 11447.86, 11117.28));
    	ticks.add(new MockTick(11121.89, 10733.83, 11122.12, 10597.14));
    	ticks.add(new MockTick(10732.77, 10771.48, 10808.49, 10638.73));
    	ticks.add(new MockTick(10771.78, 11043.86, 11057.49, 10771.78));
    	ticks.add(new MockTick(11045.23, 11190.69, 11369.30, 11045.23));
    	ticks.add(new MockTick(11189.10, 11010.90, 11317.08, 10996.98));
    	ticks.add(new MockTick(11012.79, 11153.98, 11271.14, 10965.45));
    	ticks.add(new MockTick(11152.32, 10913.38, 11152.39, 10909.52));
    	ticks.add(new MockTick(10912.10, 10655.30, 10979.19, 10653.34));
    	ticks.add(new MockTick(10651.44, 10808.71, 10825.44, 10404.49));
    	ticks.add(new MockTick(10800.47, 10939.95, 10950.89, 10738.10));
    	ticks.add(new MockTick(10939.87, 11123.33, 11132.60, 10858.67));
    	ticks.add(new MockTick(11123.41, 11103.12, 11232.05, 11051.13));
    	ticks.add(new MockTick(11104.56, 11433.18, 11433.33, 11104.56));
    	ticks.add(new MockTick(11432.80, 11416.30, 11447.86, 11365.67));
    	ticks.add(new MockTick(11417.36, 11518.85, 11625.30, 11417.28));
    	ticks.add(new MockTick(11518.09, 11478.13, 11518.09, 11377.82));
    	ticks.add(new MockTick(11478.97, 11644.49, 11646.83, 11478.66));
    	ticks.add(new MockTick(11643.35, 11397.00, 11643.35, 11378.35));
    	ticks.add(new MockTick(11396.17, 11577.05, 11652.74, 11296.12));
    	ticks.add(new MockTick(11577.54, 11504.62, 11633.70, 11469.17));
    	ticks.add(new MockTick(11502.13, 11541.78, 11581.25, 11391.14));
    	ticks.add(new MockTick(11543.22, 11808.79, 11812.46, 11542.84));
    	ticks.add(new MockTick(11807.96, 11913.62, 11940.75, 11805.77));
    	ticks.add(new MockTick(11912.63, 11706.62, 11912.86, 11682.52));
    	ticks.add(new MockTick(11707.76, 11869.04, 11891.21, 11694.36));
    	ticks.add(new MockTick(11872.07, 12208.55, 12284.31, 11872.07));
    	ticks.add(new MockTick(12207.34, 12231.11, 12251.92, 12164.24));
    	ticks.add(new MockTick(12229.22, 11955.01, 12229.29, 11954.41));
    	ticks.add(new MockTick(11951.53, 11657.96, 11951.76, 11630.03));
    	ticks.add(new MockTick(11658.49, 11836.04, 11876.83, 11658.49));
    	ticks.add(new MockTick(11835.59, 12044.47, 12065.93, 11835.43));
    	ticks.add(new MockTick(12043.41, 11983.24, 12043.49, 11850.31));
    	ticks.add(new MockTick(11983.02, 12068.39, 12074.44, 11880.69));
    	ticks.add(new MockTick(12055.52, 12170.18, 12187.51, 12002.17));
    	ticks.add(new MockTick(12166.40, 11780.94, 12166.40, 11736.93));
    	ticks.add(new MockTick(11780.03, 11893.79, 11961.14, 11779.88));
    	ticks.add(new MockTick(11896.28, 12153.68, 12179.72, 11896.28));
    	ticks.add(new MockTick(12153.00, 12078.98, 12170.56, 12027.03));
    	ticks.add(new MockTick(12077.92, 12096.16, 12165.11, 12001.26));
    	ticks.add(new MockTick(12084.74, 11905.59, 12109.03, 11890.57));
        data = new MockTimeSeries(ticks);
    }
    
    @Test
    public void keltnerMiddleIndicatorTest()
    {
    	KeltnerMiddleIndicator km = new KeltnerMiddleIndicator(new ClosePriceIndicator(data), 14);
    	
    	assertDecimalEquals(km.getValue(13), 11764.2300);
    	assertDecimalEquals(km.getValue(14), 11794.2345);
    	assertDecimalEquals(km.getValue(15), 11819.9335);
    	assertDecimalEquals(km.getValue(16), 11842.0440);
    	assertDecimalEquals(km.getValue(17), 11861.5333);
    	assertDecimalEquals(km.getValue(18), 11865.3065);
    	assertDecimalEquals(km.getValue(19), 11888.6047);
    	assertDecimalEquals(km.getValue(20), 11908.9567);
    	assertDecimalEquals(km.getValue(21), 11929.2530);
    	assertDecimalEquals(km.getValue(22), 11950.4925);
    	assertDecimalEquals(km.getValue(23), 11977.7606);
    	assertDecimalEquals(km.getValue(24), 12010.9641);
    	assertDecimalEquals(km.getValue(25), 12040.6320);
    	assertDecimalEquals(km.getValue(26), 12064.4474);
    	assertDecimalEquals(km.getValue(27), 12090.7992);
    	assertDecimalEquals(km.getValue(28), 12113.2256);
    	assertDecimalEquals(km.getValue(29), 12127.1137);
    	assertDecimalEquals(km.getValue(30), 12147.9852);
    	assertDecimalEquals(km.getValue(31), 12171.6973);
    	assertDecimalEquals(km.getValue(32), 12177.5097);
    	assertDecimalEquals(km.getValue(33), 12168.4757);
    	assertDecimalEquals(km.getValue(34), 12155.0999);
    	assertDecimalEquals(km.getValue(35), 12152.1960);
    	assertDecimalEquals(km.getValue(36), 12161.7124);
    	assertDecimalEquals(km.getValue(37), 12146.8211);
    	assertDecimalEquals(km.getValue(38), 12135.7473);
    	assertDecimalEquals(km.getValue(39), 12152.4143);
    	assertDecimalEquals(km.getValue(40), 12144.0701);
    	assertDecimalEquals(km.getValue(41), 12152.8541);
    	assertDecimalEquals(km.getValue(42), 12161.1587);
    	assertDecimalEquals(km.getValue(43), 12142.8145);
    	assertDecimalEquals(km.getValue(44), 12129.7621);
    	assertDecimalEquals(km.getValue(45), 12114.2077);
    	assertDecimalEquals(km.getValue(46), 12086.9927);
    	assertDecimalEquals(km.getValue(47), 12026.7880);
    	assertDecimalEquals(km.getValue(48), 11995.3329);
    	assertDecimalEquals(km.getValue(49), 11978.1075);
    	assertDecimalEquals(km.getValue(50), 11987.9616);
    	assertDecimalEquals(km.getValue(51), 11993.0819);
    	assertDecimalEquals(km.getValue(52), 12004.3395);
    	assertDecimalEquals(km.getValue(53), 12027.1332);
    	assertDecimalEquals(km.getValue(54), 12051.6652);
    	assertDecimalEquals(km.getValue(55), 12069.8799);
    	assertDecimalEquals(km.getValue(56), 12098.6861);
    	assertDecimalEquals(km.getValue(57), 12132.0162);
    	assertDecimalEquals(km.getValue(58), 12156.5841);
    	assertDecimalEquals(km.getValue(59), 12187.1465);
    	assertDecimalEquals(km.getValue(60), 12220.1322);
    	assertDecimalEquals(km.getValue(61), 12246.0331);
    	assertDecimalEquals(km.getValue(62), 12270.9277);
    	assertDecimalEquals(km.getValue(63), 12287.0178);
    	assertDecimalEquals(km.getValue(64), 12241.8554);
    	assertDecimalEquals(km.getValue(65), 12218.3281);
    	assertDecimalEquals(km.getValue(66), 12171.1789);
    	assertDecimalEquals(km.getValue(67), 12140.1405);
    	assertDecimalEquals(km.getValue(68), 12117.9825);
    	assertDecimalEquals(km.getValue(69), 12122.7405);
    	assertDecimalEquals(km.getValue(70), 12117.6632);
    	assertDecimalEquals(km.getValue(71), 12105.8606);
    	assertDecimalEquals(km.getValue(72), 12080.6710);
    	assertDecimalEquals(km.getValue(73), 12074.8478);
    	assertDecimalEquals(km.getValue(74), 12093.0542);
    	assertDecimalEquals(km.getValue(75), 12117.5780);
    	assertDecimalEquals(km.getValue(76), 12159.4212);
    	assertDecimalEquals(km.getValue(77), 12221.3785);
    	assertDecimalEquals(km.getValue(78), 12272.2437);
    	assertDecimalEquals(km.getValue(79), 12324.8725);
    	assertDecimalEquals(km.getValue(80), 12381.1084);
    	assertDecimalEquals(km.getValue(81), 12420.2621);
    	assertDecimalEquals(km.getValue(82), 12432.1467);
    	assertDecimalEquals(km.getValue(83), 12434.4566);
    	assertDecimalEquals(km.getValue(84), 12441.9504);
    	assertDecimalEquals(km.getValue(85), 12442.1894);
    	assertDecimalEquals(km.getValue(86), 12445.8236);
    	assertDecimalEquals(km.getValue(87), 12434.2128);
    	assertDecimalEquals(km.getValue(88), 12450.1724);
    	assertDecimalEquals(km.getValue(89), 12459.9013);
    	assertDecimalEquals(km.getValue(90), 12487.2172);
    	assertDecimalEquals(km.getValue(91), 12510.5801);
    	assertDecimalEquals(km.getValue(92), 12518.0512);
    	assertDecimalEquals(km.getValue(93), 12514.2286);
    	assertDecimalEquals(km.getValue(94), 12485.5203);
    	assertDecimalEquals(km.getValue(95), 12454.3283);
    	assertDecimalEquals(km.getValue(96), 12413.6258);
    	assertDecimalEquals(km.getValue(97), 12377.7599);
    	assertDecimalEquals(km.getValue(98), 12312.9812);
    	assertDecimalEquals(km.getValue(99), 12262.2618);
    	assertDecimalEquals(km.getValue(100), 12150.4447);
    	assertDecimalEquals(km.getValue(101), 12058.6050);
    	assertDecimalEquals(km.getValue(102), 11894.3079);
    	assertDecimalEquals(km.getValue(103), 11806.5308);
    	assertDecimalEquals(km.getValue(104), 11660.1507);
    	assertDecimalEquals(km.getValue(105), 11590.8777);
    	assertDecimalEquals(km.getValue(106), 11547.8938);
    	assertDecimalEquals(km.getValue(107), 11539.8493);
    	assertDecimalEquals(km.getValue(108), 11523.2907);
    	assertDecimalEquals(km.getValue(109), 11511.5090);
    	assertDecimalEquals(km.getValue(110), 11445.5684);
    	assertDecimalEquals(km.getValue(111), 11366.9606);
    	assertDecimalEquals(km.getValue(112), 11303.1594);
    	assertDecimalEquals(km.getValue(113), 11294.4778);
    	assertDecimalEquals(km.getValue(114), 11304.9868);
    	assertDecimalEquals(km.getValue(115), 11293.7822);
    	assertDecimalEquals(km.getValue(116), 11295.4302);
    	assertDecimalEquals(km.getValue(117), 11332.1582);
    	assertDecimalEquals(km.getValue(118), 11360.1803);
    	assertDecimalEquals(km.getValue(119), 11389.1019);
    	assertDecimalEquals(km.getValue(120), 11395.1673);
    	assertDecimalEquals(km.getValue(121), 11368.0907);
    	assertDecimalEquals(km.getValue(122), 11330.4869);
    	assertDecimalEquals(km.getValue(123), 11338.5277);
    	assertDecimalEquals(km.getValue(124), 11329.7002);
    	assertDecimalEquals(km.getValue(125), 11282.2092);
    	assertDecimalEquals(km.getValue(126), 11245.3145);
    	assertDecimalEquals(km.getValue(127), 11222.5494);
    	assertDecimalEquals(km.getValue(128), 11222.4794);
    	assertDecimalEquals(km.getValue(129), 11252.6000);
    	assertDecimalEquals(km.getValue(130), 11282.2965);
    	assertDecimalEquals(km.getValue(131), 11299.6457);
    	assertDecimalEquals(km.getValue(132), 11311.7243);
    	assertDecimalEquals(km.getValue(133), 11284.8050);
    	assertDecimalEquals(km.getValue(134), 11209.7359);
    	assertDecimalEquals(km.getValue(135), 11151.1647);
    	assertDecimalEquals(km.getValue(136), 11133.6228);
    	assertDecimalEquals(km.getValue(137), 11143.8156);
    	assertDecimalEquals(km.getValue(138), 11133.9777);
    	assertDecimalEquals(km.getValue(139), 11143.7114);
    	assertDecimalEquals(km.getValue(140), 11116.3619);
    	assertDecimalEquals(km.getValue(141), 11055.1858);
    	assertDecimalEquals(km.getValue(142), 11022.6149);
    	assertDecimalEquals(km.getValue(143), 11010.0766);
    	assertDecimalEquals(km.getValue(144), 11022.2035);
    	assertDecimalEquals(km.getValue(145), 11029.5100);
    	assertDecimalEquals(km.getValue(146), 11081.7648);
    	assertDecimalEquals(km.getValue(147), 11129.1778);
    	assertDecimalEquals(km.getValue(148), 11185.2694);
    	assertDecimalEquals(km.getValue(149), 11226.2562);
    	assertDecimalEquals(km.getValue(150), 11279.3403);
    	assertDecimalEquals(km.getValue(151), 11295.0710);
    	assertDecimalEquals(km.getValue(152), 11334.2860);
    	assertDecimalEquals(km.getValue(153), 11360.4059);
    	assertDecimalEquals(km.getValue(154), 11389.7679);
    	assertDecimalEquals(km.getValue(155), 11447.1046);
    	assertDecimalEquals(km.getValue(156), 11506.7876);
    	assertDecimalEquals(km.getValue(157), 11527.5713);
    	assertDecimalEquals(km.getValue(158), 11568.0643);
    	assertDecimalEquals(km.getValue(159), 11644.7199);
    	assertDecimalEquals(km.getValue(160), 11717.5166);
    	assertDecimalEquals(km.getValue(161), 11747.5843);
    	assertDecimalEquals(km.getValue(162), 11735.1736);
    	assertDecimalEquals(km.getValue(163), 11744.5785);
    	assertDecimalEquals(km.getValue(164), 11782.1624);
    	assertDecimalEquals(km.getValue(165), 11807.1409);
    	assertDecimalEquals(km.getValue(166), 11840.0887);
    	assertDecimalEquals(km.getValue(167), 11885.0516);
    	assertDecimalEquals(km.getValue(168), 11873.3487);
    	assertDecimalEquals(km.getValue(169), 11877.2473);
    	assertDecimalEquals(km.getValue(170), 11917.4510);
    	assertDecimalEquals(km.getValue(171), 11939.8673);
    	assertDecimalEquals(km.getValue(172), 11959.6445);  	
    }
}
