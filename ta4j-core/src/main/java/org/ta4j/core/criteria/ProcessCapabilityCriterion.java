/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Process capability criterion (Cpk).
 *
 * <p>
 * Measures how well the per-position returns of a {@link TradingRecord} fit
 * within specification limits. Only closed positions are considered; the mean
 * and population standard deviation of their gross returns are computed over
 * the record, and the criterion returns
 *
 * <pre>
 * Cpk = min((mu - LSL) / 3sigma, (USL - mu) / 3sigma)
 * </pre>
 *
 * with the missing side dropped for one-sided specifications. A centered,
 * low-variance return stream scores high (capable), while drift towards or
 * variance beyond the limits scores low. The higher the value, the better
 * ({@link #betterThan(Num, Num)} is {@code isGreaterThan}). The gross returns
 * are multiplicative ({@link ReturnRepresentation#MULTIPLICATIVE}) and pinned
 * at construction, so a later global {@link ReturnRepresentationPolicy} change
 * cannot alter scores.
 *
 * <p>
 * An empty record or a record whose gross returns have zero variance returns
 * {@code zero()}: with no variation there is no evidence of capability, and the
 * criterion is neutral rather than infinitely good. Extreme gross returns that
 * overflow the variance accumulation are likewise treated as incapable and
 * score {@code zero()}.
 *
 * @since 0.24.2
 */
public class ProcessCapabilityCriterion extends AbstractAnalysisCriterion {

    private final Number lsl;
    private final Number usl;
    private final GrossReturnCriterion grossReturnCriterion = new GrossReturnCriterion(
            ReturnRepresentation.MULTIPLICATIVE);

    /**
     * Constructor for a one-sided, lower specification limit.
     *
     * @param lsl the lower specification limit; must not be null and must be finite
     */
    public ProcessCapabilityCriterion(Number lsl) {
        this(new Limits(lsl, null));
    }

    /**
     * Constructor for a two-sided specification.
     *
     * @param lsl the lower specification limit; must not be null and must be finite
     * @param usl the upper specification limit; may be null and must be finite and
     *            greater than {@code lsl}
     */
    public ProcessCapabilityCriterion(Number lsl, Number usl) {
        this(new Limits(lsl, usl));
    }

    private ProcessCapabilityCriterion(Limits limits) {
        super();
        this.lsl = limits.lsl();
        this.usl = limits.usl();
    }

    private record Limits(Number lsl, Number usl) {

        private Limits {
            Objects.requireNonNull(lsl, "lsl must not be null");
            BigDecimal lslValue = decimalValue(lsl, "lsl");
            if (usl != null) {
                if (lslValue.compareTo(decimalValue(usl, "usl")) >= 0) {
                    throw new IllegalArgumentException("lsl must be less than usl");
                }
            }
        }
    }

    private static BigDecimal decimalValue(Number value, String name) {
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be finite", e);
        }
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory factory = series.numFactory();
        List<Num> values = new ArrayList<>();
        for (Position position : tradingRecord.getPositions()) {
            if (position.isClosed()) {
                values.add(grossReturnCriterion.calculate(series, position));
            }
        }
        if (values.isEmpty()) {
            return factory.zero();
        }
        Num[] valueArray = values.toArray(new Num[0]);
        Num mean = Statistics.MEAN.calculate(factory, valueArray);
        Num squaredDeviationSum = factory.zero();
        for (Num value : valueArray) {
            Num deviation = value.minus(mean);
            squaredDeviationSum = squaredDeviationSum.plus(deviation.multipliedBy(deviation));
        }
        if (!Num.isFinite(squaredDeviationSum)) {
            return factory.zero();
        }
        Num standardDeviation = squaredDeviationSum.dividedBy(factory.numOf(valueArray.length)).sqrt();
        if (standardDeviation.isZero()) {
            return factory.zero();
        }
        Num threeSigma = standardDeviation.multipliedBy(factory.numOf(3));
        Num lowerCapability = mean.minus(factory.numOf(lsl)).dividedBy(threeSigma);
        if (usl == null) {
            return lowerCapability;
        }
        Num upperCapability = factory.numOf(usl).minus(mean).dividedBy(threeSigma);
        return lowerCapability.min(upperCapability);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " lsl: " + lsl + " usl: " + usl;
    }
}
