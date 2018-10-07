package org.ta4j.core.indicators.volume;

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

import static org.ta4j.core.TestUtils.assertNumEquals;

public class PVIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public PVIIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void getValue() {

        List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(1355.69, 2739.55,numFunction));
        bars.add(new MockBar(1325.51, 3119.46,numFunction));
        bars.add(new MockBar(1335.02, 3466.88,numFunction));
        bars.add(new MockBar(1313.72, 2577.12,numFunction));
        bars.add(new MockBar(1319.99, 2480.45,numFunction));
        bars.add(new MockBar(1331.85, 2329.79,numFunction));
        bars.add(new MockBar(1329.04, 2793.07,numFunction));
        bars.add(new MockBar(1362.16, 3378.78,numFunction));
        bars.add(new MockBar(1365.51, 2417.59,numFunction));
        bars.add(new MockBar(1374.02, 1442.81,numFunction));
        TimeSeries series = new MockTimeSeries(bars);

        PVIIndicator pvi = new PVIIndicator(series);
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
