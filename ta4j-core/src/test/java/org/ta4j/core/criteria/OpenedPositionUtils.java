/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OpenedPositionUtils {

    public void testCalculateOneOpenPositionShouldReturnExpectedValue(NumFactory numFactory,
            AnalysisCriterion criterion, Num expectedValue) {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();

        var trade = new Position(Trade.TradeType.BUY);
        trade.operate(0, series.numFactory().numOf(2.5), series.numFactory().one());

        final Num value = criterion.calculate(series, trade);

        assertNumEquals(expectedValue, value);
    }

    public void testCalculateOneOpenPositionShouldReturnExpectedValue(NumFactory numFactory,
            AnalysisCriterion criterion, int expectedValue) {
        this.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, criterion,
                numFactory.numOf(expectedValue));
    }
}
