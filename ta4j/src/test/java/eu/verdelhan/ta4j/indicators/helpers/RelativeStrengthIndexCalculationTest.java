package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import static org.junit.Assert.assertEquals;

public class RelativeStrengthIndexCalculationTest {

    private TimeSeries gains;
    private TimeSeries losses;

    @Before
    public void prepare() {
        gains = new MockTimeSeries(1, 1, 0.8, 0.84, 0.672, 0.5376, 0.43008);
        losses = new MockTimeSeries(2, 0, 0.2, 0.16, 0.328, 0.4624, 0.36992);
    }

    @Test
    public void rsiCalculationFromMockedGainsAndLosses() {
        RelativeStrengthIndexCalculation rsiCalc = new RelativeStrengthIndexCalculation(
            new ClosePriceIndicator(gains),
            new ClosePriceIndicator(losses)
        );

        assertDecimalEquals(rsiCalc.getValue(2), 80.0);
        assertDecimalEquals(rsiCalc.getValue(3), 84.0);
        assertDecimalEquals(rsiCalc.getValue(4), 67.2);
        assertDecimalEquals(rsiCalc.getValue(5), 53.76);
        assertDecimalEquals(rsiCalc.getValue(6), 53.76);

    }

    @Test
    public void rsiCalcFirstValueShouldBeZero() {
        RelativeStrengthIndexCalculation rsiCalc = new RelativeStrengthIndexCalculation(
            new ClosePriceIndicator(gains),
            new ClosePriceIndicator(losses)
        );

        assertEquals(Decimal.ZERO, rsiCalc.getValue(0));
    }

    @Test
    public void rsiCalcHundredIfNoLoss() {
        RelativeStrengthIndexCalculation rsiCalc = new RelativeStrengthIndexCalculation(
            new ClosePriceIndicator(gains),
            new ClosePriceIndicator(losses)
        );

        assertEquals(Decimal.HUNDRED, rsiCalc.getValue(1));
    }

}
