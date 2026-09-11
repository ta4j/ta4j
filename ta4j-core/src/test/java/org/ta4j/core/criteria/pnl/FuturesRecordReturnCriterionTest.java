/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesCashFlow;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies the record-level native futures override of
 * {@link NetReturnCriterion} and {@link GrossReturnCriterion}: a financed
 * futures account returns {@code 1 + realized profit / initialCapital} instead
 * of the product of the matched position returns, and partial closes never
 * compound as separate investments. Position-level futures returns stay
 * unlevered.
 */
class FuturesRecordReturnCriterionTest {

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

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
    void futuresRecordReturnIsNormalizedByAccountCapital() {
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
    void openFuturesPositionReturnCountsPaidFeesFundingAndVariationMargin() {
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
    void partialFuturesSlicesDoNotCompoundAsSeparateInvestments() {
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
    void futuresRecordReturnRequiresExplicitCapital() {
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
    void futuresPositionReturnStaysUnlevered() {
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
    void spotRecordReturnKeepsThePositionProduct() {
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
}
