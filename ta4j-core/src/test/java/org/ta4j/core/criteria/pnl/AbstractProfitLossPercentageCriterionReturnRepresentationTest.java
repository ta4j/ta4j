/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * Tests for {@link ReturnRepresentation} functionality in
 * {@link AbstractProfitLossPercentageCriterion}.
 */
public class AbstractProfitLossPercentageCriterionReturnRepresentationTest {

    @Test
    public void calculatePositionWithRateOfReturn_Profit() {
        // Test: 100 -> 105 = 5% profit
        // Rate: 0.05, Total Return: 1.05
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, position);

        assertNumEquals(0.05, result);
    }

    @Test
    public void calculatePositionWithTotalReturn_Profit() {
        // Test: 100 -> 105 = 5% profit
        // Rate: 0.05, Total Return: 1.05
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, position);

        assertNumEquals(1.05, result);
    }

    @Test
    public void calculatePositionWithRateOfReturn_Loss() {
        // Test: 100 -> 95 = -5% loss
        // Rate: -0.05, Total Return: 0.95
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, position);

        assertNumEquals(-0.05, result);
    }

    @Test
    public void calculatePositionWithTotalReturn_Loss() {
        // Test: 100 -> 95 = -5% loss
        // Rate: -0.05, Total Return: 0.95
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, position);

        assertNumEquals(0.95, result);
    }

    @Test
    public void calculatePositionWithRateOfReturn_OpenPosition() {
        // Open positions should return 0.0 for DECIMAL (neutral)
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var record = new BaseTradingRecord();
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        var position = record.getCurrentPosition();

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, position);

        assertNumEquals(0.0, result);
    }

    @Test
    public void calculatePositionWithTotalReturn_OpenPosition() {
        // Open positions should return 1.0 for MULTIPLICATIVE (neutral)
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var record = new BaseTradingRecord();
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        var position = record.getCurrentPosition();

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, position);

        assertNumEquals(1.0, result);
    }

    @Test
    public void calculateTradingRecordWithRateOfReturn_MultiplePositions() {
        // Test: 100->105 (5%), 100->110 (10%)
        // Combined: (5+10)/200 = 7.5% = 0.075 rate, 1.075 total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105, 100, 110)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series));

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, record);

        assertNumEquals(0.075, result);
    }

    @Test
    public void calculateTradingRecordWithTotalReturn_MultiplePositions() {
        // Test: 100->105 (5%), 100->110 (10%)
        // Combined: (5+10)/200 = 7.5% = 0.075 rate, 1.075 total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105, 100, 110)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series));

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, record);

        assertNumEquals(1.075, result);
    }

    @Test
    public void calculateTradingRecordWithRateOfReturn_NoPositions() {
        // No positions should return 0.0 for DECIMAL (neutral)
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var record = new BaseTradingRecord();

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, record);

        assertNumEquals(0.0, result);
    }

    @Test
    public void calculateTradingRecordWithTotalReturn_NoPositions() {
        // No positions should return 1.0 for MULTIPLICATIVE (neutral)
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var record = new BaseTradingRecord();

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, record);

        assertNumEquals(1.0, result);
    }

    @Test
    public void calculateTradingRecordWithRateOfReturn_MixedProfitLoss() {
        // Test: 100->95 (-5%), 100->110 (10%)
        // Combined: (-5+10)/200 = 2.5% = 0.025 rate, 1.025 total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95, 100, 110)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series));

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, record);

        assertNumEquals(0.025, result);
    }

    @Test
    public void calculateTradingRecordWithTotalReturn_MixedProfitLoss() {
        // Test: 100->95 (-5%), 100->110 (10%)
        // Combined: (-5+10)/200 = 2.5% = 0.025 rate, 1.025 total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95, 100, 110)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series));

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, record);

        assertNumEquals(1.025, result);
    }

    @Test
    public void grossProfitLossPercentageCriterionWithRateOfReturn() {
        // Test GrossProfitLossPercentageCriterion with DECIMAL
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var criterion = new GrossProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, position);

        assertNumEquals(0.05, result);
    }

    @Test
    public void grossProfitLossPercentageCriterionWithTotalReturn() {
        // Test GrossProfitLossPercentageCriterion with MULTIPLICATIVE
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var criterion = new GrossProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, position);

        assertNumEquals(1.05, result);
    }

    @Test
    public void defaultConstructorUsesGlobalDefault() {
        // Verify that default constructor uses
        // ReturnRepresentationPolicy.getDefaultRepresentation()
        var originalDefault = ReturnRepresentationPolicy.getDefaultRepresentation();

        try {
            // Set a known default
            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.DECIMAL);

            var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                    .withData(100, 105)
                    .build();
            var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

            // Use default constructor
            var criterion = new NetProfitLossPercentageCriterion();
            var result = criterion.calculate(series, position);

            // Should use DECIMAL (0.05, not 1.05)
            assertNumEquals(0.05, result);

            // Change default and verify it's used
            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.MULTIPLICATIVE);
            var criterion2 = new NetProfitLossPercentageCriterion();
            var result2 = criterion2.calculate(series, position);

            // Should use MULTIPLICATIVE (1.05, not 0.05)
            assertNumEquals(1.05, result2);
        } finally {
            // Restore original default
            ReturnRepresentationPolicy.setDefaultRepresentation(originalDefault);
        }
    }

    @Test
    public void explicitRepresentationOverridesDefault() {
        // Verify that explicit representation in constructor overrides global default
        var originalDefault = ReturnRepresentationPolicy.getDefaultRepresentation();

        try {
            // Set global default to MULTIPLICATIVE
            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.MULTIPLICATIVE);

            var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                    .withData(100, 105)
                    .build();
            var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

            // Explicitly use DECIMAL
            var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
            var result = criterion.calculate(series, position);

            // Should use explicit DECIMAL (0.05), not default MULTIPLICATIVE (1.05)
            assertNumEquals(0.05, result);
        } finally {
            // Restore original default
            ReturnRepresentationPolicy.setDefaultRepresentation(originalDefault);
        }
    }

    @Test
    public void worksWithDecimalNumFactory() {
        // Verify it works with DecimalNumFactory (not just DoubleNumFactory)
        var series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var criterion = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, position);

        assertNumEquals(0.05, result);
    }

    @Test
    public void betterThanWorksWithBothRepresentations() {
        // Verify betterThan() works correctly with both representations
        var criterionRate = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var criterionTotal = new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);

        // Both should correctly identify that 0.05 > 0.03 (rate) or 1.05 > 1.03 (total)
        var factory = DoubleNumFactory.getInstance();
        assertEquals(true, criterionRate.betterThan(factory.numOf(0.05), factory.numOf(0.03)));
        assertEquals(true, criterionTotal.betterThan(factory.numOf(1.05), factory.numOf(1.03)));
        assertEquals(false, criterionRate.betterThan(factory.numOf(0.03), factory.numOf(0.05)));
        assertEquals(false, criterionTotal.betterThan(factory.numOf(1.03), factory.numOf(1.05)));
    }

    @Test
    public void zeroEntryValueReturnsZero() {
        // Edge case: zero entry value should return zero regardless of representation
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(0, 105).build();
        // Create a position with zero entry value by using zero price
        var record = new BaseTradingRecord();
        record.enter(0, series.numFactory().zero(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), series.numFactory().one());
        var position = record.getLastPosition();

        var criterionRate = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var criterionTotal = new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);

        var resultRate = criterionRate.calculate(series, position);
        var resultTotal = criterionTotal.calculate(series, position);

        assertNumEquals(0.0, resultRate);
        assertNumEquals(0.0, resultTotal);
    }

    @Test
    public void largePercentageChange() {
        // Test with large percentage change: 100 -> 200 = 100% = 1.0 rate, 2.0 total
        // return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 200)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var criterionRate = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var criterionTotal = new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);

        var resultRate = criterionRate.calculate(series, position);
        var resultTotal = criterionTotal.calculate(series, position);

        assertNumEquals(1.0, resultRate);
        assertNumEquals(2.0, resultTotal);
    }

    @Test
    public void largeLossPercentageChange() {
        // Test with large loss: 100 -> 50 = -50% = -0.5 rate, 0.5 total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 50)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var criterionRate = new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL);
        var criterionTotal = new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);

        var resultRate = criterionRate.calculate(series, position);
        var resultTotal = criterionTotal.calculate(series, position);

        assertNumEquals(-0.5, resultRate);
        assertNumEquals(0.5, resultTotal);
    }
}
