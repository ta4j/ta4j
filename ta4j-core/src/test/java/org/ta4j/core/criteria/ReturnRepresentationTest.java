/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ReturnRepresentationTest {

    @Test
    public void toRepresentationFromTotalReturn_Multiplicative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.12); // +12% gain

        Num result = ReturnRepresentation.MULTIPLICATIVE.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toRepresentationFromTotalReturn_Decimal() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.12); // +12% gain

        Num result = ReturnRepresentation.DECIMAL.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toRepresentationFromRateOfReturn_Multiplicative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num rateOfReturn = factory.numOf(0.12); // +12% gain

        Num result = ReturnRepresentation.MULTIPLICATIVE.toRepresentationFromRateOfReturn(rateOfReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toRepresentationFromRateOfReturn_Decimal() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num rateOfReturn = factory.numOf(0.12); // +12% gain

        Num result = ReturnRepresentation.DECIMAL.toRepresentationFromRateOfReturn(rateOfReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toRepresentationFromLogReturn_Multiplicative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(1.12)); // log of +12% gain

        Num result = ReturnRepresentation.MULTIPLICATIVE.toRepresentationFromLogReturn(logReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toRepresentationFromLogReturn_Decimal() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(1.12)); // log of +12% gain

        Num result = ReturnRepresentation.DECIMAL.toRepresentationFromLogReturn(logReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toTotalReturn_Multiplicative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(1.12);

        Num result = ReturnRepresentation.MULTIPLICATIVE.toTotalReturn(representedReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toTotalReturn_Decimal() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(0.12);

        Num result = ReturnRepresentation.DECIMAL.toTotalReturn(representedReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toRateOfReturn_Multiplicative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(1.12);

        Num result = ReturnRepresentation.MULTIPLICATIVE.toRateOfReturn(representedReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toRateOfReturn_Decimal() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(0.12);

        Num result = ReturnRepresentation.DECIMAL.toRateOfReturn(representedReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toTotalReturnFromLogReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(1.12));

        Num result = ReturnRepresentation.MULTIPLICATIVE.toTotalReturnFromLogReturn(logReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void roundTripConversion_Multiplicative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num original = factory.numOf(1.12);

        // Convert to decimal and back
        Num rateOfReturn = ReturnRepresentation.MULTIPLICATIVE.toRateOfReturn(original);
        Num backToMultiplicative = ReturnRepresentation.DECIMAL.toTotalReturn(rateOfReturn);

        assertNumEquals(original, backToMultiplicative);
    }

    @Test
    public void roundTripConversion_Decimal() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num original = factory.numOf(0.12);

        // Convert to multiplicative and back
        Num totalReturn = ReturnRepresentation.DECIMAL.toTotalReturn(original);
        Num backToDecimal = ReturnRepresentation.MULTIPLICATIVE.toRateOfReturn(totalReturn);

        assertNumEquals(original, backToDecimal);
    }

    @Test
    public void worksWithDecimalNumFactory() {
        NumFactory factory = DecimalNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.12);

        Num result = ReturnRepresentation.DECIMAL.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(0.12, result);

        // Verify it uses the same factory
        assertEquals(factory.getClass(), result.getNumFactory().getClass());
    }

    @Test
    public void negativeReturns() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(0.85); // -15% loss

        Num decimalResult = ReturnRepresentation.DECIMAL.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(-0.15, decimalResult);

        Num backToTotal = ReturnRepresentation.DECIMAL.toTotalReturn(decimalResult);
        assertNumEquals(0.85, backToTotal);
    }

    @Test
    public void zeroReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.0); // 0% change

        Num decimalResult = ReturnRepresentation.DECIMAL.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(0.0, decimalResult);

        Num backToTotal = ReturnRepresentation.DECIMAL.toTotalReturn(decimalResult);
        assertNumEquals(1.0, backToTotal);
    }

    @Test
    public void toRepresentationFromTotalReturn_Percentage() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.05); // +5% gain

        Num result = ReturnRepresentation.PERCENTAGE.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(5.0, result);
    }

    @Test
    public void toRepresentationFromTotalReturn_Percentage_100PercentGain() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(2.0); // +100% gain

        Num result = ReturnRepresentation.PERCENTAGE.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(100.0, result);
    }

    @Test
    public void toRepresentationFromTotalReturn_Percentage_Negative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(0.85); // -15% loss

        Num result = ReturnRepresentation.PERCENTAGE.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(-15.0, result);
    }

    @Test
    public void toRepresentationFromRateOfReturn_Percentage() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num rateOfReturn = factory.numOf(0.05); // +5% gain

        Num result = ReturnRepresentation.PERCENTAGE.toRepresentationFromRateOfReturn(rateOfReturn);
        assertNumEquals(5.0, result);
    }

    @Test
    public void toRepresentationFromRateOfReturn_Percentage_Negative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num rateOfReturn = factory.numOf(-0.15); // -15% loss

        Num result = ReturnRepresentation.PERCENTAGE.toRepresentationFromRateOfReturn(rateOfReturn);
        assertNumEquals(-15.0, result);
    }

    @Test
    public void toRepresentationFromLogReturn_Percentage() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(1.12)); // log of +12% gain

        Num result = ReturnRepresentation.PERCENTAGE.toRepresentationFromLogReturn(logReturn);
        assertNumEquals(12.0, result);
    }

    @Test
    public void toTotalReturn_Percentage() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(5.0); // 5% gain

        Num result = ReturnRepresentation.PERCENTAGE.toTotalReturn(representedReturn);
        assertNumEquals(1.05, result);
    }

    @Test
    public void toTotalReturn_Percentage_100PercentGain() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(100.0); // 100% gain

        Num result = ReturnRepresentation.PERCENTAGE.toTotalReturn(representedReturn);
        assertNumEquals(2.0, result);
    }

    @Test
    public void toTotalReturn_Percentage_Negative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(-15.0); // -15% loss

        Num result = ReturnRepresentation.PERCENTAGE.toTotalReturn(representedReturn);
        assertNumEquals(0.85, result);
    }

    @Test
    public void toRateOfReturn_Percentage() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(5.0); // 5% gain

        Num result = ReturnRepresentation.PERCENTAGE.toRateOfReturn(representedReturn);
        assertNumEquals(0.05, result);
    }

    @Test
    public void toRateOfReturn_Percentage_Negative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(-15.0); // -15% loss

        Num result = ReturnRepresentation.PERCENTAGE.toRateOfReturn(representedReturn);
        assertNumEquals(-0.15, result);
    }

    @Test
    public void roundTripConversion_Percentage() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num original = factory.numOf(5.0); // 5% gain

        // Convert to total return and back
        Num totalReturn = ReturnRepresentation.PERCENTAGE.toTotalReturn(original);
        Num backToPercentage = ReturnRepresentation.PERCENTAGE.toRepresentationFromTotalReturn(totalReturn);

        assertNumEquals(original, backToPercentage);
    }

    @Test
    public void roundTripConversion_Percentage_FromTotalReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num original = factory.numOf(1.05); // +5% gain as total return

        // Convert to percentage and back
        Num percentage = ReturnRepresentation.PERCENTAGE.toRepresentationFromTotalReturn(original);
        Num backToTotal = ReturnRepresentation.PERCENTAGE.toTotalReturn(percentage);

        assertNumEquals(original, backToTotal);
    }

    @Test
    public void roundTripConversion_Percentage_FromRateOfReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num original = factory.numOf(0.05); // +5% gain as rate of return

        // Convert to percentage and back
        Num percentage = ReturnRepresentation.PERCENTAGE.toRepresentationFromRateOfReturn(original);
        Num backToRate = ReturnRepresentation.PERCENTAGE.toRateOfReturn(percentage);

        assertNumEquals(original, backToRate);
    }

    @Test
    public void percentageWorksWithDecimalNumFactory() {
        NumFactory factory = DecimalNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.05); // +5% gain

        Num result = ReturnRepresentation.PERCENTAGE.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(5.0, result);

        // Verify it uses the same factory
        assertEquals(factory.getClass(), result.getNumFactory().getClass());
    }

    @Test
    public void percentageNegativeReturns() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(0.85); // -15% loss

        Num percentageResult = ReturnRepresentation.PERCENTAGE.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(-15.0, percentageResult);

        Num backToTotal = ReturnRepresentation.PERCENTAGE.toTotalReturn(percentageResult);
        assertNumEquals(0.85, backToTotal);
    }

    @Test
    public void percentageZeroReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.0); // 0% change

        Num percentageResult = ReturnRepresentation.PERCENTAGE.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(0.0, percentageResult);

        Num backToTotal = ReturnRepresentation.PERCENTAGE.toTotalReturn(percentageResult);
        assertNumEquals(1.0, backToTotal);
    }

    @Test
    public void toRepresentationFromTotalReturn_Log() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.12); // +12% gain

        Num result = ReturnRepresentation.LOG.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(Math.log(1.12), result);
    }

    @Test
    public void toRepresentationFromTotalReturn_Log_100PercentGain() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(2.0); // +100% gain

        Num result = ReturnRepresentation.LOG.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(Math.log(2.0), result);
    }

    @Test
    public void toRepresentationFromTotalReturn_Log_Negative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(0.85); // -15% loss

        Num result = ReturnRepresentation.LOG.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(Math.log(0.85), result);
    }

    @Test
    public void toRepresentationFromRateOfReturn_Log() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num rateOfReturn = factory.numOf(0.12); // +12% gain

        Num result = ReturnRepresentation.LOG.toRepresentationFromRateOfReturn(rateOfReturn);
        assertNumEquals(Math.log(1.12), result);
    }

    @Test
    public void toRepresentationFromRateOfReturn_Log_Negative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num rateOfReturn = factory.numOf(-0.15); // -15% loss

        Num result = ReturnRepresentation.LOG.toRepresentationFromRateOfReturn(rateOfReturn);
        assertNumEquals(Math.log(0.85), result);
    }

    @Test
    public void toRepresentationFromLogReturn_Log() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(1.12)); // log of +12% gain

        Num result = ReturnRepresentation.LOG.toRepresentationFromLogReturn(logReturn);
        assertNumEquals(Math.log(1.12), result);
    }

    @Test
    public void toTotalReturn_Log() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(1.12)); // log of +12% gain

        Num result = ReturnRepresentation.LOG.toTotalReturn(logReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toTotalReturn_Log_100PercentGain() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(2.0)); // log of +100% gain

        Num result = ReturnRepresentation.LOG.toTotalReturn(logReturn);
        assertNumEquals(2.0, result);
    }

    @Test
    public void toTotalReturn_Log_Negative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(0.85)); // log of -15% loss

        Num result = ReturnRepresentation.LOG.toTotalReturn(logReturn);
        assertNumEquals(0.85, result);
    }

    @Test
    public void toRateOfReturn_Log() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(1.12)); // log of +12% gain

        Num result = ReturnRepresentation.LOG.toRateOfReturn(logReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toRateOfReturn_Log_Negative() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(0.85)); // log of -15% loss

        Num result = ReturnRepresentation.LOG.toRateOfReturn(logReturn);
        assertNumEquals(-0.15, result);
    }

    @Test
    public void roundTripConversion_Log() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num original = factory.numOf(Math.log(1.12)); // log of +12% gain

        // Convert to total return and back
        Num totalReturn = ReturnRepresentation.LOG.toTotalReturn(original);
        Num backToLog = ReturnRepresentation.LOG.toRepresentationFromTotalReturn(totalReturn);

        assertNumEquals(original, backToLog);
    }

    @Test
    public void roundTripConversion_Log_FromTotalReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num original = factory.numOf(1.12); // +12% gain as total return

        // Convert to log and back
        Num logReturn = ReturnRepresentation.LOG.toRepresentationFromTotalReturn(original);
        Num backToTotal = ReturnRepresentation.LOG.toTotalReturn(logReturn);

        assertNumEquals(original, backToTotal);
    }

    @Test
    public void roundTripConversion_Log_FromRateOfReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num original = factory.numOf(0.12); // +12% gain as rate of return

        // Convert to log and back
        Num logReturn = ReturnRepresentation.LOG.toRepresentationFromRateOfReturn(original);
        Num backToRate = ReturnRepresentation.LOG.toRateOfReturn(logReturn);

        assertNumEquals(original, backToRate);
    }

    @Test
    public void logWorksWithDecimalNumFactory() {
        NumFactory factory = DecimalNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.12); // +12% gain

        Num result = ReturnRepresentation.LOG.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(Math.log(1.12), result);

        // Verify it uses the same factory
        assertEquals(factory.getClass(), result.getNumFactory().getClass());
    }

    @Test
    public void logNegativeReturns() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(0.85); // -15% loss

        Num logResult = ReturnRepresentation.LOG.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(Math.log(0.85), logResult);

        Num backToTotal = ReturnRepresentation.LOG.toTotalReturn(logResult);
        assertNumEquals(0.85, backToTotal);
    }

    @Test
    public void logZeroReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.0); // 0% change

        Num logResult = ReturnRepresentation.LOG.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(0.0, logResult);

        Num backToTotal = ReturnRepresentation.LOG.toTotalReturn(logResult);
        assertNumEquals(1.0, backToTotal);
    }
}
