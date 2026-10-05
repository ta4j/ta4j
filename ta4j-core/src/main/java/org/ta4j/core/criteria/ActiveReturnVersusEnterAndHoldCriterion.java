/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Active return versus an enter-and-hold benchmark.
 *
 * <p>
 * This criterion compares a strategy {@link ReturnCriterion return criterion}
 * to the same criterion calculated over an {@link EnterAndHoldCriterion}. The
 * result is the percentage-point active return:
 *
 * <pre>
 * activeReturn = strategyReturn - enterAndHoldReturn
 * </pre>
 *
 * <p>
 * A value of {@code 0.0} in {@link ReturnRepresentation#DECIMAL} or {@code 0.0}
 * in {@link ReturnRepresentation#PERCENTAGE} means the strategy matched
 * enter-and-hold. A positive value means the strategy outperformed
 * enter-and-hold, and a negative value means it underperformed.
 * <p>
 * <b>Examples:</b>
 * <ul>
 * <li>If the strategy returns {@code 15.5%} and enter-and-hold returns
 * {@code 5.0%}, the active return is {@code 10.5} percentage points.
 * {@link ReturnRepresentation#PERCENTAGE} returns {@code 10.5},
 * {@link ReturnRepresentation#DECIMAL} returns {@code 0.105}, and
 * {@link ReturnRepresentation#MULTIPLICATIVE} returns {@code 1.105}.
 * <li>If the strategy returns {@code -33.5%} and enter-and-hold returns
 * {@code -30.0%}, the active return is {@code -3.5} percentage points.
 * {@link ReturnRepresentation#PERCENTAGE} returns {@code -3.5},
 * {@link ReturnRepresentation#DECIMAL} returns {@code -0.035}, and
 * {@link ReturnRepresentation#MULTIPLICATIVE} returns {@code 0.965}.
 * <li>If both the strategy and enter-and-hold return {@code 5.0%}, the active
 * return is parity. {@link ReturnRepresentation#PERCENTAGE} and
 * {@link ReturnRepresentation#DECIMAL} return {@code 0.0}, while
 * {@link ReturnRepresentation#MULTIPLICATIVE} returns {@code 1.0}.
 * </ul>
 *
 * <p>
 * This criterion delegates the active-return calculation to
 * {@link ActiveReturnCriterion}. It intentionally differs from
 * {@link VersusEnterAndHoldCriterion}, which divides the difference by the
 * absolute enter-and-hold return.
 *
 * @see ActiveReturnCriterion
 * @see EnterAndHoldCriterion
 * @see VersusEnterAndHoldCriterion
 * @since 0.22.7
 */
public class ActiveReturnVersusEnterAndHoldCriterion extends AbstractAnalysisCriterion {

    private final ActiveReturnCriterion activeReturnCriterion;
    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with a buy-and-hold benchmark, an entry amount of {@code 1}, and
     * {@link ReturnRepresentation#DECIMAL} output.
     *
     * @param criterion the return criterion to compare to enter-and-hold
     * @throws IllegalArgumentException if {@code criterion} does not expose a
     *                                  {@link ReturnRepresentation}, is not a
     *                                  {@link ReturnCriterion}, or is itself a
     *                                  relative-return criterion
     * @throws NullPointerException     if {@code criterion} is {@code null}
     * @since 0.22.7
     */
    public ActiveReturnVersusEnterAndHoldCriterion(AnalysisCriterion criterion) {
        this(TradeType.BUY, criterion);
    }

    /**
     * Constructor with an entry amount of {@code 1} and
     * {@link ReturnRepresentation#DECIMAL} output.
     *
     * @param tradeType the {@link TradeType} used to open the benchmark position
     * @param criterion the return criterion to compare to enter-and-hold
     * @throws IllegalArgumentException if {@code criterion} does not expose a
     *                                  {@link ReturnRepresentation}, is not a
     *                                  {@link ReturnCriterion}, or is itself a
     *                                  relative-return criterion
     * @throws NullPointerException     if {@code tradeType} or {@code criterion} is
     *                                  {@code null}
     * @since 0.22.7
     */
    public ActiveReturnVersusEnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion) {
        this(tradeType, criterion, BigDecimal.ONE);
    }

    /**
     * Constructor with {@link ReturnRepresentation#DECIMAL} output.
     *
     * @param tradeType the {@link TradeType} used to open the benchmark position
     * @param criterion the return criterion to compare to enter-and-hold
     * @param amount    the amount to use for the benchmark position
     * @throws IllegalArgumentException if {@code criterion} does not expose a
     *                                  {@link ReturnRepresentation}, is not a
     *                                  {@link ReturnCriterion}, or is itself a
     *                                  relative-return criterion
     * @throws NullPointerException     if any argument is {@code null}
     * @since 0.22.7
     */
    public ActiveReturnVersusEnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion,
            BigDecimal amount) {
        this(tradeType, criterion, amount, ReturnRepresentation.DECIMAL);
    }

    /**
     * Constructor with explicit output representation.
     *
     * @param tradeType            the {@link TradeType} used to open the benchmark
     *                             position
     * @param criterion            the return criterion to compare to enter-and-hold
     * @param amount               the amount to use for the benchmark position
     * @param returnRepresentation the representation to use for the active-return
     *                             output
     * @throws IllegalArgumentException if {@code criterion} does not expose a
     *                                  {@link ReturnRepresentation}, is not a
     *                                  {@link ReturnCriterion}, or is itself a
     *                                  relative-return criterion
     * @throws NullPointerException     if any argument is {@code null}
     * @since 0.22.7
     */
    public ActiveReturnVersusEnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion, BigDecimal amount,
            ReturnRepresentation returnRepresentation) {
        Objects.requireNonNull(tradeType, "tradeType");
        Objects.requireNonNull(criterion, "criterion");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(returnRepresentation, "returnRepresentation");

        EnterAndHoldCriterion enterAndHoldCriterion = new EnterAndHoldCriterion(tradeType, criterion, amount);
        this.activeReturnCriterion = new ActiveReturnCriterion(criterion, enterAndHoldCriterion, returnRepresentation);
        this.returnRepresentation = returnRepresentation;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (series.isEmpty()) {
            return returnRepresentation.toRepresentationFromRateOfReturn(series.numFactory().zero());
        }
        return activeReturnCriterion.calculate(series, position);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (series.isEmpty()) {
            return returnRepresentation.toRepresentationFromRateOfReturn(series.numFactory().zero());
        }
        return activeReturnCriterion.calculate(series, tradingRecord);
    }

    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return activeReturnCriterion.getReturnRepresentation();
    }

    /** The higher the active return, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return activeReturnCriterion.betterThan(criterionValue1, criterionValue2);
    }
}
