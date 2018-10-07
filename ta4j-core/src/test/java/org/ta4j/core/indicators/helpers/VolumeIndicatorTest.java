package org.ta4j.core.indicators.helpers;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class VolumeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public VolumeIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void indicatorShouldRetrieveBarVolume() {
        TimeSeries series = new MockTimeSeries(numFunction);
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);
        for (int i = 0; i < 10; i++) {
            assertEquals(volumeIndicator.getValue(i), series.getBar(i).getVolume());
        }
    }

    @Test
    public void sumOfVolume() {
        List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(0, 10,numFunction));
        bars.add(new MockBar(0, 11,numFunction));
        bars.add(new MockBar(0, 12,numFunction));
        bars.add(new MockBar(0, 13,numFunction));
        bars.add(new MockBar(0, 150,numFunction));
        bars.add(new MockBar(0, 155,numFunction));
        bars.add(new MockBar(0, 160,numFunction));
        VolumeIndicator volumeIndicator = new VolumeIndicator(new MockTimeSeries(bars), 3);

        assertNumEquals(10, volumeIndicator.getValue(0));
        assertNumEquals(21, volumeIndicator.getValue(1));
        assertNumEquals(33, volumeIndicator.getValue(2));
        assertNumEquals(36, volumeIndicator.getValue(3));
        assertNumEquals(175, volumeIndicator.getValue(4));
        assertNumEquals(318, volumeIndicator.getValue(5));
        assertNumEquals(465, volumeIndicator.getValue(6));
    }
}
