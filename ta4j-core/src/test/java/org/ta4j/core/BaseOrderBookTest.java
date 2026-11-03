/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.OrderBook.Metadata;
import org.ta4j.core.OrderBook.Side;
import org.ta4j.core.OrderBook.State;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class BaseOrderBookTest {
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    static Stream<NumFactory> factories() {
        return Stream.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    private static OrderBookLevel level(NumFactory factory, String price, String amount) {
        return new OrderBookLevel(factory.numOf(price), factory.numOf(amount));
    }

    private static Metadata metadata(State state, boolean bids, boolean asks) {
        return new Metadata("venue", "instrument", "contracts", null, TIME, null, state, bids, asks);
    }

    private static BaseOrderBook fixture(NumFactory factory, Metadata metadata) {
        return new BaseOrderBook(factory, metadata, List.of(level(factory, "98", "6"), level(factory, "99", "4")),
                List.of(level(factory, "102", "8"), level(factory, "101", "2")));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void normalizesAndMeasuresKnownFixture(NumFactory factory) {
        BaseOrderBook book = fixture(factory, metadata(State.SYNCHRONIZED, true, true));
        assertSame(factory, book.numFactory());
        assertEquals(List.of(level(factory, "99", "4"), level(factory, "98", "6")), book.getBids());
        assertEquals(List.of(level(factory, "101", "2"), level(factory, "102", "8")), book.getAsks());
        assertEquals(book.getBids().getFirst(), book.getBestBid().orElseThrow());
        assertEquals(book.getAsks().getFirst(), book.getBestAsk().orElseThrow());
        assertNumEquals(2, book.getSpread());
        assertNumEquals(100, book.getMidPrice());
        assertNumEquals(10, book.getDepth(Side.BID, 2));
        assertNumEquals(10, book.getDepth(Side.ASK, 10));
        assertNumEquals(0, book.getImbalance(2));
        assertNumEquals(factory.one().dividedBy(factory.three()), book.getImbalance(1));
        assertTrue(book.hasDepth(Side.BID, Integer.MAX_VALUE));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void permutationNormalizationIsDeterministicAndUnique(NumFactory factory) {
        List<OrderBookLevel> bids = new ArrayList<>();
        List<OrderBookLevel> asks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            bids.add(new OrderBookLevel(factory.numOf(i - 20), factory.numOf(i + 1)));
            asks.add(new OrderBookLevel(factory.numOf(i), factory.numOf(20 - i)));
        }
        BaseOrderBook expected = new BaseOrderBook(factory, metadata(State.SYNCHRONIZED, true, true), bids, asks);
        Random random = new Random(605);
        for (int permutation = 0; permutation < 30; permutation++) {
            Collections.shuffle(bids, random);
            Collections.shuffle(asks, random);
            BaseOrderBook actual = new BaseOrderBook(factory, expected.getMetadata(), bids, asks);
            assertEquals(expected.getBids(), actual.getBids());
            assertEquals(expected.getAsks(), actual.getAsks());
            assertNumEquals(210, actual.getDepth(Side.BID, 20));
            assertNumEquals(210, actual.getDepth(Side.ASK, 20));
        }
    }

    @ParameterizedTest
    @MethodSource("factories")
    void rejectsDuplicateNumericPricesZeroSnapshotsAndForeignValues(NumFactory factory) {
        Metadata metadata = metadata(State.SYNCHRONIZED, true, true);
        List<OrderBookLevel> duplicates = List.of(level(factory, "100.0", "1"), level(factory, "100.00", "2"));
        assertThrows(IllegalArgumentException.class, () -> new BaseOrderBook(factory, metadata, duplicates, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BaseOrderBook(factory, metadata, List.of(), duplicates));
        assertThrows(IllegalArgumentException.class,
                () -> new BaseOrderBook(factory, metadata, List.of(level(factory, "0", "0")), List.of()));
        NumFactory foreign = factory instanceof DoubleNumFactory ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();
        assertThrows(IllegalArgumentException.class,
                () -> new BaseOrderBook(factory, metadata, List.of(level(foreign, "1", "2")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BaseOrderBook(factory, metadata,
                List.of(new OrderBookLevel(factory.one(), foreign.one())), List.of()));
        assertThrows(NullPointerException.class, () -> new BaseOrderBook(null, metadata, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new BaseOrderBook(factory, null, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new BaseOrderBook(factory, metadata, null, List.of()));
        List<OrderBookLevel> nullLevel = new ArrayList<>();
        nullLevel.add(null);
        assertThrows(NullPointerException.class, () -> new BaseOrderBook(factory, metadata, List.of(), nullLevel));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void freezesCallerListsAndKeepsDiagnosticsBounded(NumFactory factory) {
        List<OrderBookLevel> levels = new ArrayList<>(List.of(level(factory, "1", "2"), level(factory, "0", "77")));
        BaseOrderBook book = new BaseOrderBook(factory, metadata(State.SYNCHRONIZED, true, true), levels, List.of());
        levels.clear();
        assertEquals(2, book.getBids().size());
        assertThrows(UnsupportedOperationException.class, () -> book.getBids().clear());
        assertThrows(UnsupportedOperationException.class, () -> book.getAsks().add(level(factory, "2", "1")));
        assertFalse(book.toString().contains("77"));
        assertTrue(book.toString().contains("bids=2"));
        assertSame(book, BaseOrderBook.copyOf(book));
        assertThrows(NullPointerException.class, () -> BaseOrderBook.copyOf(null));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void emptyAndOneSidedAreObservationsWithTruthfulCoverage(NumFactory factory) {
        BaseOrderBook empty = new BaseOrderBook(factory, metadata(State.SYNCHRONIZED, true, false), List.of(),
                List.of());
        assertTrue(empty.getBestBid().isEmpty());
        assertTrue(empty.getBestAsk().isEmpty());
        assertTrue(empty.hasDepth(Side.BID, 1));
        assertFalse(empty.hasDepth(Side.ASK, 1));
        assertNumEquals(0, empty.getDepth(Side.BID, 100));
        assertTrue(empty.getDepth(Side.ASK, 1).isNaN());
        assertTrue(empty.getSpread().isNaN());
        assertTrue(empty.getMidPrice().isNaN());
        assertTrue(empty.getImbalance(1).isNaN());
        BaseOrderBook oneSided = new BaseOrderBook(factory, metadata(State.SYNCHRONIZED, true, true),
                List.of(level(factory, "-10", "3")), List.of());
        assertNumEquals(-10, oneSided.getBestBid().orElseThrow().price());
        assertNumEquals(3, oneSided.getDepth(Side.BID, 2));
        assertTrue(oneSided.getSpread().isNaN());
        assertTrue(oneSided.getMidPrice().isNaN());
        assertTrue(oneSided.getImbalance(1).isNaN());
        BaseOrderBook askOnly = new BaseOrderBook(factory, oneSided.getMetadata(), List.of(),
                List.of(level(factory, "0", "3")));
        assertTrue(askOnly.getSpread().isNaN());
        assertNumEquals(3, askOnly.getDepth(Side.ASK, 1));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void partialDepthAndSynchronizationAreIndependent(NumFactory factory) {
        BaseOrderBook book = fixture(factory, metadata(State.SYNCHRONIZED, false, true));
        assertTrue(book.hasDepth(Side.BID, 2));
        assertFalse(book.hasDepth(Side.BID, 3));
        assertTrue(book.hasDepth(Side.ASK, 3));
        assertTrue(book.getDepth(Side.BID, 3).isNaN());
        assertTrue(book.getImbalance(3).isNaN());
        assertNumEquals(0, book.getImbalance(2));
        BaseOrderBook unsynchronized = fixture(factory, metadata(State.UNSYNCHRONIZED, false, true));
        assertTrue(unsynchronized.hasDepth(Side.BID, 2));
        assertTrue(unsynchronized.getBestAsk().isPresent());
        assertTrue(unsynchronized.getSpread().isNaN());
        assertTrue(unsynchronized.getMidPrice().isNaN());
        assertTrue(unsynchronized.getDepth(Side.ASK, 1).isNaN());
        assertTrue(unsynchronized.getImbalance(1).isNaN());
        assertThrows(IllegalArgumentException.class, () -> book.hasDepth(Side.BID, 0));
        assertThrows(IllegalArgumentException.class, () -> book.getDepth(Side.ASK, -1));
        assertThrows(IllegalArgumentException.class, () -> unsynchronized.getImbalance(0));
        assertThrows(NullPointerException.class, () -> book.hasDepth(null, 1));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void lockedAndCrossedQuotesRemainInspectable(NumFactory factory) {
        Metadata metadata = metadata(State.SYNCHRONIZED, true, true);
        BaseOrderBook locked = new BaseOrderBook(factory, metadata, List.of(level(factory, "-1", "2")),
                List.of(level(factory, "-1", "2")));
        assertNumEquals(0, locked.getSpread());
        assertNumEquals(-1, locked.getMidPrice());
        assertNumEquals(0, locked.getImbalance(1));
        BaseOrderBook crossed = new BaseOrderBook(factory, metadata, List.of(level(factory, "1", "2")),
                List.of(level(factory, "0", "2")));
        assertEquals(1, crossed.getBids().size());
        assertTrue(crossed.getSpread().isNaN());
        assertTrue(crossed.getMidPrice().isNaN());
        assertTrue(crossed.getImbalance(1).isNaN());
        assertNumEquals(2, crossed.getDepth(Side.ASK, 1));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void serializationPreservesOptionalMetadataNumericValuesCoverageAndState(NumFactory factory) throws Exception {
        for (Metadata metadata : List.of(metadata(State.UNSYNCHRONIZED, false, true), new Metadata("venue",
                "instrument", "contracts", TIME.plusSeconds(10), TIME, "reset:0", State.SYNCHRONIZED, true, false))) {
            BaseOrderBook original = new BaseOrderBook(factory, metadata,
                    List.of(level(factory, "98.12345678901234", "6.987654321098765")),
                    List.of(level(factory, "101.9876543210987", "2.123456789012345")));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(original);
            }
            BaseOrderBook restored;
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                restored = (BaseOrderBook) in.readObject();
            }
            assertEquals(metadata, restored.getMetadata());
            assertEquals(original.getBids(), restored.getBids());
            assertEquals(original.getAsks(), restored.getAsks());
            assertTrue(factory.produces(restored.getBids().getFirst().price()));
            assertNumEquals(original.getDepth(Side.BID, 1), restored.getDepth(Side.BID, 1));
            assertThrows(UnsupportedOperationException.class, () -> restored.getAsks().clear());
        }
    }
}
