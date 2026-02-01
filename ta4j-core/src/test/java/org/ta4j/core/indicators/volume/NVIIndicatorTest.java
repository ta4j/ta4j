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

public class NVIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public NVIIndicatorTest(NumFactory numFactory) {
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

        var nvi = new NVIIndicator(series);
        assertNumEquals(1000, nvi.getValue(0));
        assertNumEquals(1000, nvi.getValue(1));
        assertNumEquals(1000, nvi.getValue(2));
        assertNumEquals(984.0452, nvi.getValue(3));
        assertNumEquals(988.7417, nvi.getValue(4));
        assertNumEquals(997.6255, nvi.getValue(5));
        assertNumEquals(997.6255, nvi.getValue(6));
        assertNumEquals(997.6255, nvi.getValue(7));
        assertNumEquals(1000.079, nvi.getValue(8));
        assertNumEquals(1006.3116, nvi.getValue(9));
    }
}
