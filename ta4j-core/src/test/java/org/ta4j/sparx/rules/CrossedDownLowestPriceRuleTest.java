package org.ta4j.sparx.rules;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.*;

public class CrossedDownLowestPriceRuleTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    public CrossedDownLowestPriceRuleTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void isSatisfied() {
        MockBarSeries series = new MockBarSeries(numFunction, 4, 3, 2, 1, 1, 2);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        CrossedDownLowestPriceRule crossedDownLowestPriceRule = new CrossedDownLowestPriceRule(closePrice, 3);

        assertTrue("When latest price is < lowest price and index is > bar count then rule satisfied.", crossedDownLowestPriceRule.isSatisfied(3));
        assertFalse("When latest price is > lowest price and index is > bar count then rule is not satisfied.", crossedDownLowestPriceRule.isSatisfied(5));
        assertFalse("When index is < bar count then rule is not satisfied.", crossedDownLowestPriceRule.isSatisfied(2));
        assertFalse("When index is 0 then rule is not satisfied.", crossedDownLowestPriceRule.isSatisfied(0));
    }
}
