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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.BooleanIndicatorRule;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OrRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.StopGainRule;
import org.ta4j.core.rules.StopLossRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.strategy.named.NamedStrategy;
import org.ta4j.core.strategy.named.NamedStrategyFixture;

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
            assertThat(child.getParameters()).containsEntry("satisfied", true);
            assertThat(child.getParameters()).containsKey("__args");
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
        assertThat(restored.shouldEnter(3, record)).isTrue();
        assertThat(restored.shouldExit(3, record)).isFalse();
    }

    @Test
    public void serializeIndicatorStrategyToJson() {
        BarSeries series = new MockBarSeriesBuilder().withData(23.1, 24.2, 25.3, 26.4).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(close, 2);
        SMAIndicator longSma = new SMAIndicator(close, 3);

        Rule entry = new CrossedUpIndicatorRule(shortSma, longSma);
        Rule exit = new StopLossRule(close, 1.5);

        Strategy strategy = new BaseStrategy("SMA Cross", entry, exit, 1);

        String json = strategy.toJson();

        assertThat(json).isEqualTo(
                """
                        {"type":"BaseStrategy","label":"SMA Cross","parameters":{"unstableBars":1},"rules":[{"type":"org.ta4j.core.rules.CrossedUpIndicatorRule","label":"entry","parameters":{"__args":[{"kind":"INDICATOR","name":"first","target":"org.ta4j.core.Indicator","label":"low"},{"kind":"INDICATOR","name":"second","target":"org.ta4j.core.Indicator","label":"up"}]},"rules":[{"type":"SMAIndicator","label":"low","parameters":{"barCount":2},"rules":[{"type":"ClosePriceIndicator","label":"indicator"}]},{"type":"SMAIndicator","label":"up","parameters":{"barCount":3},"rules":[{"type":"ClosePriceIndicator","label":"indicator"}]}]},{"type":"org.ta4j.core.rules.StopLossRule","label":"exit","parameters":{"lossPercentage":"1.5","__args":[{"kind":"INDICATOR","name":"priceIndicator","target":"org.ta4j.core.Indicator","label":"priceIndicator"},{"kind":"NUMBER","name":"lossPercentage","target":"java.lang.Number"}]},"rules":[{"type":"ClosePriceIndicator","label":"priceIndicator"}]}]}""");
    }

    @Test
    public void roundTripCompositeStrategy() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator slowSma = new SMAIndicator(close, 3);
        SMAIndicator fastSma = new SMAIndicator(close, 2);
        RSIIndicator shortRsi = new RSIIndicator(close, 3);
        RSIIndicator longRsi = new RSIIndicator(close, 5);

        Rule entry = new AndRule(new OverIndicatorRule(fastSma, slowSma), new UnderIndicatorRule(shortRsi, longRsi));
        Rule exit = new OrRule(new CrossedDownIndicatorRule(fastSma, slowSma), new StopGainRule(close, 4));

        Strategy original = new BaseStrategy("Composite", entry, exit, 2);
        String json = original.toJson();
        Strategy restored = Strategy.fromJson(series, json);

        assertThat(restored.getName()).isEqualTo("Composite");
        assertThat(restored.getUnstableBars()).isEqualTo(2);

        TradingRecord originalRecord = new BaseTradingRecord();
        TradingRecord restoredRecord = new BaseTradingRecord();
        assertThat(restored.shouldEnter(3, restoredRecord)).isEqualTo(original.shouldEnter(3, originalRecord));
        assertThat(restored.shouldExit(4, restoredRecord)).isEqualTo(original.shouldExit(4, originalRecord));
    }

    @Test
    public void roundTripBooleanRuleStrategy() {
        BarSeries series = new MockBarSeriesBuilder().withData(6, 5, 7, 9, 8).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator fast = new SMAIndicator(close, 2);
        SMAIndicator slow = new SMAIndicator(close, 3);
        CrossIndicator signal = new CrossIndicator(fast, slow);

        Rule entry = new BooleanIndicatorRule(signal);
        Rule exit = entry.negation();

        Strategy original = new BaseStrategy("Boolean", entry, exit, 0);
        Strategy restored = Strategy.fromJson(series, original.toJson());

        TradingRecord originalRecord = new BaseTradingRecord();
        TradingRecord restoredRecord = new BaseTradingRecord();
        assertThat(restored.shouldEnter(1, restoredRecord)).isEqualTo(original.shouldEnter(1, originalRecord));
        assertThat(restored.shouldExit(1, restoredRecord)).isEqualTo(original.shouldExit(1, originalRecord));
        assertThat(restored.shouldExit(3, restoredRecord)).isEqualTo(original.shouldExit(3, originalRecord));
    }

    @Test
    public void describeNamedStrategy() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        ToggleNamedStrategy strategy = ToggleNamedStrategy.create(series, true, false, 4);

        ComponentDescriptor descriptor = StrategySerialization.describe(strategy);

        assertThat(descriptor.getType()).isEqualTo(ToggleNamedStrategy.class.getName());
        assertThat(descriptor.getLabel()).isEqualTo("ToggleNamedStrategy_true_false_u4");
        assertThat(descriptor.getParameters()).containsEntry("unstableBars", 4);
        assertThat(descriptor.getParameters()).containsEntry("args", List.of("true", "false"));
        assertThat(descriptor.getChildren()).isEmpty();
    }

    @Test
    public void roundTripNamedStrategy() {
        BarSeries series = new MockBarSeriesBuilder().withData(11, 12, 13, 14).build();
        ToggleNamedStrategy original = ToggleNamedStrategy.create(series, true, false, 3);

        String json = original.toJson();
        Strategy restored = Strategy.fromJson(series, json);

        assertThat(restored).isInstanceOf(ToggleNamedStrategy.class);
        assertThat(restored.getUnstableBars()).isEqualTo(3);
        assertThat(restored.toString()).isEqualTo("ToggleNamedStrategy_true_false_u3");

        TradingRecord record = new BaseTradingRecord();
        assertThat(restored.getName()).isEqualTo(original.getName());

        TradingRecord originalRecord = new BaseTradingRecord();
        assertThat(restored.shouldEnter(2, record)).isEqualTo(original.shouldEnter(2, originalRecord));
        assertThat(restored.shouldExit(2, record)).isEqualTo(original.shouldExit(2, originalRecord));

        assertThat(restored.shouldEnter(3, record)).isEqualTo(original.shouldEnter(3, originalRecord));
        assertThat(restored.shouldExit(3, record)).isEqualTo(original.shouldExit(3, originalRecord));
    }

    @Test
    public void roundTripNamedStrategyFixture() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(3, 6, 9, 12, 15)
                .build();
        NamedStrategyFixture.resetConstructionCounters();

        NamedStrategyFixture original = NamedStrategyFixture.create(series, series.numFactory().numOf(Double.NaN), 4);

        String json = original.toJson();
        Strategy restored = Strategy.fromJson(series, json);

        assertThat(restored).isInstanceOf(NamedStrategyFixture.class);
        NamedStrategyFixture reconstructed = (NamedStrategyFixture) restored;

        assertThat(NamedStrategyFixture.typedConstructionCount()).isEqualTo(2);
        assertThat(NamedStrategyFixture.varargsConstructionCount()).isEqualTo(1);
        assertThat(reconstructed.isDelegated()).isTrue();
        assertThat(Double.isNaN(reconstructed.getThreshold().doubleValue())).isTrue();
        assertThat(reconstructed.toString()).isEqualTo(original.toString());
        assertThat(reconstructed.getUnstableBars()).isEqualTo(original.getUnstableBars());
        assertThat(reconstructed.toDescriptor().getParameters()).containsEntry("args", List.of("NaN"));
        assertThat(json).contains("\"NaN\"");
    }

    @Test
    public void roundTripMultiLevelNamedStrategy() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4).build();
        MultiLevelToggleNamedStrategy.resetConstructionCounters();

        MultiLevelToggleNamedStrategy original = MultiLevelToggleNamedStrategy.create(series, true, false, 2);

        String json = original.toJson();
        Strategy restored = Strategy.fromJson(series, json);

        assertThat(restored).isInstanceOf(MultiLevelToggleNamedStrategy.class);
        assertThat(MultiLevelToggleNamedStrategy.typedConstructionCount()).isEqualTo(1);
        assertThat(MultiLevelToggleNamedStrategy.varargsConstructionCount()).isEqualTo(1);
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getUnstableBars()).isEqualTo(original.getUnstableBars());
        assertThat(restored.toString()).isEqualTo(original.toString());
    }

    @Test
    public void namedStrategyArgumentMismatchThrows() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(2, 4, 6).build();
        NamedStrategyFixture.resetConstructionCounters();
        NamedStrategyFixture strategy = NamedStrategyFixture.create(series, series.numFactory().numOf(Double.NaN), 1);

        ComponentDescriptor descriptor = strategy.toDescriptor();
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>(descriptor.getParameters());
        parameters.put("args", List.of("NaN", "extra"));

        ComponentDescriptor mutated = ComponentDescriptor.builder()
                .withType(descriptor.getType())
                .withLabel(descriptor.getLabel())
                .withParameters(parameters)
                .build();

        String json = ComponentSerialization.toJson(mutated);

        assertThatThrownBy(() -> Strategy.fromJson(series, json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Named strategy argument count mismatch");
    }

    @Test
    public void namedStrategyLabelFormatMismatchThrows() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(5, 10, 15).build();
        NamedStrategyFixture.resetConstructionCounters();
        NamedStrategyFixture strategy = NamedStrategyFixture.create(series, series.numFactory().numOf(Double.NaN), 2);

        ComponentDescriptor descriptor = strategy.toDescriptor();
        ComponentDescriptor mutated = ComponentDescriptor.builder()
                .withType(descriptor.getType())
                .withLabel("FixtureNamedStrategy_NaN")
                .withParameters(descriptor.getParameters())
                .build();

        String json = ComponentSerialization.toJson(mutated);

        assertThatThrownBy(() -> Strategy.fromJson(series, json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Descriptor label does not match strategy type");
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
    }

    private abstract static class AbstractToggleNamedStrategy extends NamedStrategy {

        protected AbstractToggleNamedStrategy(BarSeries series, String name, boolean entrySatisfied, boolean exitSatisfied,
                int unstableBars) {
            super(name, new SerializableRule(entrySatisfied), new SerializableRule(exitSatisfied), unstableBars,
                    List.of(Boolean.toString(entrySatisfied), Boolean.toString(exitSatisfied)));
        }

        protected AbstractToggleNamedStrategy(BarSeries series, String... parameters) {
            super(series, parameters);
        }
    }

    private static final class MultiLevelToggleNamedStrategy extends AbstractToggleNamedStrategy {

        private static final AtomicInteger TYPED_CONSTRUCTIONS = new AtomicInteger();
        private static final AtomicInteger VARARGS_CONSTRUCTIONS = new AtomicInteger();

        static {
            registerParser(MultiLevelToggleNamedStrategy.class, MultiLevelToggleNamedStrategy::parseTokens);
        }

        private static Specification parseTokens(BarSeries series, List<String> tokens) {
            if (tokens.size() != 3) {
                throw new IllegalArgumentException("MultiLevelToggleNamedStrategy expects [entry, exit, unstable]");
            }
            boolean entry = Boolean.parseBoolean(tokens.get(0));
            boolean exit = Boolean.parseBoolean(tokens.get(1));
            int unstable = Integer.parseInt(tokens.get(2));
            return specification("MultiLevelToggle", new SerializableRule(entry), new SerializableRule(exit), unstable,
                    tokens.subList(0, 2));
        }

        private MultiLevelToggleNamedStrategy(BarSeries series, boolean entrySatisfied, boolean exitSatisfied,
                int unstableBars) {
            super(series, "MultiLevelToggle", entrySatisfied, exitSatisfied, unstableBars);
            TYPED_CONSTRUCTIONS.incrementAndGet();
        }

        public MultiLevelToggleNamedStrategy(BarSeries series, String... parameters) {
            super(series, parameters);
            VARARGS_CONSTRUCTIONS.incrementAndGet();
        }

        static MultiLevelToggleNamedStrategy create(BarSeries series, boolean entrySatisfied, boolean exitSatisfied,
                int unstableBars) {
            return new MultiLevelToggleNamedStrategy(series, entrySatisfied, exitSatisfied, unstableBars);
        }

        static void resetConstructionCounters() {
            TYPED_CONSTRUCTIONS.set(0);
            VARARGS_CONSTRUCTIONS.set(0);
        }

        static int typedConstructionCount() {
            return TYPED_CONSTRUCTIONS.get();
        }

        static int varargsConstructionCount() {
            return VARARGS_CONSTRUCTIONS.get();
        }
    }

    private static final class ToggleNamedStrategy extends NamedStrategy {

        static {
            registerParser(ToggleNamedStrategy.class, ToggleNamedStrategy::parseTokens);
        }

        private static Specification parseTokens(BarSeries series, List<String> tokens) {
            if (tokens.size() != 3) {
                throw new IllegalArgumentException("ToggleNamedStrategy expects [entry, exit, unstable]");
            }
            boolean entry = Boolean.parseBoolean(tokens.get(0));
            boolean exit = Boolean.parseBoolean(tokens.get(1));
            int unstable = Integer.parseInt(tokens.get(2));
            return specification("Toggle", new SerializableRule(entry), new SerializableRule(exit), unstable,
                    tokens.subList(0, 2));
        }

        protected ToggleNamedStrategy(BarSeries series, boolean entrySatisfied, boolean exitSatisfied,
                int unstableBars) {
            super("Toggle", new SerializableRule(entrySatisfied), new SerializableRule(exitSatisfied), unstableBars,
                    List.of(Boolean.toString(entrySatisfied), Boolean.toString(exitSatisfied)));
        }

        public ToggleNamedStrategy(BarSeries series, String... parameters) {
            super(series, parameters);
        }

        static ToggleNamedStrategy create(BarSeries series, boolean entrySatisfied, boolean exitSatisfied,
                int unstableBars) {
            return new ToggleNamedStrategy(series, entrySatisfied, exitSatisfied, unstableBars);
        }
    }
}
