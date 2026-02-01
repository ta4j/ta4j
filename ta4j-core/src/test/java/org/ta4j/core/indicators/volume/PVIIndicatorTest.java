/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PVIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public PVIIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void getValue() {

        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(1355.69).volume(2739.55).add();
        series.barBuilder().closePrice(1325.51).volume(3119.46).add();
        series.barBuilder().closePrice(1335.02).volume(3466.88).add();
        series.barBuilder().closePrice(1313.72).volume(2577.12).add();
        series.barBuilder().closePrice(1319.99).volume(2480.45).add();
        series.barBuilder().closePrice(1331.85).volume(2329.79).add();
        series.barBuilder().closePrice(1329.04).volume(2793.07).add();
        series.barBuilder().closePrice(1362.16).volume(3378.78).add();
        series.barBuilder().closePrice(1365.51).volume(2417.59).add();
        series.barBuilder().closePrice(1374.02).volume(1442.81).add();

        var pvi = new PVIIndicator(series);
        assertNumEquals(1000, pvi.getValue(0));
        assertNumEquals(977.7383, pvi.getValue(1));
        assertNumEquals(984.7532, pvi.getValue(2));
        assertNumEquals(984.7532, pvi.getValue(3));
        assertNumEquals(984.7532, pvi.getValue(4));
        assertNumEquals(984.7532, pvi.getValue(5));
        assertNumEquals(982.6755, pvi.getValue(6));
        assertNumEquals(1007.164, pvi.getValue(7));
        assertNumEquals(1007.164, pvi.getValue(8));
        assertNumEquals(1007.164, pvi.getValue(9));
    }
}
