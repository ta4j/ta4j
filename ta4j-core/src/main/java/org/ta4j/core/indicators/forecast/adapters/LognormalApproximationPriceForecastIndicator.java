/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.adapters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Fits a coherent lognormal price approximation from cumulative log-return
 * moments.
 *
 * <p>
 * This is a summary-only approximation, not an empirical path transform. Mean,
 * median, standard deviation, and every retained quantile are derived from one
 * fitted lognormal distribution. Use
 * {@link org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator}
 * when Monte Carlo terminal paths are available.
 *
 * @since 0.23.1
 */
public final class LognormalApproximationPriceForecastIndicator extends CachedIndicator<Forecast>
        implements ForecastProjectionIndicator {

    /** Analytic support identifier emitted by this adapter. */
    public static final String SUPPORT_ASSUMPTION = "lognormal-moment-match";

    private static final int MAX_EXPONENT = 700;
    private static final double EXPM1_SERIES_THRESHOLD = 1e-5d;
    private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution();

    private final Indicator<Num> priceIndicator;
    private final ReturnForecastProjectionIndicator logReturnForecastProjection;
    private final List<Double> quantileProbabilities;

    /**
     * Creates an approximation using the source forecast's quantile probabilities.
     */
    public LognormalApproximationPriceForecastIndicator(Indicator<Num> priceIndicator,
            ReturnForecastProjectionIndicator logReturnForecastProjection) {
        this(builder(priceIndicator, logReturnForecastProjection));
    }

    private LognormalApproximationPriceForecastIndicator(Builder builder) {
        super(IndicatorUtils.requireSameSeries(builder.priceIndicator, builder.logReturnForecastProjection));
        this.priceIndicator = builder.priceIndicator;
        this.logReturnForecastProjection = builder.logReturnForecastProjection;
        this.quantileProbabilities = builder.quantileProbabilities;
    }

    /**
     * Returns an advanced builder with explicit optional quantiles.
     *
     * @since 0.23.1
     */
    public static Builder builder(Indicator<Num> priceIndicator,
            ReturnForecastProjectionIndicator logReturnForecastProjection) {
        return new Builder(priceIndicator, logReturnForecastProjection);
    }

    @Override
    protected Forecast calculate(int index) {
        Forecast source = logReturnForecastProjection.getValue(index);
        if (source == null || source.decisionIndex() != index || source.horizon() != getHorizon()
                || !source.isStable()) {
            return Forecast.unstable(index, getHorizon());
        }
        Num price = priceIndicator.getValue(index);
        if (!Num.isFinite(price) || !price.isPositive()) {
            return Forecast.unstable(index, getHorizon());
        }
        NumFactory numFactory = price.getNumFactory();
        Num meanLogReturn = normalize(source.mean(), numFactory);
        Num standardDeviationLogReturn = normalize(source.standardDeviation(), numFactory);
        if (!Num.isFinite(meanLogReturn) || !Num.isFinite(standardDeviationLogReturn)
                || standardDeviationLogReturn.isNegative()) {
            return Forecast.unstable(index, getHorizon());
        }
        Num variance = standardDeviationLogReturn.multipliedBy(standardDeviationLogReturn);
        Num meanExponent = meanLogReturn.plus(variance.dividedBy(numFactory.two()));
        if (!isSafeExponent(meanExponent, numFactory) || !isSafeExponent(meanLogReturn, numFactory)
                || !isSafeExponent(variance, numFactory)) {
            return Forecast.unstable(index, getHorizon());
        }

        Num mean = price.multipliedBy(meanExponent.exp());
        Num median = price.multipliedBy(meanLogReturn.exp());
        Num expm1Variance = expm1(variance, numFactory);
        Num standardDeviation = expm1Variance.isZero() ? numFactory.zero() : mean.multipliedBy(expm1Variance.sqrt());
        if (!Num.isFinite(mean) || !Num.isFinite(median) || !Num.isFinite(standardDeviation)) {
            return Forecast.unstable(index, getHorizon());
        }

        List<Double> probabilities = quantileProbabilities == null ? new ArrayList<>(source.quantiles().keySet())
                : quantileProbabilities;
        Map<Double, Num> quantiles = new LinkedHashMap<>();
        for (Double probability : probabilities) {
            if (probability <= 0d || probability >= 1d) {
                continue;
            }
            double zScore = STANDARD_NORMAL.inverseCumulativeProbability(probability);
            Num exponent = meanLogReturn.plus(standardDeviationLogReturn.multipliedBy(numFactory.numOf(zScore)));
            if (!isSafeExponent(exponent, numFactory)) {
                continue;
            }
            Num quantile = price.multipliedBy(exponent.exp());
            if (Num.isFinite(quantile)) {
                quantiles.put(probability, quantile);
            }
        }
        return Forecast.builder(index, getHorizon(), numFactory, ForecastSupport.analytic(SUPPORT_ASSUMPTION))
                .mean(mean)
                .median(median)
                .standardDeviation(standardDeviation)
                .quantiles(quantiles)
                .build();
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(priceIndicator.getCountOfUnstableBars(), logReturnForecastProjection.getCountOfUnstableBars());
    }

    @Override
    public int getHorizon() {
        return logReturnForecastProjection.getHorizon();
    }

    private static Num expm1(Num value, NumFactory numFactory) {
        if (value.doubleValue() < EXPM1_SERIES_THRESHOLD) {
            Num square = value.multipliedBy(value);
            Num cube = square.multipliedBy(value);
            return value.plus(square.dividedBy(numFactory.two())).plus(cube.dividedBy(numFactory.numOf(6)));
        }
        return value.exp().minus(numFactory.one());
    }

    private static boolean isSafeExponent(Num value, NumFactory numFactory) {
        return Num.isFinite(value) && !value.abs().isGreaterThan(numFactory.numOf(MAX_EXPONENT));
    }

    private static Num normalize(Num value, NumFactory numFactory) {
        if (!Num.isFinite(value)) {
            return null;
        }
        Num normalized = numFactory.numOf(value.bigDecimalValue());
        return Num.isFinite(normalized) && (!normalized.isZero() || value.isZero()) ? normalized : null;
    }

    private static ReturnForecastProjectionIndicator validateProjection(ReturnForecastProjectionIndicator projection) {
        ReturnForecastProjectionIndicator validated = Objects.requireNonNull(projection,
                "logReturnForecastProjection must not be null");
        if (validated.getReturnRepresentation() != ReturnRepresentation.LOG) {
            throw new IllegalArgumentException("logReturnForecastProjection must use ReturnRepresentation.LOG");
        }
        return validated;
    }

    /**
     * Builder for explicit approximation quantiles.
     *
     * @since 0.23.1
     */
    public static final class Builder {

        private final Indicator<Num> priceIndicator;
        private final ReturnForecastProjectionIndicator logReturnForecastProjection;
        private List<Double> quantileProbabilities;

        private Builder(Indicator<Num> priceIndicator, ReturnForecastProjectionIndicator logReturnForecastProjection) {
            this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
            this.logReturnForecastProjection = validateProjection(logReturnForecastProjection);
        }

        /**
         * Sets optional quantile probabilities. Endpoint probabilities are accepted but
         * omitted because a lognormal distribution has unbounded tails.
         */
        public Builder quantiles(double... probabilities) {
            Objects.requireNonNull(probabilities, "probabilities must not be null");
            TreeSet<Double> sorted = new TreeSet<>();
            for (double probability : probabilities) {
                if (Double.isNaN(probability) || probability < 0d || probability > 1d) {
                    throw new IllegalArgumentException("quantile probability must be in [0, 1]");
                }
                sorted.add(probability);
            }
            this.quantileProbabilities = List.copyOf(sorted);
            return this;
        }

        /** Builds the analytic approximation. */
        public LognormalApproximationPriceForecastIndicator build() {
            return new LognormalApproximationPriceForecastIndicator(this);
        }
    }
}
