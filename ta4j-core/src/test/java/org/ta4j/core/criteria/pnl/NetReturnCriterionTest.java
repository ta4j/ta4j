/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesCashFlow;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradeFee;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
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

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private static FuturesContract inverseBtcPerpetual(NumFactory numFactory) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTCUSD-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.INVERSE)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("BTC")
                .contractSize(numFactory.numOf(100))
                .build();
    }

    private static TradeFee commission(NumFactory numFactory, double amount, String currency) {
        return TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(numFactory.numOf(amount))
                .currency(currency)
                .build();
    }

    private static TradeFill fill(FuturesContract contract, int index, ExecutionSide side, double amount, double price,
            List<TradeFee> fees) {
        NumFactory numFactory = contract.contractSize().getNumFactory();
        return TradeFill.builder()
                .index(index)
                .time(T0.plusSeconds(index))
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .orderId("order-" + index)
                .futuresContract(contract)
                .fees(fees)
                .build();
    }

    @Test
    public void netReturnCriterionUsesEntrySettlementNotionalForFutures() {
        FuturesContract contract = inverseBtcPerpetual(numFactory);
        Position position = new Position(
                Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, 100, 20_000,
                        List.of(commission(numFactory, 0.0001, "BTC"))), RecordedTradeCostModel.INSTANCE),
                Trade.fromFill(fill(contract, 1, ExecutionSide.SELL, 100, 25_000,
                        List.of(commission(numFactory, 0.00012, "BTC"))), RecordedTradeCostModel.INSTANCE),
                RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();

        assertNumEquals(1.19956, new NetReturnCriterion().calculate(series, position));
    }

    private static List<NumFactory> factories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    private static FuturesContract linearBtcPerpetual(NumFactory numFactory) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(0.01))
                .build();
    }

    private static BarSeries series(NumFactory numFactory, double... closes) {
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
    }

    private static TradeFee commission(NumFactory numFactory, double amount) {
        return TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(numFactory.numOf(amount))
                .currency("USD")
                .build();
    }

    private static FuturesCashFlow cashFlow(FuturesContract contract, FuturesCashFlow.Type type, String eventId,
            int index, double amount) {
        return FuturesCashFlow.builder()
                .contract(contract)
                .type(type)
                .eventId(eventId)
                .index(index)
                .time(T0.plusSeconds(index))
                .amount(contract.contractSize().getNumFactory().numOf(amount))
                .currency(contract.settlementCurrency())
                .build();
    }

    private static BaseTradingRecord fundedRecord(FuturesContract contract, NumFactory numFactory, double capital) {
        return BaseTradingRecord.builder().futuresContract(contract).initialCapital(numFactory.numOf(capital)).build();
    }

    @Test
    public void futuresRecordReturnIsNormalizedByAccountCapital() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = series(numFactory, 100, 105, 110);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 1_000);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of(commission(numFactory, 0.5))));
            record.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-1", 1, -1));
            record.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.VARIATION_MARGIN, "vm-1", 1, 2));
            record.operate(fill(contract, 2, ExecutionSide.SELL, 100, 110, List.of(commission(numFactory, 0.5))));

            // Payoff 100 * 0.01 * 10 = 10, fees 1, funding -1, variation margin already
            // inside the payoff of the closed slice: realized 8 on 1_000 capital.
            NetReturnCriterion net = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
            GrossReturnCriterion gross = new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);

            assertNumEquals(1.008, net.calculate(barSeries, record));
            assertNumEquals(1.010, gross.calculate(barSeries, record));

            assertNumEquals(0.008, new NetReturnCriterion(ReturnRepresentation.DECIMAL).calculate(barSeries, record));
            assertNumEquals(0.8, new NetReturnCriterion(ReturnRepresentation.PERCENTAGE).calculate(barSeries, record));
        }
    }

    @Test
    public void openFuturesPositionReturnCountsPaidFeesFundingAndVariationMargin() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = series(numFactory, 100, 103);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 1_000);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of(commission(numFactory, 0.5))));
            record.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-1", 1, -1));
            record.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.VARIATION_MARGIN, "vm-1", 1, 3));

            // Open economics are realized cash: -0.5 fees - 1 funding + 3 margin = 1.5.
            // Gross restores the fees and the funding, so it reports the paid margin.
            assertNumEquals(1.0015,
                    new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(barSeries, record));
            assertNumEquals(1.003,
                    new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(barSeries, record));
        }
    }

    @Test
    public void partialFuturesSlicesDoNotCompoundAsSeparateInvestments() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = series(numFactory, 100, 110, 100, 106);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 1_000);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of(commission(numFactory, 1))));
            record.operate(fill(contract, 1, ExecutionSide.SELL, 100, 110, List.of(commission(numFactory, 1))));
            record.operate(fill(contract, 2, ExecutionSide.BUY, 100, 100, List.of(commission(numFactory, 1))));
            record.operate(fill(contract, 3, ExecutionSide.SELL, 100, 106, List.of(commission(numFactory, 1))));

            // Two sequential slices realize 8 and 4 on the same account.
            assertNumEquals(1.012,
                    new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(barSeries, record));

            BaseTradingRecord spot = BaseTradingRecord.builder().transactionCostModel(new ZeroCostModel()).build();
            spot.operate(0, numFactory.numOf(100), numFactory.one());
            spot.operate(1, numFactory.numOf(108), numFactory.one());
            spot.operate(2, numFactory.numOf(100), numFactory.one());
            spot.operate(3, numFactory.numOf(104), numFactory.one());

            // Spot aggregation still compounds the matched position returns.
            assertNumEquals(1.1232,
                    new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(barSeries, spot));
        }
    }

    @Test
    public void futuresRecordReturnRequiresExplicitCapital() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = series(numFactory, 100, 105);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();
            record.operate(fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of()));

            NetReturnCriterion net = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
            GrossReturnCriterion gross = new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);

            assertThrows(IllegalStateException.class, () -> net.calculate(barSeries, record));
            assertThrows(IllegalStateException.class, () -> gross.calculate(barSeries, record));
        }
    }

    @Test
    public void futuresPositionReturnStaysUnlevered() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = series(numFactory, 100, 105, 110);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 1_000);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of(commission(numFactory, 0.5))));
            record.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-1", 1, -1));
            record.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.VARIATION_MARGIN, "vm-1", 1, 2));
            record.operate(fill(contract, 2, ExecutionSide.SELL, 100, 110, List.of(commission(numFactory, 0.5))));

            Position position = record.getPositions().getFirst();

            assertNumEquals(100.0, contract.settlementNotional(position.getEntry().getAmount(), numFactory.numOf(100)));
            // The same 8 realized on a 100 notional position is unlevered.
            assertNumEquals(1.08,
                    new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(barSeries, position));
        }
    }

    @Test
    public void spotRecordReturnKeepsThePositionProduct() {
        for (NumFactory numFactory : factories()) {
            BarSeries barSeries = series(numFactory, 100, 105);
            BaseTradingRecord record = BaseTradingRecord.builder().transactionCostModel(new ZeroCostModel()).build();
            record.operate(0, numFactory.numOf(100), numFactory.one());
            record.operate(1, numFactory.numOf(108), numFactory.one());

            assertNumEquals(1.08,
                    new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(barSeries, record));
            assertNumEquals(0.08, new NetReturnCriterion(ReturnRepresentation.DECIMAL).calculate(barSeries, record));
        }
    }

    @Test
    public void partialEntryHoldingCostOnlyUsesFillsThroughFinalIndex() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            LinearBorrowingCostModel holdingCostModel = new LinearBorrowingCostModel(0.01,
                    LinearBorrowingCostModel.Applicability.BOTH);
            Trade entry = Trade.fromFills(Trade.TradeType.BUY,
                    List.of(fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of()),
                            fill(contract, 2, ExecutionSide.BUY, 100, 100, List.of())),
                    RecordedTradeCostModel.INSTANCE);
            Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, holdingCostModel);

            assertNumEquals(1, position.getHoldingCost(1));
            assertNumEquals(4, position.getHoldingCost(2));
        }
    }

    @Test
    public void futuresRecordReturnIncludesExecutionsBeyondSeriesEnd() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = series(numFactory, 100);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 1_000);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of()));
            record.operate(fill(contract, 1, ExecutionSide.SELL, 100, 101, List.of()));

            assertNumEquals(1.001,
                    new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(barSeries, record));
        }
    }
}
