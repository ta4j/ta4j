/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.Objects;
import java.util.function.BiFunction;

import org.ta4j.core.rules.AbstractEvaluatedRule;

/** Shared implementation of the typed AND and OR overloads. */
final class JoinedEvaluatedRule<L, R, T> extends AbstractEvaluatedRule<EvaluatedRule.JoinedResult<T>> {

    private final EvaluatedRule<L> left;
    private final EvaluatedRule<R> right;
    private final BiFunction<? super L, ? super R, ? extends T> joiner;
    private final boolean conjunction;

    JoinedEvaluatedRule(EvaluatedRule<L> left, EvaluatedRule<R> right,
            BiFunction<? super L, ? super R, ? extends T> joiner, boolean conjunction) {
        this.left = left;
        this.right = right;
        this.joiner = joiner;
        this.conjunction = conjunction;
        setName(createCompositeName(getClass().getSimpleName(), left, right));
    }

    /**
     * @since 0.28.1
     */
    @Override
    public JoinedResult<T> evaluate(int index, TradingRecord tradingRecord) {
        L leftResult = Objects.requireNonNull(left.evaluate(index, tradingRecord), "evaluate(...) must not return null");
        boolean leftSatisfied = left.toBoolean(leftResult);
        R rightResult = Objects.requireNonNull(right.evaluate(index, tradingRecord), "evaluate(...) must not return null");
        boolean rightSatisfied = right.toBoolean(rightResult);
        return new JoinedResult<>(joiner.apply(leftResult, rightResult),
                conjunction ? leftSatisfied && rightSatisfied : leftSatisfied || rightSatisfied);
    }

    /**
     * @since 0.28.1
     */
    @Override
    public boolean toBoolean(JoinedResult<T> result) {
        return Objects.requireNonNull(result, "result").satisfied();
    }
}
