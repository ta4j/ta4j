/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OrRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.StopGainRule;
import org.ta4j.core.rules.StopLossRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.strategy.named.NamedStrategy;

/**
 * Serializes and deserializes {@link Strategy} instances into structured
 * {@link ComponentDescriptor} payloads.
 * <p>
 * Deserialization also accepts an opt-in {@code version: 2} authoring envelope
 * and normalizes it back into the canonical descriptor representation before
 * reconstruction.
 *
 * @since 0.19
 */
public final class StrategySerialization {

    private static final String ENTRY_LABEL = "entry";
    private static final String EXIT_LABEL = "exit";
    private static final String STRATEGY_PACKAGE = "org.ta4j.core";
    private static final String UNSTABLE_BARS_KEY = "unstableBars";
    private static final String STARTING_TYPE_KEY = "startingType";
    private static final String ARGS_KEY = "__args";
    private static final String VERSION_KEY = "version";
    private static final String NAME_KEY = "name";
    private static final String TYPE_KEY = "type";
    private static final String ENTRY_RULE_KEY = "entryRule";
    private static final String EXIT_RULE_KEY = "exitRule";
    private static final String RULES_KEY = "rules";
    private static final String V2_ARGS_KEY = "args";
    private static final String DEFAULT_V2_STRATEGY_TYPE = "BaseStrategy";
    private static final int SUPPORTED_V2_VERSION = 2;
    private static final String VERSION_TOKEN = "\"" + VERSION_KEY + "\"";
    private static final Pattern JSON_NUMBER_LITERAL = Pattern
            .compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    private StrategySerialization() {
    }

    /**
     * Serializes a {@link Strategy} to a JSON payload.
     *
     * @param strategy strategy instance
     * @return JSON representation
     */
    public static String toJson(Strategy strategy) {
        return ComponentSerialization.toJson(describe(strategy));
    }

    /**
     * Converts a {@link Strategy} into a {@link ComponentDescriptor} hierarchy.
     *
     * @param strategy strategy instance
     * @return descriptor representing the strategy
     */
    public static ComponentDescriptor describe(Strategy strategy) {
        Objects.requireNonNull(strategy, "strategy");

        if (strategy instanceof NamedStrategy) {
            return strategy.toDescriptor();
        }

        ComponentDescriptor entryDescriptor = RuleSerialization.describe(strategy.getEntryRule());
        ComponentDescriptor exitDescriptor = RuleSerialization.describe(strategy.getExitRule());

        Class<?> strategyClass = strategy.getClass();
        String typeName = strategyClass.getPackageName().equals(STRATEGY_PACKAGE) ? strategyClass.getSimpleName()
                : strategyClass.getName();
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder().withType(typeName);

        String name = strategy.getName();
        if (name != null && !name.isBlank()) {
            builder.withLabel(name);
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(UNSTABLE_BARS_KEY, strategy.getUnstableBars());
        if (strategy.getStartingType() != TradeType.BUY) {
            parameters.put(STARTING_TYPE_KEY, strategy.getStartingType().name());
        }
        builder.withParameters(parameters);

        if (entryDescriptor != null) {
            builder.addComponent(applyLabel(entryDescriptor, ENTRY_LABEL));
        }
        if (exitDescriptor != null) {
            builder.addComponent(applyLabel(exitDescriptor, EXIT_LABEL));
        }

        return builder.build();
    }

    /**
     * Rebuilds a strategy from a JSON payload.
     *
     * @param series bar series to attach to the strategy
     * @param json   canonical JSON representation generated by
     *               {@link #toJson(Strategy)} or an opt-in {@code version: 2}
     *               authoring payload
     * @return reconstructed strategy
     */
    public static Strategy fromJson(BarSeries series, String json) {
        ComponentDescriptor v2Descriptor = tryParseV2Descriptor(series, json);
        if (v2Descriptor != null) {
            return fromDescriptor(series, v2Descriptor);
        }
        ComponentDescriptor descriptor = ComponentSerialization.parse(json);
        return fromDescriptor(series, descriptor);
    }

    private static ComponentDescriptor tryParseV2Descriptor(BarSeries series, String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        if (!json.contains(VERSION_TOKEN)) {
            return null;
        }

        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonSyntaxException ex) {
            return null;
        }

        if (!root.isJsonObject()) {
            return null;
        }

        JsonObject object = root.getAsJsonObject();
        if (!object.has(VERSION_KEY)) {
            return null;
        }
        if (!looksLikeV2Envelope(object)) {
            return null;
        }

        int version = readRequiredInt(object.get(VERSION_KEY), VERSION_KEY);
        if (version != SUPPORTED_V2_VERSION) {
            throw new IllegalArgumentException("Unsupported strategy JSON version: " + version);
        }

        Strategy strategy = buildV2Strategy(series, object);
        return describe(strategy);
    }

    private static Strategy buildV2Strategy(BarSeries series, JsonObject object) {
        String strategyType = readOptionalString(object, TYPE_KEY);
        if (strategyType != null && !DEFAULT_V2_STRATEGY_TYPE.equals(strategyType)
                && !BaseStrategy.class.getName().equals(strategyType)) {
            throw new IllegalArgumentException("Unsupported v2 strategy type: " + strategyType);
        }

        String name = readRequiredString(object, NAME_KEY);
        Rule entryRule = buildV2Rule(series, readRequiredObject(object, ENTRY_RULE_KEY), ENTRY_RULE_KEY);
        Rule exitRule = buildV2Rule(series, readRequiredObject(object, EXIT_RULE_KEY), EXIT_RULE_KEY);
        int unstableBars = readOptionalInt(object, UNSTABLE_BARS_KEY, 0);
        requireNonNegativeInt(unstableBars, UNSTABLE_BARS_KEY);
        TradeType startingType = readOptionalTradeType(object, STARTING_TYPE_KEY, TradeType.BUY);

        if (startingType == TradeType.BUY) {
            return new BaseStrategy(name, entryRule, exitRule, unstableBars);
        }
        return new BaseStrategy(name, entryRule, exitRule, unstableBars, startingType);
    }

    private static Rule buildV2Rule(BarSeries series, JsonObject object, String location) {
        String type = readRequiredString(object, TYPE_KEY, location + "." + TYPE_KEY);
        JsonElement rulesElement = object.get(RULES_KEY);
        if (rulesElement != null && !rulesElement.isJsonNull()) {
            rejectUnexpectedField(object, V2_ARGS_KEY, location + "." + V2_ARGS_KEY);
            JsonArray rulesArray = requireArray(rulesElement, location + "." + RULES_KEY);
            if (rulesArray.size() != 2) {
                throw new IllegalArgumentException(
                        "V2 composite rules require exactly 2 child rules at " + location + "." + RULES_KEY);
            }
            Rule left = buildV2Rule(series, requireObject(rulesArray.get(0), location + "." + RULES_KEY + "[0]"),
                    location + "." + RULES_KEY + "[0]");
            Rule right = buildV2Rule(series, requireObject(rulesArray.get(1), location + "." + RULES_KEY + "[1]"),
                    location + "." + RULES_KEY + "[1]");
            return switch (type) {
            case "AndRule" -> new AndRule(left, right);
            case "OrRule" -> new OrRule(left, right);
            default -> throw new IllegalArgumentException("Unsupported v2 composite rule type: " + type);
            };
        }

        JsonArray args = requireArray(object.get(V2_ARGS_KEY), location + "." + V2_ARGS_KEY);
        return switch (type) {
        case "CrossedUpIndicatorRule" -> buildCrossedUpRule(series, args, location);
        case "CrossedDownIndicatorRule" -> buildCrossedDownRule(series, args, location);
        case "OverIndicatorRule" -> buildOverRule(series, args, location);
        case "UnderIndicatorRule" -> buildUnderRule(series, args, location);
        case "StopLossRule" -> buildStopLossRule(series, args, location);
        case "StopGainRule" -> buildStopGainRule(series, args, location);
        default -> throw new IllegalArgumentException("Unsupported v2 rule type: " + type);
        };
    }

    private static Rule buildCrossedUpRule(BarSeries series, JsonArray args, String location) {
        ensureArgCount(args, 2, location);
        Indicator<Num> indicator = buildV2Indicator(series, args.get(0), location + ".args[0]");
        JsonElement thresholdOrIndicator = args.get(1);
        if (looksLikeIndicator(thresholdOrIndicator)) {
            Indicator<Num> secondIndicator = buildV2Indicator(series, thresholdOrIndicator, location + ".args[1]");
            return new CrossedUpIndicatorRule(indicator, secondIndicator);
        }
        return new CrossedUpIndicatorRule(indicator, parseNumericArgument(thresholdOrIndicator, location + ".args[1]"));
    }

    private static Rule buildCrossedDownRule(BarSeries series, JsonArray args, String location) {
        ensureArgCount(args, 2, location);
        Indicator<Num> indicator = buildV2Indicator(series, args.get(0), location + ".args[0]");
        JsonElement thresholdOrIndicator = args.get(1);
        if (looksLikeIndicator(thresholdOrIndicator)) {
            Indicator<Num> secondIndicator = buildV2Indicator(series, thresholdOrIndicator, location + ".args[1]");
            return new CrossedDownIndicatorRule(indicator, secondIndicator);
        }
        return new CrossedDownIndicatorRule(indicator,
                parseNumericArgument(thresholdOrIndicator, location + ".args[1]"));
    }

    private static Rule buildOverRule(BarSeries series, JsonArray args, String location) {
        ensureArgCount(args, 2, location);
        Indicator<Num> indicator = buildV2Indicator(series, args.get(0), location + ".args[0]");
        JsonElement thresholdOrIndicator = args.get(1);
        if (looksLikeIndicator(thresholdOrIndicator)) {
            Indicator<Num> secondIndicator = buildV2Indicator(series, thresholdOrIndicator, location + ".args[1]");
            return new OverIndicatorRule(indicator, secondIndicator);
        }
        return new OverIndicatorRule(indicator, parseNumericArgument(thresholdOrIndicator, location + ".args[1]"));
    }

    private static Rule buildUnderRule(BarSeries series, JsonArray args, String location) {
        ensureArgCount(args, 2, location);
        Indicator<Num> indicator = buildV2Indicator(series, args.get(0), location + ".args[0]");
        JsonElement thresholdOrIndicator = args.get(1);
        if (looksLikeIndicator(thresholdOrIndicator)) {
            Indicator<Num> secondIndicator = buildV2Indicator(series, thresholdOrIndicator, location + ".args[1]");
            return new UnderIndicatorRule(indicator, secondIndicator);
        }
        return new UnderIndicatorRule(indicator, parseNumericArgument(thresholdOrIndicator, location + ".args[1]"));
    }

    private static Rule buildStopLossRule(BarSeries series, JsonArray args, String location) {
        ensureArgCount(args, 1, location);
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        return new StopLossRule(closePriceIndicator, parseNumericArgument(args.get(0), location + ".args[0]"));
    }

    private static Rule buildStopGainRule(BarSeries series, JsonArray args, String location) {
        ensureArgCount(args, 1, location);
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        return new StopGainRule(closePriceIndicator, parseNumericArgument(args.get(0), location + ".args[0]"));
    }

    private static Indicator<Num> buildV2Indicator(BarSeries series, JsonElement element, String location) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Missing indicator expression at " + location);
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return buildV2IndicatorFromString(series, element.getAsString(), location);
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Unsupported v2 indicator payload at " + location + ": " + element);
        }

        JsonObject object = element.getAsJsonObject();
        String type = readRequiredString(object, TYPE_KEY, location + "." + TYPE_KEY);
        String normalizedType = normalizeIndicatorType(type);

        if ("ClosePriceIndicator".equals(normalizedType)) {
            rejectUnexpectedField(object, V2_ARGS_KEY, location + "." + V2_ARGS_KEY);
            return new ClosePriceIndicator(series);
        }

        JsonArray args = requireArray(object.get(V2_ARGS_KEY), location + "." + V2_ARGS_KEY);
        if (args.size() == 1) {
            Indicator<Num> closePriceIndicator = new ClosePriceIndicator(series);
            int barCount = readRequiredInt(args.get(0), location + ".args[0]");
            return instantiateParameterizedIndicator(normalizedType, closePriceIndicator, barCount, location,
                    location + ".args[0]");
        }
        if (args.size() == 2) {
            Indicator<Num> baseIndicator = buildV2Indicator(series, args.get(0), location + ".args[0]");
            int barCount = readRequiredInt(args.get(1), location + ".args[1]");
            return instantiateParameterizedIndicator(normalizedType, baseIndicator, barCount, location,
                    location + ".args[1]");
        }

        throw new IllegalArgumentException("Unsupported v2 indicator arg count at " + location + ": " + args.size());
    }

    private static Indicator<Num> buildV2IndicatorFromString(BarSeries series, String expression, String location) {
        String trimmed = expression == null ? null : expression.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new IllegalArgumentException("Empty indicator expression at " + location);
        }
        if ("ClosePrice".equals(trimmed) || "ClosePrice()".equals(trimmed) || "ClosePriceIndicator".equals(trimmed)) {
            return new ClosePriceIndicator(series);
        }

        int openParen = trimmed.indexOf('(');
        int closeParen = trimmed.lastIndexOf(')');
        if (openParen <= 0 || closeParen != trimmed.length() - 1) {
            throw new IllegalArgumentException("Unsupported v2 indicator expression: " + trimmed);
        }

        String type = normalizeIndicatorType(trimmed.substring(0, openParen).trim());
        String argumentText = trimmed.substring(openParen + 1, closeParen).trim();
        if (argumentText.isEmpty()) {
            throw new IllegalArgumentException("Missing indicator arguments in expression: " + trimmed);
        }

        Indicator<Num> closePriceIndicator = new ClosePriceIndicator(series);
        int barCount = parseInt(argumentText, location);
        return instantiateParameterizedIndicator(type, closePriceIndicator, barCount, location, location);
    }

    private static Indicator<Num> instantiateParameterizedIndicator(String type, Indicator<Num> baseIndicator,
            int barCount, String location, String barCountLocation) {
        return switch (type) {
        case "SMAIndicator" -> {
            requirePositiveInt(barCount, barCountLocation);
            yield new SMAIndicator(baseIndicator, barCount);
        }
        case "EMAIndicator" -> {
            requirePositiveInt(barCount, barCountLocation);
            yield new EMAIndicator(baseIndicator, barCount);
        }
        case "RSIIndicator" -> {
            requirePositiveInt(barCount, barCountLocation);
            yield new RSIIndicator(baseIndicator, barCount);
        }
        default -> throw new IllegalArgumentException("Unsupported v2 indicator type at " + location + ": " + type);
        };
    }

    private static String normalizeIndicatorType(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
        case "SMA" -> "SMAIndicator";
        case "EMA" -> "EMAIndicator";
        case "RSI" -> "RSIIndicator";
        case "ClosePrice" -> "ClosePriceIndicator";
        default -> type;
        };
    }

    private static boolean looksLikeIndicator(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonObject()) {
            return true;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return false;
        }
        String value = element.getAsString().trim();
        return "ClosePrice".equals(value) || "ClosePrice()".equals(value) || "ClosePriceIndicator".equals(value)
                || value.contains("(");
    }

    private static Number parseNumericArgument(JsonElement element, String location) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Missing numeric argument at " + location);
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return parseFiniteDouble(element.getAsString(), location);
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String text = element.getAsString().trim();
            if (text.endsWith("%")) {
                text = text.substring(0, text.length() - 1).trim();
            }
            return parseFiniteDouble(text, location);
        }
        throw new IllegalArgumentException("Unsupported numeric argument at " + location + ": " + element);
    }

    private static JsonObject readRequiredObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return requireObject(element, key);
    }

    private static JsonObject requireObject(JsonElement element, String location) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            throw new IllegalArgumentException("Expected object at " + location);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonElement element, String location) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            throw new IllegalArgumentException("Expected array at " + location);
        }
        return element.getAsJsonArray();
    }

    private static String readRequiredString(JsonObject object, String key) {
        return readRequiredString(object, key, key);
    }

    private static String readRequiredString(JsonObject object, String key, String location) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string at " + location);
        }
        String value = element.getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Expected non-blank string at " + location);
        }
        return value;
    }

    private static String readOptionalString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string at " + key);
        }
        String value = element.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    private static int readOptionalInt(JsonObject object, String key, int defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return readRequiredInt(element, key);
    }

    private static int readRequiredInt(JsonElement element, String location) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Missing integer value at " + location);
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return parseInt(element.getAsString(), location);
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return parseInt(element.getAsString().trim(), location);
        }
        throw new IllegalArgumentException("Expected integer value at " + location);
    }

    private static int parseInt(String value, String location) {
        String trimmed = value == null ? "" : value.trim();
        if (!isIntegerLiteral(trimmed)) {
            throw new IllegalArgumentException("Expected integer value at " + location + ": " + trimmed);
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected integer value at " + location + ": " + trimmed, ex);
        }
    }

    private static TradeType readOptionalTradeType(JsonObject object, String key, TradeType defaultValue) {
        String value = readOptionalString(object, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return TradeType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported trade type at " + key + ": " + value, ex);
        }
    }

    private static void ensureArgCount(JsonArray args, int expectedCount, String location) {
        if (args.size() != expectedCount) {
            throw new IllegalArgumentException(
                    "Expected " + expectedCount + " args at " + location + " but found " + args.size());
        }
    }

    private static void rejectUnexpectedField(JsonObject object, String key, String location) {
        if (object.has(key)) {
            throw new IllegalArgumentException("Unexpected field at " + location);
        }
    }

    private static boolean looksLikeV2Envelope(JsonObject object) {
        return object.has(NAME_KEY) || object.has(ENTRY_RULE_KEY) || object.has(EXIT_RULE_KEY);
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

    private static void requireNonNegativeInt(int value, String location) {
        if (value < 0) {
            throw new IllegalArgumentException("Expected integer value >= 0 at " + location + ": " + value);
        }
    }

    private static void requirePositiveInt(int value, String location) {
        if (value <= 0) {
            throw new IllegalArgumentException("Expected integer value > 0 at " + location + ": " + value);
        }
    }

    /**
     * Rebuilds a strategy from a descriptor tree.
     * <p>
     * If the specified strategy type cannot be instantiated (e.g., no matching
     * constructor is found), this method will silently fall back to creating a
     * {@link BaseStrategy} instance with the same entry/exit rules and parameters.
     * This fallback behavior may mask configuration issues where a specific
     * strategy type was expected but could not be constructed. Callers should
     * verify the returned strategy type matches expectations if strict type
     * checking is required.
     *
     * @param series     bar series to attach to the strategy
     * @param descriptor descriptor describing the strategy
     * @return reconstructed strategy (may be a {@link BaseStrategy} fallback if the
     *         specified type could not be instantiated)
     */
    public static Strategy fromDescriptor(BarSeries series, ComponentDescriptor descriptor) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(descriptor, "descriptor");

        String descriptorType = descriptor.getType();
        if (NamedStrategy.SERIALIZED_TYPE.equals(descriptorType)
                || NamedStrategy.class.getName().equals(descriptorType)) {
            return instantiateNamedStrategy(series, descriptor, null);
        }

        Class<? extends Strategy> strategyType = resolveStrategyClass(descriptorType);
        if (NamedStrategy.class.isAssignableFrom(strategyType)) {
            return instantiateNamedStrategy(series, descriptor, strategyType);
        }

        // Create a Strategy-level context that contains all Strategy components
        // This allows rule deserialization to resolve Strategy-level indicators and
        // rules
        // For now, pass null as parent context - rule components contain all their
        // dependencies
        // and don't need Strategy-level resolution during constructor matching
        Rule entryRule = instantiateRule(series, extractChild(descriptor, ENTRY_LABEL), null);
        Rule exitRule = instantiateRule(series, extractChild(descriptor, EXIT_LABEL), null);

        String name = descriptor.getLabel();
        int unstableBars = extractUnstableBars(descriptor.getParameters().get(UNSTABLE_BARS_KEY));
        TradeType startingType = extractStartingType(descriptor.getParameters().get(STARTING_TYPE_KEY));

        Strategy strategy = instantiateStrategy(strategyType, name, entryRule, exitRule, unstableBars, startingType);
        strategy.setUnstableBars(unstableBars);
        return strategy;
    }

    private static ComponentDescriptor applyLabel(ComponentDescriptor descriptor, String label) {
        if (descriptor == null) {
            return null;
        }
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder()
                .withType(descriptor.getType())
                .withLabel(label);
        if (!descriptor.getParameters().isEmpty()) {
            builder.withParameters(descriptor.getParameters());
        }
        for (ComponentDescriptor component : descriptor.getComponents()) {
            builder.addComponent(component);
        }
        return builder.build();
    }

    private static ComponentDescriptor extractChild(ComponentDescriptor descriptor, String label) {
        for (ComponentDescriptor component : descriptor.getComponents()) {
            if (label.equals(component.getLabel())) {
                return cloneWithoutLabel(component);
            }
        }
        throw new IllegalArgumentException("Missing strategy " + label + " rule descriptor");
    }

    private static ComponentDescriptor cloneWithoutLabel(ComponentDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder().withType(descriptor.getType());
        if (!descriptor.getParameters().isEmpty()) {
            builder.withParameters(descriptor.getParameters());
        }
        for (ComponentDescriptor component : descriptor.getComponents()) {
            builder.addComponent(component);
        }
        return builder.build();
    }

    private static int extractUnstableBars(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static TradeType extractStartingType(Object value) {
        if (value == null) {
            return TradeType.BUY;
        }
        if (value instanceof TradeType tradeType) {
            return tradeType;
        }
        try {
            return TradeType.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TradeType.BUY;
        }
    }

    private static Rule instantiateRule(BarSeries series, ComponentDescriptor descriptor) {
        return instantiateRule(series, descriptor, null);
    }

    private static Rule instantiateRule(BarSeries series, ComponentDescriptor descriptor,
            RuleSerialization.ReconstructionContext parentContext) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Rule descriptor cannot be null");
        }

        // Check if this is a rule by type name (contains "Rule")
        String type = descriptor.getType();
        if (type != null && type.contains("Rule")) {
            return RuleSerialization.fromDescriptor(series, descriptor, parentContext);
        }

        // Legacy check for __args (for backwards compatibility)
        if (descriptor.getParameters().containsKey(ARGS_KEY)) {
            return RuleSerialization.fromDescriptor(series, descriptor, parentContext);
        }

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Rule descriptor missing type: " + descriptor);
        }
        Class<?> clazz = resolveRuleClass(type);
        if (!Rule.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Descriptor type does not implement Rule: " + type);
        }
        @SuppressWarnings("unchecked")
        Class<? extends Rule> ruleType = (Class<? extends Rule>) clazz;

        Optional<Rule> instance = invokeFactory(ruleType, series, descriptor);
        if (instance.isPresent()) {
            return instance.get();
        }

        if (!descriptor.getComponents().isEmpty()) {
            List<Rule> components = new ArrayList<>(descriptor.getComponents().size());
            for (ComponentDescriptor component : descriptor.getComponents()) {
                components.add(instantiateRule(series, component, parentContext));
            }
            Optional<Rule> composite = tryCompositeConstructor(ruleType, components);
            if (composite.isPresent()) {
                return composite.get();
            }
        }

        Optional<Rule> constructed = tryDescriptorConstructor(ruleType, series, descriptor);
        if (constructed.isPresent()) {
            return constructed.get();
        }

        throw new IllegalArgumentException("Unable to instantiate rule type: " + type);
    }

    private static Optional<Rule> invokeFactory(Class<? extends Rule> ruleType, BarSeries series,
            ComponentDescriptor descriptor) {
        try {
            Method factory = ruleType.getDeclaredMethod("fromDescriptor", BarSeries.class, ComponentDescriptor.class);
            factory.setAccessible(true);
            return Optional.of((Rule) factory.invoke(null, series, descriptor));
        } catch (NoSuchMethodException ex) {
            // ignore and try the next option
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to invoke factory on rule type: " + ruleType.getName(), ex);
        }

        try {
            Method factory = ruleType.getDeclaredMethod("fromDescriptor", ComponentDescriptor.class);
            factory.setAccessible(true);
            return Optional.of((Rule) factory.invoke(null, descriptor));
        } catch (NoSuchMethodException ex) {
            // ignore and try next
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to invoke factory on rule type: " + ruleType.getName(), ex);
        }

        return Optional.empty();
    }

    private static Optional<Rule> tryCompositeConstructor(Class<? extends Rule> ruleType, List<Rule> components) {
        Constructor<?>[] constructors = ruleType.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != components.size()) {
                continue;
            }
            boolean matches = true;
            for (Class<?> parameterType : parameterTypes) {
                if (!Rule.class.isAssignableFrom(parameterType)) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            try {
                constructor.setAccessible(true);
                return Optional.of((Rule) constructor.newInstance(components.toArray()));
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalStateException("Failed to construct composite rule: " + ruleType.getName(), ex);
            }
        }
        return Optional.empty();
    }

    private static Optional<Rule> tryDescriptorConstructor(Class<? extends Rule> ruleType, BarSeries series,
            ComponentDescriptor descriptor) {
        try {
            Constructor<? extends Rule> constructor = ruleType.getDeclaredConstructor(BarSeries.class,
                    ComponentDescriptor.class);
            constructor.setAccessible(true);
            return Optional.of(constructor.newInstance(series, descriptor));
        } catch (NoSuchMethodException ex) {
            // ignore
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct rule: " + ruleType.getName(), ex);
        }

        try {
            Constructor<? extends Rule> constructor = ruleType.getDeclaredConstructor(ComponentDescriptor.class);
            constructor.setAccessible(true);
            return Optional.of(constructor.newInstance(descriptor));
        } catch (NoSuchMethodException ex) {
            // ignore
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct rule: " + ruleType.getName(), ex);
        }

        try {
            Constructor<? extends Rule> constructor = ruleType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return Optional.of(constructor.newInstance());
        } catch (NoSuchMethodException ex) {
            // ignore
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct rule: " + ruleType.getName(), ex);
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Rule> resolveRuleClass(String type) {
        try {
            return (Class<? extends Rule>) Class.forName(type);
        } catch (ClassNotFoundException ex) {
            // try ta4j core rules package
        }
        try {
            return (Class<? extends Rule>) Class.forName("org.ta4j.core.rules." + type);
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Unknown rule type: " + type, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Strategy> resolveStrategyClass(String type) {
        if (type == null || type.isBlank()) {
            return BaseStrategy.class;
        }
        try {
            Class<?> clazz = Class.forName(type);
            if (Strategy.class.isAssignableFrom(clazz)) {
                return (Class<? extends Strategy>) clazz;
            }
        } catch (ClassNotFoundException ex) {
            // ignore and try package-local lookup
        }
        try {
            Class<?> clazz = Class.forName(STRATEGY_PACKAGE + '.' + type);
            if (Strategy.class.isAssignableFrom(clazz)) {
                return (Class<? extends Strategy>) clazz;
            }
        } catch (ClassNotFoundException ex) {
            // ignore and fall back to BaseStrategy
        }
        return BaseStrategy.class;
    }

    /**
     * Attempts to instantiate a strategy of the specified type using various
     * constructor patterns.
     * <p>
     * This method tries multiple constructor signatures in order:
     * <ol>
     * <li>{@code (String, Rule, Rule, int, TradeType)} - name, entry, exit,
     * unstableBars, startingType</li>
     * <li>{@code (String, Rule, Rule, TradeType)} - name, entry, exit, startingType
     * (unstableBars set via setter)</li>
     * <li>{@code (Rule, Rule, int, TradeType)} - entry, exit, unstableBars,
     * startingType</li>
     * <li>{@code (Rule, Rule, TradeType)} - entry, exit, startingType (unstableBars
     * set via setter)</li>
     * <li>{@code (String, Rule, Rule, int)} - name, entry, exit, unstableBars</li>
     * <li>{@code (String, Rule, Rule)} - name, entry, exit (unstableBars set via
     * setter)</li>
     * <li>{@code (Rule, Rule, int)} - entry, exit, unstableBars</li>
     * <li>{@code (Rule, Rule)} - entry, exit (unstableBars set via setter)</li>
     * </ol>
     * <p>
     * <strong>Fallback Behavior:</strong> If none of the above constructors are
     * found and the requested type is not {@link BaseStrategy}, this method will
     * silently create a {@link BaseStrategy} instance instead. This fallback
     * provides resilience but may mask configuration issues where a specific
     * strategy type was expected. The returned instance will have the same entry
     * rule, exit rule, name, and unstableBars value, but will be of type
     * {@code BaseStrategy} rather than the requested type.
     * <p>
     * If the requested type is already {@code BaseStrategy} and no suitable
     * constructor is found, an {@link IllegalStateException} is thrown.
     *
     * @param strategyType the class of strategy to instantiate
     * @param name         strategy name (may be null)
     * @param entryRule    entry rule (required)
     * @param exitRule     exit rule (required)
     * @param unstableBars number of unstable bars
     * @param startingType strategy entry trade type
     * @return instantiated strategy (may be a {@link BaseStrategy} fallback if the
     *         requested type could not be instantiated)
     * @throws IllegalStateException if the requested type is {@code BaseStrategy}
     *                               and no suitable constructor is found
     */
    private static Strategy instantiateStrategy(Class<? extends Strategy> strategyType, String name, Rule entryRule,
            Rule exitRule, int unstableBars, TradeType startingType) {
        try {
            Constructor<? extends Strategy> constructor = strategyType.getDeclaredConstructor(String.class, Rule.class,
                    Rule.class, int.class, TradeType.class);
            constructor.setAccessible(true);
            return constructor.newInstance(name, entryRule, exitRule, unstableBars, startingType);
        } catch (NoSuchMethodException ex) {
            // ignore and try the next options
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct strategy: " + strategyType.getName(), ex);
        }

        try {
            Constructor<? extends Strategy> constructor = strategyType.getDeclaredConstructor(String.class, Rule.class,
                    Rule.class, TradeType.class);
            constructor.setAccessible(true);
            Strategy strategy = constructor.newInstance(name, entryRule, exitRule, startingType);
            strategy.setUnstableBars(unstableBars);
            return strategy;
        } catch (NoSuchMethodException ex) {
            // ignore and try the next options
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct strategy: " + strategyType.getName(), ex);
        }

        try {
            Constructor<? extends Strategy> constructor = strategyType.getDeclaredConstructor(Rule.class, Rule.class,
                    int.class, TradeType.class);
            constructor.setAccessible(true);
            return constructor.newInstance(entryRule, exitRule, unstableBars, startingType);
        } catch (NoSuchMethodException ex) {
            // ignore and try the next options
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct strategy: " + strategyType.getName(), ex);
        }

        try {
            Constructor<? extends Strategy> constructor = strategyType.getDeclaredConstructor(Rule.class, Rule.class,
                    TradeType.class);
            constructor.setAccessible(true);
            Strategy strategy = constructor.newInstance(entryRule, exitRule, startingType);
            strategy.setUnstableBars(unstableBars);
            return strategy;
        } catch (NoSuchMethodException ex) {
            // ignore and try the next options
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct strategy: " + strategyType.getName(), ex);
        }

        try {
            Constructor<? extends Strategy> constructor = strategyType.getDeclaredConstructor(String.class, Rule.class,
                    Rule.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(name, entryRule, exitRule, unstableBars);
        } catch (NoSuchMethodException ex) {
            // ignore and try the next options
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct strategy: " + strategyType.getName(), ex);
        }

        try {
            Constructor<? extends Strategy> constructor = strategyType.getDeclaredConstructor(String.class, Rule.class,
                    Rule.class);
            constructor.setAccessible(true);
            Strategy strategy = constructor.newInstance(name, entryRule, exitRule);
            strategy.setUnstableBars(unstableBars);
            return strategy;
        } catch (NoSuchMethodException ex) {
            // ignore
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct strategy: " + strategyType.getName(), ex);
        }

        try {
            Constructor<? extends Strategy> constructor = strategyType.getDeclaredConstructor(Rule.class, Rule.class,
                    int.class);
            constructor.setAccessible(true);
            Strategy strategy = constructor.newInstance(entryRule, exitRule, unstableBars);
            return strategy;
        } catch (NoSuchMethodException ex) {
            // ignore
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct strategy: " + strategyType.getName(), ex);
        }

        try {
            Constructor<? extends Strategy> constructor = strategyType.getDeclaredConstructor(Rule.class, Rule.class);
            constructor.setAccessible(true);
            Strategy strategy = constructor.newInstance(entryRule, exitRule);
            strategy.setUnstableBars(unstableBars);
            return strategy;
        } catch (NoSuchMethodException ex) {
            // ignore
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct strategy: " + strategyType.getName(), ex);
        }

        // Fallback: If the requested strategy type cannot be instantiated, create a
        // BaseStrategy instead. This provides resilience but may mask configuration
        // issues where a specific strategy type was expected. The fallback preserves
        // the entry/exit rules and parameters but changes the strategy type.
        if (!strategyType.equals(BaseStrategy.class)) {
            return new BaseStrategy(name, entryRule, exitRule, unstableBars, startingType);
        }
        throw new IllegalStateException("No suitable constructor found for strategy type: " + strategyType.getName());
    }

    private static Strategy instantiateNamedStrategy(BarSeries series, ComponentDescriptor descriptor,
            Class<? extends Strategy> resolvedType) {
        String label = descriptor.getLabel();
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException(
                    "Named strategy descriptor missing label (type=" + descriptor.getType() + ")");
        }

        List<String> labelTokens = NamedStrategy.splitLabel(label);
        if (labelTokens.isEmpty()) {
            throw new IllegalArgumentException(
                    "Named strategy label missing strategy identifier: label='" + label + "'");
        }

        String simpleName = labelTokens.get(0);
        if (simpleName == null || simpleName.isBlank()) {
            throw new IllegalArgumentException(
                    "Named strategy label missing strategy identifier (leading underscore or empty token): label='"
                            + label + "'");
        }

        String[] parameters = labelTokens.size() == 1 ? new String[0]
                : labelTokens.subList(1, labelTokens.size()).toArray(new String[0]);

        Class<? extends NamedStrategy> strategyType = resolveNamedStrategyType(simpleName, resolvedType);
        Constructor<? extends Strategy> constructor = findNamedStrategyConstructor(strategyType);
        try {
            return constructor.newInstance(new Object[] { series, parameters });
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new IllegalStateException(
                    "Failed to construct named strategy: " + strategyType.getName() + " (label='" + label + "')", ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                throw new IllegalArgumentException("Named strategy reconstruction failed for label='" + label
                        + "' params=" + Arrays.toString(parameters) + ": " + cause.getMessage(), cause);
            }
            throw new IllegalStateException(
                    "Failed to construct named strategy: " + strategyType.getName() + " (label='" + label + "')",
                    cause);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends NamedStrategy> resolveNamedStrategyType(String simpleName,
            Class<? extends Strategy> resolvedType) {
        if (resolvedType != null && resolvedType != NamedStrategy.class
                && NamedStrategy.class.isAssignableFrom(resolvedType)) {
            return (Class<? extends NamedStrategy>) resolvedType;
        }
        return NamedStrategy.requireRegistered(simpleName);
    }

    private static Constructor<? extends Strategy> findNamedStrategyConstructor(
            Class<? extends NamedStrategy> strategyType) {
        try {
            Constructor<? extends Strategy> constructor = strategyType.getDeclaredConstructor(BarSeries.class,
                    String[].class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(
                    "Named strategy missing (BarSeries, String...) constructor: " + strategyType.getName(), ex);
        }
    }

}
