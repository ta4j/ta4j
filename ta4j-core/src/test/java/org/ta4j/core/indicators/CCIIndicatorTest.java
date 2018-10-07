package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class CCIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private double[] typicalPrices = new double[] {
        23.98, 23.92, 23.79, 23.67, 23.54,
        23.36, 23.65, 23.72, 24.16, 23.91,
        23.81, 23.92, 23.74, 24.68, 24.94,
        24.93, 25.10, 25.12, 25.20, 25.06,
        24.50, 24.31, 24.57, 24.62, 24.49,
        24.37, 24.41, 24.35, 23.75, 24.09
    };

    private MockTimeSeries series;

    /**
     * Constructor.
     * @param function
     */
    public CCIIndicatorTest(Function<Number, Num> function) {
        super(function);
    }

    @Before
    public void setUp() {
        ArrayList<Bar> bars = new ArrayList<Bar>();
        for (Double price : typicalPrices) {
            bars.add(new MockBar(price, price, price, price,numFunction));
        }
        series = new MockTimeSeries(bars);
    }

    @Test
    public void getValueWhenBarCountIs20() {
        CCIIndicator cci = new CCIIndicator(series, 20);

        // Incomplete time frame
        assertNumEquals(0, cci.getValue(0));
        assertNumEquals(-66.6667, cci.getValue(1));
        assertNumEquals(-100d, cci.getValue(2));
        assertNumEquals(14.365, cci.getValue(10));
        assertNumEquals(54.2544, cci.getValue(11));

        // Complete time frame
        double[] results20to30 = new double[] { 101.9185, 31.1946, 6.5578, 33.6078, 34.9686, 13.6027,
            -10.6789, -11.471, -29.2567, -128.6, -72.7273 };
        for (int i = 0; i < results20to30.length; i++) {
            assertNumEquals(results20to30[i], cci.getValue(i + 19));
        }
    }
}
