/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertThrows;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesCashFlow;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class GrossReturnCriterionTest extends AbstractPnlCriterionTest {

    public GrossReturnCriterionTest(NumFactory numFactory) {
        super(params -> new GrossReturnCriterion(), numFactory);
    }

    @Override
    protected void handleCalculateWithProfits(Num result) {
        assertNumEquals(1.26, result);
    }

    @Override
    protected void handleCalculateWithLosses(Num result) {
        assertNumEquals(0.665, result);
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

    private static final Instant FUTURES_T0 = Instant.parse("2025-01-01T00:00:00Z");

    private FuturesContract grossLinearBtcPerpetual() {
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

    private TradeFee grossCommission(double amount) {
        return TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(numFactory.numOf(amount))
                .currency("USD")
                .build();
    }

    private TradeFill grossFill(FuturesContract contract, int index, ExecutionSide side, double amount, double price,
            List<TradeFee> fees) {
        return TradeFill.builder()
                .index(index)
                .time(FUTURES_T0.plusSeconds(index))
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .orderId("gross-order-" + index)
                .futuresContract(contract)
                .fees(fees)
                .build();
    }

    private FuturesCashFlow grossCashFlow(FuturesContract contract, FuturesCashFlow.Type type, String eventId,
            int index, double amount) {
        return FuturesCashFlow.builder()
                .contract(contract)
                .type(type)
                .eventId(eventId)
                .index(index)
                .time(FUTURES_T0.plusSeconds(index))
                .amount(numFactory.numOf(amount))
                .currency(contract.settlementCurrency())
                .build();
    }

    @Test
    public void grossReturnIsNormalizedByAccountCapital() {
        FuturesContract contract = grossLinearBtcPerpetual();
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(1_000))
                .build();
        record.operate(grossFill(contract, 0, ExecutionSide.BUY, 100, 100, List.of(grossCommission(0.5))));
        record.recordCashFlow(grossCashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-1", 1, -1));
        record.recordCashFlow(grossCashFlow(contract, FuturesCashFlow.Type.VARIATION_MARGIN, "vm-1", 1, 2));
        record.operate(grossFill(contract, 2, ExecutionSide.SELL, 100, 110, List.of(grossCommission(0.5))));

        assertNumEquals(1.010,
                new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(barSeries, record));
    }

    @Test
    public void grossOpenFuturesPositionReturnCountsPaidFeesFundingAndVariationMargin() {
        FuturesContract contract = grossLinearBtcPerpetual();
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 103).build();
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(1_000))
                .build();
        record.operate(grossFill(contract, 0, ExecutionSide.BUY, 100, 100, List.of(grossCommission(0.5))));
        record.recordCashFlow(grossCashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-1", 1, -1));
        record.recordCashFlow(grossCashFlow(contract, FuturesCashFlow.Type.VARIATION_MARGIN, "vm-1", 1, 3));

        assertNumEquals(1.003,
                new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(barSeries, record));
    }

    @Test
    public void grossReturnRequiresExplicitCapital() {
        FuturesContract contract = grossLinearBtcPerpetual();
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105).build();
        BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();
        record.operate(grossFill(contract, 0, ExecutionSide.BUY, 100, 100, List.of()));

        assertThrows(IllegalStateException.class,
                () -> new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(barSeries, record));
    }

}
