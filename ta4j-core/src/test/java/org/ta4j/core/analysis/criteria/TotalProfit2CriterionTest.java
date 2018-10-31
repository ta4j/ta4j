package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.num.Num;

import java.util.function.Function;

public class TotalProfit2CriterionTest extends AbstractCriterionTest {

    public TotalProfit2CriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new TotalProfit2Criterion(), numFunction);
    }

    @Test
    public void testCalculateOneOpenTradeShouldReturnZero() {
        openedTradeUtils.testCalculateOneOpenTradeShouldReturnExpectedValue(numFunction, getCriterion(), 0);
    }
}