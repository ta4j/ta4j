/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies the declared specification and settlement arithmetic of
 * {@link FuturesContract}: contract-to-base conversion, quote and settlement
 * notional, contract P&amp;L, signed funding, margin and leverage, rejections
 * of unsupported settlement conventions, and preservation of the full declared
 * specification across serialization.
 */
class FuturesContractTest {

    private static final Instant EXPIRY = Instant.parse("2030-01-01T08:00:00Z");

    private static List<NumFactory> factories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    private static FuturesContract.Builder linearBuilder(NumFactory numFactory) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(0.01));
    }

    private static FuturesContract.Builder inverseBuilder(NumFactory numFactory) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTCUSD-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.INVERSE)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("BTC")
                .contractSize(numFactory.numOf(100));
    }

    @Test
    void linearContractConvertsContractsIntoDollarExposure() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBuilder(numFactory).build();
            Num price = numFactory.numOf(50_000);

            // 3 contracts of 0.01 BTC at 50,000: 0.03 BTC and 1,500 USD of exposure
            assertNumEquals(0.03, contract.baseQuantity(numFactory.numOf(3), price));
            assertNumEquals(1_500, contract.quoteNotional(numFactory.numOf(3), price));
            assertNumEquals(1_500, contract.settlementNotional(numFactory.numOf(3), price));
            assertNumEquals(150, contract.marginRequirement(numFactory.numOf(3), price, numFactory.numOf(0.1)));
            assertNumEquals(1.5, contract.effectiveLeverage(numFactory.numOf(3), price, numFactory.numOf(1_000)));

            assertNumEquals(60, contract.profit(TradeType.BUY, numFactory.numOf(3), price, numFactory.numOf(52_000)));
            assertNumEquals(-60, contract.profit(TradeType.SELL, numFactory.numOf(3), price, numFactory.numOf(52_000)));

            // a positive rate charges longs, and a negative rate reverses that sign
            assertNumEquals(-0.153,
                    contract.fundingCashFlow(numFactory.numOf(3), numFactory.numOf(51_000), numFactory.numOf(0.0001)));
            assertNumEquals(0.153,
                    contract.fundingCashFlow(numFactory.numOf(-3), numFactory.numOf(51_000), numFactory.numOf(0.0001)));
            assertNumEquals(0.153,
                    contract.fundingCashFlow(numFactory.numOf(3), numFactory.numOf(51_000), numFactory.numOf(-0.0001)));

            assertNumEquals(0d, contract.baseQuantity(numFactory.zero(), price));
            assertNumEquals(0d, contract.quoteNotional(numFactory.zero(), price));
            assertNumEquals(0d, contract.settlementNotional(numFactory.zero(), price));
            assertNumEquals(0d, contract.profit(TradeType.BUY, numFactory.zero(), price, numFactory.numOf(52_000)));
            assertNumEquals(0d, contract.fundingCashFlow(numFactory.zero(), price, numFactory.numOf(0.0001)));
        }
    }

    @Test
    void inverseContractInvertsPriceSensitivityAndSettlesInBaseCurrency() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = inverseBuilder(numFactory).build();
            Num entryPrice = numFactory.numOf(20_000);

            // 100 contracts of 100 USD face: 10,000 USD of quote notional settles as 0.5
            // BTC
            assertNumEquals(0.5, contract.baseQuantity(numFactory.numOf(100), entryPrice));
            assertNumEquals(0.5, contract.settlementNotional(numFactory.numOf(100), entryPrice));
            assertNumEquals(10_000, contract.quoteNotional(numFactory.numOf(100), entryPrice));
            assertNumEquals(0.4, contract.settlementNotional(numFactory.numOf(100), numFactory.numOf(25_000)));
            assertNumEquals(0.025,
                    contract.marginRequirement(numFactory.numOf(100), entryPrice, numFactory.numOf(0.05)));

            // the same 5,000 quote move settles as 0.1 BTC, and reverses with the entry
            // side
            assertNumEquals(0.1,
                    contract.profit(TradeType.BUY, numFactory.numOf(100), entryPrice, numFactory.numOf(25_000)));
            assertNumEquals(-0.1,
                    contract.profit(TradeType.SELL, numFactory.numOf(100), entryPrice, numFactory.numOf(25_000)));
            assertNumEquals(-0.00005,
                    contract.fundingCashFlow(numFactory.numOf(100), entryPrice, numFactory.numOf(0.0001)));
            assertNumEquals(0.00005,
                    contract.fundingCashFlow(numFactory.numOf(-100), entryPrice, numFactory.numOf(0.0001)));

            assertNumEquals(0d, contract.baseQuantity(numFactory.zero(), entryPrice));
            assertNumEquals(0d, contract.settlementNotional(numFactory.zero(), entryPrice));
            assertNumEquals(0d,
                    contract.profit(TradeType.BUY, numFactory.zero(), entryPrice, numFactory.numOf(25_000)));
        }
    }

    @Test
    void rejectsUnsupportedSettlementConventionsAndInvalidSpecifications() {
        for (NumFactory numFactory : factories()) {
            // quanto and mismatched settlement currencies are not silently converted
            assertThrows(IllegalArgumentException.class,
                    () -> linearBuilder(numFactory).quoteCurrency("USD").settlementCurrency("USDC").build());
            assertThrows(IllegalArgumentException.class,
                    () -> inverseBuilder(numFactory).settlementCurrency("USD").build());
            assertThrows(IllegalArgumentException.class,
                    () -> linearBuilder(numFactory).settlementCurrency("BTC").build());
            assertThrows(IllegalArgumentException.class,
                    () -> inverseBuilder(numFactory).settlementCurrency("USDC").build());

            // expiry is declared metadata: required for DATED, allowed but unused for
            // PERPETUAL
            assertThrows(IllegalArgumentException.class,
                    () -> linearBuilder(numFactory).productType(FuturesContract.ProductType.DATED).build());
            assertEquals(EXPIRY, linearBuilder(numFactory).expiry(EXPIRY).build().expiry());

            assertThrows(IllegalArgumentException.class, () -> linearBuilder(numFactory).symbol(" ").build());
            assertThrows(NullPointerException.class, () -> linearBuilder(numFactory).baseCurrency(null).build());
            assertThrows(IllegalArgumentException.class,
                    () -> linearBuilder(numFactory).contractSize(numFactory.zero()).build());
            assertThrows(IllegalArgumentException.class,
                    () -> linearBuilder(numFactory).quantityIncrement(numFactory.numOf(-1)).build());
            assertThrows(IllegalArgumentException.class,
                    () -> linearBuilder(numFactory).minimumQuantity(numFactory.numOf(5))
                            .maximumQuantity(numFactory.numOf(1))
                            .build());
            assertThrows(IllegalArgumentException.class,
                    () -> linearBuilder(numFactory).minimumNotional(numFactory.numOf(5))
                            .maximumNotional(numFactory.numOf(1))
                            .build());

            FuturesContract contract = linearBuilder(numFactory).build();
            Num price = numFactory.numOf(50_000);
            assertThrows(IllegalArgumentException.class, () -> contract.quoteNotional(numFactory.numOf(-1), price));
            assertThrows(IllegalArgumentException.class,
                    () -> contract.settlementNotional(numFactory.numOf(1), numFactory.zero()));
            assertThrows(IllegalArgumentException.class,
                    () -> contract.profit(TradeType.BUY, numFactory.numOf(1), price, numFactory.zero()));
            assertThrows(IllegalArgumentException.class,
                    () -> contract.marginRequirement(numFactory.numOf(1), price, numFactory.numOf(-0.1)));
            assertThrows(IllegalArgumentException.class,
                    () -> contract.effectiveLeverage(numFactory.numOf(1), price, numFactory.zero()));
        }
    }

    @Test
    void serializesItsDeclaredSpecificationAndChangedTermsAreANewContract() throws Exception {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = FuturesContract.builder()
                    .venue("CDE")
                    .symbol("BTC-28JAN30-CDE")
                    .productId("BTC-28JAN30-CDE")
                    .contractCode("BTC")
                    .contractRoot("BTC")
                    .displayName("BTC Perpetual")
                    .contractExpiryType("expiring")
                    .contractRootUnit("BTC")
                    .productType(FuturesContract.ProductType.DATED)
                    .settlementType(FuturesContract.SettlementType.LINEAR)
                    .baseCurrency("BTC")
                    .quoteCurrency("USD")
                    .settlementCurrency("USD")
                    .contractSize(numFactory.numOf(0.01))
                    .expiry(EXPIRY)
                    .expiryTimeZone(ZoneId.of("America/Chicago"))
                    .tradingDisabledAt(EXPIRY.minus(Duration.ofHours(1)))
                    .priceIncrement(numFactory.numOf(0.5))
                    .quantityIncrement(numFactory.one())
                    .minimumQuantity(numFactory.one())
                    .maximumQuantity(numFactory.numOf(1_000))
                    .minimumNotional(numFactory.numOf(100))
                    .maximumNotional(numFactory.numOf(1_000_000))
                    .perpetualStyle(false)
                    .trading24x7(true)
                    .nonCrypto(false)
                    .riskManagedBy("CDE")
                    .attributes(Map.of("status", "online", "auctionMode", "false"))
                    .build();

            FuturesContract copy = roundTrip(contract);

            assertEquals(contract, copy);
            assertEquals(contract.hashCode(), copy.hashCode());
            assertEquals(EXPIRY, copy.expiry());
            assertEquals(ZoneId.of("America/Chicago"), copy.expiryTimeZone());
            assertEquals(EXPIRY.minus(Duration.ofHours(1)), copy.tradingDisabledAt());
            assertEquals(Map.of("status", "online", "auctionMode", "false"), copy.attributes());
            assertNumEquals(0.01, copy.contractSize());
            assertNumEquals(1_000, copy.maximumQuantity());
            assertEquals("BTC-28JAN30-CDE", copy.productId());

            // changing a declared economic term is a different contract, never a
            // reinterpretation
            FuturesContract resized = contract.toBuilder().contractSize(numFactory.numOf(0.1)).build();
            assertNotEquals(contract, resized);
            assertNumEquals(0.01, contract.contractSize());
            assertNumEquals(0.1, resized.contractSize());
            assertEquals(contract.productId(), resized.productId());
        }
    }

    private static FuturesContract roundTrip(FuturesContract contract) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(contract);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (FuturesContract) in.readObject();
        }
    }
}
