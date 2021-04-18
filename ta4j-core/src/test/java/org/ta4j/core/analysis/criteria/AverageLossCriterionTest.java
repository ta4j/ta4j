package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class AverageLossCriterionTest extends AbstractCriterionTest {

    public AverageLossCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new AverageLossCriterion(), numFunction);
    }

    @Test
    public void calculate() {
        BarSeries series = new MockBarSeries(numFunction, 100d, 95d, 102d, 105d, 97d, 113d, 115d, 120d, 110d, 90d);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Trade.buyAt(0, series), Trade.sellAt(1, series), // losing -5
                Trade.buyAt(2, series), Trade.sellAt(3, series), // winning +3
                Trade.buyAt(4, series), Trade.sellAt(5, series), // winning +16
                Trade.buyAt(6, series), Trade.sellAt(8, series) // losing -5
        );
        // (-5 + 3 + 16 -5) / 4 = 2.25 avg pnl
        // Abs(-5 -5)) / 2 = 5

        AnalysisCriterion avgLoss = getCriterion();

        assertNumEquals(5d, avgLoss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithShortPositions() {
        BarSeries series = new MockBarSeries(numFunction, 100d, 95d, 102d, 105d, 97d, 113d);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Trade.sellAt(0, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series), Trade.buyAt(4, series));
        AnalysisCriterion avgPnl = getCriterion();

        assertNumEquals(2d, avgPnl.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOnePosition() {
        BarSeries series = new MockBarSeries(numFunction, 100d, 95d, 102d, 105d, 97d, 113d);
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        AnalysisCriterion average = getCriterion();
        assertNumEquals(5d, average.calculate(series, position));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertFalse(criterion.betterThan(numOf(12), numOf(8)));
        assertTrue(criterion.betterThan(numOf(8), numOf(12)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction, getCriterion(), 0);
    }
}