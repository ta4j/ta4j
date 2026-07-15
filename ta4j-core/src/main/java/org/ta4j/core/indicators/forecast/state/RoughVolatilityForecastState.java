/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Return state enriched with rough-volatility diagnostics and a cumulative
 * horizon variance term structure.
 *
 * <p>
 * The common return fields live in {@link #moments()}. For a stable state,
 * {@link #roughnessHurst()} is in {@code (0, 0.5)}, {@link #volOfVol()} is
 * non-negative, and entry {@code h - 1} of {@link #horizonVarianceForecasts()}
 * is the cumulative return variance forecast through horizon {@code h}. An
 * unstable state retains known observation provenance in its moments, uses
 * {@link NaN#NaN} for specialized scalars, and exposes an empty term structure.
 *
 * @param moments                  validated log-return moments
 * @param roughnessHurst           bounded roughness exponent
 * @param volOfVol                 population standard deviation of the rolling
 *                                 log-volatility proxy
 * @param horizonVarianceForecasts cumulative variance forecasts for horizons
 *                                 {@code 1..n}
 * @since 0.23.1
 */
public record RoughVolatilityForecastState(ReturnMoments moments, Num roughnessHurst, Num volOfVol,
        List<Num> horizonVarianceForecasts) implements ReturnMomentState {

    /**
     * Creates validated rough-volatility state with defensive numeric
     * normalization.
     *
     * @since 0.23.1
     */
    public RoughVolatilityForecastState {
        moments = Objects.requireNonNull(moments, "moments must not be null");
        if (moments.representation() != ReturnRepresentation.LOG) {
            throw new IllegalArgumentException("rough-volatility moments must use ReturnRepresentation.LOG");
        }
        roughnessHurst = Objects.requireNonNull(roughnessHurst, "roughnessHurst must not be null");
        volOfVol = Objects.requireNonNull(volOfVol, "volOfVol must not be null");
        horizonVarianceForecasts = List
                .copyOf(Objects.requireNonNull(horizonVarianceForecasts, "horizonVarianceForecasts must not be null"));

        if (!moments.isStable()) {
            if (!roughnessHurst.isNaN() || !volOfVol.isNaN() || !horizonVarianceForecasts.isEmpty()) {
                throw new IllegalArgumentException(
                        "unstable rough-volatility state must use NaN scalars and an empty term structure");
            }
        } else {
            NumFactory numFactory = moments.variance().getNumFactory();
            roughnessHurst = normalize(roughnessHurst, numFactory, "roughnessHurst");
            volOfVol = normalize(volOfVol, numFactory, "volOfVol");
            Num upperHurstBound = numFactory.numOf(0.5d);
            if (!roughnessHurst.isPositive() || !roughnessHurst.isLessThan(upperHurstBound)) {
                throw new IllegalArgumentException("roughnessHurst must be in (0, 0.5)");
            }
            if (volOfVol.isNegative()) {
                throw new IllegalArgumentException("volOfVol must be >= 0");
            }
            if (horizonVarianceForecasts.isEmpty()) {
                throw new IllegalArgumentException("stable rough-volatility state must include horizon variances");
            }
            List<Num> normalizedForecasts = new ArrayList<>(horizonVarianceForecasts.size());
            Num previous = null;
            for (Num forecast : horizonVarianceForecasts) {
                Num normalized = normalize(forecast, numFactory, "horizon variance");
                if (normalized.isNegative()) {
                    throw new IllegalArgumentException("horizon variances must be >= 0");
                }
                if (previous != null && normalized.isLessThan(previous)) {
                    throw new IllegalArgumentException("cumulative horizon variances must be non-decreasing");
                }
                normalizedForecasts.add(normalized);
                previous = normalized;
            }
            if (!normalizedForecasts.get(0).isEqual(moments.variance())) {
                throw new IllegalArgumentException("first horizon variance must equal current variance");
            }
            horizonVarianceForecasts = List.copyOf(normalizedForecasts);
        }
    }

    /**
     * Creates stable rough-volatility state.
     *
     * @param moments                  stable log-return moments
     * @param roughnessHurst           roughness exponent in {@code (0, 0.5)}
     * @param volOfVol                 finite, non-negative log-volatility proxy
     *                                 dispersion
     * @param horizonVarianceForecasts non-empty cumulative variance term structure
     * @return stable rough-volatility state
     * @since 0.23.1
     */
    public static RoughVolatilityForecastState stable(ReturnMoments moments, Num roughnessHurst, Num volOfVol,
            List<Num> horizonVarianceForecasts) {
        ReturnMoments validated = Objects.requireNonNull(moments, "moments must not be null");
        if (!validated.isStable()) {
            throw new IllegalArgumentException("moments must be stable");
        }
        return new RoughVolatilityForecastState(validated, roughnessHurst, volOfVol, horizonVarianceForecasts);
    }

    /**
     * Creates unavailable rough-volatility state while preserving observation
     * provenance.
     *
     * @param index            source index
     * @param observationCount observations incorporated by the estimator
     * @return unstable log-return rough-volatility state
     * @since 0.23.1
     */
    public static RoughVolatilityForecastState unstable(int index, int observationCount) {
        return new RoughVolatilityForecastState(
                ReturnMoments.unstable(index, observationCount, ReturnRepresentation.LOG), NaN.NaN, NaN.NaN, List.of());
    }

    private static Num normalize(Num value, NumFactory numFactory, String fieldName) {
        Num input = Objects.requireNonNull(value, fieldName + " must not be null");
        if (!Num.isFinite(input)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        Num normalized = numFactory.numOf(input.bigDecimalValue());
        if (!Num.isFinite(normalized)) {
            throw new IllegalArgumentException(fieldName + " must be finite in the moments factory");
        }
        if (normalized.isZero() && !input.isZero()) {
            throw new IllegalArgumentException(fieldName + " underflows the moments factory");
        }
        return normalized;
    }
}
