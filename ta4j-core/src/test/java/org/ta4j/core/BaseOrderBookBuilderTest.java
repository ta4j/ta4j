/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.OrderBook.Metadata;
import org.ta4j.core.OrderBook.Side;
import org.ta4j.core.OrderBook.State;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class BaseOrderBookBuilderTest {
    private final NumFactory factory = DecimalNumFactory.getInstance();
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    private Metadata metadata(int second, State state, boolean bids, boolean asks) {
        return new Metadata("venue", "instrument", "base", null, TIME.plusSeconds(second), "opaque:" + second, state,
                bids, asks);
    }

    private OrderBookLevel level(int price, int amount) {
        return new OrderBookLevel(factory.numOf(price), factory.numOf(amount));
    }

    private BaseOrderBookBuilder builder() {
        return new BaseOrderBookBuilder(factory, "venue", "instrument", "base");
    }

    private BaseOrderBookBuilder initialized() {
        return builder().replace(metadata(0, State.SYNCHRONIZED, true, true), List.of(level(99, 4), level(98, 6)),
                List.of(level(101, 2), level(102, 8)));
    }

    @Test
    void requiresExplicitInitializationAndBoundIdentity() {
        BaseOrderBookBuilder builder = builder();
        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(IllegalStateException.class, () -> builder.invalidate(TIME));
        assertThrows(IllegalStateException.class,
                () -> builder.apply(metadata(1, State.SYNCHRONIZED, true, true), List.of(), List.of()));
        for (Metadata other : List.of(
                new Metadata("other", "instrument", "base", null, TIME, null, State.SYNCHRONIZED, true, true),
                new Metadata("venue", "other", "base", null, TIME, null, State.SYNCHRONIZED, true, true),
                new Metadata("venue", "instrument", "other", null, TIME, null, State.SYNCHRONIZED, true, true))) {
            assertThrows(IllegalArgumentException.class, () -> builder.replace(other, List.of(), List.of()));
        }
        assertThrows(NullPointerException.class, () -> builder.replace(null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BaseOrderBookBuilder(factory, " ", "I", "base"));
        assertThrows(NullPointerException.class, () -> new BaseOrderBookBuilder(null, "V", "I", "base"));
    }

    @Test
    void appliesAbsoluteAmountsDeletesNumericallyAndPreservesPublishedSnapshots() {
        BaseOrderBookBuilder builder = initialized();
        BaseOrderBook old = builder.build();
        Metadata next = metadata(1, State.SYNCHRONIZED, true, true);
        builder.apply(next, List.of(level(99, 0), level(97, 0), level(100, 3)), List.of(level(101, 5)));
        BaseOrderBook updated = builder.build();
        assertEquals(List.of(level(100, 3), level(98, 6)), updated.getBids());
        assertEquals(List.of(level(101, 5), level(102, 8)), updated.getAsks());
        assertNumEquals(5, updated.getBestAsk().orElseThrow().amount());
        assertEquals(next, updated.getMetadata());
        assertEquals(List.of(level(99, 4), level(98, 6)), old.getBids());
        assertNumEquals(2, old.getBestAsk().orElseThrow().amount());
        builder.apply(next, List.of(level(100, 3)), List.of(level(101, 5)));
        assertEquals(updated.getBids(), builder.build().getBids());
        assertEquals(updated.getAsks(), builder.build().getAsks());
        builder.apply(metadata(2, State.SYNCHRONIZED, true, true),
                List.of(new OrderBookLevel(factory.numOf("100.00"), factory.zero())), List.of());
        assertEquals(List.of(level(98, 6)), builder.build().getBids());
    }

    @Test
    void malformedAskBatchRollsBackBidsMetadataAndReplacement() {
        BaseOrderBookBuilder builder = initialized();
        BaseOrderBook old = builder.build();
        Metadata next = metadata(1, State.SYNCHRONIZED, true, true);
        List<OrderBookLevel> duplicateAsks = List.of(level(101, 5),
                new OrderBookLevel(factory.numOf("101.00"), factory.one()));
        assertThrows(IllegalArgumentException.class, () -> builder.apply(next, List.of(level(99, 10)), duplicateAsks));
        assertSame(old, builder.build());
        assertThrows(IllegalArgumentException.class,
                () -> builder.replace(next, List.of(level(100, 10)), duplicateAsks));
        assertSame(old, builder.build());
        OrderBookLevel foreign = new OrderBookLevel(DoubleNumFactory.getInstance().one(), factory.one());
        assertThrows(IllegalArgumentException.class,
                () -> builder.apply(next, List.of(level(99, 10)), List.of(foreign)));
        assertSame(old, builder.build());
        assertThrows(NullPointerException.class, () -> builder.apply(next, List.of(level(99, 10)), null));
        assertSame(old, builder.build());
        assertThrows(IllegalArgumentException.class,
                () -> builder.apply(next, List.of(level(99, 1), level(99, 2)), List.of()));
        assertSame(old, builder.build());
        assertThrows(IllegalArgumentException.class,
                () -> builder.replace(next, List.of(level(100, 1)), List.of(level(101, 0))));
        assertSame(old, builder.build());
    }

    @Test
    void replacementClearsOldPricesAndCopiesInputs() {
        BaseOrderBookBuilder builder = initialized();
        List<OrderBookLevel> bids = new ArrayList<>(List.of(level(90, 1)));
        Metadata metadata = metadata(5, State.SYNCHRONIZED, false, false);
        builder.replace(metadata, bids, List.of());
        bids.clear();
        assertEquals(List.of(level(90, 1)), builder.build().getBids());
        assertTrue(builder.build().getAsks().isEmpty());
        assertFalse(builder.build().hasDepth(Side.BID, 2));
    }

    @Test
    void partialSideRequiresReplacementButCompleteOtherSideCanUpdate() {
        BaseOrderBookBuilder builder = builder().replace(metadata(0, State.SYNCHRONIZED, false, true),
                List.of(level(99, 1)), List.of(level(101, 2)));
        BaseOrderBook old = builder.build();
        Metadata next = metadata(1, State.SYNCHRONIZED, false, true);
        assertThrows(IllegalArgumentException.class,
                () -> builder.apply(next, List.of(level(99, 0)), List.of(level(101, 5))));
        assertSame(old, builder.build());
        assertThrows(IllegalArgumentException.class,
                () -> builder.apply(metadata(1, State.SYNCHRONIZED, true, true), List.of(), List.of()));
        builder.apply(next, List.of(), List.of(level(101, 5)));
        assertNumEquals(5, builder.build().getBestAsk().orElseThrow().amount());
        assertEquals(old.getBids(), builder.build().getBids());
        builder.replace(metadata(2, State.SYNCHRONIZED, true, false), List.of(level(98, 1)), List.of(level(102, 3)));
        BaseOrderBook partialAsk = builder.build();
        assertThrows(IllegalArgumentException.class, () -> builder.apply(metadata(3, State.SYNCHRONIZED, true, false),
                List.of(level(98, 5)), List.of(level(102, 0))));
        assertSame(partialAsk, builder.build());
        builder.apply(metadata(3, State.SYNCHRONIZED, true, false), List.of(level(98, 5)), List.of());
        assertNumEquals(5, builder.build().getBestBid().orElseThrow().amount());
    }

    @Test
    void invalidationRequiresReplacementAndBuildDoesNotManufactureFreshness() {
        BaseOrderBookBuilder builder = initialized();
        BaseOrderBook healthy = builder.build();
        assertSame(healthy, builder.build());
        builder.invalidate(TIME.plusSeconds(1));
        BaseOrderBook invalid = builder.build();
        assertEquals(State.UNSYNCHRONIZED, invalid.getMetadata().state());
        assertEquals(TIME.plusSeconds(1), invalid.getMetadata().availableTime());
        assertEquals(healthy.getMetadata().eventTime(), invalid.getMetadata().eventTime());
        assertEquals(healthy.getMetadata().sourceSequence(), invalid.getMetadata().sourceSequence());
        assertEquals(healthy.getBids(), invalid.getBids());
        assertTrue(invalid.getSpread().isNaN());
        assertThrows(IllegalStateException.class,
                () -> builder.apply(metadata(2, State.SYNCHRONIZED, true, true), List.of(), List.of()));
        assertSame(invalid, builder.build());
        assertThrows(NullPointerException.class, () -> builder.invalidate(null));
        assertSame(invalid, builder.build());
        builder.replace(metadata(0, State.SYNCHRONIZED, true, true), List.of(level(95, 1)), List.of(level(96, 1)));
        assertNumEquals(1, builder.build().getSpread());
        assertEquals("opaque:0", builder.build().getMetadata().sourceSequence());
        assertEquals(TIME, builder.build().getMetadata().availableTime());
        assertThrows(IllegalArgumentException.class,
                () -> builder.apply(metadata(1, State.UNSYNCHRONIZED, true, true), List.of(), List.of()));
        builder.replace(metadata(2, State.UNSYNCHRONIZED, true, true), List.of(), List.of());
        assertThrows(IllegalStateException.class,
                () -> builder.apply(metadata(3, State.SYNCHRONIZED, true, true), List.of(), List.of()));
    }

    @Test
    void randomizedAbsoluteUpdatesMatchIntegerPriceOracleAndKeepSnapshotsFrozen() {
        BaseOrderBookBuilder builder = builder().replace(metadata(0, State.SYNCHRONIZED, true, true), List.of(),
                List.of());
        Map<Integer, Integer> expected = new TreeMap<>();
        Random random = new Random(605);
        for (int batch = 1; batch <= 100; batch++) {
            BaseOrderBook before = builder.build();
            List<OrderBookLevel> beforeLevels = List.copyOf(before.getBids());
            int price = random.nextInt(21) - 10;
            int amount = random.nextInt(5);
            if (amount == 0) {
                expected.remove(price);
            } else {
                expected.put(price, amount);
            }
            builder.apply(metadata(batch, State.SYNCHRONIZED, true, true), List.of(level(price, amount)), List.of());
            List<OrderBookLevel> oracle = new ArrayList<>();
            expected.entrySet()
                    .stream()
                    .sorted(Map.Entry.<Integer, Integer>comparingByKey().reversed())
                    .forEach(entry -> oracle.add(level(entry.getKey(), entry.getValue())));
            assertEquals(oracle, builder.build().getBids());
            assertEquals(beforeLevels, before.getBids());
        }
    }

    @Test
    void signedZeroUpdatesUseOneNumericPriceAndFailedDuplicatesRollBack() {
        NumFactory doubles = DoubleNumFactory.getInstance();
        OrderBookLevel negativeZero = new OrderBookLevel(doubles.numOf(-0.0), doubles.one());
        OrderBookLevel positiveZero = new OrderBookLevel(doubles.numOf(0.0), doubles.two());
        BaseOrderBookBuilder builder = new BaseOrderBookBuilder(doubles, "venue", "instrument", "base")
                .replace(metadata(0, State.SYNCHRONIZED, true, true), List.of(negativeZero), List.of());
        builder.apply(metadata(1, State.SYNCHRONIZED, true, true), List.of(positiveZero), List.of());
        assertEquals(1, builder.build().getBids().size());
        assertNumEquals(2, builder.build().getBestBid().orElseThrow().amount());
        BaseOrderBook before = builder.build();
        assertThrows(IllegalArgumentException.class, () -> builder.apply(metadata(2, State.SYNCHRONIZED, true, true),
                List.of(negativeZero, positiveZero), List.of()));
        assertSame(before, builder.build());
        builder.apply(metadata(3, State.SYNCHRONIZED, true, true),
                List.of(new OrderBookLevel(doubles.numOf(-0.0), doubles.zero())), List.of());
        assertTrue(builder.build().getBids().isEmpty());
    }
}
