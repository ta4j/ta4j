/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.OrderBook.Metadata;
import org.ta4j.core.OrderBook.State;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class OrderBookTest {
    private static final Instant TIME = Instant.EPOCH;

    @Test
    void defaultMeasurementsAndCopySupportExternalImplementationsWithoutBars() {
        NumFactory factory = DecimalNumFactory.getInstance();
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        List<OrderBookLevel> bids = new ArrayList<>(List.of(new OrderBookLevel(factory.numOf(99), factory.numOf(4))));
        List<OrderBookLevel> asks = new ArrayList<>(List.of(new OrderBookLevel(factory.numOf(101), factory.two())));
        OrderBook external = new OrderBook() {
            @Override
            public NumFactory numFactory() {
                return factory;
            }

            @Override
            public Metadata getMetadata() {
                return metadata;
            }

            @Override
            public List<OrderBookLevel> getBids() {
                return bids;
            }

            @Override
            public List<OrderBookLevel> getAsks() {
                return asks;
            }
        };
        assertNumEquals(2, external.getSpread());
        assertNumEquals(100, external.getMidPrice());
        assertNumEquals(factory.one().dividedBy(factory.three()), external.getImbalance(1));
        BaseOrderBook frozen = BaseOrderBook.copyOf(external);
        // Deliberately mutate a boundary fixture after materialization.
        bids.clear();
        asks.clear();
        assertNumEquals(2, frozen.getSpread());
        assertEquals(1, frozen.getBids().size());
    }

    @Test
    void metadataValidatesIdentityButPreservesMissingValuesAndClockSkew() {
        Metadata metadata = new Metadata("V", "I", "contracts", TIME.plusSeconds(5), TIME, null, State.SYNCHRONIZED,
                false, true);
        assertTrue(metadata.eventTime().isAfter(metadata.availableTime()));
        assertNull(metadata.sourceSequence());
        assertThrows(IllegalArgumentException.class,
                () -> new Metadata(" ", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true));
        assertThrows(IllegalArgumentException.class,
                () -> new Metadata("V", "", "base", null, TIME, null, State.SYNCHRONIZED, true, true));
        assertThrows(IllegalArgumentException.class,
                () -> new Metadata("V", "I", "\t", null, TIME, null, State.SYNCHRONIZED, true, true));
        assertThrows(NullPointerException.class,
                () -> new Metadata(null, "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true));
        assertThrows(NullPointerException.class,
                () -> new Metadata("V", "I", "base", null, null, null, State.SYNCHRONIZED, true, true));
        assertThrows(NullPointerException.class,
                () -> new Metadata("V", "I", "base", null, TIME, null, null, true, true));
    }

    @Test
    void doubleMeasurementOverflowDoesNotInventValidDepthOrImbalance() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        OrderBookLevel bid = new OrderBookLevel(factory.numOf(-1), factory.numOf(Double.MAX_VALUE));
        OrderBookLevel ask = new OrderBookLevel(factory.one(), factory.numOf(Double.MAX_VALUE));
        BaseOrderBook book = new BaseOrderBook(factory, metadata, List.of(bid), List.of(ask));
        assertTrue(book.getImbalance(1).isNaN());
        BaseOrderBook overflow = new BaseOrderBook(factory, metadata,
                List.of(bid, new OrderBookLevel(factory.numOf(-2), bid.amount())), List.of(ask));
        assertTrue(overflow.getDepth(OrderBook.Side.BID, 2).isNaN());
    }

    @Test
    void signedZeroIsOnePriceAndBothSignsFormALockedQuote() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        OrderBookLevel positive = new OrderBookLevel(factory.numOf(+0.0), factory.one());
        OrderBookLevel negative = new OrderBookLevel(factory.numOf(-0.0), factory.one());
        assertThrows(IllegalArgumentException.class,
                () -> new BaseOrderBook(factory, metadata, List.of(positive, negative), List.of()));
        BaseOrderBook locked = new BaseOrderBook(factory, metadata, List.of(positive), List.of(negative));
        assertTrue(locked.getSpread().isZero());
        assertNumEquals(0, locked.getMidPrice());
        assertNumEquals(0, locked.getImbalance(1));
    }

    @Test
    void midpointPreservesSubnormalPricesAndAvoidsFiniteQuoteSumOverflow() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        for (double price : new double[] { Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE }) {
            OrderBookLevel level = new OrderBookLevel(factory.numOf(price), factory.one());
            BaseOrderBook locked = new BaseOrderBook(factory, metadata, List.of(level), List.of(level));
            assertEquals(price, locked.getMidPrice().doubleValue());
        }
    }

    @Test
    void decimalContextsFollowFactoryProducesWithoutConvertingInputValues() {
        NumFactory owner = DecimalNumFactory.getInstance(16);
        NumFactory higherPrecision = DecimalNumFactory.getInstance(32);
        OrderBookLevel level = new OrderBookLevel(higherPrecision.numOf("1.12345678901234567890123456789"),
                higherPrecision.one());
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        BaseOrderBook book = new BaseOrderBook(owner, metadata, List.of(level), List.of());
        assertSame(owner, book.numFactory());
        assertSame(level.price(), book.getBestBid().orElseThrow().price());
        assertTrue(owner.produces(level.price()));
    }
}
