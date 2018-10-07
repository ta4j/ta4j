package org.ta4j.core.indicators.statistics;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class MeanDeviationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TimeSeries data;

    public MeanDeviationIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction,1, 2, 7, 6, 3, 4, 5, 11, 3, 0, 9);
    }

    @Test
    public void meanDeviationUsingBarCount5UsingClosePrice() {
        MeanDeviationIndicator meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals(2.44444444444444, meanDeviation.getValue(2));
        assertNumEquals(2.5, meanDeviation.getValue(3));
        assertNumEquals(2.16, meanDeviation.getValue(7));
        assertNumEquals(2.32, meanDeviation.getValue(8));
        assertNumEquals(2.72, meanDeviation.getValue(9));
    }

    @Test
    public void firstValueShouldBeZero() {
        MeanDeviationIndicator meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals(0, meanDeviation.getValue(0));
    }

    @Test
    public void meanDeviationShouldBeZeroWhenBarCountIs1() {
        MeanDeviationIndicator meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(0, meanDeviation.getValue(2));
        assertNumEquals(0, meanDeviation.getValue(7));
    }
}
