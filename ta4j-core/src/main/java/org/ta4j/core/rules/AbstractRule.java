/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

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
        if (isTraceEnabled()) {
            log.trace("{}", createTraceEvent(index, isSatisfied, context).formatMessage());
        }
    }

    /**
     * @return true if trace logging is enabled for this rule
     */
    protected boolean isTraceEnabled() {
        return log.isTraceEnabled() && RuleTraceContext.activeMode() != null;
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
        if (RuleTraceContext.currentFrame() == null && !log.isTraceEnabled()) {
            return childRule.isSatisfied(index, tradingRecord);
        }
        try (RuleTraceContext.Scope ignored = RuleTraceContext.openChild(getTraceDisplayName(), relation)) {
            return childRule.isSatisfied(index, tradingRecord);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    public boolean isSatisfiedWithTraceMode(int index, TradingRecord tradingRecord, TraceMode traceMode) {
        if (!log.isTraceEnabled()) {
            return isSatisfied(index, tradingRecord);
        }
        return RuleTraceContext.evaluate(traceMode, "root", null, () -> isSatisfied(index, tradingRecord));
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
        RuleTraceContext.Frame frame = RuleTraceContext.currentFrame();
        String path = frame == null ? "root" : frame.path();
        int depth = frame == null ? 0 : frame.depth();
        String parentRuleName = frame == null ? null : frame.parentRuleName();
        return new RuleTraceEvent(index, className, getTraceDisplayName(), RuleTraceContext.activeMode(), isSatisfied,
                path, depth, parentRuleName, context);
    }

    /**
     * Builds deterministic trace context fields from alternating key/value pairs.
     * Null keys and values are omitted so callers can pass optional diagnostics
     * without creating partial or noisy log fields.
     *
     * @param keyValues alternating keys and values
     * @return insertion-ordered trace context
     */
    static Map<String, String> traceContext(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Trace context requires key/value pairs");
        }
        Map<String, String> context = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key == null) {
                continue;
            }
            Object value = keyValues[i + 1];
            if (value != null) {
                context.put(String.valueOf(key), String.valueOf(value));
            }
        }
        return context;
    }

    /**
     * Builds the value context needed to explain one cross-rule decision.
     *
     * @param first     the indicator expected to cross
     * @param second    the reference indicator
     * @param index     the evaluated bar index
     * @param satisfied true when the cross happened
     * @param crossedUp true for upward crosses, false for downward crosses
     * @return trace context with current, previous, and prior unequal values
     */
    static Map<String, String> traceCrossContext(Indicator<Num> first, Indicator<Num> second, int index,
            boolean satisfied, boolean crossedUp) {
        int unstableBoundary = Math.max(first.getCountOfUnstableBars(), second.getCountOfUnstableBars());
        int priorIndex = priorUnequalIndex(first, second, index, unstableBoundary);
        int previousIndex = Math.max(0, index - 1);
        Num firstValue = first.getValue(index);
        Num secondValue = second.getValue(index);
        Num priorFirstValue = first.getValue(priorIndex);
        Num priorSecondValue = second.getValue(priorIndex);
        return traceContext("firstValue", firstValue, "secondValue", secondValue, "previousIndex", previousIndex,
                "previousFirstValue", first.getValue(previousIndex), "previousSecondValue",
                second.getValue(previousIndex), "priorIndex", priorIndex, "priorFirstValue", priorFirstValue,
                "priorSecondValue", priorSecondValue, "unstableBoundary", unstableBoundary, "reason",
                crossReason(index, unstableBoundary, satisfied, crossedUp, firstValue, secondValue, priorFirstValue,
                        priorSecondValue));
    }

    private static int priorUnequalIndex(Indicator<Num> first, Indicator<Num> second, int index, int unstableBoundary) {
        int priorIndex = index;
        if (priorIndex <= unstableBoundary) {
            return priorIndex;
        }
        do {
            priorIndex--;
        } while (priorIndex > unstableBoundary && first.getValue(priorIndex).isEqual(second.getValue(priorIndex)));
        return priorIndex;
    }

    private static String crossReason(int index, int unstableBoundary, boolean satisfied, boolean crossedUp,
            Num firstValue, Num secondValue, Num priorFirstValue, Num priorSecondValue) {
        if (index <= unstableBoundary) {
            return "unstable";
        }
        if (satisfied) {
            return crossedUp ? "crossedUp" : "crossedDown";
        }
        if (crossedUp && !firstValue.isGreaterThan(secondValue)) {
            return "firstAtOrBelowSecond";
        }
        if (!crossedUp && !firstValue.isLessThan(secondValue)) {
            return "firstAtOrAboveSecond";
        }
        if (crossedUp) {
            return priorSecondValue.isGreaterThan(priorFirstValue) ? "crossedUp" : "alreadyAboveSecond";
        }
        return priorFirstValue.isGreaterThan(priorSecondValue) ? "crossedDown" : "alreadyBelowSecond";
    }
}
