/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * An abstract trading {@link Rule rule}.
 */
public abstract class AbstractRule implements Rule {

    /** The logger */
    protected final transient Logger log = LoggerFactory.getLogger(getClass());

    /** The class name */
    private final String className = getClass().getSimpleName();

    /** Configurable display name */
    private volatile String name;

    /** The trace logging mode. */
    private volatile TraceMode traceMode = TraceMode.OFF;

    /**
     * Returns the display name to use in trace logs. Uses the configured name if
     * set, otherwise falls back to the class name.
     *
     * @return display name for tracing
     */
    protected String getTraceDisplayName() {
        return name != null ? name : className;
    }

    /**
     * Traces the {@code isSatisfied()} method calls.
     *
     * @param index       the bar index
     * @param isSatisfied true if the rule is satisfied, false otherwise
     */
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        traceIsSatisfied(index, isSatisfied, Map.of());
    }

    /**
     * Traces the {@code isSatisfied()} method calls with structured diagnostic
     * context.
     *
     * @param index       the bar index
     * @param isSatisfied true if the rule is satisfied, false otherwise
     * @param context     deterministic context fields for the evaluation
     * @since 0.22.7
     */
    protected void traceIsSatisfied(int index, boolean isSatisfied, Map<String, String> context) {
        if (log.isTraceEnabled() && isTraceEnabled()) {
            log.trace("{}", createTraceEvent(index, isSatisfied, context).formatMessage());
        }
    }

    /**
     * @return true if trace logging is enabled for this rule
     */
    protected boolean isTraceEnabled() {
        return RuleTraceContext.activeMode(this) != TraceMode.OFF;
    }

    /**
     * Evaluates a child rule inside an evaluation-scoped trace context.
     *
     * @param childRule     rule to evaluate
     * @param relation      child relation label for the trace path
     * @param index         the bar index
     * @param tradingRecord trading history
     * @return true if the child rule is satisfied
     * @since 0.22.7
     */
    protected boolean evaluateChildRule(Rule childRule, String relation, int index, TradingRecord tradingRecord) {
        try (var ignored = RuleTraceContext.openChild(getTraceDisplayName(), this, childRule, relation)) {
            return childRule.isSatisfied(index, tradingRecord);
        }
    }

    @Override
    public void setTraceMode(TraceMode traceMode) {
        this.traceMode = traceMode == null ? TraceMode.OFF : traceMode;
    }

    @Override
    public TraceMode getTraceMode() {
        return traceMode;
    }

    @Override
    public void setName(String name) {
        this.name = name == null || name.isBlank() ? null : name;
    }

    @Override
    public String getName() {
        return name != null ? name : createDefaultName();
    }

    /**
     * @return true if a custom name was assigned via {@link #setName(String)}
     */
    public boolean hasCustomName() {
        return name != null;
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Creates the default JSON representation for the rule name. Sub-classes can
     * override to provide richer metadata.
     *
     * @return JSON payload describing the rule
     */
    protected String createDefaultName() {
        return className;
    }

    /**
     * Builds a JSON object containing the type plus child rule descriptors without
     * re-parsing already cached rule names.
     *
     * @param type     rule type label
     * @param children child rules
     * @return JSON string
     */
    protected String createCompositeName(String type, Rule... children) {
        StringBuilder builder = new StringBuilder(type);
        if (children != null && children.length > 0) {
            builder.append('(');
            for (int i = 0; i < children.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                if (children[i] == null) {
                    builder.append("null");
                } else {
                    builder.append(children[i].getName());
                }
            }
            builder.append(')');
        }
        return builder.toString();
    }

    private RuleTraceEvent createTraceEvent(int index, boolean isSatisfied, Map<String, String> context) {
        var frame = RuleTraceContext.currentFrame();
        var path = frame == null ? "root" : frame.path();
        var depth = frame == null ? 0 : frame.depth();
        var parentRuleName = frame == null ? null : frame.parentRuleName();
        return new RuleTraceEvent(index, className, getTraceDisplayName(), RuleTraceContext.activeMode(this),
                isSatisfied, path, depth, parentRuleName, context);
    }
}
