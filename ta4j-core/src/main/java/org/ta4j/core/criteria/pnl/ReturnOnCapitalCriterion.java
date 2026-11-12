/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.ReturnCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.num.Num;

/**
 * Net profit or loss relative to the capital committed to a strategy.
 * <p>
 * Divides the signed result of {@link NetProfitLossCriterion} by a fixed
 * capital amount, including transaction and holding costs and ignoring open
 * positions. Position results are added before division, not compounded. Unlike
 * {@link NetReturnCriterion}, the denominator is capital rather than entry
 * notional, so changing position size changes the return on capital.
 * <p>
 * Losses are not clamped: leveraged losses can exceed the supplied capital.
 * Logarithmic output follows {@link ReturnRepresentation#LOG}'s domain.
 *
 * @since 0.28.1
 */
public final class ReturnOnCapitalCriterion extends AbstractAnalysisCriterion implements ReturnCriterion {

    private static final NetProfitLossCriterion NET_PROFIT_LOSS = new NetProfitLossCriterion();

    private final BigDecimal capital;
    private final ReturnRepresentation representation;

    /**
     * Creates a criterion using the current global default representation.
     *
     * @param capital positive, finite committed capital; snapshotted at
     *                construction
     * @throws NullPointerException     if capital is null
     * @throws IllegalArgumentException if capital is not positive and finite
     * @since 0.28.1
     */
    public ReturnOnCapitalCriterion(Number capital) {
        this(capital, ReturnRepresentationPolicy.getDefaultRepresentation());
    }

    /**
     * Creates a criterion with an explicit output representation.
     *
     * @param capital        positive, finite committed capital; snapshotted without
     *                       converting decimal values to double
     * @param representation the output representation
     * @throws NullPointerException     if either argument is null
     * @throws IllegalArgumentException if capital is not positive and finite
     * @since 0.28.1
     */
    public ReturnOnCapitalCriterion(Number capital, ReturnRepresentation representation) {
        this.capital = new BigDecimal(Objects.requireNonNull(capital, "capital").toString());
        if (this.capital.signum() <= 0) {
            throw new IllegalArgumentException("Capital must be positive and finite");
        }
        this.representation = Objects.requireNonNull(representation, "representation");
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.28.1
     */
    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(representation);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.28.1
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        return represent(NET_PROFIT_LOSS.calculate(series, position));
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.28.1
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return represent(NET_PROFIT_LOSS.calculate(series, tradingRecord));
    }

    private Num represent(Num netProfit) {
        Num rate = netProfit.dividedBy(netProfit.getNumFactory().numOf(capital));
        return representation.toRepresentationFromRateOfReturn(rate);
    }

    /**
     * Returns whether the first return is higher than the second.
     *
     * @since 0.28.1
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
