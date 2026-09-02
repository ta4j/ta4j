/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Correntropy Kalman filter indicator based on the maximum correntropy
 * criterion (MCC).
 * <p>
 * This indicator applies the covariance-whitened bounded fixed-point maximum
 * correntropy Kalman filter of Chen et al. (2017, arXiv:1509.04580),
 * specialized to ta4j's scalar random-walk state model:
 *
 * <pre>
 * x_t = x_{t-1} + q_t
 * y_t = x_t + r_t
 * </pre>
 *
 * where {@code Q_t} is the process-noise variance, {@code R_t} the
 * measurement-noise variance and {@code y_t} the source indicator value. The
 * correntropy kernel down-weights large innovations in a redescending fashion,
 * so a single outlier or a run of outliers cannot drag the estimate away from
 * the underlying level. Values produced by the source, Q and R indicators are
 * normalized through the series {@link NumFactory} before validation and
 * arithmetic, so compatible inputs may use different {@link Num}
 * implementations. With dynamic Q and R indicators this is a useful robust
 * alternative to {@link KalmanFilterIndicator} for noisy or outlier-prone
 * source indicators.
 * <p>
 * At each index the update solves the fixed-point equation
 *
 * <pre>
 * x* = x_t^- + K(x*) * (y_t - x_t^-)
 * </pre>
 *
 * with the covariance-whitened kernel weights
 *
 * <pre>
 * e_x = (x_t^- - x) / sqrt(P_t^-)   e_y = (y_t - x) / sqrt(R_t)
 * c_x = exp(-e_x^2 / (2 * sigma^2)) c_y = exp(-e_y^2 / (2 * sigma^2))
 * K   = P_t^- * c_y / (P_t^- * c_y + R_t * c_x)
 * </pre>
 *
 * using a bounded fixed-point iteration (tolerance {@code 1e-6} relative to
 * {@code max(1, |x|)}, at most 20 iterations). The posterior covariance uses
 * the Joseph form with the robust gain. The kernel therefore operates on
 * dimensionless errors and {@code kernelBandwidth} is a dimensionless kernel
 * scale, not a raw-price distance.
 * <p>
 * When the measurement noise is far smaller than the predicted covariance the
 * correntropy objective can be bimodal, and the bounded fixed-point iteration
 * may settle at the predict-side stationary point instead of the
 * measurement-side maximum. The oracle-backed test fixtures record this
 * behavior in their per-index diagnostics (maximal/local maxima counts), so
 * downstream consumers can distinguish converged acceptances from predict-side
 * saturations.
 * <p>
 * Kernel exponents beyond a fixed bound saturate to zero weight instead of
 * being evaluated with {@link Num#exp()}. This keeps every {@code Num.exp()}
 * call inside the numerically reliable range of every active {@code NumFactory}
 * (verified for {@code DoubleNum} and {@code DecimalNum}) and makes the kernel
 * weights identical across factories. A saturated zero measurement weight is a
 * valid redescending rejection: it yields gain zero, the isolated measurement
 * is ignored, and the estimate stays at the prior state. Finite endpoint
 * differences that exceed a {@link Num} implementation's range are whitened
 * before subtraction, and the corresponding state update uses an
 * endpoint-weighted form. This prevents representable extreme observations from
 * becoming unavailable solely because their raw innovation overflows.
 * <p>
 * Unavailable inputs (non-finite source or non-finite/non-positive Q/R), a
 * non-converged fixed-point iteration or invalid numerical state make the
 * public estimate, weight and residual {@code NaN.NaN} for that index; the last
 * initialized valid state is preserved internally and later valid indices
 * recover.
 *
 * <p>
 * While the source stays pinned at an extreme value or keeps producing
 * saturated rejections the accepted candidate remains at the last trusted level
 * (zero-gain persistence); the filter resumes tracking as soon as the source
 * returns to values consistent with the predicted state. This lockout is a
 * documented property of the redescending kernel, not an error condition.
 *
 * @see CorrentropyKalmanWeightIndicator
 * @see KalmanFilterIndicator
 * @since 0.24.2
 */
public class CorrentropyKalmanFilterIndicator extends CachedIndicator<Num> {

    private static final double DEFAULT_KERNEL_BANDWIDTH = 2.0;

    private static final int DEFAULT_MAX_ITERATIONS = 20;

    private static final double CONVERGENCE_TOLERANCE = 1e-6;

    /**
     * Largest squared kernel exponent for which {@link Num#exp()} is evaluated.
     * Squared exponents above this bound saturate to a zero kernel weight.
     */
    private static final double KERNEL_EXPONENT_BOUND = 15.0;

    private final Indicator<Num> indicator;
    private final Indicator<Num> processNoiseIndicator;
    private final Indicator<Num> measurementNoiseIndicator;
    private final Num kernelBandwidth;

    private transient volatile StateIndicator stateIndicator;
    private transient volatile CorrentropyKalmanWeightIndicator measurementWeightIndicator;
    private transient volatile Indicator<Num> residualIndicator;
    private final transient int maxIterations;
    private final transient Num kernelErrorBound;
    private final transient Num sqrtTwo;
    private final transient Num convergenceTolerance;

    /**
     * Constructs a correntropy Kalman filter indicator with the given source
     * indicator, dynamic process and measurement noise variances and kernel
     * bandwidth.
     *
     * @param indicator                the indicator whose values will be robustly
     *                                 smoothed
     * @param processNoiseVariance     the dynamic process-noise variance indicator
     * @param measurementNoiseVariance the dynamic measurement-noise variance
     *                                 indicator
     * @param kernelBandwidth          the dimensionless correntropy kernel
     *                                 bandwidth (sigma)
     * @throws NullPointerException     if {@code kernelBandwidth} is {@code null}
     * @throws IllegalArgumentException if {@code kernelBandwidth} does not remain
     *                                  finite and positive when converted in the
     *                                  series {@link NumFactory}
     */
    public CorrentropyKalmanFilterIndicator(Indicator<Num> indicator, Indicator<Num> processNoiseVariance,
            Indicator<Num> measurementNoiseVariance, Num kernelBandwidth) {
        this(validateBandwidth(indicator, kernelBandwidth), DEFAULT_MAX_ITERATIONS, indicator, processNoiseVariance,
                measurementNoiseVariance);
    }

    /**
     * Package-private constructor that bounds the fixed-point iteration. Used by
     * owning tests to force and prove the non-convergence path; production callers
     * always use the default of 20 iterations.
     *
     * @param indicator                the indicator whose values will be robustly
     *                                 smoothed
     * @param processNoiseVariance     the dynamic process-noise variance indicator
     * @param measurementNoiseVariance the dynamic measurement-noise variance
     *                                 indicator
     * @param kernelBandwidth          the dimensionless correntropy kernel
     *                                 bandwidth (sigma)
     * @param maxIterations            the maximum number of fixed-point iterations
     * @throws NullPointerException     if {@code kernelBandwidth} is {@code null}
     * @throws IllegalArgumentException if {@code kernelBandwidth} does not remain
     *                                  finite and positive when converted in the
     *                                  series {@link NumFactory}
     */
    CorrentropyKalmanFilterIndicator(Indicator<Num> indicator, Indicator<Num> processNoiseVariance,
            Indicator<Num> measurementNoiseVariance, Num kernelBandwidth, int maxIterations) {
        this(validateBandwidth(indicator, kernelBandwidth), maxIterations, indicator, processNoiseVariance,
                measurementNoiseVariance);
    }

    private CorrentropyKalmanFilterIndicator(Num kernelBandwidth, int maxIterations, Indicator<Num> indicator,
            Indicator<Num> processNoiseVariance, Indicator<Num> measurementNoiseVariance) {
        super(IndicatorUtils.requireSameSeries(indicator, processNoiseVariance, measurementNoiseVariance));
        this.indicator = indicator;
        this.processNoiseIndicator = processNoiseVariance;
        this.measurementNoiseIndicator = measurementNoiseVariance;
        this.kernelBandwidth = kernelBandwidth;
        this.maxIterations = maxIterations;
        NumFactory numFactory = getBarSeries().numFactory();
        this.sqrtTwo = numFactory.numOf(2.0).sqrt();
        this.convergenceTolerance = numFactory.numOf(CONVERGENCE_TOLERANCE);
        this.kernelErrorBound = numFactory.numOf(KERNEL_EXPONENT_BOUND).sqrt();
    }

    /**
     * Calculates the correntropy-filtered value of the underlying indicator at the
     * given index.
     *
     * @param index the index for which to calculate the correntropy-filtered value
     * @return the correntropy-filtered value of the underlying indicator at the
     *         given index, or {@link NaN NaN} when that index is unavailable
     */
    @Override
    protected Num calculate(int index) {
        if (indicator.getBarSeries().getBarCount() == 0) {
            return NaN.NaN;
        }

        KalmanState state = stateIndicator().getValue(index);
        if (!state.currentValuesValid()) {
            return NaN.NaN;
        }
        return state.estimate();
    }

    /**
     * Returns the number of bars up to which this indicator calculates unstable
     * values. This is the maximum of the unstable-bar counts of the source and the
     * two noise indicators.
     *
     * @return the number of unstable bars
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(indicator.getCountOfUnstableBars(), Math.max(processNoiseIndicator.getCountOfUnstableBars(),
                measurementNoiseIndicator.getCountOfUnstableBars()));
    }

    /**
     * Returns an indicator of the difference between the source indicator and this
     * filter, i.e. the residual {@code y_t - x_t}. The view reports {@link NaN NaN}
     * when the series is empty, when the measurement or the filter's estimate is
     * unavailable at the requested index, or when their difference is not
     * representable in the series {@link org.ta4j.core.num.NumFactory}.
     *
     * @return the residual indicator
     * @since 0.24.2
     */
    public Indicator<Num> residual() {
        Indicator<Num> current = residualIndicator;
        if (current == null) {
            synchronized (this) {
                current = residualIndicator;
                if (current == null) {
                    current = new CorrentropyKalmanResidualIndicator(this);
                    residualIndicator = current;
                }
            }
        }
        return current;
    }

    /**
     * Returns the measurement-side kernel weight {@code c_y} at the accepted
     * fixed-point candidate. The weight is one at the first valid observation and
     * otherwise a finite value in {@code [0, 1]} describing how much the
     * measurement contributed to the estimate; zero means the measurement was
     * rejected.
     * <p>
     * The view shares the filter's private recursive state and is cached for
     * repeated use; it does not rerun the fixed-point iteration.
     *
     * @return the measurement-weight indicator
     * @since 0.24.2
     */
    public CorrentropyKalmanWeightIndicator measurementWeight() {
        CorrentropyKalmanWeightIndicator current = measurementWeightIndicator;
        if (current == null) {
            synchronized (this) {
                current = measurementWeightIndicator;
                if (current == null) {
                    current = new CorrentropyKalmanWeightIndicator(this);
                    measurementWeightIndicator = current;
                }
            }
        }
        return current;
    }

    /**
     * Package-private scalar accessor used by the shared measurement-weight view.
     *
     * @param index the index
     * @return the measurement weight at the given index, or {@link NaN NaN} when
     *         the filter's current index is unavailable
     */
    Num measurementWeightAt(int index) {
        if (indicator.getBarSeries().getBarCount() == 0) {
            return NaN.NaN;
        }
        KalmanState state = stateIndicator().getValue(index);
        if (!state.currentValuesValid()) {
            return NaN.NaN;
        }
        return state.weight();
    }

    /**
     * Package-private scalar accessor used by the shared residual view.
     *
     * @param index the index
     * @return the measurement residual at the given index, or {@link NaN NaN} when
     *         the measurement or the filter's current estimate is unavailable or
     *         their difference is not representable
     */
    Num residualAt(int index) {
        Num measurement = normalizeInput(indicator.getValue(index), getBarSeries().numFactory());
        Num estimate = getValue(index);
        if (!Num.isFinite(measurement) || !Num.isFinite(estimate)) {
            return NaN.NaN;
        }
        Num residual = measurement.minus(estimate);
        return Num.isFinite(residual) ? residual : NaN.NaN;
    }

    private StateIndicator stateIndicator() {
        StateIndicator current = stateIndicator;
        if (current == null) {
            synchronized (this) {
                current = stateIndicator;
                if (current == null) {
                    current = new StateIndicator();
                    stateIndicator = current;
                }
            }
        }
        return current;
    }

    /**
     * First-valid initialization: the estimate is the measurement, the covariance
     * starts from the factory's one-valued prior and is corrected with the current
     * Q/R values under zero innovation, and the measurement weight is one.
     */
    private KalmanState initialize(Num measurement, Num processNoise, Num measurementNoise) {
        Num predictedCovariance = getBarSeries().numFactory().one().plus(processNoise);
        Num gain = predictedCovariance.dividedBy(predictedCovariance.plus(measurementNoise));
        Num gainError = getBarSeries().numFactory().one().minus(gain);
        Num covariance = gainError.multipliedBy(gainError)
                .multipliedBy(predictedCovariance)
                .plus(gain.multipliedBy(gain).multipliedBy(measurementNoise));
        if (!Num.isFinite(covariance) || covariance.isNegative()) {
            return KalmanState.UNINITIALIZED;
        }
        return new KalmanState(measurement, covariance, getBarSeries().numFactory().one(), true, true);
    }

    /**
     * Bounded fixed-point MCC update with Joseph-form posterior covariance.
     * Non-convergence, a non-finite/non-positive gain denominator or invalid
     * posterior state makes the current index unavailable while preserving the
     * previous initialized state.
     */
    private KalmanState update(KalmanState previous, Num measurement, Num processNoise, Num measurementNoise) {
        Num predicted = previous.estimate();
        Num predictedCovariance = previous.covariance().plus(processNoise);
        Num innovation = measurement.minus(predicted);
        Num candidate = predicted;
        Num gain = getBarSeries().numFactory().zero();
        boolean converged = false;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Num previousCandidate = candidate;
            Num cX = kernelWeight(predicted, previousCandidate, predictedCovariance);
            Num cY = kernelWeight(measurement, previousCandidate, measurementNoise);
            Num numerator = predictedCovariance.multipliedBy(cY);
            Num denominator = numerator.plus(measurementNoise.multipliedBy(cX));
            if (!Num.isFinite(numerator) || !Num.isFinite(denominator)
                    || denominator.isLessThanOrEqual(getBarSeries().numFactory().zero())) {
                return previous.preserved();
            }
            gain = numerator.dividedBy(denominator);
            if (Num.isFinite(innovation)) {
                candidate = predicted.plus(gain.multipliedBy(innovation));
            } else {
                Num gainError = getBarSeries().numFactory().one().minus(gain);
                candidate = predicted.multipliedBy(gainError).plus(measurement.multipliedBy(gain));
            }
            if (candidate.minus(previousCandidate)
                    .abs()
                    .isLessThanOrEqual(convergenceTolerance
                            .multipliedBy(getBarSeries().numFactory().one().max(candidate.abs())))) {
                converged = true;
                break;
            }
        }
        if (!converged) {
            return previous.preserved();
        }
        Num gainError = getBarSeries().numFactory().one().minus(gain);
        Num covariance = gainError.multipliedBy(gainError)
                .multipliedBy(predictedCovariance)
                .plus(gain.multipliedBy(gain).multipliedBy(measurementNoise));
        Num weight = kernelWeight(measurement, candidate, measurementNoise);
        if (!Num.isFinite(candidate) || !Num.isFinite(covariance) || !Num.isFinite(weight) || covariance.isNegative()) {
            return new KalmanState(candidate, covariance, weight, false, false);
        }
        return new KalmanState(candidate, covariance, weight, true, true);
    }

    /**
     * Covariance-whitened correntropy kernel weight:
     * {@code exp(-(left - right)^2 / (2 * sigma^2 * scaleVariance))}. The absolute
     * error is divided by the larger and then the smaller of
     * {@code sqrt(scaleVariance)} and {@code sigma}, and finally by
     * {@code sqrt(2)}; keeping {@code sigma} and {@code sqrt(2)} as separate
     * factors keeps the intermediate arithmetic overflow-free for any finite
     * accepted bandwidth. Finite endpoints whose direct difference overflows are
     * divided by the two scales first and only then subtracted. Squared exponents
     * above {@link #KERNEL_EXPONENT_BOUND} saturate to a zero weight.
     */
    private Num kernelWeight(Num left, Num right, Num scaleVariance) {
        Num error = left.minus(right);
        Num varianceScale = scaleVariance.sqrt();
        Num largerScale = varianceScale.max(kernelBandwidth);
        Num smallerScale = varianceScale.min(kernelBandwidth);
        Num normalizedError;
        if (Num.isFinite(error)) {
            normalizedError = error.abs().dividedBy(largerScale).dividedBy(smallerScale);
        } else if (Num.isFinite(left) && Num.isFinite(right)) {
            Num scaledLeft = left.dividedBy(largerScale).dividedBy(smallerScale);
            Num scaledRight = right.dividedBy(largerScale).dividedBy(smallerScale);
            normalizedError = scaledLeft.minus(scaledRight).abs();
        } else {
            return NaN.NaN;
        }
        if (normalizedError.isNaN()) {
            return NaN.NaN;
        }
        normalizedError = normalizedError.dividedBy(sqrtTwo);
        if (!Num.isFinite(normalizedError) || normalizedError.isGreaterThan(kernelErrorBound)) {
            return getBarSeries().numFactory().zero();
        }
        Num exponent = normalizedError.multipliedBy(normalizedError);
        return exponent.negate().exp();
    }

    /**
     * Private recursive state. The estimate and covariance are the posterior of the
     * last processed index; {@code weight} is the measurement-side kernel weight of
     * the last processed index; {@code usable} tells whether the next valid index
     * may continue from this state; {@code currentValuesValid} tells whether the
     * public views of this index are valid.
     */
    private record KalmanState(Num estimate, Num covariance, Num weight, boolean usable, boolean currentValuesValid) {

        private static final KalmanState UNINITIALIZED = new KalmanState(NaN.NaN, NaN.NaN, NaN.NaN, false, false);

        private KalmanState preserved() {
            return new KalmanState(estimate, covariance, weight, usable, false);
        }
    }

    private final class StateIndicator extends RecursiveCachedIndicator<KalmanState> {

        private StateIndicator() {
            super(CorrentropyKalmanFilterIndicator.this.indicator);
        }

        @Override
        protected KalmanState calculate(int index) {
            // Pruned requests alias the first available bar (CachedIndicator maps
            // them to calculate(0)): compute against the begin index so the
            // retained state is preserved.
            int effectiveIndex = Math.max(index, getBarSeries().getBeginIndex());
            if (effectiveIndex < getCountOfUnstableBars()) {
                return KalmanState.UNINITIALIZED;
            }
            if (effectiveIndex == getBarSeries().getBeginIndex()) {
                NumFactory numFactory = getBarSeries().numFactory();
                Num measurement = normalizeInput(indicator.getValue(effectiveIndex), numFactory);
                Num processNoise = normalizeInput(processNoiseIndicator.getValue(effectiveIndex), numFactory);
                Num measurementNoise = normalizeInput(measurementNoiseIndicator.getValue(effectiveIndex), numFactory);
                if (isValidJointObservation(measurement, processNoise, measurementNoise)) {
                    return initialize(measurement, processNoise, measurementNoise);
                }
                return KalmanState.UNINITIALIZED;
            }

            NumFactory numFactory = getBarSeries().numFactory();
            Num measurement = normalizeInput(indicator.getValue(effectiveIndex), numFactory);
            Num processNoise = normalizeInput(processNoiseIndicator.getValue(effectiveIndex), numFactory);
            Num measurementNoise = normalizeInput(measurementNoiseIndicator.getValue(effectiveIndex), numFactory);
            KalmanState previous = getValue(effectiveIndex - 1);
            if (!isValidJointObservation(measurement, processNoise, measurementNoise)) {
                return previous.preserved();
            }
            if (!previous.usable()) {
                return initialize(measurement, processNoise, measurementNoise);
            }
            return update(previous, measurement, processNoise, measurementNoise);
        }

        @Override
        public int getCountOfUnstableBars() {
            return CorrentropyKalmanFilterIndicator.this.getCountOfUnstableBars();
        }
    }

    private static Num normalizeInput(Num value, NumFactory numFactory) {
        if (!Num.isFinite(value)) {
            return NaN.NaN;
        }
        return numFactory.numOf(value.bigDecimalValue());
    }

    private static boolean isValidJointObservation(Num measurement, Num processNoise, Num measurementNoise) {
        return Num.isFinite(measurement) && Num.isFinite(processNoise) && processNoise.isPositive()
                && Num.isFinite(measurementNoise) && measurementNoise.isPositive();
    }

    private static Num validateBandwidth(Indicator<Num> indicator, Num bandwidth) {
        Num supplied = Objects.requireNonNull(bandwidth, "kernelBandwidth must not be null");
        if (!Num.isFinite(supplied) || !supplied.isPositive()) {
            throw new IllegalArgumentException("kernelBandwidth must be a finite positive Num, but was " + supplied);
        }

        NumFactory numFactory = Objects.requireNonNull(indicator, "indicator must not be null")
                .getBarSeries()
                .numFactory();
        Num normalized = numFactory.numOf(supplied.bigDecimalValue());
        if (!Num.isFinite(normalized) || !normalized.isPositive()) {
            throw new IllegalArgumentException(
                    "kernelBandwidth must remain finite and positive in the series NumFactory, but was " + supplied);
        }
        return normalized;
    }
}
