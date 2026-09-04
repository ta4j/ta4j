/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.strategy.named;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.named.NamedComponentRegistry;
import org.ta4j.core.serialization.ComponentDescriptor;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Base class for strategies that can be reconstructed from compact name tokens.
 *
 * <h2>Compact Name Format</h2>
 * <p>
 * The compact name (label) must conform to the format:
 * </p>
 *
 * <pre>{@code <SimpleName>_<param1>_<param2>_...}</pre>
 * <p>
 * Where:
 * </p>
 * <ul>
 * <li><strong>SimpleName</strong>: The simple class name (without package) of
 * the strategy implementation</li>
 * <li><strong>Parameters</strong>: Zero or more parameter values separated by
 * underscores, where each parameter is a string representation of a constructor
 * argument. <strong>Underscores are reserved as delimiters and are not
 * permitted inside parameter values.</strong></li>
 * </ul>
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li>{@code "MyStrategy"} - No parameters</li>
 * <li>{@code "MyStrategy_10"} - Single integer parameter</li>
 * <li>{@code "MyStrategy_10_0.5"} - Two parameters (integer and decimal)</li>
 * <li>{@code "DayOfWeekStrategy_MONDAY_FRIDAY"} - Two enum parameters</li>
 * </ul>
 * <p>
 * Use {@link #buildLabel(Class, String...)} when constructing the superclass to
 * guarantee consistent token formatting. The label must encode every piece of
 * information required to reconstruct the instance, including unstable bar
 * counts if they vary.
 * </p>
 *
 * <h2>Constructor Requirements</h2>
 * <p>
 * <strong>Every {@code NamedStrategy} implementation must provide a constructor
 * that accepts the compact name format:</strong>
 * </p>
 *
 * <pre>{@code
 * public YourStrategy(BarSeries series, String... parameters) {
 *     // Parse parameters and delegate to main constructor
 *     this(series, parseParam1(parameters), parseParam2(parameters), ...);
 * }
 * }</pre>
 * <p>
 * This constructor must be able to parse the parameter tokens (obtained by
 * splitting the label on underscores after the simple name) and reconstruct the
 * strategy instance. The serialization layer will invoke this constructor when
 * deserializing strategies from JSON or other formats.
 * </p>
 * <p>
 * Best practice: Parse parameters inside the varargs constructor and delegate
 * to a strongly-typed constructor to avoid duplicating rule-building logic.
 * Validate inputs eagerly and throw informative
 * {@link IllegalArgumentException}s for bad parameters.
 * </p>
 *
 * <h2>Registration</h2>
 * <p>
 * Named strategies must be registered before they can be deserialized. There
 * are two approaches:
 * </p>
 *
 * <h3>Manual Registration (Recommended for Custom Strategies)</h3>
 * <p>
 * Register each strategy class in a static initializer:
 * </p>
 *
 * <pre>{@code
 * public class MyStrategy extends NamedStrategy {
 *     static {
 *         registerImplementation(MyStrategy.class);
 *     }
 *     // ... constructors and implementation
 * }
 * }</pre>
 *
 * <h3>Automatic Package Scanning</h3>
 * <p>
 * For projects with many named strategies, you can scan entire packages at
 * application startup:
 * </p>
 *
 * <pre>{@code
 * // Scan default Ta4j packages only
 * NamedStrategy.initializeRegistry();
 *
 * // Scan default packages plus your custom packages
 * NamedStrategy.initializeRegistry("com.mycompany.strategies", "com.mycompany.trading");
 * }</pre>
 * <p>
 * Package scanning automatically discovers and registers all non-abstract
 * classes extending {@code NamedStrategy} in the specified packages. The
 * default package {@code "org.ta4j.core.strategy.named"} is always scanned
 * automatically on first use.
 * </p>
 * <p>
 * <strong>Note:</strong> Package scanning works for both file-based and
 * JAR-based class loading, but requires that classes are on the classpath at
 * runtime.
 * </p>
 *
 * <h2>Serialization</h2>
 * <p>
 * When serialized to JSON (via {@link #toDescriptor()}), the strategy type is
 * always {@link #SERIALIZED_TYPE}, and the label field contains the compact
 * name. The deserialization layer uses {@link #splitLabel(String)} to extract
 * the simple class name and parameters, then looks up the registered type and
 * invokes the varargs constructor.
 * </p>
 *
 * <h2>Example Implementation</h2>
 *
 * <pre>{@code
 * public class MovingAverageStrategy extends NamedStrategy {
 *     static {
 *         registerImplementation(MovingAverageStrategy.class);
 *     }
 *
 *     private final int shortPeriod;
 *     private final int longPeriod;
 *
 *     // Strongly-typed constructor
 *     public MovingAverageStrategy(BarSeries series, int shortPeriod, int longPeriod) {
 *         super(buildLabel(MovingAverageStrategy.class, String.valueOf(shortPeriod), String.valueOf(longPeriod)),
 *                 buildEntryRule(series, shortPeriod, longPeriod), buildExitRule(series, shortPeriod, longPeriod));
 *         this.shortPeriod = shortPeriod;
 *         this.longPeriod = longPeriod;
 *     }
 *
 *     // Varargs constructor for deserialization
 *     public MovingAverageStrategy(BarSeries series, String... parameters) {
 *         this(series, Integer.parseInt(parameters[0]), Integer.parseInt(parameters[1]));
 *     }
 *
 *     // ... rule building methods
 * }
 * }</pre>
 *
 * @since 0.19
 */
public abstract class NamedStrategy extends BaseStrategy {

    /**
     * JSON {@code type} written by {@link #toDescriptor()}.
     */
    public static final String SERIALIZED_TYPE = NamedStrategy.class.getSimpleName();

    private static final NamedComponentRegistry<NamedStrategy> REGISTRY = new NamedComponentRegistry<>(
            NamedStrategy.class, "strategy", "NamedStrategy", NamedStrategy.class, "org.ta4j.core.strategy.named");

    /**
     * Protected constructor that allows subclasses to provide the fully formatted
     * label (and therefore {@link Strategy#getName()}).
     *
     * @param label        strategy label that also serves as the serialized value
     * @param entryRule    entry rule
     * @param exitRule     exit rule
     * @param unstableBars unstable bars
     */
    protected NamedStrategy(String label, Rule entryRule, Rule exitRule, int unstableBars) {
        super(label, entryRule, exitRule, unstableBars);
        registerImplementation(getClass());
    }

    /**
     * Protected constructor that defaults {@code unstableBars} to {@code 0}.
     *
     * @param label     strategy label that also serves as the serialized value
     * @param entryRule entry rule
     * @param exitRule  exit rule
     */
    protected NamedStrategy(String label, Rule entryRule, Rule exitRule) {
        super(label, entryRule, exitRule);
        registerImplementation(getClass());
    }

    /**
     * Ensures core packages have been scanned and registers any discovered named
     * strategies.
     */
    public static void initializeRegistry(String... basePackages) {
        REGISTRY.initializeRegistry(basePackages);
    }

    /**
     * Registers a {@link NamedStrategy} implementation so it can be reconstructed
     * purely from its compact label. Custom strategies should invoke this method
     * during application startup (typically from a static initializer).
     *
     * @param type strategy subtype
     */
    public static void registerImplementation(Class<? extends NamedStrategy> type) {
        REGISTRY.registerImplementation(type);
    }

    /**
     * Unregisters a previously registered {@link NamedStrategy} implementation.
     * This method is primarily intended for testing purposes to allow cleanup of
     * test fixtures that register strategies via static initializers.
     *
     * @param type strategy subtype to unregister
     * @return {@code true} if the strategy was registered and has been removed,
     *         {@code false} if it was not registered
     */
    public static boolean unregisterImplementation(Class<? extends NamedStrategy> type) {
        return REGISTRY.unregisterImplementation(type);
    }

    /**
     * Resolves a registered named strategy type, initializing the default registry
     * first so strategies registered through the default package scan are visible
     * to a plain lookup.
     *
     * @param simpleName simple class name (without package)
     * @return optional containing the registered type
     */
    public static Optional<Class<? extends NamedStrategy>> lookup(String simpleName) {
        return REGISTRY.lookup(simpleName);
    }

    /**
     * Builds the serialized label using the simple class name and optional
     * parameters.
     *
     * @param type       concrete strategy type
     * @param parameters constructor parameters encoded as strings
     * @return compact strategy label
     */
    public static String buildLabel(Class<? extends NamedStrategy> type, String... parameters) {
        return REGISTRY.buildLabel(type, parameters);
    }

    /**
     * Splits a serialized label into the simple class name and parameter tokens.
     *
     * @param label serialized label
     * @return immutable token list where index {@code 0} is the simple class name
     * @throws IllegalArgumentException if label is null or blank
     */
    public static List<String> splitLabel(String label) {
        return REGISTRY.splitLabel(label);
    }

    /**
     * Builds strategies for every provided parameter permutation.
     *
     * @param series                backing bar series
     * @param parameterPermutations ordered permutations of constructor parameters
     * @param factory               factory responsible for instantiating the
     *                              strategy
     * @param <T>                   concrete named strategy type
     * @return list of instantiated strategies
     */
    public static <T extends NamedStrategy> List<Strategy> buildAllStrategyPermutations(BarSeries series,
            Iterable<String[]> parameterPermutations, Factory<T> factory) {
        return buildAllStrategyPermutations(series, parameterPermutations, factory, null);
    }

    /**
     * Builds strategies for every provided parameter permutation.
     *
     * @param series                backing bar series
     * @param parameterPermutations ordered permutations of constructor parameters
     * @param factory               factory responsible for instantiating the
     *                              strategy
     * @param failureHandler        optional handler that receives the parameter
     *                              snapshot alongside the
     *                              {@link IllegalArgumentException} thrown by the
     *                              factory. When {@code null} the exception is
     *                              rethrown.
     * @param <T>                   concrete named strategy type
     * @return list of instantiated strategies
     */
    public static <T extends NamedStrategy> List<Strategy> buildAllStrategyPermutations(BarSeries series,
            Iterable<String[]> parameterPermutations, Factory<T> factory,
            BiConsumer<String[], IllegalArgumentException> failureHandler) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(parameterPermutations, "parameterPermutations");
        Objects.requireNonNull(factory, "factory");

        List<Strategy> strategies = new ArrayList<>();
        for (String[] parameters : parameterPermutations) {
            if (parameters == null) {
                throw new IllegalArgumentException("Parameter entry cannot be null");
            }
            String[] args = Arrays.copyOf(parameters, parameters.length);
            try {
                strategies.add(factory.create(series, args));
            } catch (IllegalArgumentException ex) {
                if (failureHandler == null) {
                    throw ex;
                }
                failureHandler.accept(Arrays.copyOf(args, args.length), ex);
            }
        }
        return strategies;
    }

    /**
     * Restores the default-scan baseline for tests: clears the scanned-package set
     * and the initialized flag so the next {@link #initializeRegistry(String...)}
     * or default lookup re-scans. Registered implementations are not removed.
     */
    static void resetRegistryStateForTests() {
        REGISTRY.resetScanState();
    }

    /**
     * Helper used by the serialization layer to enforce that a strategy has been
     * registered.
     *
     * @param simpleName named strategy simple class name
     * @return registered type
     */
    public static Class<? extends NamedStrategy> requireRegistered(String simpleName) {
        return REGISTRY.requireRegistered(simpleName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        return ComponentDescriptor.builder().withType(SERIALIZED_TYPE).withLabel(getName()).build();
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Factory interface used by
     * {@link #buildAllStrategyPermutations(BarSeries, Iterable, Factory)}.
     *
     * @param <T> concrete named strategy type
     */
    @FunctionalInterface
    public interface Factory<T extends NamedStrategy> {
        T create(BarSeries series, String... parameters);
    }
}
