/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.NotRule;
import org.ta4j.core.rules.OrRule;
import org.ta4j.core.rules.XorRule;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.RuleSerialization;

/**
 * A rule (also called "trading rule") used to build a {@link Strategy trading
 * strategy}. A trading rule can consist of a combination of other rules.
 */
public interface Rule {

    /**
     * Controls trace logging behavior for rule evaluation.
     *
     * @since 0.22.7
     */
    enum TraceMode {
        /** Do not emit trace logs. */
        OFF,
        /**
         * Emit trace logs only for this rule while evaluating children in a scoped
         * {@link #OFF} context without mutating child rule instances.
         */
        ROLLUP,
        /** Emit trace logs for this rule and all children in an evaluation scope. */
        VERBOSE
    }

    /**
     * Serializes this rule to JSON.
     *
     * @return JSON representation
     * @since 0.22
     */
    default String toJson() {
        ComponentDescriptor descriptor = RuleSerialization.describe(this);
        return ComponentSerialization.toJson(descriptor);
    }

    /**
     * Builds a rule from JSON.
     *
     * @param series bar series context
     * @param json   payload
     * @return reconstructed rule
     * @since 0.22
     */
    static Rule fromJson(BarSeries series, String json) {
        ComponentDescriptor descriptor = ComponentSerialization.parse(json);
        return RuleSerialization.fromDescriptor(series, descriptor);
    }

    /**
     * @param rule another trading rule
     * @return a rule which is the AND combination of this rule with the provided
     *         one
     */
    default Rule and(Rule rule) {
        return new AndRule(this, rule);
    }

    /**
     * @param rule another trading rule
     * @return a rule which is the OR combination of this rule with the provided one
     */
    default Rule or(Rule rule) {
        return new OrRule(this, rule);
    }

    /**
     * @param rule another trading rule
     * @return a rule which is the XOR combination of this rule with the provided
     *         one
     */
    default Rule xor(Rule rule) {
        return new XorRule(this, rule);
    }

    /**
     * @return a rule which is the logical negation of this rule
     */
    default Rule negation() {
        return new NotRule(this);
    }

    /**
     * @param index the bar index
     * @return true if this rule is satisfied for the provided index, false
     *         otherwise
     */
    default boolean isSatisfied(int index) {
        return isSatisfied(index, null);
    }

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true if this rule is satisfied for the provided index, false
     *         otherwise
     */
    boolean isSatisfied(int index, TradingRecord tradingRecord);

    /**
     * Configures trace logging for this rule.
     *
     * @param traceMode OFF, ROLLUP, or VERBOSE. A {@code null} value is treated as
     *                  OFF.
     * @since 0.22.7
     */
    default void setTraceMode(TraceMode traceMode) {
        // no-op by default to preserve compatibility for custom Rule implementations
        // that do not support trace mode yet
    }

    /**
     * Returns the current trace mode for this rule.
     *
     * @return the active trace mode, defaults to {@link TraceMode#OFF}
     * @since 0.22.7
     */
    default TraceMode getTraceMode() {
        return TraceMode.OFF;
    }

    /**
     * Sets a human friendly name for this rule. Implementations that support naming
     * should override this method.
     *
     * <p>
     * The default implementation is a no-op and does not store the name.
     *
     * @param name desired name; {@code null} or blank should reset the rule name
     *             back to the implementation specific default
     * @since 0.19
     */
    default void setName(String name) {
        // no-op by default to preserve backwards compatibility for custom Rule
        // implementations that do not support naming yet
    }

    /**
     * Returns the configured name for this rule.
     *
     * @return a descriptive name or a lightweight default
     * @since 0.19
     */
    default String getName() {
        return toString();
    }
}
