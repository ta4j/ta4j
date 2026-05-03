/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.named;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.ta4j.core.BaseStrategy;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.strategy.named.NamedStrategy;

/**
 * Immutable registry for compact, named ta4j component shorthand.
 * <p>
 * The registry expands expressions such as {@code SMA(7)} or
 * {@code CrossedUp(SMA(7),SMA(21))} into canonical {@link ComponentDescriptor}
 * trees. Serialization and reconstruction still use the existing descriptor
 * model; shorthand is an authoring and compact-export layer only.
 * <p>
 * Aliases are scoped by {@link NamedAssetKind}, so the same alias may be reused
 * for different component families when the meaning is clear from context.
 *
 * @since 0.22.7
 */
public final class NamedAssetRegistry {

    private static final Pattern JSON_NUMBER_LITERAL = Pattern
            .compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");
    private static final AtomicReference<NamedAssetRegistry> DEFAULT_REGISTRY = new AtomicReference<>();

    private final Map<NamedAssetKind, Map<String, Binding>> bindings;

    private NamedAssetRegistry(Map<NamedAssetKind, ? extends Map<String, Binding>> bindings) {
        EnumMap<NamedAssetKind, Map<String, Binding>> copied = new EnumMap<>(NamedAssetKind.class);
        for (NamedAssetKind kind : NamedAssetKind.values()) {
            Map<String, Binding> byAlias = bindings.get(kind);
            if (byAlias == null) {
                byAlias = Map.of();
            }
            copied.put(kind, Collections.unmodifiableMap(new LinkedHashMap<>(byAlias)));
        }
        this.bindings = Collections.unmodifiableMap(copied);
    }

    /**
     * Returns the default registry containing ta4j's built-in shorthand bindings
     * plus any {@link NamedAssetProvider} bindings available through
     * {@link ServiceLoader}.
     *
     * @return immutable default registry
     * @since 0.22.7
     */
    public static NamedAssetRegistry defaultRegistry() {
        NamedAssetRegistry existing = DEFAULT_REGISTRY.get();
        if (existing != null) {
            return existing;
        }
        Builder builder = builder().withDefaults();
        for (NamedAssetProvider provider : ServiceLoader.load(NamedAssetProvider.class)) {
            provider.registerNamedAssets(builder);
        }
        NamedAssetRegistry created = builder.build();
        if (DEFAULT_REGISTRY.compareAndSet(null, created)) {
            return created;
        }
        return DEFAULT_REGISTRY.get();
    }

    /**
     * Creates a new mutable builder. The built registry is immutable.
     *
     * @return builder
     * @since 0.22.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Expands a shorthand expression into a descriptor.
     *
     * @param kind       owning asset family
     * @param expression shorthand expression
     * @return canonical descriptor
     * @since 0.22.7
     */
    public ComponentDescriptor toDescriptor(NamedAssetKind kind, String expression) {
        return toDescriptor(kind, expression, kind.name().toLowerCase());
    }

    /**
     * Expands a shorthand expression into a descriptor.
     *
     * @param kind       owning asset family
     * @param expression shorthand expression
     * @param location   location used in validation errors
     * @return canonical descriptor
     * @since 0.22.7
     */
    public ComponentDescriptor toDescriptor(NamedAssetKind kind, String expression, String location) {
        Objects.requireNonNull(kind, "kind");
        ParsedExpression parsed = new Parser(expression, location).parseExpressionDocument();
        return toDescriptor(kind, parsed, location);
    }

    /**
     * Attempts to render a descriptor back to a compact expression.
     *
     * @param kind       owning asset family
     * @param descriptor descriptor to render
     * @return shorthand expression when a registered formatter recognizes the
     *         descriptor
     * @since 0.22.7
     */
    public Optional<String> toExpression(NamedAssetKind kind, ComponentDescriptor descriptor) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(descriptor, "descriptor");
        Map<String, Binding> byAlias = bindings.getOrDefault(kind, Map.of());
        for (Binding binding : byAlias.values()) {
            Optional<String> expression = binding.formatter().format(descriptor, this);
            if (expression.isPresent()) {
                return expression;
            }
        }
        return Optional.empty();
    }

    /**
     * Splits comma-separated shorthand lists without splitting commas nested inside
     * function calls or string literals.
     *
     * @param text list text
     * @return immutable list of top-level entries
     * @since 0.22.7
     */
    public List<String> splitTopLevel(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Collections.unmodifiableList(Parser.splitTopLevel(text, "expression list"));
    }

    private ComponentDescriptor toDescriptor(NamedAssetKind kind, ParsedExpression parsed, String location) {
        Binding binding = bindings.getOrDefault(kind, Map.of()).get(parsed.alias());
        if (binding == null) {
            if (kind == NamedAssetKind.STRATEGY && parsed.arguments().isEmpty() && parsed.alias().contains("_")) {
                return ComponentDescriptor.builder()
                        .withType(NamedStrategy.SERIALIZED_TYPE)
                        .withLabel(parsed.alias())
                        .build();
            }
            if (kind == NamedAssetKind.ANALYSIS_CRITERION && parsed.arguments().isEmpty()) {
                return ComponentDescriptor.typeOnly(parsed.alias());
            }
            throw new IllegalArgumentException(
                    "Unknown named " + displayKind(kind) + " alias at " + location + ": " + parsed.alias());
        }
        Arguments arguments = new Arguments(this, kind, binding, parsed.arguments(), location);
        return binding.factory().create(arguments);
    }

    private static String displayKind(NamedAssetKind kind) {
        return switch (kind) {
        case ANALYSIS_CRITERION -> "analysis criterion";
        case INDICATOR -> "indicator";
        case RULE -> "rule";
        case STRATEGY -> "strategy";
        };
    }

    private static ComponentDescriptor descriptor(String type) {
        return ComponentDescriptor.builder().withType(type).build();
    }

    private static ComponentDescriptor indicatorDescriptor(String type, ComponentDescriptor base, int barCount) {
        return ComponentDescriptor.builder()
                .withType(type)
                .withParameters(Map.of("barCount", barCount))
                .addComponent(base)
                .build();
    }

    private static ComponentDescriptor constantDescriptor(String value) {
        return ComponentDescriptor.builder()
                .withType("ConstantIndicator")
                .withParameters(Map.of("value", value))
                .build();
    }

    private static ComponentDescriptor comparisonRule(String type, Arguments arguments) {
        arguments.requireCount(2);
        ComponentDescriptor first = arguments.indicatorDescriptor(0);
        ComponentDescriptor second = arguments.indicatorOrNumericDescriptor(1);
        return ComponentDescriptor.builder().withType(type).addComponent(first).addComponent(second).build();
    }

    private static ComponentDescriptor compositeRule(String type, Arguments arguments) {
        arguments.requireCount(2);
        ComponentDescriptor first = arguments.ruleDescriptor(0);
        ComponentDescriptor second = arguments.ruleDescriptor(1);
        return ComponentDescriptor.builder().withType(type).addComponent(first).addComponent(second).build();
    }

    private static ComponentDescriptor stopRule(String type, String parameter, Arguments arguments) {
        arguments.requireCount(1);
        return ComponentDescriptor.builder()
                .withType(type)
                .withParameters(Map.of(parameter, arguments.finiteNumberText(0)))
                .addComponent(descriptor("ClosePriceIndicator"))
                .build();
    }

    private static ComponentDescriptor smaStrategy(Arguments arguments) {
        arguments.requireCount(2);
        int fast = arguments.positiveInt(0);
        int slow = arguments.positiveInt(1);
        ComponentDescriptor fastSma = indicatorDescriptor("SMAIndicator", descriptor("ClosePriceIndicator"), fast);
        ComponentDescriptor slowSma = indicatorDescriptor("SMAIndicator", descriptor("ClosePriceIndicator"), slow);
        ComponentDescriptor entry = ComponentDescriptor.builder()
                .withType("CrossedUpIndicatorRule")
                .withLabel("entry")
                .addComponent(fastSma)
                .addComponent(slowSma)
                .build();
        ComponentDescriptor exit = ComponentDescriptor.builder()
                .withType("CrossedDownIndicatorRule")
                .withLabel("exit")
                .addComponent(slowSma)
                .addComponent(fastSma)
                .build();
        return ComponentDescriptor.builder()
                .withType(BaseStrategy.class.getSimpleName())
                .withLabel("SMA(" + fast + "," + slow + ")")
                .withParameters(Map.of("unstableBars", slow))
                .addComponent(entry)
                .addComponent(exit)
                .build();
    }

    private static ComponentDescriptor movingAverageIndicator(String type, Arguments arguments) {
        if (arguments.size() == 1) {
            int barCount = arguments.positiveInt(0);
            return indicatorDescriptor(type, descriptor("ClosePriceIndicator"), barCount);
        }
        arguments.requireCount(2);
        ComponentDescriptor base = arguments.indicatorDescriptor(0);
        int barCount = arguments.positiveInt(1);
        return indicatorDescriptor(type, base, barCount);
    }

    private static Optional<String> closePriceExpression(ComponentDescriptor descriptor, NamedAssetRegistry registry) {
        if ("ClosePriceIndicator".equals(descriptor.getType()) && descriptor.getComponents().isEmpty()
                && descriptor.getParameters().isEmpty()) {
            return Optional.of("ClosePrice");
        }
        return Optional.empty();
    }

    private static Optional<String> movingAverageExpression(String alias, String type, ComponentDescriptor descriptor,
            NamedAssetRegistry registry) {
        if (!type.equals(descriptor.getType()) || descriptor.getComponents().size() != 1) {
            return Optional.empty();
        }
        Integer barCount = intParameter(descriptor, "barCount");
        if (barCount == null) {
            return Optional.empty();
        }
        ComponentDescriptor base = descriptor.getComponents().get(0);
        if (isClosePrice(base)) {
            return Optional.of(alias + "(" + barCount + ")");
        }
        Optional<String> baseExpression = registry.toExpression(NamedAssetKind.INDICATOR, base);
        return baseExpression.map(expression -> alias + "(" + expression + "," + barCount + ")");
    }

    private static Optional<String> comparisonRuleExpression(String alias, String type, ComponentDescriptor descriptor,
            NamedAssetRegistry registry) {
        if (!type.equals(descriptor.getType()) || descriptor.getComponents().size() != 2) {
            return Optional.empty();
        }
        Optional<String> first = registry.toExpression(NamedAssetKind.INDICATOR, descriptor.getComponents().get(0));
        Optional<String> second = indicatorOrConstantExpression(descriptor.getComponents().get(1), registry);
        if (first.isPresent() && second.isPresent()) {
            return Optional.of(alias + "(" + first.get() + "," + second.get() + ")");
        }
        return Optional.empty();
    }

    private static Optional<String> compositeRuleExpression(String alias, String type, ComponentDescriptor descriptor,
            NamedAssetRegistry registry) {
        if (!type.equals(descriptor.getType()) || descriptor.getComponents().size() != 2) {
            return Optional.empty();
        }
        Optional<String> first = registry.toExpression(NamedAssetKind.RULE, descriptor.getComponents().get(0));
        Optional<String> second = registry.toExpression(NamedAssetKind.RULE, descriptor.getComponents().get(1));
        if (first.isPresent() && second.isPresent()) {
            return Optional.of(alias + "(" + first.get() + "," + second.get() + ")");
        }
        return Optional.empty();
    }

    private static Optional<String> smaCrossRuleExpression(String alias, String type, ComponentDescriptor descriptor,
            boolean reverseComponents) {
        if (!type.equals(descriptor.getType()) || descriptor.getComponents().size() != 2) {
            return Optional.empty();
        }
        ComponentDescriptor first = reverseComponents ? descriptor.getComponents().get(1)
                : descriptor.getComponents().get(0);
        ComponentDescriptor second = reverseComponents ? descriptor.getComponents().get(0)
                : descriptor.getComponents().get(1);
        Integer fast = simpleSmaBarCount(first);
        Integer slow = simpleSmaBarCount(second);
        if (fast == null || slow == null) {
            return Optional.empty();
        }
        return Optional.of(alias + "(" + fast + "," + slow + ")");
    }

    private static Optional<String> stopRuleExpression(String alias, String type, String parameter,
            ComponentDescriptor descriptor) {
        if (!type.equals(descriptor.getType()) || descriptor.getComponents().size() != 1
                || !isClosePrice(descriptor.getComponents().get(0))) {
            return Optional.empty();
        }
        Object value = descriptor.getParameters().get(parameter);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(alias + "(" + value + ")");
    }

    private static Optional<String> smaStrategyExpression(ComponentDescriptor descriptor) {
        if (!BaseStrategy.class.getSimpleName().equals(descriptor.getType())
                || descriptor.getComponents().size() != 2) {
            return Optional.empty();
        }
        ComponentDescriptor entry = descriptor.getComponents().get(0);
        ComponentDescriptor exit = descriptor.getComponents().get(1);
        if (!"entry".equals(entry.getLabel()) || !"exit".equals(exit.getLabel())) {
            return Optional.empty();
        }
        if (!"CrossedUpIndicatorRule".equals(entry.getType()) || !"CrossedDownIndicatorRule".equals(exit.getType())) {
            return Optional.empty();
        }
        Integer entryFast = entry.getComponents().size() == 2 ? simpleSmaBarCount(entry.getComponents().get(0)) : null;
        Integer entrySlow = entry.getComponents().size() == 2 ? simpleSmaBarCount(entry.getComponents().get(1)) : null;
        Integer exitFast = exit.getComponents().size() == 2 ? simpleSmaBarCount(exit.getComponents().get(0)) : null;
        Integer exitSlow = exit.getComponents().size() == 2 ? simpleSmaBarCount(exit.getComponents().get(1)) : null;
        boolean exitMatchesSameOrder = entryFast != null && entrySlow != null && entryFast.equals(exitFast)
                && entrySlow.equals(exitSlow);
        boolean exitMatchesSerializedCrossDownOrder = entryFast != null && entrySlow != null
                && entryFast.equals(exitSlow) && entrySlow.equals(exitFast);
        if (!exitMatchesSameOrder && !exitMatchesSerializedCrossDownOrder) {
            return Optional.empty();
        }
        return Optional.of("SMA(" + entryFast + "," + entrySlow + ")");
    }

    private static Optional<String> criterionExpression(String alias, String type, ComponentDescriptor descriptor) {
        String descriptorType = descriptor.getType();
        String simpleType = type.substring(type.lastIndexOf('.') + 1);
        if (type.equals(descriptorType) || simpleType.equals(descriptorType)
                || descriptorType != null && descriptorType.endsWith('.' + simpleType)) {
            if (descriptor.getParameters().isEmpty() && descriptor.getComponents().isEmpty()) {
                return Optional.of(alias);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> indicatorOrConstantExpression(ComponentDescriptor descriptor,
            NamedAssetRegistry registry) {
        if ("ConstantIndicator".equals(descriptor.getType())) {
            Object value = descriptor.getParameters().get("value");
            if (value != null) {
                return Optional.of(value.toString());
            }
        }
        return registry.toExpression(NamedAssetKind.INDICATOR, descriptor);
    }

    private static boolean isClosePrice(ComponentDescriptor descriptor) {
        return descriptor != null && "ClosePriceIndicator".equals(descriptor.getType())
                && descriptor.getComponents().isEmpty();
    }

    private static Integer simpleSmaBarCount(ComponentDescriptor descriptor) {
        if (descriptor == null || !"SMAIndicator".equals(descriptor.getType()) || descriptor.getComponents().size() != 1
                || !isClosePrice(descriptor.getComponents().get(0))) {
            return null;
        }
        return intParameter(descriptor, "barCount");
    }

    private static Integer intParameter(ComponentDescriptor descriptor, String key) {
        Object value = descriptor.getParameters().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * Builder for immutable named asset registries.
     *
     * @since 0.22.7
     */
    public static final class Builder {

        private final EnumMap<NamedAssetKind, LinkedHashMap<String, Binding>> bindings = new EnumMap<>(
                NamedAssetKind.class);

        private Builder() {
            for (NamedAssetKind kind : NamedAssetKind.values()) {
                bindings.put(kind, new LinkedHashMap<>());
            }
        }

        /**
         * Adds ta4j's built-in shorthand bindings.
         *
         * @return this builder
         * @since 0.22.7
         */
        public Builder withDefaults() {
            registerIndicator("ClosePrice", List.of(), args -> {
                args.requireCount(0);
                return descriptor("ClosePriceIndicator");
            }, NamedAssetRegistry::closePriceExpression);
            registerIndicator("ClosePriceIndicator", List.of(), args -> {
                args.requireCount(0);
                return descriptor("ClosePriceIndicator");
            }, DescriptorFormatter.none());
            registerIndicator("SMA", List.of("barCount"), args -> movingAverageIndicator("SMAIndicator", args),
                    (descriptor, registry) -> movingAverageExpression("SMA", "SMAIndicator", descriptor, registry));
            registerIndicator("EMA", List.of("barCount"), args -> movingAverageIndicator("EMAIndicator", args),
                    (descriptor, registry) -> movingAverageExpression("EMA", "EMAIndicator", descriptor, registry));
            registerIndicator("RSI", List.of("barCount"), args -> movingAverageIndicator("RSIIndicator", args),
                    (descriptor, registry) -> movingAverageExpression("RSI", "RSIIndicator", descriptor, registry));

            registerRule("SmaCrossUp", List.of("fast", "slow"), args -> {
                args.requireCount(2);
                ComponentDescriptor fast = indicatorDescriptor("SMAIndicator", descriptor("ClosePriceIndicator"),
                        args.positiveInt(0));
                ComponentDescriptor slow = indicatorDescriptor("SMAIndicator", descriptor("ClosePriceIndicator"),
                        args.positiveInt(1));
                return ComponentDescriptor.builder()
                        .withType("CrossedUpIndicatorRule")
                        .addComponent(fast)
                        .addComponent(slow)
                        .build();
            }, (descriptor, registry) -> smaCrossRuleExpression("SmaCrossUp", "CrossedUpIndicatorRule", descriptor,
                    false));
            registerRule("SmaCrossDown", List.of("fast", "slow"), args -> {
                args.requireCount(2);
                ComponentDescriptor fast = indicatorDescriptor("SMAIndicator", descriptor("ClosePriceIndicator"),
                        args.positiveInt(0));
                ComponentDescriptor slow = indicatorDescriptor("SMAIndicator", descriptor("ClosePriceIndicator"),
                        args.positiveInt(1));
                return ComponentDescriptor.builder()
                        .withType("CrossedDownIndicatorRule")
                        .addComponent(slow)
                        .addComponent(fast)
                        .build();
            }, (descriptor, registry) -> smaCrossRuleExpression("SmaCrossDown", "CrossedDownIndicatorRule", descriptor,
                    true));
            registerRule("And", List.of("left", "right"), args -> compositeRule("AndRule", args),
                    (descriptor, registry) -> compositeRuleExpression("And", "AndRule", descriptor, registry));
            registerRule("AndRule", List.of("left", "right"), args -> compositeRule("AndRule", args),
                    DescriptorFormatter.none());
            registerRule("Or", List.of("left", "right"), args -> compositeRule("OrRule", args),
                    (descriptor, registry) -> compositeRuleExpression("Or", "OrRule", descriptor, registry));
            registerRule("OrRule", List.of("left", "right"), args -> compositeRule("OrRule", args),
                    DescriptorFormatter.none());
            registerRule("CrossedUp", List.of("indicator", "thresholdOrIndicator"),
                    args -> comparisonRule("CrossedUpIndicatorRule", args),
                    (descriptor, registry) -> comparisonRuleExpression("CrossedUp", "CrossedUpIndicatorRule",
                            descriptor, registry));
            registerRule("CrossedUpIndicatorRule", List.of("indicator", "thresholdOrIndicator"),
                    args -> comparisonRule("CrossedUpIndicatorRule", args), DescriptorFormatter.none());
            registerRule("CrossedDown", List.of("indicator", "thresholdOrIndicator"),
                    args -> comparisonRule("CrossedDownIndicatorRule", args),
                    (descriptor, registry) -> comparisonRuleExpression("CrossedDown", "CrossedDownIndicatorRule",
                            descriptor, registry));
            registerRule("CrossedDownIndicatorRule", List.of("indicator", "thresholdOrIndicator"),
                    args -> comparisonRule("CrossedDownIndicatorRule", args), DescriptorFormatter.none());
            registerRule("Over", List.of("indicator", "thresholdOrIndicator"),
                    args -> comparisonRule("OverIndicatorRule", args), (descriptor,
                            registry) -> comparisonRuleExpression("Over", "OverIndicatorRule", descriptor, registry));
            registerRule("OverIndicatorRule", List.of("indicator", "thresholdOrIndicator"),
                    args -> comparisonRule("OverIndicatorRule", args), DescriptorFormatter.none());
            registerRule("Under", List.of("indicator", "thresholdOrIndicator"),
                    args -> comparisonRule("UnderIndicatorRule", args), (descriptor,
                            registry) -> comparisonRuleExpression("Under", "UnderIndicatorRule", descriptor, registry));
            registerRule("UnderIndicatorRule", List.of("indicator", "thresholdOrIndicator"),
                    args -> comparisonRule("UnderIndicatorRule", args), DescriptorFormatter.none());
            registerRule("StopLoss", List.of("lossPercentage"),
                    args -> stopRule("StopLossRule", "lossPercentage", args), (descriptor,
                            registry) -> stopRuleExpression("StopLoss", "StopLossRule", "lossPercentage", descriptor));
            registerRule("StopLossRule", List.of("lossPercentage"),
                    args -> stopRule("StopLossRule", "lossPercentage", args), DescriptorFormatter.none());
            registerRule("StopGain", List.of("gainPercentage"),
                    args -> stopRule("StopGainRule", "gainPercentage", args), (descriptor,
                            registry) -> stopRuleExpression("StopGain", "StopGainRule", "gainPercentage", descriptor));
            registerRule("StopGainRule", List.of("gainPercentage"),
                    args -> stopRule("StopGainRule", "gainPercentage", args), DescriptorFormatter.none());

            registerStrategy("SMA", List.of("fast", "slow"), NamedAssetRegistry::smaStrategy,
                    (descriptor, registry) -> smaStrategyExpression(descriptor));

            registerCriterion("NetProfit", "org.ta4j.core.criteria.pnl.NetProfitCriterion");
            registerCriterion("GrossReturn", "org.ta4j.core.criteria.pnl.GrossReturnCriterion");
            registerCriterion("NetReturn", "org.ta4j.core.criteria.pnl.NetReturnCriterion");
            registerCriterion("MaximumDrawdown", "org.ta4j.core.criteria.MaximumDrawdownCriterion");
            registerCriterion("ReturnOverMaxDrawdown", "org.ta4j.core.criteria.ReturnOverMaxDrawdownCriterion");
            registerCriterion("SharpeRatio", "org.ta4j.core.criteria.SharpeRatioCriterion");
            registerCriterion("SortinoRatio", "org.ta4j.core.criteria.SortinoRatioCriterion");
            registerCriterion("TotalFees", "org.ta4j.core.criteria.commissions.TotalFeesCriterion");
            registerCriterion("NumberOfPositions", "org.ta4j.core.criteria.NumberOfPositionsCriterion");
            return this;
        }

        /**
         * Registers an indicator shorthand binding.
         *
         * @param alias          shorthand alias
         * @param parameterNames documented parameter names
         * @param factory        descriptor factory
         * @return this builder
         * @since 0.22.7
         */
        public Builder registerIndicator(String alias, List<String> parameterNames, DescriptorFactory factory) {
            return register(NamedAssetKind.INDICATOR, alias, parameterNames, factory);
        }

        /**
         * Registers an indicator shorthand binding.
         *
         * @param alias          shorthand alias
         * @param parameterNames documented parameter names
         * @param factory        descriptor factory
         * @param formatter      optional compact formatter
         * @return this builder
         * @since 0.22.7
         */
        public Builder registerIndicator(String alias, List<String> parameterNames, DescriptorFactory factory,
                DescriptorFormatter formatter) {
            return register(NamedAssetKind.INDICATOR, alias, parameterNames, factory, formatter);
        }

        /**
         * Registers a rule shorthand binding.
         *
         * @param alias          shorthand alias
         * @param parameterNames documented parameter names
         * @param factory        descriptor factory
         * @return this builder
         * @since 0.22.7
         */
        public Builder registerRule(String alias, List<String> parameterNames, DescriptorFactory factory) {
            return register(NamedAssetKind.RULE, alias, parameterNames, factory);
        }

        /**
         * Registers a rule shorthand binding.
         *
         * @param alias          shorthand alias
         * @param parameterNames documented parameter names
         * @param factory        descriptor factory
         * @param formatter      optional compact formatter
         * @return this builder
         * @since 0.22.7
         */
        public Builder registerRule(String alias, List<String> parameterNames, DescriptorFactory factory,
                DescriptorFormatter formatter) {
            return register(NamedAssetKind.RULE, alias, parameterNames, factory, formatter);
        }

        /**
         * Registers a strategy shorthand binding.
         *
         * @param alias          shorthand alias
         * @param parameterNames documented parameter names
         * @param factory        descriptor factory
         * @return this builder
         * @since 0.22.7
         */
        public Builder registerStrategy(String alias, List<String> parameterNames, DescriptorFactory factory) {
            return register(NamedAssetKind.STRATEGY, alias, parameterNames, factory);
        }

        /**
         * Registers a strategy shorthand binding.
         *
         * @param alias          shorthand alias
         * @param parameterNames documented parameter names
         * @param factory        descriptor factory
         * @param formatter      optional compact formatter
         * @return this builder
         * @since 0.22.7
         */
        public Builder registerStrategy(String alias, List<String> parameterNames, DescriptorFactory factory,
                DescriptorFormatter formatter) {
            return register(NamedAssetKind.STRATEGY, alias, parameterNames, factory, formatter);
        }

        /**
         * Registers an analysis criterion shorthand binding.
         *
         * @param alias          shorthand alias
         * @param parameterNames documented parameter names
         * @param factory        descriptor factory
         * @return this builder
         * @since 0.22.7
         */
        public Builder registerAnalysisCriterion(String alias, List<String> parameterNames, DescriptorFactory factory) {
            return register(NamedAssetKind.ANALYSIS_CRITERION, alias, parameterNames, factory);
        }

        /**
         * Registers an analysis criterion shorthand binding.
         *
         * @param alias          shorthand alias
         * @param parameterNames documented parameter names
         * @param factory        descriptor factory
         * @param formatter      optional compact formatter
         * @return this builder
         * @since 0.22.7
         */
        public Builder registerAnalysisCriterion(String alias, List<String> parameterNames, DescriptorFactory factory,
                DescriptorFormatter formatter) {
            return register(NamedAssetKind.ANALYSIS_CRITERION, alias, parameterNames, factory, formatter);
        }

        /**
         * Registers a shorthand binding.
         *
         * @param kind           owning asset kind
         * @param alias          shorthand alias
         * @param parameterNames documented parameter names
         * @param factory        descriptor factory
         * @return this builder
         * @since 0.22.7
         */
        public Builder register(NamedAssetKind kind, String alias, List<String> parameterNames,
                DescriptorFactory factory) {
            return register(kind, alias, parameterNames, factory, DescriptorFormatter.none());
        }

        /**
         * Registers a shorthand binding.
         *
         * @param kind           owning asset kind
         * @param alias          shorthand alias
         * @param parameterNames documented parameter names
         * @param factory        descriptor factory
         * @param formatter      optional compact formatter
         * @return this builder
         * @since 0.22.7
         */
        public Builder register(NamedAssetKind kind, String alias, List<String> parameterNames,
                DescriptorFactory factory, DescriptorFormatter formatter) {
            Objects.requireNonNull(kind, "kind");
            requireAlias(alias);
            Objects.requireNonNull(parameterNames, "parameterNames");
            Objects.requireNonNull(factory, "factory");
            Objects.requireNonNull(formatter, "formatter");
            LinkedHashMap<String, Binding> byAlias = bindings.get(kind);
            if (byAlias.containsKey(alias)) {
                throw new IllegalArgumentException(
                        "Named " + displayKind(kind) + " alias already registered: " + alias);
            }
            byAlias.put(alias, new Binding(alias, List.copyOf(parameterNames), factory, formatter));
            return this;
        }

        /**
         * Builds an immutable registry.
         *
         * @return registry
         * @since 0.22.7
         */
        public NamedAssetRegistry build() {
            return new NamedAssetRegistry(bindings);
        }

        private Builder registerCriterion(String alias, String type) {
            return registerAnalysisCriterion(alias, List.of(), args -> {
                args.requireCount(0);
                return ComponentDescriptor.typeOnly(type);
            }, (descriptor, registry) -> criterionExpression(alias, type, descriptor));
        }

        private static void requireAlias(String alias) {
            if (alias == null || alias.isBlank()) {
                throw new IllegalArgumentException("Named asset alias cannot be blank");
            }
            if (!Parser.isIdentifier(alias)) {
                throw new IllegalArgumentException("Invalid named asset alias: " + alias);
            }
        }
    }

    /**
     * Factory that expands parsed expression arguments into a descriptor.
     *
     * @since 0.22.7
     */
    @FunctionalInterface
    public interface DescriptorFactory {

        /**
         * Builds a descriptor from parsed expression arguments.
         *
         * @param arguments parsed arguments
         * @return descriptor
         * @since 0.22.7
         */
        ComponentDescriptor create(Arguments arguments);
    }

    /**
     * Formatter that renders descriptors back to compact expressions when possible.
     *
     * @since 0.22.7
     */
    @FunctionalInterface
    public interface DescriptorFormatter {

        /**
         * Formatter that never matches.
         *
         * @return no-op formatter
         * @since 0.22.7
         */
        static DescriptorFormatter none() {
            return (descriptor, registry) -> Optional.empty();
        }

        /**
         * Attempts to render a descriptor.
         *
         * @param descriptor descriptor
         * @param registry   registry for nested descriptor formatting
         * @return expression when matched
         * @since 0.22.7
         */
        Optional<String> format(ComponentDescriptor descriptor, NamedAssetRegistry registry);
    }

    /**
     * Typed view over parsed shorthand arguments.
     *
     * @since 0.22.7
     */
    public static final class Arguments {

        private final NamedAssetRegistry registry;
        private final NamedAssetKind kind;
        private final Binding binding;
        private final List<ParsedArgument> arguments;
        private final String location;

        private Arguments(NamedAssetRegistry registry, NamedAssetKind kind, Binding binding,
                List<ParsedArgument> arguments, String location) {
            this.registry = registry;
            this.kind = kind;
            this.binding = binding;
            this.arguments = arguments;
            this.location = location;
        }

        /**
         * @return number of parsed arguments
         * @since 0.22.7
         */
        public int size() {
            return arguments.size();
        }

        /**
         * @return alias currently being expanded
         * @since 0.22.7
         */
        public String alias() {
            return binding.alias();
        }

        /**
         * Requires an exact argument count.
         *
         * @param expected expected count
         * @since 0.22.7
         */
        public void requireCount(int expected) {
            if (arguments.size() != expected) {
                throw new IllegalArgumentException("Expected " + expected + " argument(s) at " + location + " for "
                        + binding.alias() + " but found " + arguments.size());
            }
        }

        /**
         * Reads a positive integer argument.
         *
         * @param index argument index
         * @return positive integer
         * @since 0.22.7
         */
        public int positiveInt(int index) {
            int value = intValue(index);
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "Expected integer value > 0 at " + argLocation(index) + ": " + value);
            }
            return value;
        }

        /**
         * Reads an integer argument.
         *
         * @param index argument index
         * @return integer
         * @since 0.22.7
         */
        public int intValue(int index) {
            String text = literalText(index);
            if (!isIntegerLiteral(text)) {
                throw new IllegalArgumentException("Expected integer value at " + argLocation(index) + ": " + text);
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Expected integer value at " + argLocation(index) + ": " + text, ex);
            }
        }

        /**
         * Reads a finite JSON-style number as text.
         *
         * @param index argument index
         * @return normalized number text
         * @since 0.22.7
         */
        public String finiteNumberText(int index) {
            String text = literalText(index);
            if (text.endsWith("%")) {
                text = text.substring(0, text.length() - 1).trim();
            }
            parseFiniteDouble(text, argLocation(index));
            return trimNumber(text);
        }

        /**
         * Resolves an indicator argument.
         *
         * @param index argument index
         * @return indicator descriptor
         * @since 0.22.7
         */
        public ComponentDescriptor indicatorDescriptor(int index) {
            return descriptorValue(index, NamedAssetKind.INDICATOR);
        }

        /**
         * Resolves a rule argument.
         *
         * @param index argument index
         * @return rule descriptor
         * @since 0.22.7
         */
        public ComponentDescriptor ruleDescriptor(int index) {
            return descriptorValue(index, NamedAssetKind.RULE);
        }

        private ComponentDescriptor indicatorOrNumericDescriptor(int index) {
            ParsedArgument argument = argument(index);
            if (!argument.isExpression() && looksNumeric(argument.literal())) {
                return constantDescriptor(finiteNumberText(index));
            }
            return indicatorDescriptor(index);
        }

        private ComponentDescriptor descriptorValue(int index, NamedAssetKind descriptorKind) {
            ParsedArgument argument = argument(index);
            if (argument.isExpression()) {
                return registry.toDescriptor(descriptorKind, argument.expression(), argLocation(index));
            }
            return registry.toDescriptor(descriptorKind, argument.literal(), argLocation(index));
        }

        private String literalText(int index) {
            ParsedArgument argument = argument(index);
            if (argument.isExpression()) {
                throw new IllegalArgumentException(
                        "Expected literal value at " + argLocation(index) + " but found " + argument.source());
            }
            return argument.literal();
        }

        private ParsedArgument argument(int index) {
            if (index < 0 || index >= arguments.size()) {
                throw new IllegalArgumentException("Missing argument at " + argLocation(index) + " for "
                        + binding.alias() + " expected " + binding.parameterNames());
            }
            return arguments.get(index);
        }

        private String argLocation(int index) {
            return location + ".args[" + index + "]";
        }

        private NamedAssetKind kind() {
            return kind;
        }
    }

    private record Binding(String alias, List<String> parameterNames, DescriptorFactory factory,
            DescriptorFormatter formatter) {
    }

    private record ParsedExpression(String alias, List<ParsedArgument> arguments, String source) {
    }

    private record ParsedArgument(ParsedExpression expression, String literal, String source) {

        boolean isExpression() {
            return expression != null;
        }
    }

    private static final class Parser {

        private final String text;
        private final String location;
        private int index;

        private Parser(String text, String location) {
            this.text = text == null ? "" : text.trim();
            this.location = location;
        }

        static boolean isIdentifier(String value) {
            if (value == null || value.isEmpty()) {
                return false;
            }
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                boolean valid = i == 0 ? isIdentifierStart(character) : isIdentifierPart(character);
                if (!valid) {
                    return false;
                }
            }
            return true;
        }

        static List<String> splitTopLevel(String value, String location) {
            List<String> entries = new ArrayList<>();
            int depth = 0;
            boolean inString = false;
            boolean escaping = false;
            int start = 0;
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                if (inString) {
                    if (escaping) {
                        escaping = false;
                    } else if (character == '\\') {
                        escaping = true;
                    } else if (character == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (character == '"') {
                    inString = true;
                } else if (character == '(') {
                    depth++;
                } else if (character == ')') {
                    depth--;
                    if (depth < 0) {
                        throw new IllegalArgumentException("Unexpected ')' at " + location);
                    }
                } else if (character == ',' && depth == 0) {
                    addSplitEntry(entries, value.substring(start, i), location);
                    start = i + 1;
                }
            }
            if (inString) {
                throw new IllegalArgumentException("Unterminated string in " + location);
            }
            if (depth != 0) {
                throw new IllegalArgumentException("Unbalanced parentheses in " + location);
            }
            addSplitEntry(entries, value.substring(start), location);
            return entries;
        }

        private static void addSplitEntry(List<String> entries, String raw, String location) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Empty entry in " + location);
            }
            entries.add(trimmed);
        }

        ParsedExpression parseExpressionDocument() {
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Empty shorthand expression at " + location);
            }
            ParsedExpression expression = parseExpression();
            skipWhitespace();
            if (!isEnd()) {
                throw new IllegalArgumentException(
                        "Unexpected trailing input at " + location + ": " + text.substring(index));
            }
            return expression;
        }

        private ParsedExpression parseExpression() {
            skipWhitespace();
            int start = index;
            String alias = parseIdentifier();
            skipWhitespace();
            List<ParsedArgument> arguments = List.of();
            if (peek('(')) {
                index++;
                arguments = parseArguments();
            }
            String source = text.substring(start, index).trim();
            return new ParsedExpression(alias, List.copyOf(arguments), source);
        }

        private List<ParsedArgument> parseArguments() {
            List<ParsedArgument> arguments = new ArrayList<>();
            skipWhitespace();
            if (peek(')')) {
                index++;
                return arguments;
            }
            while (true) {
                arguments.add(parseArgument());
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    skipWhitespace();
                    if (peek(')')) {
                        throw new IllegalArgumentException("Empty argument at " + location);
                    }
                    continue;
                }
                if (peek(')')) {
                    index++;
                    return arguments;
                }
                throw new IllegalArgumentException(
                        "Expected ',' or ')' at " + location + " near " + text.substring(index));
            }
        }

        private ParsedArgument parseArgument() {
            skipWhitespace();
            int start = index;
            if (isEnd()) {
                throw new IllegalArgumentException("Missing argument at " + location);
            }
            if (peek('"')) {
                String literal = parseQuotedString();
                return new ParsedArgument(null, literal, text.substring(start, index));
            }
            if (isIdentifierStart(current())) {
                String token = parseIdentifier();
                skipWhitespace();
                if (peek('(')) {
                    index = start;
                    ParsedExpression expression = parseExpression();
                    return new ParsedArgument(expression, null, expression.source());
                }
                return new ParsedArgument(null, token, token);
            }
            String literal = parseBareLiteral();
            return new ParsedArgument(null, literal, literal);
        }

        private String parseIdentifier() {
            if (isEnd() || !isIdentifierStart(current())) {
                throw new IllegalArgumentException(
                        "Expected identifier at " + location + " near " + (isEnd() ? "<end>" : text.substring(index)));
            }
            int start = index++;
            while (!isEnd() && isIdentifierPart(current())) {
                index++;
            }
            return text.substring(start, index);
        }

        private String parseQuotedString() {
            StringBuilder builder = new StringBuilder();
            index++;
            boolean escaping = false;
            while (!isEnd()) {
                char character = current();
                index++;
                if (escaping) {
                    builder.append(switch (character) {
                    case '"', '\\', '/' -> character;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> throw new IllegalArgumentException(
                            "Unsupported string escape at " + location + ": \\" + character);
                    });
                    escaping = false;
                } else if (character == '\\') {
                    escaping = true;
                } else if (character == '"') {
                    return builder.toString();
                } else {
                    builder.append(character);
                }
            }
            throw new IllegalArgumentException("Unterminated string at " + location);
        }

        private String parseBareLiteral() {
            int start = index;
            while (!isEnd() && current() != ',' && current() != ')') {
                index++;
            }
            String literal = text.substring(start, index).trim();
            if (literal.isEmpty()) {
                throw new IllegalArgumentException("Empty argument at " + location);
            }
            return literal;
        }

        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(current())) {
                index++;
            }
        }

        private boolean peek(char expected) {
            return !isEnd() && current() == expected;
        }

        private char current() {
            return text.charAt(index);
        }

        private boolean isEnd() {
            return index >= text.length();
        }

        private static boolean isIdentifierStart(char character) {
            return Character.isLetter(character) || character == '_' || character == '$';
        }

        private static boolean isIdentifierPart(char character) {
            return Character.isLetterOrDigit(character) || character == '_' || character == '$' || character == '.';
        }
    }

    private static boolean looksNumeric(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.endsWith("%")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return JSON_NUMBER_LITERAL.matcher(trimmed).matches();
    }

    private static boolean isIntegerLiteral(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int start = value.charAt(0) == '-' || value.charAt(0) == '+' ? 1 : 0;
        if (start == value.length()) {
            return false;
        }
        for (int index = start; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static double parseFiniteDouble(String value, String location) {
        String trimmed = value == null ? "" : value.trim();
        if (!JSON_NUMBER_LITERAL.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid numeric argument at " + location + ": " + trimmed);
        }
        try {
            double parsed = Double.parseDouble(trimmed);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException("non-finite numeric value");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric argument at " + location + ": " + trimmed, ex);
        }
    }

    private static String trimNumber(String text) {
        try {
            BigDecimal decimal = new BigDecimal(text);
            return decimal.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return text;
        }
    }
}
