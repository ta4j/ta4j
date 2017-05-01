package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalNotEquals;

public class SmoothedAverageGainIndicatorTest {

    private TimeSeries data;

    @Before
    public void prepare() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void smoothedAverageGainUsingTimeFrame5UsingClosePrice() {
        SmoothedAverageGainIndicator averageGain = new SmoothedAverageGainIndicator(new ClosePriceIndicator(data), 5);

        assertDecimalEquals(averageGain.getValue(5), "0.8");
        assertDecimalEquals(averageGain.getValue(6), "0.84");
        assertDecimalEquals(averageGain.getValue(7), "0.672");
        assertDecimalEquals(averageGain.getValue(8), "0.5376");
        assertDecimalEquals(averageGain.getValue(9), "0.43008");
        assertDecimalEquals(averageGain.getValue(10), "0.544064");
        assertDecimalEquals(averageGain.getValue(11), "0.4352512");
        assertDecimalEquals(averageGain.getValue(12), "0.34820096");
    }

    @Test
    public void smoothedAverageGainMustReturnNonZeroWhenDataGainedAtLeastOnce() {
        SmoothedAverageGainIndicator averageGain = new SmoothedAverageGainIndicator(new ClosePriceIndicator(data), 3);
        assertDecimalNotEquals(averageGain.getValue(9), 0);
    }

    @Test
    public void smoothedAverageGainWhenTimeFrameIsGreaterThanIndicatorDataShouldBeCalculatedWithDataSize() {
        SmoothedAverageGainIndicator averageGain = new SmoothedAverageGainIndicator(new ClosePriceIndicator(data), 1000);
        assertDecimalEquals(averageGain.getValue(12), 6d / data.getTickCount());
    }

    @Test
    public void smoothedAverageGainWhenIndexIsZeroMustBeZero() {
        SmoothedAverageGainIndicator averageGain = new SmoothedAverageGainIndicator(new ClosePriceIndicator(data), 10);
        assertDecimalEquals(averageGain.getValue(0), 0);
    }
}
