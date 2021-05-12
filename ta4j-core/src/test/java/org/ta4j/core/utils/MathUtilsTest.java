package org.ta4j.core.utils;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.TestUtils;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

public class MathUtilsTest {

    private static final Function<Number, Num> function = DecimalNum::valueOf;

    @Test
    public final void testCompoundInterest() {
        Num initialCapital = function.apply(1000);
        Num percentGrowth = function.apply(0.01);
        int days = 100;
        Num compoundInterest = MathUtils.compoundInterest(initialCapital, percentGrowth, days);
        TestUtils.assertNumEquals(10.0496620928765688550188629073, compoundInterest);
    }

}
