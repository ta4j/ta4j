/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.NumberOfWinningPositionsCriterion;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class CriterionIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private CriterionIndicator criterionIndicator;
    private NumberOfWinningPositionsCriterion numberOfWinningPositionsCriterion;

    public CriterionIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testWithEmptyTradingRecord() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord();
        numberOfWinningPositionsCriterion = new NumberOfWinningPositionsCriterion();
        Num requiredCriterionValue = series.numOf(1);
        criterionIndicator = new CriterionIndicator(series, tradingRecord, numberOfWinningPositionsCriterion,
                requiredCriterionValue);

        // empty tradingRecords must return always false
        assertFalse(criterionIndicator.getValue(0));
        assertFalse(criterionIndicator.getValue(1));
        assertFalse(criterionIndicator.getValue(2));
        assertFalse(criterionIndicator.getValue(3));
        assertFalse(criterionIndicator.getValue(4));
        assertFalse(criterionIndicator.getValue(5));
    }

    @Test
    public void testWithTradingRecord() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);

        // TradingRecord has 2 winning positions
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        numberOfWinningPositionsCriterion = new NumberOfWinningPositionsCriterion();

        // you need at least 1 winning position
        Num requiredCriterionValue = series.numOf(1);
        criterionIndicator = new CriterionIndicator(series, tradingRecord, numberOfWinningPositionsCriterion,
                requiredCriterionValue);

        // till index 0, no winning position is made
        assertFalse(criterionIndicator.getValue(0));
        // till index 1, no winning position is made
        assertFalse(criterionIndicator.getValue(1));
        // till index 2, at least one winning position is made
        assertTrue(criterionIndicator.getValue(2));
        // till index 3, at least one winning position is made
        assertTrue(criterionIndicator.getValue(3));
        // till index 4, at least one winning position is made
        assertTrue(criterionIndicator.getValue(4));
        // till index 5, at least one winning position is made
        assertTrue(criterionIndicator.getValue(5));
    }

    @Test
    public void testWithPosition() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 115, 105, 110);

        // Position is a winning position
        Position position = new Position(Trade.buyAt(2, series), Trade.sellAt(3, series));

        numberOfWinningPositionsCriterion = new NumberOfWinningPositionsCriterion();

        // you need at least 1 winning position
        Num requiredCriterionValue = series.numOf(1);
        criterionIndicator = new CriterionIndicator(series, position, numberOfWinningPositionsCriterion,
                requiredCriterionValue);

        // till index 0, no winning position is made
        assertFalse(criterionIndicator.getValue(0));
        // till index 1, no winning position is made
        assertFalse(criterionIndicator.getValue(1));
        // till index 2, no winning position is made
        assertFalse(criterionIndicator.getValue(2));
        // till index 3, at least one winning position is made
        assertTrue(criterionIndicator.getValue(3));
        // till index 4, at least one winning position is made
        assertTrue(criterionIndicator.getValue(4));
        // till index 5, at least one winning position is made
        assertTrue(criterionIndicator.getValue(5));
    }

}
