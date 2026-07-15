/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.indicators.forecast.state.RoughVolatilityForecastState;
import org.ta4j.core.indicators.statistics.VarianceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Estimates log-return moments, roughness, log-volatility variability, and a
 * cumulative fractional horizon variance term structure.
 *
 * <p>
 * The shortest operator path uses a 60-observation EWMA summary, 120-bar
 * roughness window, 60-bar vol-of-vol window, and five forecast horizons:
 *
 * <pre>{@code
 * RoughVolatilityForecastStateIndicator states = new RoughVolatilityForecastStateIndicator(logReturns);
 * }</pre>
 *
 * <p>
 * Advanced construction changes explicit model assumptions without repeating
 * the semantic return source:
 *
 * <pre>{@code
 * RoughVolatilityForecastStateIndicator states = RoughVolatilityForecastStateIndicator.builder(logReturns)
 *         .initializationBarCount(90)
 *         .decayFactor(0.97)
 *         .roughnessWindow(180)
 *         .volOfVolWindow(90)
 *         .horizon(10)
 *         .build();
 * }</pre>
 *
 * <p>
 * Roughness is estimated by regressing log variograms of
 * {@code log(abs(return) + 1e-8)} over lags one through ten. The bounded Hurst
 * estimate produces cumulative horizon variance
 * {@code currentVariance * horizon^(2 * H)}. Primitive conversion is confined
 * to the regression and fractional-exponent boundaries. State remains unstable
 * until every configured window is complete and finite. A non-finite return
 * makes each affected rolling window unavailable; later clean windows recover
 * automatically.
 *
 * @since 0.23.1
 */
public final class RoughVolatilityForecastStateIndicator extends CachedIndicator<RoughVolatilityForecastState>
        implements ReturnForecastStateIndicator<RoughVolatilityForecastState> {

    private static final int MAX_VARIOGRAM_LAG = 10;
    private static final double PROXY_EPSILON = 1e-8d;
    private static final double VARIOGRAM_FLOOR = 1e-12d;
    private static final double MIN_HURST = 0.01d;
    private static final double MAX_HURST = 0.49d;

    private final ReturnIndicator returnIndicator;
    private final EwmaReturnForecastStateIndicator momentIndicator;
    private final LogVolatilityProxyIndicator proxyIndicator;
    private final VarianceIndicator proxyVarianceIndicator;
    private final int roughnessWindow;
    private final int horizon;

    /**
     * Creates rough-volatility state with operator defaults.
     *
     * @param returnIndicator log-return source
     * @since 0.23.1
     */
    public RoughVolatilityForecastStateIndicator(ReturnIndicator returnIndicator) {
        this(builder(returnIndicator));
    }

    private RoughVolatilityForecastStateIndicator(Builder builder) {
        super(validatedReturnIndicator(builder));
        this.returnIndicator = builder.returnIndicator;
        this.momentIndicator = new EwmaReturnForecastStateIndicator(returnIndicator, builder.initializationBarCount,
                builder.decayFactor, builder.driftMode);
        this.proxyIndicator = new LogVolatilityProxyIndicator(returnIndicator);
        this.proxyVarianceIndicator = VarianceIndicator.ofPopulation(proxyIndicator, builder.volOfVolWindow);
        this.roughnessWindow = builder.roughnessWindow;
        this.horizon = builder.horizon;
    }

    /**
     * Starts an advanced rough-volatility builder.
     *
     * @param returnIndicator log-return source
     * @return builder with operator defaults
     * @since 0.23.1
     */
    public static Builder builder(ReturnIndicator returnIndicator) {
        return new Builder(returnIndicator);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public ReturnIndicator getReturnIndicator() {
        return returnIndicator;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public ReturnRepresentation getReturnRepresentation() {
        return ReturnRepresentation.LOG;
    }

    @Override
    protected RoughVolatilityForecastState calculate(int index) {
        ReturnForecastState baseState = momentIndicator.getValue(index);
        int observationCount = baseState.observationCount();
        if (index < getCountOfUnstableBars() || !baseState.isStable()) {
            return RoughVolatilityForecastState.unstable(index, observationCount);
        }

        double hurst = estimateHurst(index);
        Num proxyVariance = proxyVarianceIndicator.getValue(index);
        if (!Double.isFinite(hurst) || !Num.isFinite(proxyVariance) || proxyVariance.isNegative()) {
            return RoughVolatilityForecastState.unstable(index, observationCount);
        }

        NumFactory numFactory = getBarSeries().numFactory();
        Num roughnessHurst = numFactory.numOf(hurst);
        Num volOfVol = proxyVariance.isZero() ? numFactory.zero() : proxyVariance.sqrt();
        double normalizedHurst = finitePrimitive(roughnessHurst);
        List<Num> horizonVariances = horizonVariances(baseState.variance(), normalizedHurst, numFactory);
        if (!Num.isFinite(roughnessHurst) || !Double.isFinite(normalizedHurst) || !Num.isFinite(volOfVol)
                || horizonVariances.isEmpty()) {
            return RoughVolatilityForecastState.unstable(index, observationCount);
        }

        try {
            ReturnMoments moments = ReturnMoments.stable(index, observationCount, ReturnRepresentation.LOG,
                    baseState.mean(), baseState.drift(), baseState.variance());
            return RoughVolatilityForecastState.stable(moments, roughnessHurst, volOfVol, horizonVariances);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return RoughVolatilityForecastState.unstable(index, observationCount);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public int getCountOfUnstableBars() {
        int roughnessUnstable = proxyIndicator.getCountOfUnstableBars() + roughnessWindow - 1;
        return Math.max(momentIndicator.getCountOfUnstableBars(),
                Math.max(roughnessUnstable, proxyVarianceIndicator.getCountOfUnstableBars()));
    }

    private double estimateHurst(int index) {
        int firstIndex = index - roughnessWindow + 1;
        int maximumLag = Math.min(MAX_VARIOGRAM_LAG, roughnessWindow - 1);
        SimpleRegression regression = new SimpleRegression(true);
        for (int lag = 1; lag <= maximumLag; lag++) {
            double squaredDifferenceTotal = 0d;
            int pairCount = 0;
            for (int currentIndex = firstIndex + lag; currentIndex <= index; currentIndex++) {
                double current = finitePrimitive(proxyIndicator.getValue(currentIndex));
                double previous = finitePrimitive(proxyIndicator.getValue(currentIndex - lag));
                if (!Double.isFinite(current) || !Double.isFinite(previous)) {
                    return Double.NaN;
                }
                double difference = current - previous;
                squaredDifferenceTotal += difference * difference;
                if (!Double.isFinite(squaredDifferenceTotal)) {
                    return Double.NaN;
                }
                pairCount++;
            }
            double variogram = squaredDifferenceTotal / pairCount;
            regression.addData(Math.log(lag), Math.log(Math.max(variogram, VARIOGRAM_FLOOR)));
        }
        double slope = regression.getSlope();
        return Double.isFinite(slope) ? Math.max(MIN_HURST, Math.min(MAX_HURST, slope * 0.5d)) : Double.NaN;
    }

    private List<Num> horizonVariances(Num currentVariance, double hurst, NumFactory numFactory) {
        if (!Num.isFinite(currentVariance) || currentVariance.isNegative()) {
            return List.of();
        }
        List<Num> result = new ArrayList<>(horizon);
        result.add(currentVariance);
        for (int step = 2; step <= horizon; step++) {
            double factor = Math.pow(step, 2d * hurst);
            if (!Double.isFinite(factor)) {
                return List.of();
            }
            Num variance = currentVariance.multipliedBy(numFactory.numOf(factor));
            if (!Num.isFinite(variance) || variance.isNegative() || (variance.isZero() && !currentVariance.isZero())) {
                return List.of();
            }
            result.add(variance);
        }
        return List.copyOf(result);
    }

    private static double finitePrimitive(Num value) {
        if (!Num.isFinite(value)) {
            return Double.NaN;
        }
        double primitive = value.doubleValue();
        return Double.isFinite(primitive) && (primitive != 0d || value.isZero()) ? primitive : Double.NaN;
    }

    private static ReturnIndicator validatedReturnIndicator(Builder builder) {
        builder.validate();
        return builder.returnIndicator;
    }

    /**
     * Builder for advanced rough-volatility settings.
     *
     * @since 0.23.1
     */
    public static final class Builder {

        private final ReturnIndicator returnIndicator;
        private int initializationBarCount = 60;
        private double decayFactor = 0.94d;
        private EwmaReturnForecastStateIndicator.DriftMode driftMode = EwmaReturnForecastStateIndicator.DriftMode.ZERO;
        private int roughnessWindow = 120;
        private int volOfVolWindow = 60;
        private int horizon = 5;

        private Builder(ReturnIndicator returnIndicator) {
            this.returnIndicator = Objects.requireNonNull(returnIndicator, "returnIndicator must not be null");
        }

        /**
         * Sets the EWMA initialization observation count.
         *
         * @param value positive initialization count
         * @return this builder
         * @since 0.23.1
         */
        public Builder initializationBarCount(int value) {
            initializationBarCount = value;
            return this;
        }

        /**
         * Sets the EWMA decay factor.
         *
         * @param value factor in {@code (0, 1)}
         * @return this builder
         * @since 0.23.1
         */
        public Builder decayFactor(double value) {
            decayFactor = value;
            return this;
        }

        /**
         * Sets the forward drift assumption exposed through common moments.
         *
         * @param value existing EWMA drift mode
         * @return this builder
         * @since 0.23.1
         */
        public Builder driftMode(EwmaReturnForecastStateIndicator.DriftMode value) {
            driftMode = value;
            return this;
        }

        /**
         * Sets the rolling log-variogram window.
         *
         * @param value window of at least three observations
         * @return this builder
         * @since 0.23.1
         */
        public Builder roughnessWindow(int value) {
            roughnessWindow = value;
            return this;
        }

        /**
         * Sets the population vol-of-vol window.
         *
         * @param value window of at least two observations
         * @return this builder
         * @since 0.23.1
         */
        public Builder volOfVolWindow(int value) {
            volOfVolWindow = value;
            return this;
        }

        /**
         * Sets the number of cumulative variance horizons emitted by each state.
         *
         * @param value positive horizon count
         * @return this builder
         * @since 0.23.1
         */
        public Builder horizon(int value) {
            horizon = value;
            return this;
        }

        /**
         * Builds the validated state estimator.
         *
         * @return configured rough-volatility estimator
         * @since 0.23.1
         */
        public RoughVolatilityForecastStateIndicator build() {
            return new RoughVolatilityForecastStateIndicator(this);
        }

        private void validate() {
            if (returnIndicator.getReturnRepresentation() != ReturnRepresentation.LOG) {
                throw new IllegalArgumentException("returnIndicator must use ReturnRepresentation.LOG");
            }
            if (initializationBarCount < 1) {
                throw new IllegalArgumentException("initializationBarCount must be >= 1");
            }
            if (!Double.isFinite(decayFactor) || decayFactor <= 0d || decayFactor >= 1d) {
                throw new IllegalArgumentException("decayFactor must be in (0, 1)");
            }
            driftMode = Objects.requireNonNull(driftMode, "driftMode must not be null");
            if (roughnessWindow < 3) {
                throw new IllegalArgumentException("roughnessWindow must be >= 3");
            }
            if (volOfVolWindow < 2) {
                throw new IllegalArgumentException("volOfVolWindow must be >= 2");
            }
            if (horizon < 1) {
                throw new IllegalArgumentException("horizon must be >= 1");
            }
        }
    }

    private static final class LogVolatilityProxyIndicator extends CachedIndicator<Num> {

        private final ReturnIndicator returnIndicator;
        private final Num epsilon;

        private LogVolatilityProxyIndicator(ReturnIndicator returnIndicator) {
            super(returnIndicator);
            this.returnIndicator = returnIndicator;
            this.epsilon = returnIndicator.getBarSeries().numFactory().numOf(PROXY_EPSILON);
        }

        @Override
        protected Num calculate(int index) {
            Num value = returnIndicator.getValue(index);
            if (!Num.isFinite(value)) {
                return NaN.NaN;
            }
            Num proxy = value.abs().plus(epsilon).log();
            return Num.isFinite(proxy) ? proxy : NaN.NaN;
        }

        @Override
        public int getCountOfUnstableBars() {
            return returnIndicator.getCountOfUnstableBars();
        }
    }
}
