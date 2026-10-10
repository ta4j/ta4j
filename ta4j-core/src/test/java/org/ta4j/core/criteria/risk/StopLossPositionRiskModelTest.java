/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.risk;

import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.rules.FixedAmountStopLossRule;

public class StopLossPositionRiskModelTest {

    @Test
    public void calculatesRiskForLongPosition() {
        var series = new MockBarSeriesBuilder().withData(100, 110).build();
        var numFactory = series.numFactory();
        Position position = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.two()),
                Trade.sellAt(1, numFactory.numOf(110), numFactory.two()));

        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertNumEquals(10, model.risk(series, position));
    }

    @Test
    public void calculatesRiskForShortPosition() {
        var series = new MockBarSeriesBuilder().withData(200, 190).build();
        var numFactory = series.numFactory();
        Position position = new Position(Trade.sellAt(0, numFactory.numOf(200), numFactory.three()),
                Trade.buyAt(1, numFactory.numOf(190), numFactory.three()));

        PositionRiskModel model = new StopLossPositionRiskModel(2.5);

        assertNumEquals(15, model.risk(series, position));
    }

    @Test
    public void calculatesRiskWithInjectedStopLossRule() {
        var series = new MockBarSeriesBuilder().withData(100, 95).build();
        var numFactory = series.numFactory();
        Position position = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.two()),
                Trade.sellAt(1, numFactory.numOf(95), numFactory.two()));

        var rule = new FixedAmountStopLossRule(new ClosePriceIndicator(series), numFactory.numOf(7));
        PositionRiskModel model = new StopLossPositionRiskModel(rule);

        assertNumEquals(14, model.risk(series, position));
    }

    @Test
    public void rejectsNonPositiveLossPercentage() {
        assertThrows(IllegalArgumentException.class, () -> new StopLossPositionRiskModel(0));
        assertThrows(IllegalArgumentException.class, () -> new StopLossPositionRiskModel(-1));
    }

    @Test
    public void returnsZeroRiskForMissingPositionContext() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 110).build();
        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertNumEquals(0, model.risk(series, null));
        assertNumEquals(0, model.risk(series, new Position()));
    }

    @Test
    public void rejectsNullSeriesWhenCalculatingRisk() {
        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertThrows(IllegalArgumentException.class, () -> model.risk(null, new Position()));
    }

    @Test
    public void returnsZeroRiskForNaNEntryPrice() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 110).build();
        Position position = new Position(Trade.buyAt(0, NaN.NaN, series.numFactory().one()),
                Trade.sellAt(1, series.numFactory().numOf(110), series.numFactory().one()));
        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertNumEquals(0, model.risk(series, position));
    }

    @Test
    public void returnsZeroRiskForZeroAmount() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 110).build();
        Position position = new Position(Trade.buyAt(0, series.numFactory().hundred(), series.numFactory().zero()),
                Trade.sellAt(1, series.numFactory().numOf(110), series.numFactory().zero()));
        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertNumEquals(0, model.risk(series, position));
    }
}
