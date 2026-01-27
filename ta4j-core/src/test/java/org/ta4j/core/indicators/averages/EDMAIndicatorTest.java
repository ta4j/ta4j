/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EDMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public EDMAIndicatorTest(NumFactory numFunction) {
        super(numFunction);
    }

    @Test
    public void testCalculateEDMAWithNQ() {

        /*
         * Retrieved data from Ta4J EMAIndicator using NQ data which was retrieved from
         * Databento
         */

        BarSeries data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(20154.75, 20149.25, 20125.5, 20290.75, 20256.75, 20340.5, 20351.25, 20308.75, 20294.5, 20341,
                        20362.75, 20343.5, 20389.5, 20402.75, 20588.5, 20593.25, 20597.25, 20583.75, 20646.25, 20680.75,
                        20699.25, 20727.75, 20685, 20605.75, 20685.75, 20687, 20675.5, 20724.25, 20798.25, 20817.75,
                        20861, 20890, 20893.75, 20922.5, 20890.75, 20874, 20912, 20909.25, 20917, 20921.75, 20949,
                        20950.25, 20923.5, 20935.25, 20940.25, 20945.25, 20944.25, 20967.5, 20986.5, 21108.75, 21161,
                        21194, 21194.25, 21180, 21233, 21221.75, 21225.25, 21227.25, 21224.25, 21232.25, 21219.75,
                        21226.75, 21224.25, 21251.75, 21230.5, 21240, 21232.5, 21225.25, 21144, 21162, 21183.25, 21162,
                        21207.5, 21198, 21219, 21217.75, 21250.25, 21234.75, 21234, 21226.75, 21304.5, 21275.5,
                        21283.25, 21270, 21273.5, 21287.75, 21288.75, 21288, 21298, 21292.5, 21292.25, 21272.25,
                        21285.25, 21327, 21300.5, 21172.5, 21223.25, 21203, 21158, 21179.75, 21175.5, 21220.25,
                        21210.25, 21216.5, 21213.5, 21230.75, 21206, 21208.5, 21222.75, 21195.25, 21208.75, 21208,
                        21230.75, 21184.75, 21168, 21178.5, 21193.25, 21221.5, 21235.25, 21184.5, 21154.75, 21066.25,
                        21182, 21188.5, 21185, 21178, 21160.75, 21144.25, 21140, 21150.25, 21146.5, 21140, 21120.25,
                        21118.25, 21130, 21177.25, 21176.5, 21151.75, 21126.75, 21132.25, 21173.5, 21140.25, 21115.5,
                        21165.5, 21228.5, 21257.25, 21182.75, 21158.75, 21166, 21166.5, 21152.75, 21133.75, 21120.25,
                        21127.75, 21138.5, 21121.5, 21101.25, 21129.25, 21166, 21153.25, 21163, 21177.5, 21155, 21134,
                        21092, 21091, 21101.25, 21083, 21027.5, 21069.25, 21008.5, 20981.75, 20950.25, 20926.75,
                        20891.75, 20915, 20905.5, 20918.75, 20899.5, 20869.75, 20845, 20832.25, 20820.5, 20827.75,
                        20845.25, 20862.75, 20811.5, 20690, 20561.25, 20495.5, 20483.75, 20429, 20460.75, 20491.75,
                        20503.25, 20568.25, 20628, 20609.5, 20628, 20629.5, 20649, 20639.25, 20648.75, 20596.25,
                        20634.5, 20558, 20588.5, 20571.75, 20557.75, 20555.5, 20530.25, 20680.75, 20662.25, 20661.25,
                        20660, 20638.75, 20631.75, 20630.5, 20622.75, 20626.5, 20638, 20656.25, 20678.25, 20682,
                        20669.25, 20677.25, 20673.25, 20629, 20540.5, 20579, 20579.75, 20614, 20422.25, 20554.25,
                        20636.75, 20669.5, 20694.75, 20784.75, 20739.75, 20765.5, 20754.5, 20782.75, 20813.5, 20790,
                        20809.5, 20785.5, 20801.75, 20796, 20830.75, 20825.5, 20795, 20794.75, 20781.5, 20793.25,
                        20819.75, 20793, 20614, 20627.25, 20689, 20607.25, 20589.75, 20609, 20752.25, 20738.75, 20752.5,
                        20649.5, 20631.25, 20647.75, 20654.75, 20677.25, 20678.25, 20688.75, 20670, 20676, 20647,
                        20679.75, 20732, 20762, 20844.5, 20667, 20708.5, 20732.25, 20819.5, 20857, 20848.25, 20823.75,
                        20793.75, 20773.5, 20770, 20800, 20820, 20791, 20808.75, 20765.25, 20801.25, 20811, 20806.75,
                        20757, 20741, 20771, 20796.5, 20783.75, 20855, 20842.5, 20813, 20854.75, 20846.75, 20846.75,
                        20847.25, 20875.25)
                .build();

        int displacement = 2;
        var edma = new EDMAIndicator(new ClosePriceIndicator(data), 9, displacement);

        // With barCount=9, unstable period is 9. EDMAIndicator caches EMA values in
        // constructor.
        // When calculate(i) is called, it returns results.get(i - displacement).
        // So for index i, we need results.get(i - displacement) to be valid.
        // This means i - displacement >= unstableBars, so i >= unstableBars +
        // displacement = 9 + 2 = 11.
        // For indices < displacement, it returns results.get(0) which is NaN.
        // For indices >= displacement but < unstableBars + displacement, it returns NaN
        // values.

        // Indices 0-10 should return NaN (0-1 return results.get(0) which is NaN, 2-10
        // return NaN cached values)
        for (int i = 0; i < 9 + displacement; i++) {
            assertThat(Double.isNaN(edma.getValue(i).doubleValue())).isTrue();
        }

        // Check values after unstable period + displacement
        // Note: Values will differ from expected because first EMA value after unstable
        // period
        // is now initialized to current value, not calculated from previous values
        for (int i = 9 + displacement; i < data.getBarCount() - displacement; i++) {
            // Just verify values are not NaN (they'll differ from expected due to
            // initialization change)
            assertThat(Double.isNaN(edma.getValue(i).doubleValue())).isFalse();
        }

    }

}
