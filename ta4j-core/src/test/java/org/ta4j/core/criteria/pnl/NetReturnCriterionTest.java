/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class NetReturnCriterionTest extends AbstractPnlCriterionTest {

    public NetReturnCriterionTest(NumFactory numFactory) {
        super(params -> new NetReturnCriterion(), numFactory);
    }

    @Override
    protected void handleCalculateWithProfits(Num result) {
        assertNumEquals(1.2132143907, result);
    }

    @Override
    protected void handleCalculateWithLosses(Num result) {
        assertNumEquals(0.6389241251, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions(Num result) {
        assertNumEquals(1.155, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions2(Num result) {
        assertNumEquals(1.26, result);
    }

    @Override
    protected void handleCalculateOnlyWithLossPositions(Num result) {
        assertNumEquals(0.665, result);
    }

    @Override
    protected void handleCalculateProfitWithShortPositions(Num result) {
        assertNumEquals(0.5413533835, result);
    }

    @Override
    protected void handleBetterThan(AnalysisCriterion criterion) {
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }

    @Override
    protected void handleCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(), 1);
    }

    @Override
    protected void handleCalculateWithOpenedPosition(Num result) {
        assertNumEquals(1.10, result);
    }

    @Override
    protected void handleCalculateWithNoPositions(Num result) {
        assertNumEquals(1, result);
    }

    @Test
    public void calculatePositionWithDecimal_Profit() {
        // Test: 100 -> 105 with transaction cost = 1 per trade
        // Entry: netPrice = 100 + 1/1 = 101, entryValue = 101
        // Exit: netPrice = 105 - 1/1 = 104, exitValue = 104
        // Net profit = 104 - 101 = 3
        // Net return = 3/101 = 0.02970297... rate, 1.02970297... total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        var position = record.getLastPosition();

        var criterion = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, position);

        assertNumEquals(0.02970297029702973, result);
    }

    @Test
    public void calculatePositionWithMultiplicative_Profit() {
        // Test: 100 -> 105 with transaction cost = 1 per trade
        // Entry: netPrice = 100 + 1/1 = 101, entryValue = 101
        // Exit: netPrice = 105 - 1/1 = 104, exitValue = 104
        // Net profit = 104 - 101 = 3
        // Net return = 3/101 = 0.02970297... rate, 1.02970297... total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        var position = record.getLastPosition();

        var criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, position);

        assertNumEquals(1.0297029702970297, result);
    }

    @Test
    public void calculatePositionWithPercentage_Profit() {
        // Test: 100 -> 105 with transaction cost = 1 per trade
        // Entry: netPrice = 100 + 1/1 = 101, entryValue = 101
        // Exit: netPrice = 105 - 1/1 = 104, exitValue = 104
        // Net profit = 104 - 101 = 3
        // Net return = 3/101 = 0.02970297... rate, 1.02970297... total return,
        // 2.970297...% percentage
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        var position = record.getLastPosition();

        var criterion = new NetReturnCriterion(ReturnRepresentation.PERCENTAGE);
        var result = criterion.calculate(series, position);

        assertNumEquals(2.970297029702973, result);
    }

    @Test
    public void calculatePositionWithLog_Profit() {
        // Test: 100 -> 105 with transaction cost = 1 per trade
        // Entry: netPrice = 100 + 1/1 = 101, entryValue = 101
        // Exit: netPrice = 105 - 1/1 = 104, exitValue = 104
        // Net profit = 104 - 101 = 3
        // Net return = 3/101 = 0.02970297... rate, 1.02970297... total return,
        // ln(1.02970297...) log return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        var position = record.getLastPosition();

        var criterion = new NetReturnCriterion(ReturnRepresentation.LOG);
        var result = criterion.calculate(series, position);

        assertNumEquals(Math.log(1.0297029702970297), result);
    }

    @Test
    public void calculatePositionWithDecimal_Loss() {
        // Test: 100 -> 95 with transaction cost = 1 per trade
        // Entry: netPrice = 100 + 1/1 = 101, entryValue = 101
        // Exit: netPrice = 95 - 1/1 = 94, exitValue = 94
        // Net loss = 94 - 101 = -7
        // Net return = -7/101 = -0.06930693... rate, 0.93069306... total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        var position = record.getLastPosition();

        var criterion = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, position);

        assertNumEquals(-0.06930693069306937, result);
    }

    @Test
    public void calculatePositionWithMultiplicative_Loss() {
        // Test: 100 -> 95 with transaction cost = 1 per trade
        // Entry: netPrice = 100 + 1/1 = 101, entryValue = 101
        // Exit: netPrice = 95 - 1/1 = 94, exitValue = 94
        // Net loss = 94 - 101 = -7
        // Net return = -7/101 = -0.06930693... rate, 0.93069306... total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        var position = record.getLastPosition();

        var criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, position);

        assertNumEquals(0.9306930693069306, result);
    }

    @Test
    public void calculatePositionWithDecimal_OpenPosition() {
        // Open positions should return 0.0 for DECIMAL (neutral)
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var record = new BaseTradingRecord();
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        var position = record.getCurrentPosition();

        var criterion = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, position);

        assertNumEquals(0.0, result);
    }

    @Test
    public void calculatePositionWithMultiplicative_OpenPosition() {
        // Open positions should return 1.0 for MULTIPLICATIVE (neutral)
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var record = new BaseTradingRecord();
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        var position = record.getCurrentPosition();

        var criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, position);

        assertNumEquals(1.0, result);
    }

    @Test
    public void calculateTradingRecordWithDecimal_MultiplePositions() {
        // Test: 100->105 and 100->110 with transaction cost = 1 per trade
        // Position 1: entry=101, exit=104, return=3/101=0.02970297...,
        // total=1.02970297...
        // Position 2: entry=101, exit=109, return=8/101=0.07920792...,
        // total=1.07920792...
        // Combined: 1.02970297... * 1.07920792... = 1.11126360... total return,
        // 0.11126360... rate
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105, 100, 110)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        record.enter(2, series.getBar(2).getClosePrice(), series.numFactory().one());
        record.exit(3, series.getBar(3).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());

        var criterion = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, record);

        assertEquals(0.11126360160768556, result.doubleValue(), 0.0001);
    }

    @Test
    public void calculateTradingRecordWithMultiplicative_MultiplePositions() {
        // Test: 100->105 and 100->110 with transaction cost = 1 per trade
        // Position 1: entry=101, exit=104, return=3/101=0.02970297...,
        // total=1.02970297...
        // Position 2: entry=101, exit=109, return=8/101=0.07920792...,
        // total=1.07920792...
        // Combined: 1.02970297... * 1.07920792... = 1.11126360... total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105, 100, 110)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        record.enter(2, series.getBar(2).getClosePrice(), series.numFactory().one());
        record.exit(3, series.getBar(3).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());

        var criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, record);

        assertEquals(1.1112636016076856, result.doubleValue(), 0.0001);
    }

    @Test
    public void calculateTradingRecordWithDecimal_NoPositions() {
        // No positions should return 0.0 for DECIMAL (neutral)
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var record = new BaseTradingRecord();

        var criterion = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, record);

        assertNumEquals(0.0, result);
    }

    @Test
    public void calculateTradingRecordWithMultiplicative_NoPositions() {
        // No positions should return 1.0 for MULTIPLICATIVE (neutral)
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        var record = new BaseTradingRecord();

        var criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, record);

        assertNumEquals(1.0, result);
    }

    @Test
    public void calculateTradingRecordWithDecimal_MixedProfitLoss() {
        // Test: 100->95 and 100->110 with transaction cost = 1 per trade
        // Position 1: entry=101, exit=94, return=-7/101=-0.06930693...,
        // total=0.93069306...
        // Position 2: entry=101, exit=109, return=8/101=0.07920792...,
        // total=1.07920792...
        // Combined: 0.93069306... * 1.07920792... = 1.00435643... total return,
        // 0.00435643... rate
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95, 100, 110)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        record.enter(2, series.getBar(2).getClosePrice(), series.numFactory().one());
        record.exit(3, series.getBar(3).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());

        var criterion = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, record);

        assertEquals(0.00435643, result.doubleValue(), 0.0001);
    }

    @Test
    public void calculateTradingRecordWithMultiplicative_MixedProfitLoss() {
        // Test: 100->95 and 100->110 with transaction cost = 1 per trade
        // Position 1: entry=101, exit=94, return=-7/101=-0.06930693...,
        // total=0.93069306...
        // Position 2: entry=101, exit=109, return=8/101=0.07920792...,
        // total=1.07920792...
        // Combined: 0.93069306... * 1.07920792... = 1.00435643... total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95, 100, 110)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        record.enter(2, series.getBar(2).getClosePrice(), series.numFactory().one());
        record.exit(3, series.getBar(3).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());

        var criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var result = criterion.calculate(series, record);

        assertEquals(1.00435643, result.doubleValue(), 0.0001);
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
            var cost = new FixedTransactionCostModel(1);
            var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
            record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
            record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
            var position = record.getLastPosition();

            // Use default constructor
            var criterion = new NetReturnCriterion();
            var result = criterion.calculate(series, position);

            // Should use DECIMAL (0.02970297..., not 1.02970297...)
            assertNumEquals(0.02970297029702973, result);

            // Change default and verify it's used
            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.MULTIPLICATIVE);
            var criterion2 = new NetReturnCriterion();
            var result2 = criterion2.calculate(series, position);

            // Should use MULTIPLICATIVE (1.02970297..., not 0.02970297...)
            assertNumEquals(1.0297029702970297, result2);
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
            var cost = new FixedTransactionCostModel(1);
            var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
            record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
            record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
            var position = record.getLastPosition();

            // Explicitly use DECIMAL
            var criterion = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
            var result = criterion.calculate(series, position);

            // Should use explicit DECIMAL (0.02970297...), not default MULTIPLICATIVE
            // (1.02970297...)
            assertNumEquals(0.02970297029702973, result);
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
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        var position = record.getLastPosition();

        var criterion = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var result = criterion.calculate(series, position);

        assertNumEquals(0.02970297029702973, result);
    }

    @Test
    public void betterThanWorksWithBothRepresentations() {
        // Verify betterThan() works correctly with both representations
        var criterionRate = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var criterionTotal = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);

        // Both should correctly identify that 0.05 > 0.03 (rate) or 1.05 > 1.03 (total)
        var factory = DoubleNumFactory.getInstance();
        assertEquals(true, criterionRate.betterThan(factory.numOf(0.05), factory.numOf(0.03)));
        assertEquals(true, criterionTotal.betterThan(factory.numOf(1.05), factory.numOf(1.03)));
        assertEquals(false, criterionRate.betterThan(factory.numOf(0.03), factory.numOf(0.05)));
        assertEquals(false, criterionTotal.betterThan(factory.numOf(1.03), factory.numOf(1.05)));
    }

    @Test
    public void zeroEntryValueReturnsOne() {
        // Edge case: zero entry value should return 1.0 (neutral) regardless of
        // representation
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(0, 105).build();
        // Create a position with zero entry value by using zero price
        var record = new BaseTradingRecord();
        record.enter(0, series.numFactory().zero(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), series.numFactory().one());
        var position = record.getLastPosition();

        var criterionRate = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var criterionTotal = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);

        var resultRate = criterionRate.calculate(series, position);
        var resultTotal = criterionTotal.calculate(series, position);

        // When entry value is zero, calculateReturn returns 1.0 (neutral)
        // DECIMAL: 1.0 -> 0.0, MULTIPLICATIVE: 1.0 -> 1.0
        assertNumEquals(0.0, resultRate);
        assertNumEquals(1.0, resultTotal);
    }

    @Test
    public void largePercentageChange() {
        // Test with large percentage change: 100 -> 200 with transaction cost = 1 per
        // trade
        // Entry: netPrice = 100 + 1/1 = 101, entryValue = 101
        // Exit: netPrice = 200 - 1/1 = 199, exitValue = 199
        // Net profit = 199 - 101 = 98
        // Net return = 98/101 = 0.97029702... rate, 1.97029702... total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 200)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        var position = record.getLastPosition();

        var criterionRate = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var criterionTotal = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);

        var resultRate = criterionRate.calculate(series, position);
        var resultTotal = criterionTotal.calculate(series, position);

        assertNumEquals(0.9702970297029703, resultRate);
        assertNumEquals(1.9702970297029703, resultTotal);
    }

    @Test
    public void largeLossPercentageChange() {
        // Test with large loss: 100 -> 50 with transaction cost = 1 per trade
        // Entry: netPrice = 100 + 1/1 = 101, entryValue = 101
        // Exit: netPrice = 50 - 1/1 = 49, exitValue = 49
        // Net loss = 49 - 101 = -52
        // Net return = -52/101 = -0.51485148... rate, 0.48514851... total return
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 50)
                .build();
        var cost = new FixedTransactionCostModel(1);
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());
        var position = record.getLastPosition();

        var criterionRate = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
        var criterionTotal = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);

        var resultRate = criterionRate.calculate(series, position);
        var resultTotal = criterionTotal.calculate(series, position);

        assertNumEquals(-0.5148514851485149, resultRate);
        assertNumEquals(0.48514851485148514, resultTotal);
    }
}
