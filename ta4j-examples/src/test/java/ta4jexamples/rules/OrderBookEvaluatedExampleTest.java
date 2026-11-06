/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseOrderBook;
import org.ta4j.core.BaseOrderBookBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.OrderBook;
import org.ta4j.core.OrderBookLevel;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.orderbook.OrderBookIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.RuleCopies;
import org.ta4j.core.serialization.IndicatorSerializationException;

import ta4jexamples.rules.OrderBookEvaluatedExample.LiquidityEvaluation;
import ta4jexamples.rules.OrderBookEvaluatedExample.OrderBookLiquidityRule;

class OrderBookEvaluatedExampleTest {
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final NumFactory FACTORY = DecimalNumFactory.getInstance();

    private static BarSeries series() {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(FACTORY).build();
        for (int second = 0; second < 4; second++) {
            series.barBuilder()
                    .timePeriod(Duration.ofSeconds(1))
                    .endTime(TIME.plusSeconds(second))
                    .openPrice(100)
                    .highPrice(101)
                    .lowPrice(99)
                    .closePrice(100)
                    .volume(1)
                    .add();
        }
        return series;
    }

    private static OrderBook.Metadata metadata(int second, OrderBook.State state, boolean complete) {
        return new OrderBook.Metadata("venue", "instrument", "base", null, TIME.plusSeconds(second), null, state,
                complete, complete);
    }

    private static OrderBookLevel level(int price, int amount) {
        return new OrderBookLevel(FACTORY.numOf(price), FACTORY.numOf(amount));
    }

    private static BaseOrderBook book(int second, OrderBook.State state, boolean complete, int bid, int ask) {
        return new BaseOrderBook(FACTORY, metadata(second, state, complete), List.of(level(bid, 8)),
                List.of(level(ask, 2)));
    }

    @Test
    void capturedDecisionSurvivesBuilderAndSourceMutationWithoutRereading() {
        BaseOrderBookBuilder builder = new BaseOrderBookBuilder(FACTORY, "venue", "instrument", "base").replace(
                metadata(0, OrderBook.State.SYNCHRONIZED, true), List.of(level(99, 8)), List.of(level(101, 2)));
        CountingSource source = new CountingSource(series(), Optional.of(builder.build()));
        OrderBookLiquidityRule rule = new OrderBookLiquidityRule(source, 1, FACTORY.two(), FACTORY.numOf("0.6"));
        LiquidityEvaluation captured = rule.evaluate(1, new BaseTradingRecord());
        assertEquals(1, source.reads);
        assertEquals(1, source.lastIndex);
        assertEquals(FACTORY.two(), captured.spread());
        assertEquals(FACTORY.numOf("0.6"), captured.imbalance());
        builder.apply(metadata(1, OrderBook.State.SYNCHRONIZED, true), List.of(level(99, 1)), List.of(level(101, 9)));
        source.value = Optional.of(builder.build());
        assertTrue(rule.toBoolean(captured));
        assertTrue(rule.toBoolean(captured));
        assertEquals(1, source.reads);
        assertEquals(FACTORY.numOf(8), captured.book().orElseThrow().getBestBid().orElseThrow().amount());
        assertFalse(rule.isSatisfied(1, null));
        assertEquals(2, source.reads);
    }

    @Test
    void missingStaleGapCrossedAndInsufficientDepthProjectToFalse() {
        BarSeries series = series();
        OrderBook good = book(1, OrderBook.State.SYNCHRONIZED, true, 99, 101);
        OrderBook gap = book(2, OrderBook.State.UNSYNCHRONIZED, true, 99, 101);
        OrderBookIndicator source = new OrderBookIndicator(series, List.of(good, gap), Duration.ZERO,
                OrderBookIndicator.BarTime.END);
        OrderBookLiquidityRule rule = new OrderBookLiquidityRule(source, 1, FACTORY.two(), FACTORY.zero());
        assertTrue(rule.evaluate(0).book().isEmpty());
        assertFalse(rule.isSatisfied(0));
        assertTrue(rule.isSatisfied(1));
        LiquidityEvaluation invalidated = rule.evaluate(2);
        assertSame(gap, invalidated.book().orElseThrow());
        assertFalse(invalidated.usable());
        assertTrue(invalidated.spread().isNaN());
        assertFalse(rule.toBoolean(invalidated));
        assertTrue(rule.evaluate(3).book().isEmpty());
        assertFalse(rule.isSatisfied(3));
        assertThrows(IndexOutOfBoundsException.class, () -> rule.evaluate(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> rule.evaluate(4));
        for (OrderBook invalid : List.of(book(0, OrderBook.State.SYNCHRONIZED, true, 102, 101),
                book(0, OrderBook.State.SYNCHRONIZED, false, 99, 101),
                new BaseOrderBook(FACTORY, metadata(0, OrderBook.State.SYNCHRONIZED, true), List.of(), List.of()))) {
            CountingSource changing = new CountingSource(series, Optional.of(invalid));
            OrderBookLiquidityRule unavailable = new OrderBookLiquidityRule(changing, 2, FACTORY.two(), FACTORY.zero());
            assertFalse(unavailable.evaluate(0).usable());
            assertFalse(unavailable.isSatisfied(0));
        }
    }

    @Test
    void lockedBalancedQuotesSatisfyInclusiveZeroThresholdsAndWarmupRespectsRetention() {
        BarSeries series = series();
        BaseOrderBook locked = new BaseOrderBook(FACTORY, metadata(0, OrderBook.State.SYNCHRONIZED, true),
                List.of(level(100, 2)), List.of(level(100, 2)));
        CountingSource source = new CountingSource(series, Optional.of(locked));
        OrderBookLiquidityRule rule = new OrderBookLiquidityRule(source, 1, FACTORY.zero(), FACTORY.zero());
        assertTrue(rule.isSatisfied(0));
        source.unstable = 1;
        source.reads = 0;
        series.setMaximumBarCount(2);
        assertFalse(rule.evaluate(2).usable());
        assertEquals(0, source.reads);
        assertTrue(rule.isSatisfied(3));
        assertEquals(1, source.reads);
    }

    @Test
    void ordinaryCompositionPreservesOperandOrderAndShortCircuiting() {
        CountingSource source = new CountingSource(series(),
                Optional.of(book(0, OrderBook.State.SYNCHRONIZED, true, 99, 101)));
        Rule rich = new OrderBookLiquidityRule(source, 1, FACTORY.two(), FACTORY.zero());
        BaseTradingRecord record = new BaseTradingRecord();
        AtomicInteger legacyCalls = new AtomicInteger();
        Rule legacy = (index, receivedRecord) -> {
            assertEquals(1, index);
            assertSame(record, receivedRecord);
            legacyCalls.incrementAndGet();
            return true;
        };
        assertTrue(rich.and(legacy).isSatisfied(1, record));
        assertTrue(legacy.and(rich).isSatisfied(1, record));
        assertEquals(2, source.reads);
        assertEquals(1, source.lastIndex);
        assertEquals(2, legacyCalls.get());
        assertFalse(BooleanRule.FALSE.and(rich).isSatisfied(1, record));
        assertEquals(2, source.reads);
        source.value = Optional.empty();
        assertFalse(rich.and(legacy).isSatisfied(1, record));
        assertEquals(2, legacyCalls.get());
        assertTrue(BooleanRule.TRUE.or(rich).isSatisfied(1, record));
        assertEquals(3, source.reads);
    }

    @Test
    void externalHistoryCannotSilentlyDisappearDuringSerializationOrCopy() {
        OrderBookIndicator source = new OrderBookIndicator(series(),
                List.of(book(1, OrderBook.State.SYNCHRONIZED, true, 99, 101)), Duration.ZERO,
                OrderBookIndicator.BarTime.END);
        Rule rule = new OrderBookLiquidityRule(source, 1, FACTORY.two(), FACTORY.zero());
        assertThrows(IndicatorSerializationException.class, rule::toJson);
        assertThrows(IndicatorSerializationException.class, rule::toExpression);
        Rule copied = RuleCopies.copy(rule);
        assertFalse(copied.isSatisfied(0));
        assertTrue(copied.isSatisfied(1));
        assertFalse(copied.isSatisfied(2));
    }

    @Test
    void programmingErrorsAreNotMappedToMissingMarketData() {
        CountingSource source = new CountingSource(series(), Optional.empty());
        assertThrows(NullPointerException.class,
                () -> new OrderBookLiquidityRule(null, 1, FACTORY.one(), FACTORY.zero()));
        assertThrows(NullPointerException.class, () -> new OrderBookLiquidityRule(source, 1, null, FACTORY.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBookLiquidityRule(source, 0, FACTORY.one(), FACTORY.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBookLiquidityRule(source, 1, NaN.NaN, FACTORY.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBookLiquidityRule(source, 1, FACTORY.numOf(-1), FACTORY.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBookLiquidityRule(source, 1, FACTORY.one(), FACTORY.two()));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBookLiquidityRule(source, 1, FACTORY.one(), FACTORY.numOf(-2)));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBookLiquidityRule(source, 1, DoubleNumFactory.getInstance().one(), FACTORY.zero()));
        source.value = null;
        OrderBookLiquidityRule rule = new OrderBookLiquidityRule(source, 1, FACTORY.one(), FACTORY.zero());
        assertThrows(NullPointerException.class, () -> rule.evaluate(0));
    }

    private static final class CountingSource extends AbstractIndicator<Optional<OrderBook>> {
        private Optional<OrderBook> value;
        private int reads;
        private int lastIndex;
        private int unstable;

        private CountingSource(BarSeries series, Optional<OrderBook> value) {
            super(series);
            this.value = value;
        }

        @Override
        public Optional<OrderBook> getValue(int index) {
            reads++;
            lastIndex = index;
            return value;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstable;
        }
    }
}
