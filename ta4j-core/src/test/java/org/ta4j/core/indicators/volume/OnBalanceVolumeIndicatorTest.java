package org.ta4j.core.indicators.volume;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class OnBalanceVolumeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public OnBalanceVolumeIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void getValue() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(now, 0, 10, 0, 0, 0, 4, 0,numFunction));
        bars.add(new MockBar(now, 0, 5, 0, 0, 0, 2, 0,numFunction));
        bars.add(new MockBar(now, 0, 6, 0, 0, 0, 3, 0,numFunction));
        bars.add(new MockBar(now, 0, 7, 0, 0, 0, 8, 0,numFunction));
        bars.add(new MockBar(now, 0, 7, 0, 0, 0, 6, 0,numFunction));
        bars.add(new MockBar(now, 0, 6, 0, 0, 0, 10, 0,numFunction));

        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(new MockTimeSeries(bars));
        assertNumEquals(0, obv.getValue(0));
        assertNumEquals(-2, obv.getValue(1));
        assertNumEquals(1, obv.getValue(2));
        assertNumEquals(9, obv.getValue(3));
        assertNumEquals(9, obv.getValue(4));
        assertNumEquals(-1, obv.getValue(5));
    }

    @Test
    public void stackOverflowError() {
        List<Bar> bigListOfBars = new ArrayList<Bar>();
        for (int i = 0; i < 10000; i++) {
            bigListOfBars.add(new MockBar(i,numFunction));
        }
        MockTimeSeries bigSeries = new MockTimeSeries(bigListOfBars);
        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(bigSeries);
        // If a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        assertNumEquals(0, obv.getValue(9999));
    }
}
