/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
    public void toRepresentationFromTotalReturn_TotalReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.12); // +12% gain

        Num result = ReturnRepresentation.TOTAL_RETURN.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toRepresentationFromTotalReturn_RateOfReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.12); // +12% gain

        Num result = ReturnRepresentation.RATE_OF_RETURN.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toRepresentationFromRateOfReturn_TotalReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num rateOfReturn = factory.numOf(0.12); // +12% gain

        Num result = ReturnRepresentation.TOTAL_RETURN.toRepresentationFromRateOfReturn(rateOfReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toRepresentationFromRateOfReturn_RateOfReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num rateOfReturn = factory.numOf(0.12); // +12% gain

        Num result = ReturnRepresentation.RATE_OF_RETURN.toRepresentationFromRateOfReturn(rateOfReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toRepresentationFromLogReturn_TotalReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(1.12)); // log of +12% gain

        Num result = ReturnRepresentation.TOTAL_RETURN.toRepresentationFromLogReturn(logReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toRepresentationFromLogReturn_RateOfReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(1.12)); // log of +12% gain

        Num result = ReturnRepresentation.RATE_OF_RETURN.toRepresentationFromLogReturn(logReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toTotalReturn_TotalReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(1.12);

        Num result = ReturnRepresentation.TOTAL_RETURN.toTotalReturn(representedReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toTotalReturn_RateOfReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(0.12);

        Num result = ReturnRepresentation.RATE_OF_RETURN.toTotalReturn(representedReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void toRateOfReturn_TotalReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(1.12);

        Num result = ReturnRepresentation.TOTAL_RETURN.toRateOfReturn(representedReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toRateOfReturn_RateOfReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num representedReturn = factory.numOf(0.12);

        Num result = ReturnRepresentation.RATE_OF_RETURN.toRateOfReturn(representedReturn);
        assertNumEquals(0.12, result);
    }

    @Test
    public void toTotalReturnFromLogReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num logReturn = factory.numOf(Math.log(1.12));

        Num result = ReturnRepresentation.TOTAL_RETURN.toTotalReturnFromLogReturn(logReturn);
        assertNumEquals(1.12, result);
    }

    @Test
    public void roundTripConversion_TotalReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num original = factory.numOf(1.12);

        // Convert to rate of return and back
        Num rateOfReturn = ReturnRepresentation.TOTAL_RETURN.toRateOfReturn(original);
        Num backToTotal = ReturnRepresentation.RATE_OF_RETURN.toTotalReturn(rateOfReturn);

        assertNumEquals(original, backToTotal);
    }

    @Test
    public void roundTripConversion_RateOfReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num original = factory.numOf(0.12);

        // Convert to total return and back
        Num totalReturn = ReturnRepresentation.RATE_OF_RETURN.toTotalReturn(original);
        Num backToRate = ReturnRepresentation.TOTAL_RETURN.toRateOfReturn(totalReturn);

        assertNumEquals(original, backToRate);
    }

    @Test
    public void worksWithDecimalNumFactory() {
        NumFactory factory = DecimalNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.12);

        Num result = ReturnRepresentation.RATE_OF_RETURN.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(0.12, result);

        // Verify it uses the same factory
        assertEquals(factory.getClass(), result.getNumFactory().getClass());
    }

    @Test
    public void negativeReturns() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(0.85); // -15% loss

        Num rateResult = ReturnRepresentation.RATE_OF_RETURN.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(-0.15, rateResult);

        Num backToTotal = ReturnRepresentation.RATE_OF_RETURN.toTotalReturn(rateResult);
        assertNumEquals(0.85, backToTotal);
    }

    @Test
    public void zeroReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num totalReturn = factory.numOf(1.0); // 0% change

        Num rateResult = ReturnRepresentation.RATE_OF_RETURN.toRepresentationFromTotalReturn(totalReturn);
        assertNumEquals(0.0, rateResult);

        Num backToTotal = ReturnRepresentation.RATE_OF_RETURN.toTotalReturn(rateResult);
        assertNumEquals(1.0, backToTotal);
    }
}
