package org.ta4j.core.indicators.numeric;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class NumericIndicatorTest extends AbstractIndicatorTest<NumericIndicator, Num> {


    public NumericIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void plus() {

        final BarSeries series = new MockBarSeries(numFunction, 1,2,3,4,5,6,7,8,9,8,7,6,5,4,3);
        final ClosePriceIndicator cp1 = new ClosePriceIndicator(series);
        final EMAIndicator ema = new EMAIndicator(cp1, 3);

        final NumericIndicator numericIndicator = new NumericIndicator(cp1);

        final NumericIndicator staticPlus = numericIndicator.plus(5);
        assertNumEquals(1 + 5, staticPlus.getValue(0));
        assertNumEquals(9 + 5, staticPlus.getValue(8));

        final NumericIndicator dynamicPlus = numericIndicator.plus(ema);
        assertNumEquals(cp1.getValue(0).plus(ema.getValue(0)), dynamicPlus.getValue(0));
        assertNumEquals(cp1.getValue(8).plus(ema.getValue(8)), dynamicPlus.getValue(8));

    }

    @Test
    public void minus() {
        fail("todo");
    }

    @Test
    public void sqrt() {
        fail("todo");
    }

}