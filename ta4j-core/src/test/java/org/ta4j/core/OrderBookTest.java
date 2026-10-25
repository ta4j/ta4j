/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.math.MathContext;
import java.math.RoundingMode;
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
        BaseOrderBook subnormal = new BaseOrderBook(factory, metadata,
                List.of(new OrderBookLevel(factory.numOf(Double.MIN_VALUE), factory.one())),
                List.of(new OrderBookLevel(factory.numOf(4 * Double.MIN_VALUE), factory.one())));
        assertEquals(2 * Double.MIN_VALUE, subnormal.getMidPrice().doubleValue());
    }

    @Test
    public void decimalMidpointsAvoidRoundingTheQuoteSumBeforeDivision() {
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        for (RoundingMode rounding : List.of(RoundingMode.HALF_UP, RoundingMode.UNNECESSARY)) {
            NumFactory factory = DecimalNumFactory.getInstance(new MathContext(1, rounding));
            for (int price : new int[] { 9, -9 }) {
                OrderBookLevel level = new OrderBookLevel(factory.numOf(price), factory.one());
                BaseOrderBook locked = new BaseOrderBook(factory, metadata, List.of(level), List.of(level));
                assertNumEquals(level.price(), locked.getMidPrice());
            }
        }
        NumFactory factory = DecimalNumFactory.getInstance(new MathContext(1, RoundingMode.HALF_UP));
        BaseOrderBook positive = new BaseOrderBook(factory, metadata,
                List.of(new OrderBookLevel(factory.numOf(8), factory.one())),
                List.of(new OrderBookLevel(factory.numOf(9), factory.one())));
        BaseOrderBook negative = new BaseOrderBook(factory, metadata,
                List.of(new OrderBookLevel(factory.numOf(-9), factory.one())),
                List.of(new OrderBookLevel(factory.numOf(-8), factory.one())));
        assertNumEquals(9, positive.getMidPrice());
        assertNumEquals(-9, negative.getMidPrice());
    }

    @Test
    public void decimalMidpointsRoundOnlyAfterAveragingUnequalQuotes() {
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        for (RoundingMode rounding : RoundingMode.values()) {
            NumFactory factory = DecimalNumFactory.getInstance(new MathContext(1, rounding));
            BaseOrderBook positive = new BaseOrderBook(factory, metadata,
                    List.of(new OrderBookLevel(factory.numOf(6), factory.one())),
                    List.of(new OrderBookLevel(factory.numOf(80), factory.one())));
            BaseOrderBook negative = new BaseOrderBook(factory, metadata,
                    List.of(new OrderBookLevel(factory.numOf(-80), factory.one())),
                    List.of(new OrderBookLevel(factory.numOf(-6), factory.one())));
            if (rounding == RoundingMode.UNNECESSARY) {
                assertTrue(positive.getMidPrice().isNaN());
                assertTrue(negative.getMidPrice().isNaN());
            } else {
                int positiveMidpoint = rounding == RoundingMode.UP || rounding == RoundingMode.CEILING ? 50 : 40;
                int negativeMidpoint = rounding == RoundingMode.UP || rounding == RoundingMode.FLOOR ? -50 : -40;
                assertNumEquals(positiveMidpoint, positive.getMidPrice());
                assertNumEquals(negativeMidpoint, negative.getMidPrice());
            }
        }
    }

    @Test
    public void decimalMidpointsPreserveTheGreaterQuoteOrFactoryPrecision() {
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        NumFactory oneDigit = DecimalNumFactory.getInstance(new MathContext(1, RoundingMode.UNNECESSARY));
        NumFactory twoDigits = DecimalNumFactory.getInstance(new MathContext(2, RoundingMode.UNNECESSARY));
        BaseOrderBook preciseQuotes = new BaseOrderBook(oneDigit, metadata,
                List.of(new OrderBookLevel(twoDigits.numOf(6), twoDigits.one())),
                List.of(new OrderBookLevel(oneDigit.numOf(80), oneDigit.one())));
        BaseOrderBook preciseFactory = new BaseOrderBook(twoDigits, metadata,
                List.of(new OrderBookLevel(oneDigit.numOf(6), oneDigit.one())),
                List.of(new OrderBookLevel(oneDigit.numOf(80), oneDigit.one())));
        assertNumEquals(43, preciseQuotes.getMidPrice());
        assertNumEquals(43, preciseFactory.getMidPrice());
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

    @Test
    void overflowingSpreadIsUnavailableForFiniteSignedQuotes() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        OrderBookLevel bid = new OrderBookLevel(factory.numOf(-Double.MAX_VALUE), factory.one());
        OrderBookLevel ask = new OrderBookLevel(factory.numOf(Double.MAX_VALUE), factory.one());
        BaseOrderBook book = new BaseOrderBook(factory, metadata, List.of(bid), List.of(ask));
        assertTrue(book.getSpread().isNaN());
        assertTrue(book.getMidPrice().isZero());
    }

    @Test
    void unlimitedDecimalImbalanceIsNaNOnlyWhenRatioCannotBeRepresented() {
        NumFactory factory = DecimalNumFactory.getInstance(MathContext.UNLIMITED);
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        BaseOrderBook nonterminating = new BaseOrderBook(factory, metadata,
                List.of(new OrderBookLevel(factory.one(), factory.two())),
                List.of(new OrderBookLevel(factory.two(), factory.one())));
        assertTrue(nonterminating.getImbalance(1).isNaN());
        BaseOrderBook terminating = new BaseOrderBook(factory, metadata,
                List.of(new OrderBookLevel(factory.one(), factory.three())),
                List.of(new OrderBookLevel(factory.two(), factory.one())));
        assertNumEquals(factory.numOf("0.5"), terminating.getImbalance(1));
        assertSame(factory, nonterminating.numFactory());
    }

    @Test
    void exactDecimalContextsDoNotLeakUnrepresentableMeasurementExceptions() {
        NumFactory factory = DecimalNumFactory.getInstance(new MathContext(1, RoundingMode.UNNECESSARY));
        Metadata metadata = new Metadata("V", "I", "base", null, TIME, null, State.SYNCHRONIZED, true, true);
        BaseOrderBook spread = new BaseOrderBook(factory, metadata,
                List.of(new OrderBookLevel(factory.numOf("-0.9"), factory.one())),
                List.of(new OrderBookLevel(factory.one(), factory.one())));
        assertTrue(spread.getSpread().isNaN());
        assertNumEquals(factory.numOf("0.05"), spread.getMidPrice());
        BaseOrderBook midpoint = new BaseOrderBook(factory, metadata,
                List.of(new OrderBookLevel(factory.one(), factory.two())),
                List.of(new OrderBookLevel(factory.two(), factory.one())));
        assertTrue(midpoint.getMidPrice().isNaN());
        assertTrue(midpoint.getImbalance(1).isNaN());
        BaseOrderBook depth = new BaseOrderBook(factory, metadata,
                List.of(new OrderBookLevel(factory.zero(), factory.numOf(9)),
                        new OrderBookLevel(factory.minusOne(), factory.two())),
                List.of(new OrderBookLevel(factory.one(), factory.one())));
        assertTrue(depth.getDepth(OrderBook.Side.BID, 2).isNaN());
        assertTrue(depth.getImbalance(2).isNaN());
    }
}
