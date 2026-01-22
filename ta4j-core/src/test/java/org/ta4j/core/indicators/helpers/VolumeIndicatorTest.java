/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VolumeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public VolumeIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void indicatorShouldRetrieveBarVolume() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        var volumeIndicator = new VolumeIndicator(series);
        for (int i = 0; i < 10; i++) {
            assertEquals(volumeIndicator.getValue(i), series.getBar(i).getVolume());
        }
    }

    @Test
    public void sumOfVolume() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(0).volume(10).add();
        series.barBuilder().closePrice(0).volume(11).add();
        series.barBuilder().closePrice(0).volume(12).add();
        series.barBuilder().closePrice(0).volume(13).add();
        series.barBuilder().closePrice(0).volume(150).add();
        series.barBuilder().closePrice(0).volume(155).add();
        series.barBuilder().closePrice(0).volume(160).add();

        var volumeIndicator = new VolumeIndicator(series, 3);

        assertNumEquals(10, volumeIndicator.getValue(0));
        assertNumEquals(21, volumeIndicator.getValue(1));
        assertNumEquals(33, volumeIndicator.getValue(2));
        assertNumEquals(36, volumeIndicator.getValue(3));
        assertNumEquals(175, volumeIndicator.getValue(4));
        assertNumEquals(318, volumeIndicator.getValue(5));
        assertNumEquals(465, volumeIndicator.getValue(6));
    }
}
