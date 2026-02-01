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

public class MVWAPIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    protected BarSeries data;

    public MVWAPIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {

        data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        data.barBuilder().openPrice(44.98).closePrice(45.05).highPrice(45.17).lowPrice(44.96).volume(1).add();
        data.barBuilder().openPrice(45.05).closePrice(45.10).highPrice(45.15).lowPrice(44.99).volume(2).add();
        data.barBuilder().openPrice(45.11).closePrice(45.19).highPrice(45.32).lowPrice(45.11).volume(1).add();
        data.barBuilder().openPrice(45.19).closePrice(45.14).highPrice(45.25).lowPrice(45.04).volume(3).add();
        data.barBuilder().openPrice(45.12).closePrice(45.15).highPrice(45.20).lowPrice(45.10).volume(1).add();
        data.barBuilder().openPrice(45.15).closePrice(45.14).highPrice(45.20).lowPrice(45.10).volume(2).add();
        data.barBuilder().openPrice(45.13).closePrice(45.10).highPrice(45.16).lowPrice(45.07).volume(1).add();
        data.barBuilder().openPrice(45.12).closePrice(45.15).highPrice(45.22).lowPrice(45.10).volume(5).add();
        data.barBuilder().openPrice(45.15).closePrice(45.22).highPrice(45.27).lowPrice(45.14).volume(1).add();
        data.barBuilder().openPrice(45.24).closePrice(45.43).highPrice(45.45).lowPrice(45.20).volume(1).add();
        data.barBuilder().openPrice(45.43).closePrice(45.44).highPrice(45.50).lowPrice(45.39).volume(1).add();
        data.barBuilder().openPrice(45.43).closePrice(45.55).highPrice(45.60).lowPrice(45.35).volume(5).add();
        data.barBuilder().openPrice(45.58).closePrice(45.55).highPrice(45.61).lowPrice(45.39).volume(7).add();
        data.barBuilder().openPrice(45.45).closePrice(45.01).highPrice(45.55).lowPrice(44.80).volume(6).add();
        data.barBuilder().openPrice(45.03).closePrice(44.23).highPrice(45.04).lowPrice(44.17).volume(1).add();
        data.barBuilder().openPrice(44.23).closePrice(43.95).highPrice(44.29).lowPrice(43.81).volume(2).add();
        data.barBuilder().openPrice(43.91).closePrice(43.08).highPrice(43.99).lowPrice(43.08).volume(1).add();
        data.barBuilder().openPrice(43.07).closePrice(43.55).highPrice(43.65).lowPrice(43.06).volume(7).add();
        data.barBuilder().openPrice(43.56).closePrice(43.95).highPrice(43.99).lowPrice(43.53).volume(6).add();
        data.barBuilder().openPrice(43.93).closePrice(44.47).highPrice(44.58).lowPrice(43.93).volume(1).add();
    }

    @Test
    public void mvwap() {
        VWAPIndicator vwap = new VWAPIndicator(data, 5);
        MVWAPIndicator mvwap = new MVWAPIndicator(vwap, 8);

        assertNumEquals(45.1271, mvwap.getValue(8));
        assertNumEquals(45.1399, mvwap.getValue(9));
        assertNumEquals(45.1530, mvwap.getValue(10));
        assertNumEquals(45.1790, mvwap.getValue(11));
        assertNumEquals(45.2227, mvwap.getValue(12));
        assertNumEquals(45.2533, mvwap.getValue(13));
        assertNumEquals(45.2769, mvwap.getValue(14));
        assertNumEquals(45.2844, mvwap.getValue(15));
        assertNumEquals(45.2668, mvwap.getValue(16));
        assertNumEquals(45.1386, mvwap.getValue(17));
        assertNumEquals(44.9487, mvwap.getValue(18));
    }
}
