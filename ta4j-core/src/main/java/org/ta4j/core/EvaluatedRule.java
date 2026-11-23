/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A {@link Rule} whose {@link #evaluate(int, TradingRecord)} returns an
 * immutable, non-null result snapshot and whose {@link #toBoolean(Object)}
 * projects that snapshot without re-evaluating. {@code isSatisfied} evaluates
 * and projects exactly once; subclass
 * {@link org.ta4j.core.rules.AbstractEvaluatedRule} rather than overriding it.
 *
 * @param <R> the evaluation result type
 * @since 0.29.1
 */
public interface EvaluatedRule<R> extends Rule {

    /**
     * Evaluates this rule and returns an immutable result snapshot.
     *
     * @param index         the bar index
     * @param tradingRecord the trading record, may be {@code null}
     * @return the evaluation result, never {@code null}
     * @since 0.29.1
     */
    R evaluate(int index, TradingRecord tradingRecord);

    /**
     * Projects an evaluation result to this rule's boolean decision.
     *
     * @param result a non-null result previously returned by
     *               {@link #evaluate(int, TradingRecord)}
     * @return {@code true} if the result satisfies this rule
     * @since 0.29.1
     */
    boolean toBoolean(R result);

    /**
     * Evaluates this rule without a trading record.
     *
     * @param index the bar index
     * @return the evaluation result, never {@code null}
     * @since 0.29.1
     */
    default R evaluate(int index) {
        return evaluate(index, null);
    }

    /**
     * Joins two evaluated rules with AND, preserving a caller-defined payload.
     * Unlike {@link #and(Rule)}, both operands are always evaluated. Joined rules
     * cannot be serialized or copied.
     *
     * @param <S>    the other operand's snapshot type
     * @param <T>    the joined payload type
     * @param other  the right operand
     * @param joiner combines the left and right snapshots into a non-null payload
     * @return an evaluated rule containing the payload and captured AND decision
     * @throws NullPointerException if {@code other} or {@code joiner} is null, or
     *                              an evaluation or joiner returns null
     * @since 0.29.1
     */
    default <S, T> EvaluatedRule<JoinedResult<T>> and(EvaluatedRule<S> other,
            BiFunction<? super R, ? super S, ? extends T> joiner) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(joiner, "joiner");
        return new JoinedEvaluatedRule<>(this, other, joiner, true);
    }

    /**
     * Joins two evaluated rules with OR; see
     * {@link #and(EvaluatedRule, BiFunction)}.
     *
     * @param <S>    the other operand's snapshot type
     * @param <T>    the joined payload type
     * @param other  the right operand
     * @param joiner combines the left and right snapshots into a non-null payload
     * @return an evaluated rule containing the payload and captured OR decision
     * @throws NullPointerException if {@code other} or {@code joiner} is null, or
     *                              an evaluation or joiner returns null
     * @since 0.29.1
     */
    default <S, T> EvaluatedRule<JoinedResult<T>> or(EvaluatedRule<S> other,
            BiFunction<? super R, ? super S, ? extends T> joiner) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(joiner, "joiner");
        return new JoinedEvaluatedRule<>(this, other, joiner, false);
    }

    /**
     * A joined payload and the decision captured from its operands.
     *
     * @param <T>       the immutable payload type
     * @param value     the non-null joined payload
     * @param satisfied the captured AND or OR decision
     * @since 0.29.1
     */
    record JoinedResult<T>(T value, boolean satisfied) {

        /**
         * Creates a joined snapshot.
         *
         * @throws NullPointerException if {@code value} is null
         * @since 0.29.1
         */
        public JoinedResult {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Evaluates this rule once and projects the result. {@link #toBoolean(Object)}
     * once.
     *
     * @param index         the bar index
     * @param tradingRecord the trading record, may be {@code null}
     * @return the boolean projection of the evaluation result
     * @throws NullPointerException if {@link #evaluate(int, TradingRecord)} returns
     *                              {@code null}
     * @since 0.29.1
     */
    @Override
    default boolean isSatisfied(int index, TradingRecord tradingRecord) {
        R result = Objects.requireNonNull(evaluate(index, tradingRecord), "evaluate(...) must not return null");
        return toBoolean(result);
    }
}
