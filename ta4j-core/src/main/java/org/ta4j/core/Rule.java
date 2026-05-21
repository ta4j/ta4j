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
 *
 * <p>
 * Rules encapsulate the logic for trading decisions (e.g., crossing indicators,
 * stop-loss thresholds, or boolean conditions). They are evaluated per bar
 * index using {@link #isSatisfied(int, TradingRecord)}. Complex logic is built
 * by composing primitive rules with logical operators like {@link #and(Rule)},
 * {@link #or(Rule)}, or {@link #negation()}.
 * </p>
 */
public interface Rule {

    /**
     * Controls trace logging behavior for rule evaluation.
     *
     * <p>
     * SLF4J TRACE logging is the off switch. This selector only changes the amount
     * of detail emitted during an evaluation where the relevant logger is already
     * TRACE-enabled.
     *
     * @since 0.22.7
     */
    enum TraceMode {
        /**
         * Emit one trace event for the evaluated rule while suppressing child-rule
         * trace events inside the same scoped evaluation.
         */
        SUMMARY,
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
        return isSatisfied(index, (TradingRecord) null);
    }

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true if this rule is satisfied for the provided index, false
     *         otherwise
     */
    boolean isSatisfied(int index, TradingRecord tradingRecord);

    /**
     * Evaluates this rule once with the supplied trace detail. Implementations that
     * do not support scoped tracing may ignore {@code traceMode} and delegate to
     * {@link #isSatisfied(int, TradingRecord)}.
     *
     * @param index     the bar index
     * @param traceMode trace detail for this evaluation only; {@code null} uses
     *                  {@link TraceMode#VERBOSE}
     * @return true if this rule is satisfied for the provided index, false
     *         otherwise
     * @since 0.22.7
     */
    default boolean isSatisfiedWithTraceMode(int index, TraceMode traceMode) {
        return isSatisfiedWithTraceMode(index, null, traceMode);
    }

    /**
     * Evaluates this rule once with the supplied trace detail. Implementations that
     * do not support scoped tracing may ignore {@code traceMode} and delegate to
     * {@link #isSatisfied(int, TradingRecord)}.
     *
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @param traceMode     trace detail for this evaluation only; {@code null} uses
     *                      {@link TraceMode#VERBOSE}
     * @return true if this rule is satisfied for the provided index, false
     *         otherwise
     * @since 0.22.7
     */
    default boolean isSatisfiedWithTraceMode(int index, TradingRecord tradingRecord, TraceMode traceMode) {
        return isSatisfied(index, tradingRecord);
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
