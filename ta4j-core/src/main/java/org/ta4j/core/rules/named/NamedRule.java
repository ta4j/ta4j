/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.named;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.named.NamedComponentRegistry;
import org.ta4j.core.rules.AbstractRule;

import java.util.List;
import java.util.Optional;

/**
 * Base class for rules that can be reconstructed from compact name tokens.
 *
 * <p>
 * Named rules follow the same compact-label convention as
 * {@code NamedStrategy}: the label starts with the simple class name followed
 * by underscore-delimited constructor parameters. The label is fixed for the
 * lifetime of the rule and is used as the canonical rule name in logs, JSON,
 * and CLI inputs.
 * </p>
 *
 * <p>
 * Implementations must provide a {@code (BarSeries, String...)} constructor
 * that parses the compact label parameters and delegates to a strongly typed
 * constructor. The strongly typed constructor should call
 * {@link #NamedRule(String)} with a label generated via
 * {@link #buildLabel(Class, String...)}. Implementations must also register
 * their concrete type in a static initializer with
 * {@link #registerImplementation(Class)}.
 * </p>
 *
 * <p>
 * Unlike {@code NamedStrategy}, named rules serialize as their concrete rule
 * type while preserving the compact label as the rule name. This keeps rule
 * serialization compatible with the existing rule descriptor format.
 * </p>
 *
 * <p>
 * Registry bookkeeping (package scanning, class loading, registration, and
 * label handling) is shared with named strategies through
 * {@link NamedComponentRegistry}.
 * </p>
 *
 * @since 0.24.2
 */
public abstract class NamedRule extends AbstractRule {

    private static final NamedComponentRegistry<NamedRule> REGISTRY = new NamedComponentRegistry<>(NamedRule.class,
            "rule", "NamedRule", NamedRule.class, "org.ta4j.core.rules.named");

    private final String label;

    /**
     * Protected constructor that fixes the rule label.
     *
     * @param label compact label produced by {@link #buildLabel(Class, String...)}
     *              and used for lookup and serialization
     * @since 0.24.2
     */
    protected NamedRule(String label) {
        this.label = label;
    }

    /**
     * Ensures core packages have been scanned and registers any discovered named
     * rules. Concurrent initialization calls wait for an in-progress scan to finish
     * before returning.
     *
     * @param basePackages optional extra packages to scan
     * @since 0.24.2
     */
    public static void initializeRegistry(String... basePackages) {
        REGISTRY.initializeRegistry(basePackages);
    }

    /**
     * Registers a named rule implementation.
     *
     * @param type named rule subtype
     * @throws IllegalArgumentException when the type name cannot form a valid
     *                                  compact label
     * @since 0.24.2
     */
    public static void registerImplementation(Class<? extends NamedRule> type) {
        REGISTRY.registerImplementation(type);
    }

    /**
     * Unregisters a named rule implementation. This is primarily intended for
     * tests.
     *
     * @param type named rule subtype
     * @return {@code true} when the rule was removed
     * @since 0.24.2
     */
    public static boolean unregisterImplementation(Class<? extends NamedRule> type) {
        return REGISTRY.unregisterImplementation(type);
    }

    /**
     * Resolves a registered named rule type, initializing the default registry
     * first so rules registered through the default package scan are visible to a
     * plain lookup.
     *
     * @param simpleName simple class name
     * @return optional containing the registered type
     * @since 0.24.2
     */
    public static Optional<Class<? extends NamedRule>> lookup(String simpleName) {
        return REGISTRY.lookup(simpleName);
    }

    /**
     * Builds a compact label using the simple class name and optional parameters.
     *
     * @param type       concrete named rule type
     * @param parameters constructor parameters encoded as strings
     * @return compact rule label
     * @throws IllegalArgumentException when the rule type is anonymous or has a
     *                                  blank simple name, or when the rule type or
     *                                  a parameter contains the underscore label
     *                                  delimiter
     * @since 0.24.2
     */
    public static String buildLabel(Class<? extends NamedRule> type, String... parameters) {
        return REGISTRY.buildLabel(type, parameters);
    }

    /**
     * Splits a compact label into the simple class name and parameter tokens.
     *
     * @param label serialized label
     * @return immutable token list
     * @since 0.24.2
     */
    public static List<String> splitLabel(String label) {
        return REGISTRY.splitLabel(label);
    }

    /**
     * Resolves a registered named rule type or throws a descriptive error.
     *
     * @param simpleName named rule simple class name
     * @return registered type
     * @since 0.24.2
     */
    public static Class<? extends NamedRule> requireRegistered(String simpleName) {
        return REGISTRY.requireRegistered(simpleName);
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
     * Keeps the reconstruction label fixed by ignoring rename attempts.
     *
     * @param name ignored because named-rule labels are reconstruction-critical
     * @since 0.24.2
     */
    @Override
    public final void setName(String name) {
        // NamedRule labels are reconstruction-critical and stay fixed.
    }

    /**
     * Returns the compact reconstruction label.
     *
     * @return compact rule label
     * @since 0.24.2
     */
    @Override
    public final String getName() {
        return label;
    }

    /**
     * Indicates that named rules always expose their compact label as a custom
     * name.
     *
     * @return always {@code true}
     * @since 0.24.2
     */
    @Override
    public boolean hasCustomName() {
        return true;
    }

    /**
     * Returns the compact reconstruction label as the default name.
     *
     * @return compact rule label
     * @since 0.24.2
     */
    @Override
    protected final String createDefaultName() {
        return label;
    }

    /**
     * Returns the compact reconstruction label as the trace display name so
     * named-rule evaluations are logged under their label rather than the concrete
     * implementation class.
     *
     * @return compact rule label used in trace logs
     * @since 0.24.2
     */
    @Override
    protected final String getTraceDisplayName() {
        return label;
    }

    /**
     * Evaluates the delegate rule as a trace child and traces the outer named-rule
     * result under the compact label.
     *
     * @param delegateRule  delegate rule performing the actual condition check
     * @param index         the bar index
     * @param tradingRecord trading history
     * @return true if the delegate rule is satisfied
     * @since 0.24.2
     */
    protected final boolean evaluateDelegate(Rule delegateRule, int index, TradingRecord tradingRecord) {
        boolean satisfied = evaluateChildRule(delegateRule, "delegate", index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
