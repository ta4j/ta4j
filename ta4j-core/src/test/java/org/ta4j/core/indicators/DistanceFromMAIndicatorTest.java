package org.ta4j.core.indicators;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class DistanceFromMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public DistanceFromMAIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockBarSeries(numFunction, 10, 15, 20, 18, 17, 18, 15, 12, 10, 8, 5, 2);
    }

    @Test
    public void DistanceFromMovingAverageTest() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
        DistanceFromMAIndicator distanceFromMAIndicator = new DistanceFromMAIndicator(data, sma);
        assertNumEquals(0.3333, distanceFromMAIndicator.getValue(2));
        assertNumEquals(0.01886792452830182, distanceFromMAIndicator.getValue(5));
        assertNumEquals(-0.1, distanceFromMAIndicator.getValue(6));
    }
}
