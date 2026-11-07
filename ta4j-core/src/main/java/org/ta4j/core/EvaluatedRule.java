/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A {@link Rule} that exposes a typed evaluation result and an explicit boolean
 * projection of that result.
 *
 * <p>
 * An evaluated rule is still an ordinary {@link Rule}: it can be passed
 * directly to {@link BaseStrategy}, composed with {@link #and(Rule)},
 * {@link #or(Rule)}, {@link #xor(Rule)} or {@link #negation()}, traced, named,
 * serialized and copied when its configuration supports reconstruction.
 * Callback joins described below do not support serialization or independent
 * copying. The inherited {@link #isSatisfied(int, TradingRecord)} bridge calls
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
 * @since 0.28.1
 */
public interface EvaluatedRule<R> extends Rule {

    /**
     * Evaluates this rule and returns an immutable result snapshot.
     *
     * @param index         the bar index
     * @param tradingRecord the trading record, may be {@code null}
     * @return the evaluation result, never {@code null}
     * @since 0.28.1
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
     * @since 0.28.1
     */
    boolean toBoolean(R result);

    /**
     * Evaluates this rule without a trading record.
     *
     * @param index the bar index
     * @return the evaluation result, never {@code null}
     * @since 0.28.1
     */
    default R evaluate(int index) {
        return evaluate(index, null);
    }

    /**
     * Joins two evaluated rules with AND, preserving a caller-defined payload.
     *
     * <p>
     * Unlike {@link #and(Rule)}, this overload is eager: it evaluates and projects
     * the left operand, then the right operand, exactly once each with the same
     * index and record, even when the left is false. It then invokes {@code joiner}
     * once with both snapshots. Failures propagate immediately and stop subsequent
     * steps. Repeated occurrences of the same rule are evaluated separately;
     * observations across operands are sequential, not atomic.
     *
     * <p>
     * The joined payload must be immutable and must not retain live inputs. The
     * captured decision is the AND of the child decisions, independent of the
     * payload. Projecting it never re-evaluates children or invokes the joiner.
     * Chaining passes the previous {@link JoinedResult} to the next joiner; use
     * {@link JoinedResult#value()} to access its payload.
     *
     * <p>
     * The default implementation traces only the composite decision when called
     * through the boolean bridge, not individual child decisions. Direct rich
     * evaluation emits no boolean trace. Callback-backed joins cannot be
     * serialized; best-effort rule copying returns the same instance, not an
     * independent copy. Overrides must preserve coherent snapshots and AND
     * decisions.
     *
     * @param <S>    the other operand's snapshot type
     * @param <T>    the joined payload type
     * @param other  the right operand
     * @param joiner combines the left and right snapshots into a non-null payload
     * @return an evaluated rule containing the payload and captured AND decision
     * @throws NullPointerException if {@code other} or {@code joiner} is null, or
     *                              an evaluation or joiner returns null
     * @since 0.28.1
     */
    default <S, T> EvaluatedRule<JoinedResult<T>> and(EvaluatedRule<S> other,
            BiFunction<? super R, ? super S, ? extends T> joiner) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(joiner, "joiner");
        return new JoinedEvaluatedRule<>(this, other, joiner, true);
    }

    /**
     * Joins two evaluated rules with OR, preserving a caller-defined payload.
     *
     * <p>
     * Has the same snapshot, evaluation order, failure, tracing, chaining and
     * persistence contracts as {@link #and(EvaluatedRule, BiFunction)}, except that
     * the captured decision is OR. Unlike {@link #or(Rule)}, both operands are
     * evaluated even when the left is true. The joiner is still invoked once.
     * Overrides must preserve coherent snapshots and OR decisions.
     *
     * @param <S>    the other operand's snapshot type
     * @param <T>    the joined payload type
     * @param other  the right operand
     * @param joiner combines the left and right snapshots into a non-null payload
     * @return an evaluated rule containing the payload and captured OR decision
     * @throws NullPointerException if {@code other} or {@code joiner} is null, or
     *                              an evaluation or joiner returns null
     * @since 0.28.1
     */
    default <S, T> EvaluatedRule<JoinedResult<T>> or(EvaluatedRule<S> other,
            BiFunction<? super R, ? super S, ? extends T> joiner) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(joiner, "joiner");
        return new JoinedEvaluatedRule<>(this, other, joiner, false);
    }

    /**
     * A joined payload and the logical decision captured from its operands.
     *
     * <p>
     * The caller owns deep immutability of {@code value}; this carrier cannot copy
     * an arbitrary payload. The captured decision never depends on later changes to
     * a trading record or on how the caller aggregates the payload.
     *
     * @param <T>       the immutable payload type
     * @param value     the non-null joined payload
     * @param satisfied the captured AND or OR decision
     * @since 0.28.1
     */
    record JoinedResult<T>(T value, boolean satisfied) {

        /**
         * Creates a joined snapshot.
         *
         * @throws NullPointerException if {@code value} is null
         * @since 0.28.1
         */
        public JoinedResult {
            Objects.requireNonNull(value, "value");
        }
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
     * @since 0.28.1
     */
    @Override
    default boolean isSatisfied(int index, TradingRecord tradingRecord) {
        R result = Objects.requireNonNull(evaluate(index, tradingRecord), "evaluate(...) must not return null");
        return toBoolean(result);
    }
}
