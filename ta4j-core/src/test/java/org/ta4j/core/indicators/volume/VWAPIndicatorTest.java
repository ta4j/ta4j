/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VWAPIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    protected BarSeries data;

    public VWAPIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        data.barBuilder().openPrice(44.98).closePrice(45.05).highPrice(45.17).lowPrice(44.96).volume(1).add();
        data.barBuilder().openPrice(45.05).closePrice(45.10).highPrice(45.15).lowPrice(44.99).volume(1).add();
        data.barBuilder().openPrice(45.11).closePrice(45.19).highPrice(45.32).lowPrice(45.11).volume(1).add();
        data.barBuilder().openPrice(45.19).closePrice(45.14).highPrice(45.25).lowPrice(45.04).volume(1).add();
        data.barBuilder().openPrice(45.12).closePrice(45.15).highPrice(45.20).lowPrice(45.10).volume(1).add();
        data.barBuilder().openPrice(45.15).closePrice(45.14).highPrice(45.20).lowPrice(45.10).volume(1).add();
        data.barBuilder().openPrice(45.13).closePrice(45.10).highPrice(45.16).lowPrice(45.07).volume(1).add();
        data.barBuilder().openPrice(45.12).closePrice(45.15).highPrice(45.22).lowPrice(45.10).volume(1).add();
        data.barBuilder().openPrice(45.15).closePrice(45.22).highPrice(45.27).lowPrice(45.14).volume(1).add();
        data.barBuilder().openPrice(45.24).closePrice(45.43).highPrice(45.45).lowPrice(45.20).volume(1).add();
        data.barBuilder().openPrice(45.43).closePrice(45.44).highPrice(45.50).lowPrice(45.39).volume(1).add();
        data.barBuilder().openPrice(45.43).closePrice(45.55).highPrice(45.60).lowPrice(45.35).volume(1).add();
        data.barBuilder().openPrice(45.58).closePrice(45.55).highPrice(45.61).lowPrice(45.39).volume(1).add();
        data.barBuilder().openPrice(45.45).closePrice(45.01).highPrice(45.55).lowPrice(44.80).volume(1).add();
        data.barBuilder().openPrice(45.03).closePrice(44.23).highPrice(45.04).lowPrice(44.17).volume(1).add();
        data.barBuilder().openPrice(44.23).closePrice(43.95).highPrice(44.29).lowPrice(43.81).volume(1).add();
        data.barBuilder().openPrice(43.91).closePrice(43.08).highPrice(43.99).lowPrice(43.08).volume(1).add();
        data.barBuilder().openPrice(43.07).closePrice(43.55).highPrice(43.65).lowPrice(43.06).volume(1).add();
        data.barBuilder().openPrice(43.56).closePrice(43.95).highPrice(43.99).lowPrice(43.53).volume(1).add();
        data.barBuilder().openPrice(43.93).closePrice(44.47).highPrice(44.58).lowPrice(43.93).volume(1).add();
    }

    @Test
    public void vwap() {
        var vwap = new VWAPIndicator(data, 5);

        assertNumEquals(45.1453, vwap.getValue(5));
        assertNumEquals(45.1513, vwap.getValue(6));
        assertNumEquals(45.1413, vwap.getValue(7));
        assertNumEquals(45.1547, vwap.getValue(8));
        assertNumEquals(45.1967, vwap.getValue(9));
        assertNumEquals(45.2560, vwap.getValue(10));
        assertNumEquals(45.3340, vwap.getValue(11));
        assertNumEquals(45.4060, vwap.getValue(12));
        assertNumEquals(45.3880, vwap.getValue(13));
        assertNumEquals(45.2120, vwap.getValue(14));
        assertNumEquals(44.9267, vwap.getValue(15));
        assertNumEquals(44.5033, vwap.getValue(16));
        assertNumEquals(44.0840, vwap.getValue(17));
        assertNumEquals(43.8247, vwap.getValue(18));
    }
}
