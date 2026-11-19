/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.EvaluatedRule;
import org.ta4j.core.TradingRecord;

/**
 * Base class for {@link EvaluatedRule} implementations with a final
 * {@code isSatisfied} bridge that evaluates, projects and traces once.
 *
 * @param <R> the evaluation result type
 * @since 0.29.1
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
     * @since 0.29.1
     */
    @Override
    public final boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = EvaluatedRule.super.isSatisfied(index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
