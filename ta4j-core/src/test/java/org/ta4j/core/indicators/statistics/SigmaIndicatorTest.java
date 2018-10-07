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

public class SigmaIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num>{

    private TimeSeries data;

    public SigmaIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction,1, 2, 3, 4, 5, 6);
    }

    @Test
    public void test() {
      
        SigmaIndicator zScore = new SigmaIndicator(new ClosePriceIndicator(data), 5);
      
        assertNumEquals(1.0, zScore.getValue(1));
        assertNumEquals(1.224744871391589, zScore.getValue(2));
        assertNumEquals(1.34164078649987387, zScore.getValue(3));
        assertNumEquals(1.414213562373095, zScore.getValue(4));
        assertNumEquals(1.414213562373095, zScore.getValue(5));
    }
}
