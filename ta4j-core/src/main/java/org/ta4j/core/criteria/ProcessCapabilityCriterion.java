/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DecimalNum;
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
 * decimals, and when a limit itself overflows the active representation (for
 * example an LSL of -1e400 on a {@code DoubleNum} series) or the factory's
 * narrowing rounds the retained limit (for example a precision-2 LSL of 0.944
 * becoming 0.94) the mean-to-limit distance is computed in decimal space and
 * narrowed once against the complete 3-sigma denominator. Decimal factories
 * retain their configured finite precision during this recovery, so
 * representable capabilities stay finite and a limit only marginally beyond the
 * representation range or the rounding gap still scores its full positive
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
     * @since 0.24.2
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
     * @since 0.24.2
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
        for (Num value : valueArray) {
            if (!Num.isFinite(value)) {
                // A gross return overflowed the factory's representation (for
                // example finite non-zero entry and exit prices whose ratio
                // exceeds the double range). Recompute the capability entirely
                // in decimal space from the raw trade prices: the final Cpk is
                // representable even though the individual returns are not.
                return calculateOverflowedCpk(series, tradingRecord, factory);
            }
        }
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
        for (int i = 0; i < valueArray.length; i++) {
            Num scaledDeviation = valueArray[i].dividedBy(deviationScale).minus(scaledMean);
            valueArray[i] = scaledDeviation.multipliedBy(scaledDeviation);
        }
        Num scaledSquaredSum = compensatedSum(valueArray, factory);
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
            MathContext recoveryContext = recoveryMathContext(factory);
            BigDecimal rawDistance = mean.bigDecimalValue().subtract(lsl, recoveryContext);
            lowerCapability = scaledDistance(rawDistance, deviationScale, threeScaledSigma, factory, recoveryContext);
        } else {
            Num lowerDistance = mean.minus(lslNum);
            if (lowerDistance.isZero() || lslNum.bigDecimalValue().compareTo(lsl) != 0) {
                // Narrowing the retained limit can round it onto the mean
                // and erase a representable capability gap (a precision-2
                // limit of 0.999 against a mean of 1.0 collapses the
                // distance to zero), or round it short of the mean and
                // shrink the gap (a precision-2 limit of 0.944 against a
                // mean of 1.0 loses 0.004 of its 0.056 distance). Whenever
                // the factory-rounded limit differs from the retained
                // decimal, the distance is recomputed from the lossless
                // retained limit before any narrowing.
                MathContext recoveryContext = recoveryMathContext(factory);
                BigDecimal rawDistance = mean.bigDecimalValue().subtract(lsl, recoveryContext);
                lowerCapability = scaledDistance(rawDistance, deviationScale, threeScaledSigma, factory,
                        recoveryContext);
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
            MathContext recoveryContext = recoveryMathContext(factory);
            BigDecimal rawDistance = usl.subtract(mean.bigDecimalValue(), recoveryContext);
            upperCapability = scaledDistance(rawDistance, deviationScale, threeScaledSigma, factory, recoveryContext);
        } else {
            Num upperDistance = uslNum.minus(mean);
            if (upperDistance.isZero() || uslNum.bigDecimalValue().compareTo(usl) != 0) {
                // Mirror of the lower-limit recovery: a limit the factory
                // rounded onto the mean, or short of it, must not erase or
                // shrink a representable capability gap.
                MathContext recoveryContext = recoveryMathContext(factory);
                BigDecimal rawDistance = usl.subtract(mean.bigDecimalValue(), recoveryContext);
                upperCapability = scaledDistance(rawDistance, deviationScale, threeScaledSigma, factory,
                        recoveryContext);
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

    /**
     * Recomputes Cpk entirely in decimal space when a gross return overflows the
     * active {@link Num} representation.
     *
     * <p>
     * Gross returns are quotients of trade prices; a finite non-zero entry and exit
     * whose ratio exceeds the representation range produces a non-finite
     * {@code Num}, but the final capability can still be representable (a DoubleNum
     * pair of returns 1e608 and 2e608 has mean 1.5e608, sigma 0.5e608 and Cpk 1).
     * Prices with zero or non-finite magnitude are not ratio overflow: they are
     * genuinely degenerate, so the criterion keeps its zero-score behavior for
     * them.
     *
     * @param series        the bar series (source of the price numerics)
     * @param tradingRecord the record whose closed positions supply the prices
     * @param factory       the series' numeric factory, used only for the final
     *                      narrowed result
     * @return the capability computed from lossless decimal gross returns
     */
    private Num calculateOverflowedCpk(BarSeries series, TradingRecord tradingRecord, NumFactory factory) {
        MathContext context = recoveryMathContext(factory);
        List<BigDecimal> returns = new ArrayList<>();
        for (Position position : tradingRecord.getPositions()) {
            if (!position.isClosed()) {
                continue;
            }
            Num entryPrice = position.getEntry().getPricePerAsset(series);
            Num exitPrice = position.getExit().getPricePerAsset(series);
            if (!Num.isFinite(entryPrice) || !Num.isFinite(exitPrice) || entryPrice.isZero()) {
                // A zero entry or non-finite price makes the gross return
                // genuinely undefined, not merely unrepresentable.
                return factory.zero();
            }
            BigDecimal ratio = exitPrice.bigDecimalValue().divide(entryPrice.bigDecimalValue(), context);
            if (position.getEntry().isBuy()) {
                returns.add(ratio);
            } else {
                returns.add(BigDecimal.valueOf(2).subtract(ratio, context));
            }
        }
        if (returns.isEmpty()) {
            return factory.zero();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : returns) {
            sum = sum.add(value, context);
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(returns.size()), context);
        BigDecimal squaredSum = BigDecimal.ZERO;
        for (BigDecimal value : returns) {
            BigDecimal deviation = value.subtract(mean, context);
            squaredSum = squaredSum.add(deviation.multiply(deviation, context), context);
        }
        BigDecimal threeSigma = squaredSum.divide(BigDecimal.valueOf(returns.size()), context)
                .sqrt(context)
                .multiply(BigDecimal.valueOf(3), context);
        if (threeSigma.signum() == 0) {
            return factory.zero();
        }
        BigDecimal lowerCapability = mean.subtract(lsl, context).divide(threeSigma, context);
        if (usl == null) {
            return factory.numOf(lowerCapability);
        }
        BigDecimal upperCapability = usl.subtract(mean, context).divide(threeSigma, context);
        return factory.numOf(lowerCapability.min(upperCapability));
    }

    private static Num scaledDistance(BigDecimal rawDistance, Num deviationScale, Num threeScaledSigma,
            NumFactory factory, MathContext mathContext) {
        // The raw decimal distance is narrowed once against the complete
        // 3 * sigma denominator instead of rounding separate limit/scale and
        // mean/scale quotients. Both call sites retain the limit losslessly
        // (out-of-range limits and limits the factory narrows at all); the
        // mean and scale terms are finite, and the deviation scale is finite
        // and nonzero here.
        // The operands stay in decimal space end to end: DecimalNum means can
        // exceed the double range (1.1e400), and their doubleValue() would be
        // infinite, making BigDecimal.valueOf throw NumberFormatException.
        BigDecimal rawDenominator = deviationScale.bigDecimalValue()
                .multiply(threeScaledSigma.bigDecimalValue(), mathContext);
        return factory.numOf(rawDistance.divide(rawDenominator, mathContext));
    }

    private static MathContext recoveryMathContext(NumFactory factory) {
        if (factory.one() instanceof DecimalNum decimalNum) {
            MathContext mathContext = decimalNum.getMathContext();
            if (mathContext.getPrecision() > 0) {
                return mathContext;
            }
        }
        return MathContext.DECIMAL128;
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
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(ReturnRepresentation.MULTIPLICATIVE);
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
