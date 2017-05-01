package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalNotEquals;

public class SmoothedAverageLossIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void smoothedAverageLossUsingTimeFrame5UsingClosePrice() {
        SmoothedAverageLossIndicator averageLoss = new SmoothedAverageLossIndicator(new ClosePriceIndicator(data), 5);

        assertDecimalEquals(averageLoss.getValue(5), "0.2");
        assertDecimalEquals(averageLoss.getValue(6), "0.16");
        assertDecimalEquals(averageLoss.getValue(7), "0.328");
        assertDecimalEquals(averageLoss.getValue(8), "0.4624");
        assertDecimalEquals(averageLoss.getValue(9), "0.36992");
        assertDecimalEquals(averageLoss.getValue(10), "0.295936");
        assertDecimalEquals(averageLoss.getValue(11), "0.4367488");
        assertDecimalEquals(averageLoss.getValue(12), "0.54939904");
    }

    @Test
    public void smoothedAverageLossMustReturnZeroWhenPrecedingDataOnlyGain() {
        SmoothedAverageLossIndicator averageLoss = new SmoothedAverageLossIndicator(new ClosePriceIndicator(data), 4);
        assertDecimalEquals(averageLoss.getValue(3), 0);
    }

    @Test
    public void smoothedAverageLossMustReturnNonZeroWhenDataLossAtLeastOnce() {
        SmoothedAverageLossIndicator averageLoss = new SmoothedAverageLossIndicator(new ClosePriceIndicator(data), 2);
        assertDecimalNotEquals(averageLoss.getValue(6), 0);
    }

    @Test
    public void smoothedAverageLossWhenTimeFrameIsGreaterThanIndex() {
        SmoothedAverageLossIndicator averageLoss = new SmoothedAverageLossIndicator(new ClosePriceIndicator(data), 1000);
        assertDecimalEquals(averageLoss.getValue(12), 5d / data.getTickCount());
    }

    @Test
    public void smoothedAverageLossWhenIndexIsZeroMustBeZero() {
        AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 10);
        assertDecimalEquals(averageLoss.getValue(0), 0);
    }
}
