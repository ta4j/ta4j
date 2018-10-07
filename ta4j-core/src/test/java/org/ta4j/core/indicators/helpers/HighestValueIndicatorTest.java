package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;
import java.util.function.Function;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.num.NaN.NaN;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class HighestValueIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TimeSeries data;

    public HighestValueIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction, 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void highestValueUsingBarCount5UsingClosePrice() {
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals("4.0", highestValue.getValue(4));
        assertNumEquals("4.0", highestValue.getValue(5));
        assertNumEquals("5.0", highestValue.getValue(6));
        assertNumEquals("6.0", highestValue.getValue(7));
        assertNumEquals("6.0", highestValue.getValue(8));
        assertNumEquals("6.0", highestValue.getValue(9));
        assertNumEquals("6.0", highestValue.getValue(10));
        assertNumEquals("6.0", highestValue.getValue(11));
        assertNumEquals("4.0", highestValue.getValue(12));
    }

    @Test
    public void firstHighestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals("1.0", highestValue.getValue(0));
    }

    @Test
    public void highestValueIndicatorWhenBarCountIsGreaterThanIndex() {
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 500);
        assertNumEquals("6.0", highestValue.getValue(12));
    }

    @Test
    public void onlyNaNValues(){
        BaseTimeSeries series = new BaseTimeSeries("NaN test");
        for (long i = 0; i<= 10000; i++){
            series.addBar(ZonedDateTime.now().plusDays(i), NaN, NaN,NaN, NaN, NaN);
        }


        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 5);
        for (int i = series.getBeginIndex(); i<= series.getEndIndex(); i++){
            assertEquals(NaN.toString(),highestValue.getValue(i).toString());
        }
    }

    @Test
    public void naNValuesInIntervall(){
        BaseTimeSeries series = new BaseTimeSeries("NaN test");
        for (long i = 0; i<= 10; i++){ // (0, NaN, 2, NaN, 3, NaN, 4, NaN, 5, ...)
            Num closePrice = i % 2 == 0 ? series.numOf(i): NaN;
            series.addBar(ZonedDateTime.now().plusDays(i),NaN, NaN,NaN, closePrice, NaN);
        }


        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 2);

        // index is the biggest of (index, index-1)
        for (int i = series.getBeginIndex(); i<= series.getEndIndex(); i++){
            if (i % 2 != 0) // current is NaN take the previous as highest
                assertEquals(series.getBar(i-1).getClosePrice().toString(), highestValue.getValue(i).toString());
            else // current is not NaN but previous, take the current
                assertEquals(series.getBar(i).getClosePrice().toString(), highestValue.getValue(i).toString());
        }
    }
}
