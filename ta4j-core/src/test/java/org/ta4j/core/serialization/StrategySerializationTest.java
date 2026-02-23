/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;
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

    /**
     * Cleans up static registrations made by test fixtures to prevent interference
     * between test runs, especially when tests execute in parallel or the test
     * suite runs multiple times in the same JVM.
     */
    @After
    public void tearDown() {
        // Unregister test-specific named strategies that use static initializers
        NamedStrategy.unregisterImplementation(ToggleNamedStrategy.class);
        NamedStrategy.unregisterImplementation(MultiLevelToggleNamedStrategy.class);
        NamedStrategy.unregisterImplementation(AutoScanNamedStrategy.class);
    }

    @Test
    public void describeStrategy() {
        Rule entry = new SerializableRule(true);
        Rule exit = new SerializableRule(false);

        Strategy strategy = new BaseStrategy("Serializable", entry, exit, 3);

        ComponentDescriptor descriptor = strategy.toDescriptor();

        assertThat(descriptor.getType()).isEqualTo("BaseStrategy");
        assertThat(descriptor.getLabel()).isEqualTo("Serializable");
        assertThat(descriptor.getParameters()).containsEntry("unstableBars", 3);
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents()).anySatisfy(child -> {
            assertThat(child.getLabel()).isEqualTo("entry");
            // SerializableRule is an inner class, so it uses fully qualified name
            assertThat(child.getType()).isEqualTo(SerializableRule.class.getName());
            assertThat(child.getParameters()).containsEntry("satisfied", true);
            // __args metadata is no longer serialized
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
    public void roundTripBaseStrategyWithShortStartingType() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4).build();
        Strategy original = new BaseStrategy("RoundTripShort", new SerializableRule(true), new SerializableRule(false),
                2, TradeType.SELL);

        ComponentDescriptor descriptor = original.toDescriptor();
        assertThat(descriptor.getParameters()).containsEntry("startingType", "SELL");

        String json = original.toJson();
        Strategy restored = Strategy.fromJson(series, json);

        assertThat(restored).isInstanceOf(BaseStrategy.class);
        assertThat(restored.getStartingType()).isEqualTo(TradeType.SELL);
        assertThat(restored.getUnstableBars()).isEqualTo(original.getUnstableBars());
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
                        {"type":"BaseStrategy","label":"SMA Cross","parameters":{"unstableBars":1},"rules":[{"type":"CrossedUpIndicatorRule","label":"entry","components":[{"type":"SMAIndicator","parameters":{"barCount":2},"components":[{"type":"ClosePriceIndicator"}]},{"type":"SMAIndicator","parameters":{"barCount":3},"components":[{"type":"ClosePriceIndicator"}]}]},{"type":"StopLossRule","label":"exit","parameters":{"lossPercentage":"1.5"},"components":[{"type":"ClosePriceIndicator"}]}]}""");
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

        assertThat(descriptor.getType()).isEqualTo(NamedStrategy.SERIALIZED_TYPE);
        assertThat(descriptor.getLabel()).isEqualTo("ToggleNamedStrategy_true_false_u4");
        assertThat(descriptor.getParameters()).isEmpty();
        assertThat(descriptor.getComponents()).isEmpty();
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
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(3, 6, 9, 12, 15)
                .build();
        NamedStrategyFixture.resetConstructionCounters();

        NamedStrategyFixture original = NamedStrategyFixture.create(series, series.numFactory().numOf(Double.NaN), 4);

        String json = original.toJson();
        Strategy restored = Strategy.fromJson(series, json);

        assertThat(restored).isInstanceOf(NamedStrategyFixture.class);
        NamedStrategyFixture reconstructed = (NamedStrategyFixture) restored;

        assertThat(NamedStrategyFixture.typedConstructionCount()).isEqualTo(1);
        assertThat(NamedStrategyFixture.varargsConstructionCount()).isEqualTo(1);
        assertThat(reconstructed.isDelegated()).isTrue();
        assertThat(Double.isNaN(reconstructed.getThreshold().doubleValue())).isTrue();
        assertThat(reconstructed.toString()).isEqualTo(original.toString());
        assertThat(reconstructed.getUnstableBars()).isEqualTo(original.getUnstableBars());
        assertThat(reconstructed.toDescriptor().getLabel()).isEqualTo(original.toDescriptor().getLabel());
        assertThat(json).contains("NaN");
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
    public void namedStrategyMissingRegistrationThrows() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        ComponentDescriptor descriptor = ComponentDescriptor.builder()
                .withType(NamedStrategy.SERIALIZED_TYPE)
                .withLabel("UnregisteredStrategy_param")
                .build();

        assertThatThrownBy(() -> StrategySerialization.fromDescriptor(series, descriptor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown named strategy");
    }

    @Test
    public void namedStrategyInvalidLabelThrows() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        ComponentDescriptor descriptor = ComponentDescriptor.builder()
                .withType(NamedStrategy.SERIALIZED_TYPE)
                .withLabel("_missingType")
                .build();

        assertThatThrownBy(() -> StrategySerialization.fromDescriptor(series, descriptor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown named strategy");
    }

    @Test
    public void namedStrategyLegacyTypeDescriptorStillWorks() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        ToggleNamedStrategy.create(series, true, false, 2); // ensure registration

        ComponentDescriptor descriptor = ComponentDescriptor.builder()
                .withType(ToggleNamedStrategy.class.getName())
                .withLabel("ToggleNamedStrategy_true_false_u2")
                .build();

        Strategy restored = StrategySerialization.fromDescriptor(series, descriptor);

        assertThat(restored).isInstanceOf(ToggleNamedStrategy.class);
        assertThat(restored.getName()).isEqualTo("ToggleNamedStrategy_true_false_u2");
    }

    @Test
    public void initializeRegistryScansAdditionalPackages() {
        BarSeries series = new MockBarSeriesBuilder().withData(2, 4, 6).build();
        NamedStrategy.initializeRegistry("org.ta4j.core.serialization");

        ComponentDescriptor descriptor = ComponentDescriptor.builder()
                .withType(NamedStrategy.SERIALIZED_TYPE)
                .withLabel("AutoScanNamedStrategy_true_u0")
                .build();

        Strategy restored = StrategySerialization.fromDescriptor(series, descriptor);

        assertThat(restored).isInstanceOf(AutoScanNamedStrategy.class);
        assertThat(restored.getName()).isEqualTo("AutoScanNamedStrategy_true_u0");
    }

    @Test
    public void customStrategyOutsideCorePackageUsesFullyQualifiedName() {
        // Test that strategies outside org.ta4j.core use fully qualified names
        // We'll manually create a descriptor with a fully qualified name to simulate
        // a strategy from a different package (e.g., com.example.MyStrategy)
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4).build();

        // Create a descriptor with a fully qualified name outside org.ta4j.core
        ComponentDescriptor descriptor = ComponentDescriptor.builder()
                .withType("com.example.CustomStrategy")
                .withLabel("TestStrategy")
                .withParameters(Map.of("unstableBars", 1))
                .addComponent(ComponentDescriptor.builder()
                        .withType(SerializableRule.class.getName())
                        .withLabel("entry")
                        .withParameters(Map.of("satisfied", true))
                        .build())
                .addComponent(ComponentDescriptor.builder()
                        .withType(SerializableRule.class.getName())
                        .withLabel("exit")
                        .withParameters(Map.of("satisfied", false))
                        .build())
                .build();

        // This should fail to resolve the class and fall back to BaseStrategy
        // But the important thing is that the deserializer can handle fully qualified
        // names
        Strategy restored = StrategySerialization.fromDescriptor(series, descriptor);
        assertThat(restored).isInstanceOf(BaseStrategy.class);
        assertThat(restored.getName()).isEqualTo("TestStrategy");
    }

    @Test
    public void customStrategyInTestPackageRoundTrips() {
        // Test that a custom strategy in the test package (org.ta4j.core.serialization)
        // can be round-tripped correctly
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4).build();
        Rule entry = new SerializableRule(true);
        Rule exit = new SerializableRule(false);
        CustomTestStrategy original = new CustomTestStrategy("RoundTrip", entry, exit, 2);

        String json = original.toJson();
        Strategy restored = StrategySerialization.fromJson(series, json);

        // Should restore as the same type, not BaseStrategy
        assertThat(restored).isInstanceOf(CustomTestStrategy.class);
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getUnstableBars()).isEqualTo(original.getUnstableBars());

        TradingRecord record = new BaseTradingRecord();
        assertThat(restored.shouldEnter(3, record)).isTrue();
        assertThat(restored.shouldExit(3, record)).isFalse();
    }

    @Test
    public void strategyFromLegacyChildrenPayloadRoundTrips() {
        // Regression: BaseStrategy JSON using legacy "children" instead of "rules"
        // must parse and reconstruct correctly (ComponentSerialization routing)
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4).build();
        String legacyJson = "{\"type\":\"BaseStrategy\",\"label\":\"Legacy\",\"parameters\":{\"unstableBars\":2},"
                + "\"children\":[{\"type\":\"" + SerializableRule.class.getName()
                + "\",\"label\":\"entry\",\"parameters\":{\"satisfied\":true}}," + "{\"type\":\""
                + SerializableRule.class.getName() + "\",\"label\":\"exit\",\"parameters\":{\"satisfied\":false}}]}";

        Strategy restored = StrategySerialization.fromJson(series, legacyJson);

        assertThat(restored).isInstanceOf(BaseStrategy.class);
        assertThat(restored.getName()).isEqualTo("Legacy");
        assertThat(restored.getUnstableBars()).isEqualTo(2);
        TradingRecord record = new BaseTradingRecord();
        assertThat(restored.shouldEnter(3, record)).isTrue();
        assertThat(restored.shouldExit(3, record)).isFalse();
    }

    private static final class SerializableRule extends org.ta4j.core.rules.AbstractRule {

        private final boolean satisfied;

        private SerializableRule(boolean satisfied) {
            this.satisfied = satisfied;
        }

        @Override
        protected String createDefaultName() {
            ComponentDescriptor descriptor = ComponentDescriptor.builder()
                    .withType(getClass().getSimpleName())
                    .withParameters(Map.of("satisfied", satisfied))
                    .build();
            return ComponentSerialization.toJson(descriptor);
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            return satisfied;
        }
    }

    private static final class MultiLevelToggleNamedStrategy extends NamedStrategy {

        private static final AtomicInteger TYPED_CONSTRUCTIONS = new AtomicInteger();
        private static final AtomicInteger VARARGS_CONSTRUCTIONS = new AtomicInteger();

        static {
            registerImplementation(MultiLevelToggleNamedStrategy.class);
        }

        private MultiLevelToggleNamedStrategy(BarSeries series, boolean entrySatisfied, boolean exitSatisfied,
                int unstableBars, boolean delegated) {
            super(buildLabel(entrySatisfied, exitSatisfied, unstableBars), new SerializableRule(entrySatisfied),
                    new SerializableRule(exitSatisfied), unstableBars);
            if (delegated) {
                VARARGS_CONSTRUCTIONS.incrementAndGet();
            } else {
                TYPED_CONSTRUCTIONS.incrementAndGet();
            }
        }

        private MultiLevelToggleNamedStrategy(BarSeries series, boolean entrySatisfied, boolean exitSatisfied,
                int unstableBars) {
            this(series, entrySatisfied, exitSatisfied, unstableBars, false);
        }

        public MultiLevelToggleNamedStrategy(BarSeries series, String... parameters) {
            this(series, parseBoolean(parameters, 0), parseBoolean(parameters, 1), parseUnstable(parameters), true);
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

        private static boolean parseBoolean(String[] parameters, int index) {
            validateLength(parameters, 3, "MultiLevelToggleNamedStrategy");
            return Boolean.parseBoolean(parameters[index]);
        }

        private static int parseUnstable(String[] parameters) {
            validateLength(parameters, 3, "MultiLevelToggleNamedStrategy");
            return parseUnstableToken(parameters[2]);
        }

        private static String buildLabel(boolean entrySatisfied, boolean exitSatisfied, int unstableBars) {
            return NamedStrategy.buildLabel(MultiLevelToggleNamedStrategy.class, Boolean.toString(entrySatisfied),
                    Boolean.toString(exitSatisfied), "u" + unstableBars);
        }
    }

    private static final class ToggleNamedStrategy extends NamedStrategy {

        static {
            registerImplementation(ToggleNamedStrategy.class);
        }

        protected ToggleNamedStrategy(BarSeries series, boolean entrySatisfied, boolean exitSatisfied,
                int unstableBars) {
            super(buildLabel(entrySatisfied, exitSatisfied, unstableBars), new SerializableRule(entrySatisfied),
                    new SerializableRule(exitSatisfied), unstableBars);
        }

        public ToggleNamedStrategy(BarSeries series, String... parameters) {
            this(series, parseBoolean(parameters, 0), parseBoolean(parameters, 1), parseUnstable(parameters));
        }

        static ToggleNamedStrategy create(BarSeries series, boolean entrySatisfied, boolean exitSatisfied,
                int unstableBars) {
            return new ToggleNamedStrategy(series, entrySatisfied, exitSatisfied, unstableBars);
        }

        private static boolean parseBoolean(String[] parameters, int index) {
            validateLength(parameters, 3, "ToggleNamedStrategy");
            return Boolean.parseBoolean(parameters[index]);
        }

        private static int parseUnstable(String[] parameters) {
            validateLength(parameters, 3, "ToggleNamedStrategy");
            return parseUnstableToken(parameters[2]);
        }

        private static String buildLabel(boolean entrySatisfied, boolean exitSatisfied, int unstableBars) {
            return NamedStrategy.buildLabel(ToggleNamedStrategy.class, Boolean.toString(entrySatisfied),
                    Boolean.toString(exitSatisfied), "u" + unstableBars);
        }
    }

    private static final class CustomTestStrategy extends BaseStrategy {

        public CustomTestStrategy(String name, Rule entryRule, Rule exitRule, int unstableBars) {
            super(name, entryRule, exitRule, unstableBars);
        }
    }

    private static final class AutoScanNamedStrategy extends NamedStrategy {

        private AutoScanNamedStrategy(BarSeries series, boolean entrySatisfied, int unstableBars) {
            super(NamedStrategy.buildLabel(AutoScanNamedStrategy.class, Boolean.toString(entrySatisfied),
                    "u" + unstableBars), new SerializableRule(entrySatisfied), new SerializableRule(!entrySatisfied),
                    unstableBars);
        }

        public AutoScanNamedStrategy(BarSeries series, String... parameters) {
            this(series, parseEntryArgument(parameters), parseUnstableArgument(parameters));
        }

        private static boolean parseEntryArgument(String[] parameters) {
            if (parameters == null || parameters.length < 2) {
                throw new IllegalArgumentException("AutoScanNamedStrategy expects [entry, unstable]");
            }
            return Boolean.parseBoolean(parameters[0]);
        }

        private static int parseUnstableArgument(String[] parameters) {
            if (parameters == null || parameters.length < 2) {
                throw new IllegalArgumentException("AutoScanNamedStrategy expects [entry, unstable]");
            }
            return parseUnstableToken(parameters[1]);
        }
    }

    private static void validateLength(String[] parameters, int expected, String name) {
        if (parameters == null || parameters.length < expected) {
            throw new IllegalArgumentException(name + " expects " + expected + " parameters");
        }
    }

    private static int parseUnstableToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Unstable token cannot be blank");
        }
        if (token.startsWith("u")) {
            token = token.substring(1);
        }
        return Integer.parseInt(token);
    }
}
