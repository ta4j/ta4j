/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.EvaluatedRule;
import org.ta4j.core.TradingRecord;

/**
 * Standard base class for {@link EvaluatedRule} implementations.
 *
 * <p>
 * Reuses {@link AbstractRule} naming and scoped trace infrastructure. The
 * boolean bridge {@link #isSatisfied(int, TradingRecord)} is final: it
 * evaluates once, projects once through {@link #toBoolean(Object)}, and traces
 * the already-computed decision once. Direct calls to
 * {@link #evaluate(int, TradingRecord)} do not emit a boolean trace event.
 *
 * <p>
 * Subclasses implement {@link #evaluate(int, TradingRecord)} and
 * {@link #toBoolean(Object)}. Constructor-backed subclasses that keep their
 * constructor arguments as fields serialize and copy through the regular
 * {@link org.ta4j.core.Rule} serialization path; evaluation results are never
 * part of the serialized configuration.
 *
 * @param <R> the evaluation result type
 * @since 0.25.1
 */
public abstract class AbstractEvaluatedRule<R> extends AbstractRule implements EvaluatedRule<R> {

    /**
     * Evaluates once, projects the result once and traces the decision.
     *
     * @param index         the bar index
     * @param tradingRecord the trading record, may be {@code null}
     * @return the boolean projection of the evaluation result
     * @throws NullPointerException if {@link #evaluate(int, TradingRecord)} returns
     *                              {@code null}
     * @since 0.25.1
     */
    @Override
    public final boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = EvaluatedRule.super.isSatisfied(index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
