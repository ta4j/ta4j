/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

class RuleCopiesTest {

    private final BarSeries input = new BaseBarSeriesBuilder().withName("input").build();

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
