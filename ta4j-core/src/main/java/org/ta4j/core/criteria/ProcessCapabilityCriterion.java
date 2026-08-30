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
 * criterion is neutral rather than infinitely good. The population standard
 * deviation is computed with each deviation normalized by the largest absolute
 * deviation, and a mean whose naive accumulation overflows is recomputed with
 * each value normalized by the largest absolute value, so wide but finite
 * dispersions do not overflow even when the naive sums would (for example gross
 * returns of 1e308 and 1.4e308 still yield their exact Cpk); gross returns with
 * non-finite magnitude are treated as incapable and score {@code zero()}.
 *
 * @since 0.24.2
 */
public class ProcessCapabilityCriterion extends AbstractAnalysisCriterion {

    private final BigDecimal lsl;
    private final BigDecimal usl;
    private final GrossReturnCriterion grossReturnCriterion = new GrossReturnCriterion(
            ReturnRepresentation.MULTIPLICATIVE);

    /**
     * Constructor for a one-sided, lower specification limit.
     *
     * @param lsl the lower specification limit; must not be null and must be finite
     */
    public ProcessCapabilityCriterion(Number lsl) {
        this(new Limits(decimalValue(lsl, "lsl"), null));
    }

    /**
     * Constructor for a two-sided specification.
     *
     * @param lsl the lower specification limit; must not be null and must be finite
     * @param usl the upper specification limit; may be null and must be finite and
     *            greater than {@code lsl}
     */
    public ProcessCapabilityCriterion(Number lsl, Number usl) {
        this(new Limits(decimalValue(lsl, "lsl"), usl == null ? null : decimalValue(usl, "usl")));
    }

    private ProcessCapabilityCriterion(Limits limits) {
        super();
        this.lsl = limits.lsl();
        this.usl = limits.usl();
    }

    private record Limits(BigDecimal lsl, BigDecimal usl) {

        private Limits {
            Objects.requireNonNull(lsl, "lsl must not be null");
            if (usl != null && lsl.compareTo(usl) >= 0) {
                throw new IllegalArgumentException("lsl must be less than usl");
            }
        }
    }

    private static BigDecimal decimalValue(Number value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
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
        Num mean = factory.zero();
        for (Num value : valueArray) {
            mean = mean.plus(value);
        }
        mean = mean.dividedBy(factory.numOf(valueArray.length));
        if (!Num.isFinite(mean)) {
            Num maxAbsValue = factory.zero();
            for (Num value : valueArray) {
                maxAbsValue = maxAbsValue.max(value.abs());
            }
            if (!Num.isFinite(maxAbsValue) || maxAbsValue.isZero()) {
                return factory.zero();
            }
            Num scaledSum = factory.zero();
            for (Num value : valueArray) {
                scaledSum = scaledSum.plus(value.dividedBy(maxAbsValue));
            }
            mean = scaledSum.dividedBy(factory.numOf(valueArray.length)).multipliedBy(maxAbsValue);
        }
        Num maxAbsDeviation = factory.zero();
        for (Num value : valueArray) {
            maxAbsDeviation = maxAbsDeviation.max(value.minus(mean).abs());
        }
        if (!Num.isFinite(maxAbsDeviation) || maxAbsDeviation.isZero()) {
            return factory.zero();
        }
        Num scaledSquaredSum = factory.zero();
        for (Num value : valueArray) {
            Num scaledDeviation = value.minus(mean).dividedBy(maxAbsDeviation);
            scaledSquaredSum = scaledSquaredSum.plus(scaledDeviation.multipliedBy(scaledDeviation));
        }
        Num standardDeviation = maxAbsDeviation
                .multipliedBy(scaledSquaredSum.dividedBy(factory.numOf(valueArray.length)).sqrt());
        // Scale-aware per-ratio form: dividing each operand by three before
        // subtracting keeps the intermediate difference finite whether
        // 3 * sigma overflows (sigma > MAX / 3), the raw mean-to-limit
        // distance exceeds MAX even though the final Cpk is representable,
        // or sigma < 1 with a numerator near MAX; dividing by sigma last
        // preserves the ratio.
        Num meanThird = mean.dividedBy(factory.numOf(3));
        Num lowerCapability = meanThird.minus(factory.numOf(lsl).dividedBy(factory.numOf(3)))
                .dividedBy(standardDeviation);
        if (usl == null) {
            return lowerCapability;
        }
        Num upperCapability = factory.numOf(usl)
                .dividedBy(factory.numOf(3))
                .minus(meanThird)
                .dividedBy(standardDeviation);
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
