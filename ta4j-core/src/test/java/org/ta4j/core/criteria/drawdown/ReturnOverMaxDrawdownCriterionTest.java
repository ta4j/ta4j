/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.criteria.drawdown;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class ReturnOverMaxDrawdownCriterionTest extends AbstractCriterionTest {

    private AnalysisCriterion returnOverMaxDrawDown;

    public ReturnOverMaxDrawdownCriterionTest(NumFactory numFactory) {
        super(params -> new ReturnOverMaxDrawdownCriterion(), numFactory);
    }

    @Before
    public void setUp() {
        this.returnOverMaxDrawDown = getCriterion();
    }

    @Test
    public void rewardRiskRatioCriterion() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 95, 100, 90, 95, 80, 120)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(7, series));

        var netProfit = (105d / 100) * (90d / 95d) * (120d / 95) - 1;
        var peak = (105d / 100) * (100d / 95);
        var low = (105d / 100) * (90d / 95) * (80d / 95);

        var result = returnOverMaxDrawDown.calculate(series, tradingRecord);

        assertNumEquals(netProfit / ((peak - low) / peak), result);
    }

    @Test
    public void rewardRiskRatioCriterionOnlyWithGain() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 6, 8, 20, 3).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        var result = returnOverMaxDrawDown.calculate(series, tradingRecord);

        // Total return = (2/1) * (20/3) = 40/3, rate of return = 40/3 - 1 = 37/3
        // If there's no drawdown, result = rate of return
        var totalReturn = 2d * (20d / 3d);
        var rateOfReturn = totalReturn - 1;
        assertNumEquals(rateOfReturn, result);
    }

    @Test
    public void rewardRiskRatioCriterionWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 6, 8, 20, 3).build();

        var result = returnOverMaxDrawDown.calculate(series, new BaseTradingRecord());

        assertNumEquals(0, result);
    }

    @Test
    public void withOnePosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 95, 95, 100, 90, 95, 80, 120)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        var ratioCriterion = getCriterion();

        var result = ratioCriterion.calculate(series, position);

        // Rate of return = (95/100) - 1 = -0.05, drawdown = 0.05
        // Result = -0.05 / 0.05 = -1
        assertNumEquals(-1d, result);

        var ratioCriterionMultiplicative = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var resultMultiplicative = ratioCriterionMultiplicative.calculate(series, position);
        // Return = 0.95 (MULTIPLICATIVE), drawdown = 0.05
        // Result = 0.95 / 0.05 = 19.0
        assertNumEquals(19.0, resultMultiplicative);

        var ratioCriterionPercentage = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var resultPercentage = ratioCriterionPercentage.calculate(series, position);
        // Return = -5.0 (PERCENTAGE), drawdown = 0.05 (always decimal)
        // Result = -5.0 / 0.05 = -100.0
        assertNumEquals(-100.0, resultPercentage);
    }

    @Test
    public void betterThan() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(3.5), numOf(2.2)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.7)));
    }

    @Test
    public void testNoDrawDownForTradingRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 95, 100, 90, 95, 80, 120)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        var result = returnOverMaxDrawDown.calculate(series, tradingRecord);

        // Total return = (105/100) * (100/95) = 105/95, rate of return = 105/95 - 1 =
        // 10/95
        // No drawdown, so result = rate of return
        assertNumEquals((105d / 95d) - 1, result);

        var criterionMultiplicative = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var resultMultiplicative = criterionMultiplicative.calculate(series, tradingRecord);
        // Total return = 105/95, no drawdown, so result = total return
        assertNumEquals(105d / 95d, resultMultiplicative);

        var criterionPercentage = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var resultPercentage = criterionPercentage.calculate(series, tradingRecord);
        // Rate of return = (105/95 - 1) * 100, no drawdown, so result = rate of return
        assertNumEquals(((105d / 95d) - 1) * 100, resultPercentage);
    }

    @Test
    public void testNoDrawDownForPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 95, 100, 90, 95, 80, 120)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var result = returnOverMaxDrawDown.calculate(series, position);

        // Total return = 105/100 = 1.05, rate of return = 0.05
        // No drawdown, so result = rate of return
        assertNumEquals(0.05, result);

        var criterionMultiplicative = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var resultMultiplicative = criterionMultiplicative.calculate(series, position);
        // Total return = 1.05, no drawdown, so result = total return
        assertNumEquals(1.05, resultMultiplicative);

        var criterionPercentage = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var resultPercentage = criterionPercentage.calculate(series, position);
        // Rate of return = 5.0, no drawdown, so result = rate of return
        assertNumEquals(5.0, resultPercentage);
    }

    @Test
    public void testNoDrawDownForOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 95, 100, 90, 95, 80, 120)
                .build();
        var position = new Position();
        position.operate(0, numFactory.hundred(), numFactory.one());

        var result = returnOverMaxDrawDown.calculate(series, position);

        assertNumEquals(0, result);
    }

}
