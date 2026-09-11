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
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OpenPositionCostBasisCriterionTest extends AbstractCriterionTest {

    public OpenPositionCostBasisCriterionTest(NumFactory numFactory) {
        super(params -> new OpenPositionCostBasisCriterion(), numFactory);
    }

    @Test
    public void calculateUsesBaseTradingRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110), numFactory.one(),
                numFactory.numOf(0.2), ExecutionSide.BUY, null, null));

        Num expected = numFactory.hundred()
                .plus(numFactory.numOf(110))
                .plus(numFactory.numOf(0.1))
                .plus(numFactory.numOf(0.2));
        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result, 1e-12);
    }

    @Test
    public void calculateUsesCurrentPositionForStandardRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var costModel = new FixedTransactionCostModel(1.5);
        var record = new BaseTradingRecord(TradeType.BUY, costModel, new ZeroCostModel());

        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        Num expected = series.getBar(0)
                .getClosePrice()
                .multipliedBy(numFactory.one())
                .plus(costModel.calculate(series.getBar(0).getClosePrice(), numFactory.one()));

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void returnsZeroWhenNoOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());

        var result = getCriterion().calculate(series, record);

        assertNumEquals(numFactory.zero(), result);
    }

    @Test
    public void futuresCostBasisIsEntrySettlementNotionalPlusOpeningFees() {
        FuturesContract contract = linearBtcPerpetual();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(1_000))
                .build();

        record.operate(futuresFill(contract, 0, ExecutionSide.BUY, 100, 100, 2));
        Position position = record.getCurrentPosition();

        // 100 contracts x 0.01 BTC x 100 USD = 100 settlement notional plus 2 opening
        // fee.
        assertNumEquals(numFactory.numOf(100),
                contract.settlementNotional(position.getEntry().getAmount(), numFactory.numOf(100)), 1e-12);
        assertNumEquals(numFactory.numOf(2), position.getEntry().getCost(), 1e-12);
        assertNumEquals(numFactory.numOf(102), getCriterion().calculate(series, position), 1e-12);
        assertNumEquals(numFactory.numOf(102), getCriterion().calculate(series, record), 1e-12);
    }

    @Test
    public void betterThanPrefersLowerCostBasis() {
        var criterion = getCriterion();

        assertTrue(criterion.betterThan(numFactory.one(), numFactory.two()));
        assertFalse(criterion.betterThan(numFactory.two(), numFactory.one()));
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
}
