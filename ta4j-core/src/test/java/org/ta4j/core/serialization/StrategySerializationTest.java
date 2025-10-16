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

import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.serialization.ComponentDescriptor;

public class StrategySerializationTest {

    @Test
    public void describeStrategy() {
        Rule entry = new SerializableRule(true);
        Rule exit = new SerializableRule(false);

        Strategy strategy = new BaseStrategy("Serializable", entry, exit, 3);

        ComponentDescriptor descriptor = strategy.toDescriptor();

        assertThat(descriptor.getType()).isEqualTo("BaseStrategy");
        assertThat(descriptor.getLabel()).isEqualTo("Serializable");
        assertThat(descriptor.getParameters()).containsEntry("unstableBars", 3);
        assertThat(descriptor.getChildren()).hasSize(2);
        assertThat(descriptor.getChildren()).anySatisfy(child -> {
            assertThat(child.getLabel()).isEqualTo("entry");
            assertThat(child.getType()).isEqualTo(SerializableRule.class.getName());
        });
    }

    @Test
    public void roundTripBaseStrategy() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4).build();
        Strategy original = new BaseStrategy("RoundTrip", new SerializableRule(true), new SerializableRule(false), 2);

        String json = original.toJson();
        Strategy restored = Strategy.fromJson(series, json);

        assertThat(restored).isInstanceOf(BaseStrategy.class);
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getUnstableBars()).isEqualTo(original.getUnstableBars());
        assertThat(restored.getEntryRule().getName()).isEqualTo(original.getEntryRule().getName());
        assertThat(restored.getExitRule().getName()).isEqualTo(original.getExitRule().getName());

        TradingRecord record = new BaseTradingRecord();
        assertThat(restored.shouldEnter(2, record)).isTrue();
        assertThat(restored.shouldExit(2, record)).isFalse();
    }

    private static final class SerializableRule extends org.ta4j.core.rules.AbstractRule {

        private final boolean satisfied;

        private SerializableRule(boolean satisfied) {
            this.satisfied = satisfied;
        }

        @Override
        protected String createDefaultName() {
            ComponentDescriptor descriptor = ComponentDescriptor.builder()
                    .withType(getClass().getName())
                    .withParameters(Map.of("satisfied", satisfied))
                    .build();
            return ComponentSerialization.toJson(descriptor);
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            return satisfied;
        }

        static Rule fromDescriptor(ComponentDescriptor descriptor) {
            Object value = descriptor.getParameters().get("satisfied");
            boolean satisfied = value instanceof Boolean ? (Boolean) value
                    : Boolean.parseBoolean(String.valueOf(value));
            return new SerializableRule(satisfied);
        }
    }
}
