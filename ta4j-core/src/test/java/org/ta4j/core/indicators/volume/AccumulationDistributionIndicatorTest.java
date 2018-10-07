package org.ta4j.core.indicators.volume;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class AccumulationDistributionIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num>{

    public AccumulationDistributionIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void accumulationDistribution() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(now, 0d, 10d, 12d, 8d, 0d, 200d, 0,numFunction));//2-2 * 200 / 4
        bars.add(new MockBar(now, 0d, 8d, 10d, 7d, 0d, 100d, 0,numFunction));//1-2 *100 / 3
        bars.add(new MockBar(now, 0d, 9d, 15d, 6d, 0d, 300d, 0,numFunction));//3-6 *300 /9
        bars.add(new MockBar(now, 0d, 20d, 40d, 5d, 0d, 50d, 0,numFunction));//15-20 *50 / 35
        bars.add(new MockBar(now, 0d, 30d, 30d, 3d, 0d, 600d, 0,numFunction));//27-0 *600 /27

        TimeSeries series = new MockTimeSeries(bars);
        AccumulationDistributionIndicator ac = new AccumulationDistributionIndicator(series);
        assertNumEquals(0, ac.getValue(0));
        assertNumEquals(-100d / 3, ac.getValue(1));
        assertNumEquals(-100d -(100d / 3), ac.getValue(2));
        assertNumEquals((-250d/35) + (-100d -(100d / 3)), ac.getValue(3));
        assertNumEquals(600d + ((-250d/35) + (-100d -(100d / 3))), ac.getValue(4));
    }
}
