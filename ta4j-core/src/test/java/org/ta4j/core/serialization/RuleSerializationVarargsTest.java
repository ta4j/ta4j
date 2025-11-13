/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.rules.DayOfWeekRule;
import org.ta4j.core.rules.FixedRule;

public class RuleSerializationVarargsTest {

    @Test
    public void serializeAndRebuildNumericVarargs() {
        Rule rule = new FixedRule(1, 3, 5);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        Object arrayValues = null;
        for (Map.Entry<String, Object> entry : descriptor.getParameters().entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof List<?> list)) {
                continue;
            }
            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                continue;
            }
            arrayValues = list;
            break;
        }

        assertThat(arrayValues).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Integer> indexes = (List<Integer>) arrayValues;
        assertThat(indexes).containsExactly(1, 3, 5);

        // __args metadata is no longer serialized - check that parameters contain the
        // array values
        assertThat(descriptor.getParameters()).containsKey("indexes");

        Rule reconstructed = RuleSerialization.fromDescriptor(new MockBarSeriesBuilder().build(), descriptor);
        assertThat(reconstructed).isInstanceOf(FixedRule.class);
        assertThat(reconstructed.isSatisfied(1)).isTrue();
        assertThat(reconstructed.isSatisfied(2)).isFalse();
        assertThat(reconstructed.isSatisfied(5)).isTrue();
    }

    @Test
    public void serializeAndRebuildEnumVarargs() {
        var series = new MockBarSeriesBuilder().build();
        series.barBuilder().endTime(Instant.parse("2024-01-01T12:00:00Z")).add(); // Monday
        series.barBuilder().endTime(Instant.parse("2024-01-02T12:00:00Z")).add(); // Tuesday
        series.barBuilder().endTime(Instant.parse("2024-01-03T12:00:00Z")).add(); // Wednesday
        series.barBuilder().endTime(Instant.parse("2024-01-04T12:00:00Z")).add(); // Thursday
        series.barBuilder().endTime(Instant.parse("2024-01-05T12:00:00Z")).add(); // Friday

        DateTimeIndicator dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        DayOfWeekRule rule = new DayOfWeekRule(dateTime, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // __args metadata is no longer serialized - check that parameters contain the
        // enum array values
        assertThat(descriptor.getParameters()).containsKey("daysOfWeek");

        List<String> days = null;
        for (Map.Entry<String, Object> entry : descriptor.getParameters().entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof List<?> list)) {
                continue;
            }
            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                continue;
            }
            boolean allStrings = true;
            for (Object element : list) {
                if (element != null && !(element instanceof String)) {
                    allStrings = false;
                    break;
                }
            }
            if (allStrings) {
                @SuppressWarnings("unchecked")
                List<String> cast = (List<String>) list;
                days = cast;
                break;
            }
        }

        assertThat(days).isNotNull();
        assertThat(days).containsExactlyInAnyOrder("WEDNESDAY", "FRIDAY");

        Rule reconstructed = RuleSerialization.fromDescriptor(series, descriptor);
        assertThat(reconstructed).isInstanceOf(DayOfWeekRule.class);
        Set<DayOfWeek> expected = Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        Set<DayOfWeek> actual;
        try {
            var field = DayOfWeekRule.class.getDeclaredField("daysOfWeekSet");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<DayOfWeek> value = (Set<DayOfWeek>) field.get(reconstructed);
            actual = value;
        } catch (IllegalAccessException | NoSuchFieldException ex) {
            throw new AssertionError("Unable to inspect reconstructed rule", ex);
        }
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }
}
