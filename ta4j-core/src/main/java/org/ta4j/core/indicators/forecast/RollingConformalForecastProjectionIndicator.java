/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Applies rolling, finite-sample conformal calibration to forecast tails.
 *
 * <p>
 * Calibration uses only forecast decisions whose configured horizon has fully
 * matured at the queried index. The nonconformity score is the absolute error
 * from the historical forecast median. The selected finite-sample score is
 * subtracted from lower-tail quantiles and added to upper-tail quantiles while
 * mean, median, standard deviation, and {@link Forecast#support() support}
 * remain unchanged.
 *
 * <p>
 * Generic value forecasts use the realized indicator value at
 * {@code decisionIndex + horizon}:
 *
 * <pre>{@code
 * var calibrated = new RollingConformalForecastProjectionIndicator(baseForecast, closePrice);
 * }</pre>
 *
 * Cumulative log-return forecasts use
 * {@link #cumulativeLogReturnBuilder(ReturnForecastProjectionIndicator, ReturnIndicator)}.
 * The projection remains unavailable until 30 valid calibration rows mature by
 * default.
 *
 * @since 0.23.1
 */
public final class RollingConformalForecastProjectionIndicator extends CachedIndicator<Forecast>
        implements ForecastProjectionIndicator {

    private final ForecastProjectionIndicator baseForecast;
    private final RealizedValue realizedValue;
    private final int horizon;
    private final int calibrationWindow;
    private final int minimumFiniteSampleCount;
    private final double targetCoverage;
    private final int realizationDecisionWarmup;

    /**
     * Creates a rolling conformal wrapper with operator defaults.
     *
     * @param baseForecast           forecast to calibrate
     * @param realizedValueIndicator realized value observed at forecast maturity
     * @since 0.23.1
     */
    public RollingConformalForecastProjectionIndicator(ForecastProjectionIndicator baseForecast,
            Indicator<Num> realizedValueIndicator) {
        this(builder(baseForecast, realizedValueIndicator));
    }

    private RollingConformalForecastProjectionIndicator(Builder builder) {
        super(validatedAnchor(builder));
        this.baseForecast = builder.baseForecast;
        this.realizedValue = builder.realizedValue;
        this.horizon = builder.baseForecast.getHorizon();
        this.calibrationWindow = builder.calibrationWindow;
        this.targetCoverage = builder.targetCoverage;
        this.minimumFiniteSampleCount = minimumFiniteSampleCount(builder.minimumCalibrationCount,
                builder.calibrationWindow, builder.targetCoverage);
        this.realizationDecisionWarmup = builder.realizationDecisionWarmup;
    }

    /**
     * Starts an advanced builder for values observed at forecast maturity.
     *
     * @param baseForecast           forecast to calibrate
     * @param realizedValueIndicator realized value source from the same series
     * @return rolling conformal builder
     * @since 0.23.1
     */
    public static Builder builder(ForecastProjectionIndicator baseForecast, Indicator<Num> realizedValueIndicator) {
        ForecastProjectionIndicator validatedBase = Objects.requireNonNull(baseForecast,
                "baseForecast must not be null");
        Indicator<Num> validatedRealized = Objects.requireNonNull(realizedValueIndicator,
                "realizedValueIndicator must not be null");
        IndicatorUtils.requireSameSeries(validatedBase, validatedRealized);
        int decisionWarmup = Math.max(0, validatedRealized.getCountOfUnstableBars() - validatedBase.getHorizon());
        return new Builder(validatedBase, validatedRealized, decisionWarmup,
                decisionIndex -> validatedRealized.getValue(decisionIndex + validatedBase.getHorizon()));
    }

    /**
     * Starts an advanced builder whose realizations are cumulative log returns over
     * each forecast horizon.
     *
     * @param baseForecast base cumulative log-return forecast
     * @param logReturns   log-return stream from the same series
     * @return rolling conformal builder
     * @since 0.23.1
     */
    public static Builder cumulativeLogReturnBuilder(ReturnForecastProjectionIndicator baseForecast,
            ReturnIndicator logReturns) {
        ReturnForecastProjectionIndicator validatedBase = Objects.requireNonNull(baseForecast,
                "baseForecast must not be null");
        ReturnIndicator validatedReturns = Objects.requireNonNull(logReturns, "logReturns must not be null");
        if (validatedBase.getReturnRepresentation() != ReturnRepresentation.LOG
                || validatedReturns.getReturnRepresentation() != ReturnRepresentation.LOG) {
            throw new IllegalArgumentException("baseForecast and logReturns must use ReturnRepresentation.LOG");
        }
        IndicatorUtils.requireSameSeries(validatedBase, validatedReturns);
        int decisionWarmup = Math.max(0, validatedReturns.getCountOfUnstableBars() - 1);
        return new Builder(validatedBase, validatedReturns, decisionWarmup,
                decisionIndex -> cumulativeLogReturn(validatedReturns, decisionIndex, validatedBase.getHorizon()));
    }

    @Override
    protected Forecast calculate(int index) {
        Forecast current = exactForecast(baseForecast.getValue(index), index);
        if (current == null || !current.isStable()) {
            return Forecast.unstable(index, horizon);
        }
        NumFactory numFactory = current.mean().getNumFactory();
        int lastMaturedDecision = index - horizon;
        int firstDecision = Math.max(getBarSeries().getBeginIndex(), lastMaturedDecision - calibrationWindow + 1);
        List<Num> scores = new ArrayList<>(calibrationWindow);
        for (int decisionIndex = firstDecision; decisionIndex <= lastMaturedDecision; decisionIndex++) {
            Forecast historical = exactForecast(baseForecast.getValue(decisionIndex), decisionIndex);
            if (historical == null || !historical.isStable()) {
                continue;
            }
            Num predictedMedian = normalize(historical.median(), numFactory);
            Num realized = normalize(realizedValue.valueAtMaturity(decisionIndex), numFactory);
            if (predictedMedian == null || realized == null) {
                continue;
            }
            Num score = realized.minus(predictedMedian).abs();
            if (Num.isFinite(score)) {
                scores.add(score);
            }
        }
        if (scores.size() < minimumFiniteSampleCount) {
            return Forecast.unstable(index, horizon);
        }

        scores.sort(Num::compareTo);
        int rank = (int) Math.ceil((scores.size() + 1d) * targetCoverage);
        Num adjustment = scores.get(rank - 1);
        if (adjustment.isPositive() && current.standardDeviation().isZero()) {
            return Forecast.unstable(index, horizon);
        }
        return widen(current, adjustment, numFactory);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public Forecast getValue(int index) {
        if (index >= 0 && index < getBarSeries().getRemovedBarsCount()) {
            return Forecast.unstable(index, horizon);
        }
        return super.getValue(index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(baseForecast.getCountOfUnstableBars(), realizationDecisionWarmup) + horizon
                + minimumFiniteSampleCount - 1;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public int getHorizon() {
        return horizon;
    }

    private Forecast exactForecast(Forecast forecast, int expectedIndex) {
        return forecast != null && forecast.decisionIndex() == expectedIndex && forecast.horizon() == horizon ? forecast
                : null;
    }

    private Forecast widen(Forecast base, Num adjustment, NumFactory numFactory) {
        Map<Double, Num> widened = new LinkedHashMap<>();
        for (Map.Entry<Double, Num> entry : base.quantiles().entrySet()) {
            Num value = normalize(entry.getValue(), numFactory);
            if (value == null) {
                return Forecast.unstable(base.decisionIndex(), horizon);
            }
            if (entry.getKey() < 0.5d) {
                value = value.minus(adjustment);
            } else if (entry.getKey() > 0.5d) {
                value = value.plus(adjustment);
            }
            if (!Num.isFinite(value)) {
                return Forecast.unstable(base.decisionIndex(), horizon);
            }
            widened.put(entry.getKey(), value);
        }
        try {
            return Forecast.builder(base.decisionIndex(), horizon, numFactory, base.support())
                    .mean(base.mean())
                    .median(base.median())
                    .standardDeviation(base.standardDeviation())
                    .quantiles(widened)
                    .build();
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return Forecast.unstable(base.decisionIndex(), horizon);
        }
    }

    private static Num cumulativeLogReturn(ReturnIndicator logReturns, int decisionIndex, int horizon) {
        NumFactory numFactory = logReturns.getBarSeries().numFactory();
        Num result = numFactory.zero();
        for (int offset = 1; offset <= horizon; offset++) {
            Num value = normalize(logReturns.getValue(decisionIndex + offset), numFactory);
            if (value == null) {
                return null;
            }
            result = result.plus(value);
            if (!Num.isFinite(result)) {
                return null;
            }
        }
        return result;
    }

    private static Num normalize(Num value, NumFactory numFactory) {
        if (!Num.isFinite(value)) {
            return null;
        }
        Num normalized = numFactory.numOf(value.bigDecimalValue());
        return Num.isFinite(normalized) && (!normalized.isZero() || value.isZero()) ? normalized : null;
    }

    private static int minimumFiniteSampleCount(int configuredMinimum, int calibrationWindow, double coverage) {
        if (!supportsCoverage(calibrationWindow, coverage)) {
            throw new IllegalArgumentException("calibrationWindow is too small for targetCoverage");
        }
        int low = configuredMinimum;
        int high = calibrationWindow;
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (supportsCoverage(middle, coverage)) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private static boolean supportsCoverage(int scoreCount, double coverage) {
        return Math.ceil((scoreCount + 1d) * coverage) <= scoreCount;
    }

    private static Indicator<?> validatedAnchor(Builder builder) {
        builder.validate();
        return builder.seriesAnchor;
    }

    @FunctionalInterface
    private interface RealizedValue {
        Num valueAtMaturity(int decisionIndex);
    }

    /**
     * Builder for advanced rolling-conformal settings.
     *
     * @since 0.23.1
     */
    public static final class Builder {

        private final ForecastProjectionIndicator baseForecast;
        private final Indicator<?> seriesAnchor;
        private final int realizationDecisionWarmup;
        private final RealizedValue realizedValue;
        private double targetCoverage = 0.90d;
        private int calibrationWindow = 252;
        private int minimumCalibrationCount = 30;

        private Builder(ForecastProjectionIndicator baseForecast, Indicator<?> seriesAnchor,
                int realizationDecisionWarmup, RealizedValue realizedValue) {
            this.baseForecast = baseForecast;
            this.seriesAnchor = seriesAnchor;
            this.realizationDecisionWarmup = realizationDecisionWarmup;
            this.realizedValue = realizedValue;
        }

        /**
         * Sets the target marginal coverage used for the finite-sample score rank.
         *
         * @param value coverage in {@code (0, 1)}
         * @return this builder
         * @since 0.23.1
         */
        public Builder targetCoverage(double value) {
            targetCoverage = value;
            return this;
        }

        /**
         * Sets the maximum number of recent matured decision rows inspected.
         *
         * @param value positive rolling window size
         * @return this builder
         * @since 0.23.1
         */
        public Builder calibrationWindow(int value) {
            calibrationWindow = value;
            return this;
        }

        /**
         * Sets a lower bound on the valid calibration scores required before output.
         * The finite-sample coverage rank may require more scores.
         *
         * @param value positive minimum not greater than the calibration window
         * @return this builder
         * @since 0.23.1
         */
        public Builder minimumCalibrationCount(int value) {
            minimumCalibrationCount = value;
            return this;
        }

        /**
         * Builds the validated rolling conformal projection.
         *
         * @return configured projection
         * @since 0.23.1
         */
        public RollingConformalForecastProjectionIndicator build() {
            validate();
            return new RollingConformalForecastProjectionIndicator(this);
        }

        private void validate() {
            if (baseForecast.getHorizon() < 1) {
                throw new IllegalArgumentException("baseForecast horizon must be >= 1");
            }
            if (!Double.isFinite(targetCoverage) || targetCoverage <= 0d || targetCoverage >= 1d) {
                throw new IllegalArgumentException("targetCoverage must be in (0, 1)");
            }
            if (calibrationWindow < 1 || minimumCalibrationCount < 1 || minimumCalibrationCount > calibrationWindow) {
                throw new IllegalArgumentException(
                        "calibrationWindow must be >= minimumCalibrationCount and both must be >= 1");
            }
            if (!supportsCoverage(calibrationWindow, targetCoverage)) {
                throw new IllegalArgumentException("calibrationWindow is too small for targetCoverage");
            }
        }
    }
}
