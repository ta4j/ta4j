package org.ta4j.core.indicators.helpers;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class TRIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TRIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void getValue() {
        List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(0, 12, 15, 8, numFunction));
        bars.add(new MockBar(0, 8, 11, 6, numFunction));
        bars.add(new MockBar(0, 15, 17, 14, numFunction));
        bars.add(new MockBar(0, 15, 17, 14, numFunction));
        bars.add(new MockBar(0, 0, 0, 2, numFunction));
        TRIndicator tr = new TRIndicator(new MockTimeSeries(bars));

        assertNumEquals(7, tr.getValue(0));
        assertNumEquals(6, tr.getValue(1));
        assertNumEquals(9, tr.getValue(2));
        assertNumEquals(3, tr.getValue(3));
        assertNumEquals(15, tr.getValue(4));
    }
}
