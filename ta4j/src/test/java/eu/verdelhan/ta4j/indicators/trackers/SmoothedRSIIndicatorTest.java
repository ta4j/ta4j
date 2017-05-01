package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SmoothedRSIIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(
            50.45, 50.30, 50.20,
            50.15, 50.05, 50.06,
            50.10, 50.08, 50.03,
            50.07, 50.01, 50.14,
            50.22, 50.43, 50.50,
            50.56, 50.52, 50.70,
            50.55, 50.62, 50.90,
            50.82, 50.86, 51.20, 51.30, 51.10);
    }

    @Test
    public void rsiUsingTimeFrame14UsingClosePrice() {
        SmoothedRSIIndicator rsi = new SmoothedRSIIndicator(new ClosePriceIndicator(data), 2);

        assertDecimalEquals(rsi.getValue(2), 0.0);
        assertDecimalEquals(rsi.getValue(3), 0.0);
        assertDecimalEquals(rsi.getValue(4), 0.0);
        assertDecimalEquals(rsi.getValue(5), 9.638554217);
        assertDecimalEquals(rsi.getValue(6), 48.97959184);
        assertDecimalEquals(rsi.getValue(7), 34.12322275);
        assertDecimalEquals(rsi.getValue(8), 13.55932203);
        assertDecimalEquals(rsi.getValue(9), 55.99232982);
        assertDecimalEquals(rsi.getValue(10), 22.64443583);
        assertDecimalEquals(rsi.getValue(11), 78.39740119);
        assertDecimalEquals(rsi.getValue(12), 88.55224651);
    }

    @Test
    public void smoothedRsiFirstValueShouldBeZero() {
        SmoothedRSIIndicator rsi = new SmoothedRSIIndicator(new ClosePriceIndicator(data), 14);

        assertEquals(Decimal.ZERO, rsi.getValue(0));
    }

    @Test
    public void smoothedRsiIsNeverHundredIfLossPresentInDataSeries() {
        SmoothedRSIIndicator rsi = new SmoothedRSIIndicator(new ClosePriceIndicator(data), 3);

        assertNotEquals(Decimal.HUNDRED, rsi.getValue(14));
        assertNotEquals(Decimal.HUNDRED, rsi.getValue(15));
    }
}
