/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseOrderBook;
import org.ta4j.core.OrderBook;
import org.ta4j.core.OrderBookLevel;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.orderbook.OrderBookIndicator;
import org.ta4j.core.indicators.orderbook.OrderBookSpreadIndicator;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.orderbook.OrderBookReadyRule;
import org.ta4j.core.serialization.IndicatorSerializationException;

class RuleCopiesTest {

    private final BarSeries input = new BaseBarSeriesBuilder().withName("input").build();

    @Test
    void preservesExternalBookHistoryWhenConfigurationCannotReconstructIt() {
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        for (int second = 0; second < 2; second++) {
            input.barBuilder()
                    .timePeriod(Duration.ofSeconds(1))
                    .endTime(time.plusSeconds(second))
                    .openPrice(100)
                    .highPrice(100)
                    .lowPrice(100)
                    .closePrice(100)
                    .volume(1)
                    .add();
        }
        NumFactory factory = input.numFactory();
        OrderBook book = new BaseOrderBook(factory,
                new OrderBook.Metadata("venue", "instrument", "contracts", null, time, null,
                        OrderBook.State.SYNCHRONIZED, true, true),
                List.of(new OrderBookLevel(factory.numOf(99), factory.one())),
                List.of(new OrderBookLevel(factory.numOf(101), factory.one())));
        OrderBookIndicator books = new OrderBookIndicator(input, List.of(book), Duration.ZERO,
                OrderBookIndicator.BarTime.END);
        Rule source = new OrderBookReadyRule(books, 1)
                .and(new UnderIndicatorRule(new OrderBookSpreadIndicator(books), factory.numOf(3)));
        assertThrows(IndicatorSerializationException.class, source::toJson);
        assertThrows(IndicatorSerializationException.class, source::toExpression);
        Rule copied = RuleCopies.copy(source);
        assertTrue(copied.isSatisfied(0, null));
        assertFalse(copied.isSatisfied(1, null));
    }

    @Test
    void findsDirectlyReferencedSeries() {
        assertEquals("input", seriesName(new Holder("unrelated", input)));
    }

    @Test
    void findsIndicatorSeries() {
        assertEquals("input", seriesName(new Holder(null, new ClosePriceIndicator(input))));
    }

    @Test
    void findsSeriesInsideIterableAfterNonSeriesElements() {
        assertEquals("input", seriesName(new Holder(List.of("first", 2), List.of("third", input))));
    }

    @Test
    void findsSeriesInsideMapKeysAndValues() {
        Map<Object, Object> valueOnly = new LinkedHashMap<>();
        valueOnly.put("key", new ClosePriceIndicator(input));
        Map<Object, Object> keyOnly = new LinkedHashMap<>();
        keyOnly.put(input, "value");

        assertEquals("input", seriesName(new Holder(null, valueOnly)));
        assertEquals("input", seriesName(new Holder(null, keyOnly)));
    }

    @Test
    void findsSeriesInsideArrayAfterNonSeriesElements() {
        assertEquals("input", seriesName(new Holder(new Object[] { "first" }, new Object[] { "second", input })));
    }

    @Test
    void returnsEmptyWhenNoSeriesIsReachable() {
        assertTrue(RuleCopies.findBarSeries(new Holder(List.of("a"), Map.of("k", new Object[] { "v" }))).isEmpty());
    }

    @Test
    void doesNotTreatSeriesReachableOnlyThroughLoggerAsRuleInput() {
        assertTrue(RuleCopies.findBarSeries(new Holder(null, new SeriesHoldingLogger(input))).isEmpty());
    }

    private static String seriesName(Object value) {
        return RuleCopies.findBarSeries(value).orElseThrow().getName();
    }

    private record Holder(Object first, Object second) {
    }

    /** A logger whose state references a series, as a logging backend might. */
    private static final class SeriesHoldingLogger extends NOPLogger {

        private static final long serialVersionUID = 1L;

        private final transient BarSeries series;

        private SeriesHoldingLogger(BarSeries series) {
            this.series = series;
        }
    }
}
