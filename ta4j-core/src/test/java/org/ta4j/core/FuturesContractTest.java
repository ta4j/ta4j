/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void effectiveLeverageRejectsCollateralThatUnderflowsReferenceFactory() {
        FuturesContract contract = linearBuilder(DoubleNumFactory.getInstance()).build();
        Num tinyCollateral = DecimalNumFactory.getInstance().numOf("1e-400");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> contract.effectiveLeverage(DoubleNumFactory.getInstance().one(),
                        DoubleNumFactory.getInstance().numOf(100), tinyCollateral));

        assertEquals("collateral must be positive and finite", exception.getMessage());
    }

    @Test
    public void marginRequirementRejectsRateOutsideReferenceFactoryRange() {
        FuturesContract contract = linearBuilder(DoubleNumFactory.getInstance()).build();
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        NumFactory decimalFactory = DecimalNumFactory.getInstance();

        assertThrows(IllegalArgumentException.class, () -> contract.marginRequirement(doubleFactory.one(),
                doubleFactory.numOf(100), decimalFactory.numOf("1e-400")));
        assertThrows(IllegalArgumentException.class, () -> contract.marginRequirement(doubleFactory.one(),
                doubleFactory.numOf(100), decimalFactory.numOf("1e400")));
    }

    @Test
    public void rejectsUnrepresentableCrossFactoryValuesBeforeArithmetic() {
        FuturesContract contract = linearBuilder(DoubleNumFactory.getInstance()).build();
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        NumFactory decimalFactory = DecimalNumFactory.getInstance();

        assertThrows(IllegalArgumentException.class, () -> contract.profit(TradeType.BUY, doubleFactory.one(),
                doubleFactory.numOf(100), decimalFactory.numOf("1e-400")));
        assertThrows(IllegalArgumentException.class, () -> contract.profit(TradeType.BUY, doubleFactory.one(),
                doubleFactory.numOf(100), decimalFactory.numOf("1e400")));
        assertThrows(IllegalArgumentException.class,
                () -> contract.baseQuantity(decimalFactory.numOf("1e400"), doubleFactory.numOf(100)));
        assertThrows(IllegalArgumentException.class, () -> contract.fundingCashFlow(doubleFactory.one(),
                doubleFactory.numOf(100), decimalFactory.numOf("1e-400")));
        assertThrows(IllegalArgumentException.class, () -> contract.fundingCashFlow(doubleFactory.one(),
                doubleFactory.numOf(100), decimalFactory.numOf("1e400")));
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
    public void rejectsQuantityIncrementsOutsideReferenceFactoryRange() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        NumFactory decimalFactory = DecimalNumFactory.getInstance();

        FuturesContract normalized = linearBuilder(doubleFactory).quantityIncrement(decimalFactory.numOf("0.25"))
                .build();
        assertNumEquals(doubleFactory.numOf(0.25), normalized.quantityIncrement());
        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).quantityIncrement(decimalFactory.numOf("1e-400")).build());
        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).quantityIncrement(decimalFactory.numOf("1e400")).build());
    }

    @Test
    public void rejectsMaximumBoundsOutsideReferenceFactoryRange() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        NumFactory decimalFactory = DecimalNumFactory.getInstance();

        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).minimumQuantity(doubleFactory.one())
                        .maximumQuantity(decimalFactory.numOf("1e400"))
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).minimumNotional(doubleFactory.one())
                        .maximumNotional(decimalFactory.numOf("1e400"))
                        .build());

        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).minimumQuantity(doubleFactory.numOf(Double.MIN_VALUE))
                        .maximumQuantity(decimalFactory.numOf("1e-400"))
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).minimumNotional(doubleFactory.numOf(Double.MIN_VALUE))
                        .maximumNotional(decimalFactory.numOf("1e-400"))
                        .build());

        FuturesContract normalized = linearBuilder(doubleFactory).minimumQuantity(doubleFactory.one())
                .maximumQuantity(decimalFactory.numOf("2"))
                .minimumNotional(doubleFactory.one())
                .maximumNotional(decimalFactory.numOf("2"))
                .build();
        assertNumEquals(2, normalized.maximumQuantity());
        assertNumEquals(2, normalized.maximumNotional());
        assertEquals(normalized.hashCode(), normalized.toBuilder().build().hashCode());
    }

    @Test
    public void normalizesMinimumBoundsAgainstContractFactory() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        NumFactory decimalFactory = DecimalNumFactory.getInstance();

        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).minimumQuantity(decimalFactory.numOf("1e400")).build());
        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).minimumQuantity(decimalFactory.numOf("1e-400")).build());
        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).minimumNotional(decimalFactory.numOf("1e400")).build());
        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).minimumNotional(decimalFactory.numOf("1e-400")).build());

        FuturesContract normalized = linearBuilder(doubleFactory).minimumQuantity(decimalFactory.numOf("0.25"))
                .minimumNotional(decimalFactory.numOf("2"))
                .build();
        assertNumEquals(doubleFactory.numOf(0.25), normalized.minimumQuantity());
        assertNumEquals(doubleFactory.numOf(2), normalized.minimumNotional());
    }

    @Test
    public void rejectsContractSizeProductThatUnderflowsReferenceFactory() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        FuturesContract contract = linearBuilder(doubleFactory).contractSize(doubleFactory.numOf(Double.MIN_VALUE))
                .build();
        Num decimalContracts = DecimalNumFactory.getInstance().numOf("0.1");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> contract.baseQuantity(decimalContracts, doubleFactory.one()));

        assertEquals("contracts * contractSize cannot be represented in price number factory", exception.getMessage());
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

    @Test
    void includesDescriptiveIdentityFieldsInContractDiagnostics() {
        FuturesContract contract = linearBuilder(DoubleNumFactory.getInstance()).displayName("BTC Perpetual")
                .attributes(Map.of("status", "online"))
                .build();

        assertTrue(contract.toString().contains("displayName=BTC Perpetual"));
        assertTrue(contract.toString().contains("attributes={status=online}"));
    }

    @Test
    void numericallyEqualContractsHaveFactoryIndependentHashes() {
        FuturesContract doubleContract = linearBuilder(DoubleNumFactory.getInstance())
                .quantityIncrement(DoubleNumFactory.getInstance().one())
                .build();
        FuturesContract decimalContract = linearBuilder(DecimalNumFactory.getInstance())
                .quantityIncrement(DecimalNumFactory.getInstance().one())
                .build();

        assertEquals(doubleContract, decimalContract);
        assertEquals(doubleContract.hashCode(), decimalContract.hashCode());
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

    @Test
    public void rejectsMaximumBoundsWithoutMinimumBounds() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        NumFactory decimalFactory = DecimalNumFactory.getInstance();

        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).maximumQuantity(decimalFactory.numOf("1e400")).build());
        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).maximumQuantity(decimalFactory.numOf("1e-400")).build());
        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).maximumNotional(decimalFactory.numOf("1e400")).build());
        assertThrows(IllegalArgumentException.class,
                () -> linearBuilder(doubleFactory).maximumNotional(decimalFactory.numOf("1e-400")).build());
    }

    @Test
    public void rejectsNonFiniteNotionalResults() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();

        FuturesContract oversizedLinear = linearBuilder(doubleFactory).contractSize(doubleFactory.numOf("1e200"))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> oversizedLinear.quoteNotional(doubleFactory.one(), doubleFactory.numOf("1e200")));

        FuturesContract tinyPriceInverse = inverseBuilder(doubleFactory).build();
        assertThrows(IllegalArgumentException.class,
                () -> tinyPriceInverse.settlementNotional(doubleFactory.one(), doubleFactory.numOf(Double.MIN_VALUE)));

        FuturesContract unitSizeLinear = linearBuilder(doubleFactory).contractSize(doubleFactory.one()).build();
        assertThrows(IllegalArgumentException.class, () -> unitSizeLinear.profit(TradeType.BUY,
                doubleFactory.numOf("1e-200"), doubleFactory.numOf("1e-200"), doubleFactory.numOf("2e-200")));
        assertThrows(IllegalArgumentException.class, () -> unitSizeLinear.profit(TradeType.BUY,
                doubleFactory.numOf("1e200"), doubleFactory.one(), doubleFactory.numOf("1e200")));
        assertThrows(IllegalArgumentException.class, () -> unitSizeLinear.fundingCashFlow(doubleFactory.numOf("1e-200"),
                doubleFactory.one(), doubleFactory.numOf("1e-200")));
        assertThrows(IllegalArgumentException.class, () -> unitSizeLinear.fundingCashFlow(doubleFactory.numOf("1e308"),
                doubleFactory.one(), doubleFactory.numOf("2")));
        assertThrows(IllegalArgumentException.class, () -> unitSizeLinear
                .marginRequirement(doubleFactory.numOf("1e-200"), doubleFactory.one(), doubleFactory.numOf("1e-200")));
        assertThrows(IllegalArgumentException.class, () -> unitSizeLinear
                .marginRequirement(doubleFactory.numOf("1e308"), doubleFactory.one(), doubleFactory.numOf("2")));

        FuturesContract tinyBaseInverse = inverseBuilder(doubleFactory).contractSize(doubleFactory.numOf("1e-200"))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> tinyBaseInverse.baseQuantity(doubleFactory.one(), doubleFactory.numOf("1e200")));
        FuturesContract hugeBaseInverse = inverseBuilder(doubleFactory).contractSize(doubleFactory.numOf("1e200"))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> hugeBaseInverse.baseQuantity(doubleFactory.one(), doubleFactory.numOf("1e-200")));
        assertThrows(IllegalArgumentException.class, () -> unitSizeLinear.effectiveLeverage(doubleFactory.one(),
                doubleFactory.numOf("1e-200"), doubleFactory.numOf("1e200")));
        assertThrows(IllegalArgumentException.class, () -> unitSizeLinear.effectiveLeverage(doubleFactory.one(),
                doubleFactory.numOf("1e200"), doubleFactory.numOf("1e-200")));
    }

    @Test
    public void rejectsNotionalResultsThatUnderflowToZero() {
        NumFactory factory = DoubleNumFactory.getInstance();
        FuturesContract linear = linearBuilder(factory).contractSize(factory.numOf(Double.MIN_VALUE)).build();
        assertThrows(IllegalArgumentException.class, () -> linear.quoteNotional(factory.one(), factory.numOf(0.5)));

        FuturesContract inverse = inverseBuilder(factory).contractSize(factory.one()).build();
        assertThrows(IllegalArgumentException.class,
                () -> inverse.settlementNotional(factory.numOf(Double.MIN_VALUE), factory.numOf(Double.MAX_VALUE)));
    }

}
