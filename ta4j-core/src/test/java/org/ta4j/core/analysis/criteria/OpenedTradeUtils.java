package org.ta4j.core.analysis.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Order;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class OpenedTradeUtils {

    public void testCalculateOneOpenTradeShouldReturnExpectedValue(Function<Number, Num> numFunction, AnalysisCriterion criterion,
            int expectedValue) {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);

        Trade trade = new Trade(Order.OrderType.BUY);
        trade.operate(0, series.numOf(2.5), series.numOf(1));

        final Num value = criterion.calculate(series, trade);

        assertNumEquals(expectedValue, value);
    }
}
