/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.math.BigDecimal;
import java.math.MathContext;
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
 * returns of 1e308 and 1.4e308 still yield their exact Cpk); the mean is
 * accumulated with compensated (Neumaier) summation so it stays order-stable
 * across the record; gross returns with non-finite magnitude are treated as
 * incapable and score {@code zero()}. Specification limits are kept as raw
 * decimals, and when a limit itself overflows the active representation (for
 * example an LSL of -1e400 on a {@code DoubleNum} series) the mean-to-limit
 * distance is computed in decimal space and narrowed once against the complete
 * 3-sigma denominator, so representable capabilities stay finite and a limit
 * only marginally beyond the representation range still scores its positive
 * capability instead of collapsing to zero.
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
        Num mean = compensatedSum(valueArray, factory).dividedBy(factory.numOf(valueArray.length));
        if (!Num.isFinite(mean)) {
            Num maxAbsValue = factory.zero();
            for (Num value : valueArray) {
                maxAbsValue = maxAbsValue.max(value.abs());
            }
            if (!Num.isFinite(maxAbsValue) || maxAbsValue.isZero()) {
                return factory.zero();
            }
            Num[] scaledArray = new Num[valueArray.length];
            for (int i = 0; i < valueArray.length; i++) {
                scaledArray[i] = valueArray[i].dividedBy(maxAbsValue);
            }
            mean = compensatedSum(scaledArray, factory).dividedBy(factory.numOf(valueArray.length))
                    .multipliedBy(maxAbsValue);
        }
        // Scale-aware deviation pass: value - mean can overflow even though
        // the mean and every value are finite (a return near -MAX against a
        // mean near MAX / 2), so deviations are computed relative to the
        // largest magnitude first and rescaled afterward. Scaled deviations
        // stay within [-2, 2], so the squared sum is bounded by 4 * n and the
        // rescaled standard deviation cannot exceed the deviation scale
        // itself, keeping it finite whenever the scale is.
        Num deviationScale = mean.abs();
        for (Num value : valueArray) {
            deviationScale = deviationScale.max(value.abs());
        }
        if (!Num.isFinite(deviationScale) || deviationScale.isZero()) {
            return factory.zero();
        }
        Num scaledMean = mean.dividedBy(deviationScale);
        Num scaledSquaredSum = factory.zero();
        for (Num value : valueArray) {
            Num scaledDeviation = value.dividedBy(deviationScale).minus(scaledMean);
            scaledSquaredSum = scaledSquaredSum.plus(scaledDeviation.multipliedBy(scaledDeviation));
        }
        if (scaledSquaredSum.isZero()) {
            // Identical gross returns leave no dispersion to measure.
            return factory.zero();
        }
        Num scaledSigma = scaledSquaredSum.dividedBy(factory.numOf(valueArray.length)).sqrt();
        Num threeScaledSigma = scaledSigma.multipliedBy(factory.numOf(3));
        // Capability ratios are computed in the deviation-normalized domain,
        // Cpk = (mean - limit) / (3 * sigma)
        // = ((mean - limit) / deviationScale) / (3 * scaledSigma),
        // so neither the mean-to-limit distance nor 3 * sigma is ever
        // materialized: the normalized operands cannot overflow and the ratio
        // stays finite whenever the true Cpk is representable. Same-sign
        // operands are subtracted before scaling, which cannot overflow and
        // is exact when they are close, so a limit only a few ulps away from
        // the mean still scores its positive capability; opposite-sign
        // operands are scaled before subtracting because their raw distance
        // can exceed MAX even though the final ratio is representable (no
        // cancellation is possible between opposite signs).
        Num lslNum = factory.numOf(lsl);
        Num lowerCapability;
        if (!Num.isFinite(lslNum)) {
            // The limit itself overflows the factory's representation (for
            // example a DoubleNum series with an LSL of -1e400): the raw
            // mean-to-limit distance is computed in decimal space before
            // narrowing, and one division by the complete 3 * sigma
            // denominator yields the capability. Dividing the limit and the
            // mean by the same scale separately rounds both quotients for a
            // limit only marginally beyond the double range, which can
            // collapse a positive capability to zero.
            BigDecimal rawDistance = mean.bigDecimalValue().subtract(lsl, MathContext.DECIMAL128);
            lowerCapability = scaledDistance(rawDistance, deviationScale, threeScaledSigma, factory);
        } else {
            Num lowerDistance = mean.minus(lslNum);
            if (lowerDistance.isZero()) {
                // Narrowing the retained limit can round it onto the mean
                // and erase a representable capability gap (a precision-2
                // limit of 0.999 against a mean of 1.0 collapses the
                // distance to zero). Recompute the distance from the
                // lossless retained limit before the factory narrows it.
                BigDecimal rawDistance = mean.bigDecimalValue().subtract(lsl, MathContext.DECIMAL128);
                lowerCapability = scaledDistance(rawDistance, deviationScale, threeScaledSigma, factory);
            } else if (Num.isFinite(lowerDistance)) {
                Num lowerRatio = lowerDistance.dividedBy(deviationScale);
                if (Num.isFinite(lowerRatio)) {
                    lowerCapability = lowerRatio.dividedBy(threeScaledSigma);
                } else {
                    // The intermediate distance/scale division overflowed
                    // even though the full ratio is representable. This
                    // requires a deviation scale below one, so dividing once
                    // by the scale * 3 * scaledSigma product (at most 3)
                    // cannot overflow.
                    lowerCapability = lowerDistance.dividedBy(deviationScale.multipliedBy(threeScaledSigma));
                }
            } else {
                // The raw distance overflows, so the operands are scaled
                // before subtracting (opposite signs cannot cancel). A
                // non-finite distance implies the mean exceeds the double
                // overflow rounding margin of about 1e292, so the deviation
                // scale is huge and both scale divisions stay finite.
                lowerCapability = scaledMean.dividedBy(threeScaledSigma)
                        .minus(lslNum.dividedBy(deviationScale).dividedBy(threeScaledSigma));
            }
        }
        if (usl == null) {
            return lowerCapability;
        }
        Num uslNum = factory.numOf(usl);
        Num upperCapability;
        if (!Num.isFinite(uslNum)) {
            BigDecimal rawDistance = usl.subtract(mean.bigDecimalValue(), MathContext.DECIMAL128);
            upperCapability = scaledDistance(rawDistance, deviationScale, threeScaledSigma, factory);
        } else {
            Num upperDistance = uslNum.minus(mean);
            if (upperDistance.isZero()) {
                // Mirror of the lower-limit recovery: a limit the factory
                // rounded onto the mean must not erase a representable
                // capability gap.
                BigDecimal rawDistance = usl.subtract(mean.bigDecimalValue(), MathContext.DECIMAL128);
                upperCapability = scaledDistance(rawDistance, deviationScale, threeScaledSigma, factory);
            } else if (Num.isFinite(upperDistance)) {
                Num upperRatio = upperDistance.dividedBy(deviationScale);
                if (Num.isFinite(upperRatio)) {
                    upperCapability = upperRatio.dividedBy(threeScaledSigma);
                } else {
                    upperCapability = upperDistance.dividedBy(deviationScale.multipliedBy(threeScaledSigma));
                }
            } else {
                upperCapability = uslNum.dividedBy(deviationScale)
                        .dividedBy(threeScaledSigma)
                        .minus(scaledMean.dividedBy(threeScaledSigma));
            }
        }
        return lowerCapability.min(upperCapability);
    }

    private static Num scaledDistance(BigDecimal rawDistance, Num deviationScale, Num threeScaledSigma,
            NumFactory factory) {
        // The raw decimal distance is narrowed once against the complete
        // 3 * sigma denominator instead of rounding separate limit/scale and
        // mean/scale quotients. Both call sites retain the limit losslessly
        // (out-of-range limits and limits the factory rounded onto the
        // mean); the mean and scale terms are finite, and the deviation
        // scale is finite and nonzero here.
        // The operands stay in decimal space end to end: DecimalNum means can
        // exceed the double range (1.1e400), and their doubleValue() would be
        // infinite, making BigDecimal.valueOf throw NumberFormatException.
        BigDecimal rawDenominator = deviationScale.bigDecimalValue()
                .multiply(threeScaledSigma.bigDecimalValue(), MathContext.DECIMAL128);
        return factory.numOf(rawDistance.divide(rawDenominator, MathContext.DECIMAL128));
    }

    private static Num compensatedSum(Num[] values, NumFactory factory) {
        // Neumaier summation: each step carries the rounding residue of the
        // previous step, so the sum is order-stable even when large
        // opposite-sign values cancel catastrophically in a naive
        // accumulation (gross returns are multiplicative and normally
        // positive, but prices can cross zero and produce negative gross
        // returns).
        Num sum = factory.zero();
        Num compensation = factory.zero();
        for (Num value : values) {
            Num next = sum.plus(value);
            if (sum.abs().isGreaterThanOrEqual(value.abs())) {
                compensation = compensation.plus(sum.minus(next).plus(value));
            } else {
                compensation = compensation.plus(value.minus(next).plus(sum));
            }
            sum = next;
        }
        return sum.plus(compensation);
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
