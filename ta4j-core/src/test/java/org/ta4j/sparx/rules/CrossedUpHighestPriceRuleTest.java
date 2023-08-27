package org.ta4j.sparx.rules;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CrossedUpHighestPriceRuleTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public CrossedUpHighestPriceRule crossedUpHighestPriceRule;

    public CrossedUpHighestPriceRuleTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void isSatisfied() {
        MockBarSeries series = new MockBarSeries(numFunction, 1, 3, 2, 4, 3, 6, 2, 3, 4, 5);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        crossedUpHighestPriceRule = new CrossedUpHighestPriceRule(closePrice, 3);

        assertTrue("When bar count is > index and last price is > highest then satisfied", crossedUpHighestPriceRule.isSatisfied(3));
        assertTrue("When bar count is > index and last price > highest then satisfied", crossedUpHighestPriceRule.isSatisfied(9));
        assertFalse("When bar count is > index and last price < highest then fail rule", crossedUpHighestPriceRule.isSatisfied(4));
        assertFalse("When bar count is < index then fail rule", crossedUpHighestPriceRule.isSatisfied(1));
        assertFalse("When bar count is = index then fail rule", crossedUpHighestPriceRule.isSatisfied(2));
        assertFalse("When bar count is 0 index then fail rule", crossedUpHighestPriceRule.isSatisfied(0));

    }

}
