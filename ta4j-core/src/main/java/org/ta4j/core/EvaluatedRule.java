/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.Objects;

/**
 * A {@link Rule} that exposes a typed evaluation result and an explicit boolean
 * projection of that result.
 *
 * <p>
 * An evaluated rule is still an ordinary {@link Rule}: it can be passed
 * directly to {@link BaseStrategy}, composed with {@link #and(Rule)},
 * {@link #or(Rule)}, {@link #xor(Rule)} or {@link #negation()}, traced, named,
 * serialized and copied like any other rule. The inherited
 * {@link #isSatisfied(int, TradingRecord)} bridge calls
 * {@link #evaluate(int, TradingRecord)} exactly once and
 * {@link #toBoolean(Object)} exactly once, so the boolean decision is always
 * the projection of one rich snapshot.
 *
 * <p>
 * Callers that need both the rich result and the decision evaluate once and
 * project the same snapshot:
 *
 * <pre>{@code
 * R result = rule.evaluate(index, tradingRecord);
 * boolean satisfied = rule.toBoolean(result);
 * }</pre>
 *
 * Two separate invocations are two evaluations; there is no implicit result
 * sharing or cache between a rich call and a later boolean call.
 *
 * <p>
 * Implementation contract:
 * <ul>
 * <li>{@link #evaluate(int, TradingRecord)} never returns {@code null};
 * expected warm-up or unavailable states are represented inside the result (for
 * example with {@link org.ta4j.core.num.NaN NaN}).</li>
 * <li>Results are immutable snapshots that do not retain live trading records
 * or mutable collections.</li>
 * <li>{@link #toBoolean(Object)} is deterministic and side-effect free for the
 * supplied snapshot plus immutable rule configuration: it must not re-evaluate
 * indicators, reread trading state or advance rule state.</li>
 * <li>Implementations should not override
 * {@link #isSatisfied(int, TradingRecord)}; subclass
 * {@link org.ta4j.core.rules.AbstractEvaluatedRule} to get a final bridge with
 * standard naming and trace support.</li>
 * </ul>
 *
 * <p>
 * There is no implicit truthiness: every implementation defines its own
 * coercion policy. This interface has two abstract methods and is therefore not
 * a functional interface.
 *
 * @param <R> the evaluation result type
 * @since 0.25.1
 */
public interface EvaluatedRule<R> extends Rule {

    /**
     * Evaluates this rule and returns an immutable result snapshot.
     *
     * @param index         the bar index
     * @param tradingRecord the trading record, may be {@code null}
     * @return the evaluation result, never {@code null}
     * @since 0.25.1
     */
    R evaluate(int index, TradingRecord tradingRecord);

    /**
     * Projects an evaluation result to this rule's boolean decision.
     *
     * <p>
     * The projection only reads {@code result} and immutable rule configuration; it
     * never re-evaluates inputs.
     *
     * @param result a non-null result previously returned by
     *               {@link #evaluate(int, TradingRecord)}
     * @return {@code true} if the result satisfies this rule
     * @since 0.25.1
     */
    boolean toBoolean(R result);

    /**
     * Evaluates this rule without a trading record.
     *
     * @param index the bar index
     * @return the evaluation result, never {@code null}
     * @since 0.25.1
     */
    default R evaluate(int index) {
        return evaluate(index, null);
    }

    /**
     * Evaluates this rule once and projects the result through
     * {@link #toBoolean(Object)} once.
     *
     * @param index         the bar index
     * @param tradingRecord the trading record, may be {@code null}
     * @return the boolean projection of the evaluation result
     * @throws NullPointerException if {@link #evaluate(int, TradingRecord)} returns
     *                              {@code null}
     * @since 0.25.1
     */
    @Override
    default boolean isSatisfied(int index, TradingRecord tradingRecord) {
        R result = Objects.requireNonNull(evaluate(index, tradingRecord), "evaluate(...) must not return null");
        return toBoolean(result);
    }
}
