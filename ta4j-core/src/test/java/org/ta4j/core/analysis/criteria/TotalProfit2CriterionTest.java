package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Order;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class TotalProfit2CriterionTest extends AbstractCriterionTest {

    public TotalProfit2CriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new TotalProfit2Criterion(), numFunction);
    }

    @Test
    public void testCalculateProfitOneOpenTradeShouldReturnZero() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);

        Trade trade = new Trade(Order.OrderType.BUY);
        trade.operate(0, series.numOf(2.5), series.numOf(1));

        final AnalysisCriterion criterion = getCriterion();

        final Num profit = criterion.calculate(series, trade);

        assertNumEquals(0, profit);
    }
}