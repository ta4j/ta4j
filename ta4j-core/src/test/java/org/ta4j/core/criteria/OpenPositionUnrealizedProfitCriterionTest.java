/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesCashFlow;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OpenPositionUnrealizedProfitCriterionTest extends AbstractCriterionTest {

    public OpenPositionUnrealizedProfitCriterionTest(NumFactory numFactory) {
        super(params -> new OpenPositionUnrealizedProfitCriterion(), numFactory);
    }

    @Test
    public void calculateForBaseTradingRecordLong() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.two(),
                numFactory.numOf(0.5), ExecutionSide.BUY, null, null));

        Num expected = numFactory.numOf(110)
                .multipliedBy(numFactory.two())
                .minus(numFactory.hundred().multipliedBy(numFactory.two()))
                .minus(numFactory.numOf(0.5));

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateForBaseTradingRecordShort() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90).build();
        var record = new BaseTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.numOf(0.2), ExecutionSide.SELL, null, null));

        Num expected = numFactory.hundred().minus(numFactory.numOf(90)).minus(numFactory.numOf(0.2));

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateForStandardRecordOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120).build();
        var record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());

        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        Num expected = numFactory.numOf(120).minus(numFactory.hundred());

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void returnsZeroWhenNoOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120).build();
        var record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());

        var result = getCriterion().calculate(series, record);

        assertNumEquals(numFactory.zero(), result);
    }

    @Test
    public void futuresOpenPositionProfitUsesContractQuantity() {
        FuturesContract contract = linearBtcPerpetual();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        BaseTradingRecord record = futuresRecord(contract, TradeType.BUY);

        record.operate(futuresFill(contract, 0, ExecutionSide.BUY, 100, 100, 2));

        // 100 contracts x 0.01 BTC x 10 USD = 10 settlement currency, never 100 * 10.
        assertNumEquals(numFactory.numOf(10), getCriterion().calculate(series, record), 1e-12);
        assertNumEquals(numFactory.numOf(10), getCriterion().calculate(series, record.getCurrentPosition()), 1e-12);
    }

    @Test
    public void futuresShortPositionProfitIsNegativeOnARisingMark() {
        FuturesContract contract = linearBtcPerpetual();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        BaseTradingRecord record = futuresRecord(contract, TradeType.SELL);

        record.operate(futuresFill(contract, 0, ExecutionSide.SELL, 100, 100, 0));

        assertNumEquals(numFactory.numOf(-10), getCriterion().calculate(series, record), 1e-12);
    }

    @Test
    public void settledVariationMarginLeavesOnlyTheRemainingExposure() {
        FuturesContract contract = linearBtcPerpetual();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        BaseTradingRecord record = futuresRecord(contract, TradeType.BUY);

        record.operate(futuresFill(contract, 0, ExecutionSide.BUY, 100, 100, 0));
        record.recordCashFlow(variationMargin(contract, 1, 3));

        // 3 of the +10 mark-to-market move is settled cash, 7 is still unrealized.
        assertNumEquals(numFactory.numOf(7), getCriterion().calculate(series, record), 1e-12);
    }

    @Test
    public void betterThanPrefersHigherProfit() {
        var criterion = getCriterion();

        assertTrue(criterion.betterThan(numFactory.two(), numFactory.one()));
        assertFalse(criterion.betterThan(numFactory.one(), numFactory.two()));
    }

    private FuturesContract linearBtcPerpetual() {
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

    private BaseTradingRecord futuresRecord(FuturesContract contract, TradeType startingType) {
        return BaseTradingRecord.builder()
                .startingType(startingType)
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(1_000))
                .build();
    }

    private TradeFill futuresFill(FuturesContract contract, int index, ExecutionSide side, double amount, double price,
            double fee) {
        List<TradeFee> fees = fee == 0 ? List.of()
                : List.of(TradeFee.builder()
                        .type(TradeFee.Type.COMMISSION)
                        .amount(numFactory.numOf(fee))
                        .currency("USD")
                        .build());
        return TradeFill.builder()
                .index(index)
                .time(Instant.parse("2025-01-01T00:00:00Z").plusSeconds(index))
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .orderId("order-" + index)
                .futuresContract(contract)
                .fees(fees)
                .build();
    }

    private FuturesCashFlow variationMargin(FuturesContract contract, int index, double amount) {
        return FuturesCashFlow.builder()
                .contract(contract)
                .type(FuturesCashFlow.Type.VARIATION_MARGIN)
                .eventId("vm-" + index)
                .index(index)
                .time(Instant.parse("2025-01-01T00:00:00Z").plusSeconds(index))
                .amount(numFactory.numOf(amount))
                .currency(contract.settlementCurrency())
                .build();
    }
}
