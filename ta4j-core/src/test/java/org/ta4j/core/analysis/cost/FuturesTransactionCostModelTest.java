/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.RealtimeBar;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class FuturesTransactionCostModelTest {

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private static List<NumFactory> factories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    private static FuturesContract btcPerpetual(NumFactory numFactory) {
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

    private static TradeFill fill(FuturesContract contract, ExecutionSide side, Num price, Num contracts,
            RealtimeBar.Liquidity liquidity, List<TradeFee> fees) {
        TradeFill.Builder builder = TradeFill.builder()
                .index(0)
                .time(T0)
                .price(price)
                .amount(contracts)
                .side(side)
                .futuresContract(contract);
        if (liquidity != null) {
            builder.liquidity(liquidity);
        }
        if (fees != null) {
            builder.fees(fees);
        }
        return builder.build();
    }

    @Test
    void modelsLinearCommissionFromSettlementNotionalWithPerContractFloor() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = btcPerpetual(numFactory);
            TradeFill takerFill = fill(contract, ExecutionSide.BUY, numFactory.numOf(50000), numFactory.numOf(3), null,
                    null);

            FuturesTransactionCostModel unfloored = FuturesTransactionCostModel.builder()
                    .makerRate(numFactory.numOf(0.00002))
                    .takerRate(numFactory.numOf(0.00001))
                    .build();
            // 3 contracts * 0.01 BTC * 50,000 USD = 1,500 USD notional.
            assertNumEquals(0.015, unfloored.calculate(takerFill));

            FuturesTransactionCostModel floored = FuturesTransactionCostModel.builder()
                    .makerRate(numFactory.numOf(0.00002))
                    .takerRate(numFactory.numOf(0.00001))
                    .minimumPerContract(numFactory.numOf(0.05))
                    .build();
            List<TradeFee> fees = floored.calculateFees(takerFill);
            assertEquals(List.of(TradeFee.Type.COMMISSION), fees.stream().map(TradeFee::type).toList());
            assertEquals("USD", fees.getFirst().currency());
            assertNumEquals(0.15, floored.calculate(takerFill));
        }
    }

    @Test
    void selectsMakerRateForMakerFillsAndTakerRateOtherwise() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = btcPerpetual(numFactory);
            FuturesTransactionCostModel model = FuturesTransactionCostModel.builder()
                    .makerRate(numFactory.numOf(0.00001))
                    .takerRate(numFactory.numOf(0.0005))
                    .build();
            FuturesTransactionCostModel makerByDefault = FuturesTransactionCostModel.builder()
                    .makerRate(numFactory.numOf(0.00001))
                    .takerRate(numFactory.numOf(0.0005))
                    .defaultLiquidity(RealtimeBar.Liquidity.MAKER)
                    .build();

            Num price = numFactory.numOf(50000);
            Num contracts = numFactory.numOf(3);
            TradeFill unknown = fill(contract, ExecutionSide.BUY, price, contracts, null, null);
            TradeFill maker = fill(contract, ExecutionSide.BUY, price, contracts, RealtimeBar.Liquidity.MAKER, null);
            TradeFill taker = fill(contract, ExecutionSide.BUY, price, contracts, RealtimeBar.Liquidity.TAKER, null);

            // Unknown liquidity is never guessed as maker.
            assertNumEquals(0.75, model.calculate(unknown));
            assertNumEquals(0.015, model.calculate(maker));
            assertNumEquals(0.75, model.calculate(taker));
            assertNumEquals(0.015, makerByDefault.calculate(unknown));
        }
    }

    @Test
    void keepsRebatesSignedAndAddsPerContractChargesAboveCommissionFloor() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = btcPerpetual(numFactory);
            TradeFill makerFill = fill(contract, ExecutionSide.BUY, numFactory.numOf(50000), numFactory.numOf(3),
                    RealtimeBar.Liquidity.MAKER, null);

            FuturesTransactionCostModel rebate = FuturesTransactionCostModel.builder()
                    .makerRate(numFactory.numOf(-0.00001))
                    .takerRate(numFactory.numOf(0.0005))
                    .perContractCharge(TradeFee.Type.CLEARING, numFactory.numOf(0.2))
                    .build();
            List<TradeFee> fees = rebate.calculateFees(makerFill);
            assertEquals(List.of(TradeFee.Type.COMMISSION, TradeFee.Type.CLEARING),
                    fees.stream().map(TradeFee::type).toList());
            assertNumEquals(-0.015, fees.getFirst().amount());
            // The clearing charge is per contract and is not floored with the commission.
            assertNumEquals(0.6, fees.get(1).amount());
            assertNumEquals(0.585, rebate.calculate(makerFill));

            FuturesTransactionCostModel floored = FuturesTransactionCostModel.builder()
                    .makerRate(numFactory.numOf(-0.00001))
                    .takerRate(numFactory.numOf(0.0005))
                    .minimumPerContract(numFactory.numOf(0.05))
                    .perContractCharge(TradeFee.Type.CLEARING, numFactory.numOf(0.2))
                    .build();
            assertNumEquals(0.75, floored.calculate(makerFill));
        }
    }

    @Test
    void rejectsContextsWithoutContractTerms() {
        for (NumFactory numFactory : factories()) {
            FuturesTransactionCostModel model = FuturesTransactionCostModel.builder()
                    .makerRate(numFactory.numOf(0.00001))
                    .takerRate(numFactory.numOf(0.0005))
                    .build();
            TradeFill spot = new TradeFill(0, T0, numFactory.hundred(), numFactory.one(), ExecutionSide.BUY);

            assertThrows(UnsupportedOperationException.class,
                    () -> model.calculate(numFactory.hundred(), numFactory.one()));
            assertThrows(IllegalArgumentException.class, () -> model.calculateFees(spot));
        }
    }

    @Test
    void recordedFillFeesWinOverTheModeledSchedule() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = btcPerpetual(numFactory);
            FuturesTransactionCostModel expensive = FuturesTransactionCostModel.builder()
                    .makerRate(numFactory.numOf(0.00001))
                    .takerRate(numFactory.numOf(0.01))
                    .build();
            Num contracts = numFactory.numOf(3);
            Num price = numFactory.numOf(50000);

            TradeFill recorded = fill(contract, ExecutionSide.BUY, price, contracts, null,
                    List.of(TradeFee.builder()
                            .type(TradeFee.Type.COMMISSION)
                            .amount(numFactory.numOf(0.15))
                            .currency("USD")
                            .build()));
            Trade observed = Trade.fromFill(recorded, expensive);
            Position observedPosition = new Position(observed, expensive, expensive);
            assertNumEquals(0.15, expensive.calculate(observedPosition, 0));
            assertNumEquals(0.15, observed.getCost());
            assertNumEquals(contract.settlementNotional(contracts, price), observed.getValue());
            assertEquals(contract, observed.getFuturesContract());
            // Fee-adjusted quote view: 50,000 + 0.15 / (3 * 0.01) = 50,005.
            assertNumEquals(50005, observed.getNetPrice());

            TradeFill unpricedSell = fill(contract, ExecutionSide.SELL, price, contracts, null, null);
            Trade modeled = Trade.fromFill(unpricedSell, expensive);
            assertNumEquals(15, modeled.getCost());
            // 50,000 - 15 / (3 * 0.01) = 49,500.
            assertNumEquals(49500, modeled.getNetPrice());

            TradeFill explicitZero = fill(contract, ExecutionSide.BUY, price, contracts, null, List.of());
            assertNumEquals(numFactory.zero(), Trade.fromFill(explicitZero, expensive).getCost());

            // The recorded-only default still refuses to invent fees for a native fill.
            assertThrows(IllegalArgumentException.class, () -> Trade.fromFill(unpricedSell));
        }
    }

    @Test
    void normalizesModeledFeesIntoTradeFillComponents() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = btcPerpetual(numFactory);
            FuturesTransactionCostModel model = FuturesTransactionCostModel.builder()
                    .makerRate(numFactory.numOf(0.00002))
                    .takerRate(numFactory.numOf(0.00001))
                    .minimumPerContract(numFactory.numOf(0.05))
                    .build();
            TradeFill unpriced = fill(contract, ExecutionSide.BUY, numFactory.numOf(50000), numFactory.numOf(3), null,
                    null);

            Trade trade = Trade.fromFill(unpriced, model);
            TradeFill normalized = trade.getFills().getFirst();

            assertTrue(normalized.hasRecordedFees());
            assertNumEquals(0.15, normalized.fee());
            assertEquals("USD", normalized.fees().getFirst().currency());
            assertNumEquals(0.15, trade.getFees().getFirst().settlementAmount());
        }
    }
}
