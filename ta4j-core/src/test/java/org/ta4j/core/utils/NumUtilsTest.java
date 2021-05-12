package org.ta4j.core.utils;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.TestUtils;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

public class NumUtilsTest {

    private static final Function<Number, Num> function = DecimalNum::valueOf;

    @Test
    public final void testCompoundInterest() {
        Num initialCapital = function.apply(1000);
        Num percentGrowth = function.apply(5);
        int years = 1;
        Num compoundInterest = NumUtils.compoundInterest(initialCapital, percentGrowth, years);
        TestUtils.assertNumEquals(50, compoundInterest);
    }

}
