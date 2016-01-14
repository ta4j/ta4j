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

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;

public class KeltnerLowerIndicatorTest {

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
    public void keltnerLowerIndicatorTest()
    {
    	KeltnerMiddleIndicator km = new KeltnerMiddleIndicator(new ClosePriceIndicator(data), 14);
    	KeltnerLowerIndicator kl = new KeltnerLowerIndicator(km, Decimal.valueOf(2), 14);
    	
    	assertDecimalEquals(kl.getValue(13), 11658.1418);
    	assertDecimalEquals(kl.getValue(14), 11679.3012);
    	assertDecimalEquals(kl.getValue(15), 11700.7482);
    	assertDecimalEquals(kl.getValue(16), 11722.9877);
    	assertDecimalEquals(kl.getValue(17), 11744.1810);
    	assertDecimalEquals(kl.getValue(18), 11731.7722);
    	assertDecimalEquals(kl.getValue(19), 11741.9200);
    	assertDecimalEquals(kl.getValue(20), 11767.1210);
    	assertDecimalEquals(kl.getValue(21), 11783.3355);
    	assertDecimalEquals(kl.getValue(22), 11805.4777);
    	assertDecimalEquals(kl.getValue(23), 11829.3025);
    	assertDecimalEquals(kl.getValue(24), 11860.4331);
    	assertDecimalEquals(kl.getValue(25), 11891.4189);
    	assertDecimalEquals(kl.getValue(26), 11914.0423);
    	assertDecimalEquals(kl.getValue(27), 11936.0717);
    	assertDecimalEquals(kl.getValue(28), 11963.7929);
    	assertDecimalEquals(kl.getValue(29), 11977.6519);
    	assertDecimalEquals(kl.getValue(30), 11997.2893);
    	assertDecimalEquals(kl.getValue(31), 12020.6125);
    	assertDecimalEquals(kl.getValue(32), 12006.7152);
    	assertDecimalEquals(kl.getValue(33), 11987.3537);
    	assertDecimalEquals(kl.getValue(34), 11965.9937);
    	assertDecimalEquals(kl.getValue(35), 11963.7260);
    	assertDecimalEquals(kl.getValue(36), 11971.7203);
    	assertDecimalEquals(kl.getValue(37), 11940.9155);
    	assertDecimalEquals(kl.getValue(38), 11930.7650);
    	assertDecimalEquals(kl.getValue(39), 11931.1736);
    	assertDecimalEquals(kl.getValue(40), 11907.6894);
    	assertDecimalEquals(kl.getValue(41), 11907.7878);
    	assertDecimalEquals(kl.getValue(42), 11919.1371);
    	assertDecimalEquals(kl.getValue(43), 11883.9802);
    	assertDecimalEquals(kl.getValue(44), 11867.8888);
    	assertDecimalEquals(kl.getValue(45), 11850.0268);
    	assertDecimalEquals(kl.getValue(46), 11799.2662);
    	assertDecimalEquals(kl.getValue(47), 11711.6519);
    	assertDecimalEquals(kl.getValue(48), 11675.9579);
    	assertDecimalEquals(kl.getValue(49), 11659.7593);
    	assertDecimalEquals(kl.getValue(50), 11660.9554);
    	assertDecimalEquals(kl.getValue(51), 11682.5576);
    	assertDecimalEquals(kl.getValue(52), 11695.4912);
    	assertDecimalEquals(kl.getValue(53), 11725.3227);
    	assertDecimalEquals(kl.getValue(54), 11758.6655);
    	assertDecimalEquals(kl.getValue(55), 11787.0886);
    	assertDecimalEquals(kl.getValue(56), 11820.1086);
    	assertDecimalEquals(kl.getValue(57), 11858.4156);
    	assertDecimalEquals(kl.getValue(58), 11893.5735);
    	assertDecimalEquals(kl.getValue(59), 11928.6395);
    	assertDecimalEquals(kl.getValue(60), 11974.6243);
    	assertDecimalEquals(kl.getValue(61), 12005.9472);
    	assertDecimalEquals(kl.getValue(62), 12038.8094);
    	assertDecimalEquals(kl.getValue(63), 12055.4508);
    	assertDecimalEquals(kl.getValue(64), 11956.5846);
    	assertDecimalEquals(kl.getValue(65), 11929.2310);
    	assertDecimalEquals(kl.getValue(66), 11872.2201);
    	assertDecimalEquals(kl.getValue(67), 11846.2144);
    	assertDecimalEquals(kl.getValue(68), 11829.1411);
    	assertDecimalEquals(kl.getValue(69), 11824.1064);
    	assertDecimalEquals(kl.getValue(70), 11825.7687);
    	assertDecimalEquals(kl.getValue(71), 11801.2828);
    	assertDecimalEquals(kl.getValue(72), 11779.0245);
    	assertDecimalEquals(kl.getValue(73), 11771.2104);
    	assertDecimalEquals(kl.getValue(74), 11789.9409);
    	assertDecimalEquals(kl.getValue(75), 11820.6113);
    	assertDecimalEquals(kl.getValue(76), 11859.9993);
    	assertDecimalEquals(kl.getValue(77), 11915.9082);
    	assertDecimalEquals(kl.getValue(78), 11979.8470);
    	assertDecimalEquals(kl.getValue(79), 12038.4998);
    	assertDecimalEquals(kl.getValue(80), 12096.9237);
    	assertDecimalEquals(kl.getValue(81), 12134.6506);
    	assertDecimalEquals(kl.getValue(82), 12140.2361);
    	assertDecimalEquals(kl.getValue(83), 12145.7253);
    	assertDecimalEquals(kl.getValue(84), 12150.3913);
    	assertDecimalEquals(kl.getValue(85), 12147.5174);
    	assertDecimalEquals(kl.getValue(86), 12158.0953);
    	assertDecimalEquals(kl.getValue(87), 12140.8223);
    	assertDecimalEquals(kl.getValue(88), 12145.9669);
    	assertDecimalEquals(kl.getValue(89), 12169.2891);
    	assertDecimalEquals(kl.getValue(90), 12190.9601);
    	assertDecimalEquals(kl.getValue(91), 12221.6728);
    	assertDecimalEquals(kl.getValue(92), 12229.0701);
    	assertDecimalEquals(kl.getValue(93), 12230.9805);
    	assertDecimalEquals(kl.getValue(94), 12192.2742);
    	assertDecimalEquals(kl.getValue(95), 12159.4468);
    	assertDecimalEquals(kl.getValue(96), 12117.0045);
    	assertDecimalEquals(kl.getValue(97), 12061.7059);
    	assertDecimalEquals(kl.getValue(98), 11981.3695);
    	assertDecimalEquals(kl.getValue(99), 11925.1124);
    	assertDecimalEquals(kl.getValue(100), 11762.4774);
    	assertDecimalEquals(kl.getValue(101), 11638.8625);
    	assertDecimalEquals(kl.getValue(102), 11413.8670);
    	assertDecimalEquals(kl.getValue(103), 11268.9872);
    	assertDecimalEquals(kl.getValue(104), 11081.9630);
    	assertDecimalEquals(kl.getValue(105), 10974.1377);
    	assertDecimalEquals(kl.getValue(106), 10945.9938);
    	assertDecimalEquals(kl.getValue(107), 10950.1450);
    	assertDecimalEquals(kl.getValue(108), 10947.7967);
    	assertDecimalEquals(kl.getValue(109), 10947.4975);
    	assertDecimalEquals(kl.getValue(110), 10846.3276);
    	assertDecimalEquals(kl.getValue(111), 10769.8099);
    	assertDecimalEquals(kl.getValue(112), 10719.6766);
    	assertDecimalEquals(kl.getValue(113), 10706.6138);
    	assertDecimalEquals(kl.getValue(114), 10727.8946);
    	assertDecimalEquals(kl.getValue(115), 10715.1065);
    	assertDecimalEquals(kl.getValue(116), 10701.3414);
    	assertDecimalEquals(kl.getValue(117), 10743.7557);
    	assertDecimalEquals(kl.getValue(118), 10785.1379);
    	assertDecimalEquals(kl.getValue(119), 10828.7740);
    	assertDecimalEquals(kl.getValue(120), 10842.2371);
    	assertDecimalEquals(kl.getValue(121), 10814.3384);
    	assertDecimalEquals(kl.getValue(122), 10772.3269);
    	assertDecimalEquals(kl.getValue(123), 10780.6320);
    	assertDecimalEquals(kl.getValue(124), 10784.0028);
    	assertDecimalEquals(kl.getValue(125), 10724.0373);
    	assertDecimalEquals(kl.getValue(126), 10693.1163);
    	assertDecimalEquals(kl.getValue(127), 10687.8411);
    	assertDecimalEquals(kl.getValue(128), 10669.8303);
    	assertDecimalEquals(kl.getValue(129), 10712.7586);
    	assertDecimalEquals(kl.getValue(130), 10763.1495);
    	assertDecimalEquals(kl.getValue(131), 10781.3178);
    	assertDecimalEquals(kl.getValue(132), 10805.2341);
    	assertDecimalEquals(kl.getValue(133), 10767.2670);
    	assertDecimalEquals(kl.getValue(134), 10653.7792);
    	assertDecimalEquals(kl.getValue(135), 10610.6677);
    	assertDecimalEquals(kl.getValue(136), 10590.8742);
    	assertDecimalEquals(kl.getValue(137), 10593.3433);
    	assertDecimalEquals(kl.getValue(138), 10577.0962);
    	assertDecimalEquals(kl.getValue(139), 10582.9373);
    	assertDecimalEquals(kl.getValue(140), 10560.7201);
    	assertDecimalEquals(kl.getValue(141), 10492.6828);
    	assertDecimalEquals(kl.getValue(142), 10440.1550);
    	assertDecimalEquals(kl.getValue(143), 10438.8224);
    	assertDecimalEquals(kl.getValue(144), 10452.6202);
    	assertDecimalEquals(kl.getValue(145), 10474.7656);
    	assertDecimalEquals(kl.getValue(146), 10519.4721);
    	assertDecimalEquals(kl.getValue(147), 10595.3074);
    	assertDecimalEquals(kl.getValue(148), 10659.6755);
    	assertDecimalEquals(kl.getValue(149), 10718.0576);
    	assertDecimalEquals(kl.getValue(150), 10783.3415);
    	assertDecimalEquals(kl.getValue(151), 10796.4808);
    	assertDecimalEquals(kl.getValue(152), 10820.3636);
    	assertDecimalEquals(kl.getValue(153), 10859.6880);
    	assertDecimalEquals(kl.getValue(154), 10897.6570);
    	assertDecimalEquals(kl.getValue(155), 10951.4759);
    	assertDecimalEquals(kl.getValue(156), 11027.2781);
    	assertDecimalEquals(kl.getValue(157), 11049.2982);
    	assertDecimalEquals(kl.getValue(158), 11095.8321);
    	assertDecimalEquals(kl.getValue(159), 11146.8943);
    	assertDecimalEquals(kl.getValue(160), 11242.7243);
    	assertDecimalEquals(kl.getValue(161), 11267.1771);
    	assertDecimalEquals(kl.getValue(162), 11242.6555);
    	assertDecimalEquals(kl.getValue(163), 11255.9731);
    	assertDecimalEquals(kl.getValue(164), 11295.5289);
    	assertDecimalEquals(kl.getValue(165), 11327.5298);
    	assertDecimalEquals(kl.getValue(166), 11367.0569);
    	assertDecimalEquals(kl.getValue(167), 11419.3307);
    	assertDecimalEquals(kl.getValue(168), 11379.0007);
    	assertDecimalEquals(kl.getValue(169), 11392.3155);
    	assertDecimalEquals(kl.getValue(170), 11426.3101);
    	assertDecimalEquals(kl.getValue(171), 11463.3036);
    	assertDecimalEquals(kl.getValue(172), 11493.7140);
    }
}
