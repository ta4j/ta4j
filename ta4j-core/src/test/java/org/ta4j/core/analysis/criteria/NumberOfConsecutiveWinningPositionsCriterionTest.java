package org.ta4j.core.analysis.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class NumberOfConsecutiveWinningPositionsCriterionTest extends AbstractCriterionTest {

    public NumberOfConsecutiveWinningPositionsCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new NumberOfConsecutiveWinningPositionsCriterion(), numFunction);
    }

    @Test
    public void calculateWithNoPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);

        assertNumEquals(0, getCriterion().calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithTwoLongPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 120, 130, 140);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series),
                Trade.buyAt(3, series), Trade.sellAt(4, series));

        assertNumEquals(2, getCriterion().calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOneLongPosition() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 120, 95, 105);
        Position position = new Position(Trade.buyAt(1, series), Trade.sellAt(3, series));

        assertNumEquals(1, getCriterion().calculate(series, position));
    }

    @Test
    public void calculateWithTwoShortPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
                Trade.sellAt(3, series), Trade.buyAt(5, series));

        assertNumEquals(0, getCriterion().calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertFalse(criterion.betterThan(numOf(3), numOf(6)));
        assertTrue(criterion.betterThan(numOf(7), numOf(4)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction, getCriterion(), 0);
    }
}
