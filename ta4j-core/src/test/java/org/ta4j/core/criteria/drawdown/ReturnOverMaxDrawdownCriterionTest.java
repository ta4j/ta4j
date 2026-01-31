/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import static org.junit.Assert.assertEquals;
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
        // Rate of return = -0.05 (0-based), drawdown = 0.05
        // Result = -0.05 / 0.05 = -1.0
        // For MULTIPLICATIVE, return the ratio as-is (ratios can be negative)
        assertNumEquals(-1.0, resultMultiplicative);

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

    @Test
    public void testReturnRepresentationIsCorrectlyStored() {
        var decimalCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL);
        assertTrue(decimalCriterion.getReturnRepresentation().isPresent());
        assertEquals(ReturnRepresentation.DECIMAL, decimalCriterion.getReturnRepresentation().get());

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        assertTrue(multiplicativeCriterion.getReturnRepresentation().isPresent());
        assertEquals(ReturnRepresentation.MULTIPLICATIVE, multiplicativeCriterion.getReturnRepresentation().get());

        var percentageCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        assertTrue(percentageCriterion.getReturnRepresentation().isPresent());
        assertEquals(ReturnRepresentation.PERCENTAGE, percentageCriterion.getReturnRepresentation().get());
    }

    @Test
    public void testPositiveReturnWithDrawdownAllRepresentations() {
        // Position: buy at 100, price goes to 110, then drops to 105, then sell at 110
        // Net return: (110/100) - 1 = 0.10
        // Drawdown: from peak 110 to trough 105 = (110-105)/110 = 0.04545...
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105, 110).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(3, series));

        var decimalCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL);
        var decimalResult = decimalCriterion.calculate(series, position);
        // Should have a positive ratio (return > drawdown)
        assertTrue(decimalResult.isGreaterThan(numFactory.zero()));

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var multiplicativeResult = multiplicativeCriterion.calculate(series, position);
        // For positive ratio: 1 + decimalResult
        var expectedMultiplicative = decimalResult.plus(numFactory.one());
        assertNumEquals(expectedMultiplicative, multiplicativeResult);

        var percentageCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var percentageResult = percentageCriterion.calculate(series, position);
        // Ratio * 100
        var expectedPercentage = decimalResult.multipliedBy(numFactory.numOf(100));
        assertNumEquals(expectedPercentage, percentageResult);
    }

    @Test
    public void testNegativeReturnWithDrawdownAllRepresentations() {
        // Position: buy at 100, sell at 90 (10% loss)
        // Drawdown: from 100 to 90 (10% drawdown)
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90, 95, 100).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        // Rate of return = (90/100) - 1 = -0.10, drawdown = 0.10
        // Ratio = -0.10 / 0.10 = -1.0

        var decimalCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL);
        var decimalResult = decimalCriterion.calculate(series, position);
        assertNumEquals(-1.0, decimalResult);

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var multiplicativeResult = multiplicativeCriterion.calculate(series, position);
        // For negative ratio: return as-is = -1.0
        assertNumEquals(-1.0, multiplicativeResult);

        var percentageCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var percentageResult = percentageCriterion.calculate(series, position);
        // Ratio * 100 = -1.0 * 100 = -100.0
        assertNumEquals(-100.0, percentageResult);
    }

    @Test
    public void testZeroReturnWithDrawdown() {
        // Position: buy at 100, sell at 100 (0% return)
        // Drawdown: from 100 to 95 (5% drawdown)
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 100).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series));

        // Rate of return = (100/100) - 1 = 0.0, drawdown = 0.05
        // Ratio = 0.0 / 0.05 = 0.0

        var decimalCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL);
        var decimalResult = decimalCriterion.calculate(series, position);
        assertNumEquals(0.0, decimalResult);

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var multiplicativeResult = multiplicativeCriterion.calculate(series, position);
        // For zero ratio: 1 + 0.0 = 1.0
        assertNumEquals(1.0, multiplicativeResult);

        var percentageCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var percentageResult = percentageCriterion.calculate(series, position);
        // Ratio * 100 = 0.0 * 100 = 0.0
        assertNumEquals(0.0, percentageResult);
    }

    @Test
    public void testMultiplicativeUsesZeroBasedReturnInternally() {
        // This test specifically verifies the fix: MULTIPLICATIVE representation
        // should use 0-based return internally, not 1-based
        // Position: buy at 100, sell at 95 (5% loss)
        // Drawdown: 5%
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 95, 100).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        // If using 1-based return (buggy): 0.95 / 0.05 = 19.0
        // If using 0-based return (correct): -0.05 / 0.05 = -1.0
        // For MULTIPLICATIVE with negative ratio: return as-is = -1.0

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = multiplicativeCriterion.calculate(series, position);
        // Should be -1.0, not 19.0 (which would be the buggy result)
        assertNumEquals(-1.0, result);
        // Explicitly verify it's NOT the buggy value
        assertFalse(result.isEqual(numFactory.numOf(19.0)));
    }

    @Test
    public void testLargePositiveRatioAllRepresentations() {
        // Position: buy at 100, price goes to 200, then drops to 190, then sell at 200
        // Net return: (200/100) - 1 = 1.0
        // Drawdown: from peak 200 to trough 190 = (200-190)/200 = 0.05
        // Ratio = 1.0 / 0.05 = 20.0
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 200, 190, 200).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(3, series));

        var decimalCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL);
        var decimalResult = decimalCriterion.calculate(series, position);
        // Should have a large positive ratio
        assertTrue(decimalResult.isGreaterThan(numFactory.numOf(10)));

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var multiplicativeResult = multiplicativeCriterion.calculate(series, position);
        // For positive ratio: 1 + decimalResult
        var expectedMultiplicative = decimalResult.plus(numFactory.one());
        assertNumEquals(expectedMultiplicative, multiplicativeResult);

        var percentageCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var percentageResult = percentageCriterion.calculate(series, position);
        // Ratio * 100
        var expectedPercentage = decimalResult.multipliedBy(numFactory.numOf(100));
        assertNumEquals(expectedPercentage, percentageResult);
    }

    @Test
    public void testSmallPositiveRatioAllRepresentations() {
        // Position: buy at 100, price goes to 101, then drops to 100.5, then sell at
        // 101
        // Net return: (101/100) - 1 = 0.01
        // Drawdown: from peak 101 to trough 100.5 = (101-100.5)/101 ≈ 0.00495
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 101, 100.5, 101).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(3, series));

        var decimalCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL);
        var decimalResult = decimalCriterion.calculate(series, position);
        // Should have a positive ratio
        assertTrue(decimalResult.isGreaterThan(numFactory.zero()));

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var multiplicativeResult = multiplicativeCriterion.calculate(series, position);
        // For positive ratio: 1 + decimalResult
        var expectedMultiplicative = decimalResult.plus(numFactory.one());
        assertNumEquals(expectedMultiplicative, multiplicativeResult);

        var percentageCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var percentageResult = percentageCriterion.calculate(series, position);
        // Ratio * 100
        var expectedPercentage = decimalResult.multipliedBy(numFactory.numOf(100));
        assertNumEquals(expectedPercentage, percentageResult);
    }

    @Test
    public void testTradingRecordWithMultiplePositionsAllRepresentations() {
        // Multiple positions with gains and drawdowns
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 105, 115, 110, 120)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));

        // Total return = (110/100) * (115/105) * (120/110) = 1.1 * 1.095... * 1.091...
        // ≈ 1.315
        // Rate of return ≈ 0.315
        // Max drawdown occurs from 110 to 105, then recovers, so drawdown ≈ 0.045
        // Ratio ≈ 0.315 / 0.045 ≈ 7.0

        var decimalCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL);
        var decimalResult = decimalCriterion.calculate(series, tradingRecord);
        // Verify it's a positive ratio
        assertTrue(decimalResult.isGreaterThan(numFactory.zero()));

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var multiplicativeResult = multiplicativeCriterion.calculate(series, tradingRecord);
        // Should be 1 + decimalResult for positive ratio
        var expectedMultiplicative = decimalResult.plus(numFactory.one());
        assertNumEquals(expectedMultiplicative, multiplicativeResult);

        var percentageCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var percentageResult = percentageCriterion.calculate(series, tradingRecord);
        // Should be decimalResult * 100
        var expectedPercentage = decimalResult.multipliedBy(numFactory.numOf(100));
        assertNumEquals(expectedPercentage, percentageResult);
    }

    @Test
    public void testNoDrawdownReturnsNetReturnInCorrectRepresentation() {
        // Position with no drawdown: buy at 100, sell at 110
        // No drawdown occurs
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 115, 120).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        // Rate of return = (110/100) - 1 = 0.10

        var decimalCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL);
        var decimalResult = decimalCriterion.calculate(series, position);
        assertNumEquals(0.10, decimalResult);

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var multiplicativeResult = multiplicativeCriterion.calculate(series, position);
        // Should convert 0.10 rate to MULTIPLICATIVE: 1 + 0.10 = 1.10
        assertNumEquals(1.10, multiplicativeResult);

        var percentageCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var percentageResult = percentageCriterion.calculate(series, position);
        // Should convert 0.10 rate to PERCENTAGE: 0.10 * 100 = 10.0
        assertNumEquals(10.0, percentageResult);
    }

    @Test
    public void testNegativeReturnWithDrawdown() {
        // Position: buy at 100, sell at 90 (10% loss)
        // Drawdown: from 100 to 90 = 10% = 0.10
        // When return equals drawdown, ratio = -0.10 / 0.10 = -1.0
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90, 95, 100).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        // Rate of return = (90/100) - 1 = -0.10
        // Drawdown = 0.10 (from entry to exit)
        // Ratio = -0.10 / 0.10 = -1.0

        var decimalCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL);
        var decimalResult = decimalCriterion.calculate(series, position);
        assertNumEquals(-1.0, decimalResult);

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var multiplicativeResult = multiplicativeCriterion.calculate(series, position);
        // For negative ratio: return as-is = -1.0
        assertNumEquals(-1.0, multiplicativeResult);

        var percentageCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var percentageResult = percentageCriterion.calculate(series, position);
        // Ratio * 100 = -1.0 * 100 = -100.0
        assertNumEquals(-100.0, percentageResult);
    }

    @Test
    public void testBoundaryCaseZeroRatio() {
        // Position: buy at 100, price goes to 105, then drops to 100, then sell at 105
        // Net return: (105/100) - 1 = 0.05
        // Drawdown: from peak 105 to trough 100 = (105-100)/105 ≈ 0.0476
        // Ratio ≈ 0.05 / 0.0476 ≈ 1.05 (close to 1.0)
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 100, 105).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(3, series));

        var decimalCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL);
        var decimalResult = decimalCriterion.calculate(series, position);
        // Should have a positive ratio close to 1.0
        assertTrue(decimalResult.isGreaterThan(numFactory.zero()));
        assertTrue(decimalResult.isLessThanOrEqual(numFactory.numOf(2.0)));

        var multiplicativeCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var multiplicativeResult = multiplicativeCriterion.calculate(series, position);
        // For positive ratio: 1 + decimalResult
        var expectedMultiplicative = decimalResult.plus(numFactory.one());
        assertNumEquals(expectedMultiplicative, multiplicativeResult);

        var percentageCriterion = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
        var percentageResult = percentageCriterion.calculate(series, position);
        // Ratio * 100
        var expectedPercentage = decimalResult.multipliedBy(numFactory.numOf(100));
        assertNumEquals(expectedPercentage, percentageResult);
    }

}
