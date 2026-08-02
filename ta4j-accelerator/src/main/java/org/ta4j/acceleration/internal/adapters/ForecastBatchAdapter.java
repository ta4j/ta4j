/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.adapters;

import org.ta4j.acceleration.spi.AdapterMatch;
import org.ta4j.acceleration.spi.IndicatorAccelerationAdapter;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastSpec;
import org.ta4j.core.indicators.forecast.projection.Forecast;

/**
 * Adapter for the EWMA/state plus Monte Carlo price forecast family.
 *
 * @since 0.23.1
 */
public final class ForecastBatchAdapter implements IndicatorAccelerationAdapter<Forecast> {

    /**
     * Operation ID for accelerated Monte Carlo terminal price forecasts.
     *
     * @since 0.23.1
     */
    public static final String OPERATION_ID = "ta4j.forecast.monte-carlo-price.v1";

    @Override
    public String operationId() {
        return OPERATION_ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AdapterMatch<Forecast> match(Indicator<?> indicator) {
        if (!(indicator instanceof MonteCarloPriceForecastIndicator forecastIndicator)) {
            return AdapterMatch.unsupported("not a MonteCarloPriceForecastIndicator");
        }
        MonteCarloPriceForecastSpec spec = forecastIndicator.accelerationSpec();
        if (spec.stateIndicator().getReturnRepresentation() != ReturnRepresentation.LOG
                || spec.stateIndicator().getReturnIndicator().getReturnRepresentation() != ReturnRepresentation.LOG) {
            return AdapterMatch.unsupported("forecast adapter requires log-return state and return sources");
        }
        Indicator<Forecast> typed = (Indicator<Forecast>) indicator;
        String fingerprint = "%s:h%s:i%s:l%s:%s:%s".formatted(OPERATION_ID, spec.horizon(), spec.iterationCount(),
                spec.lookbackBarCount(), spec.shockModel(), spec.volatilityUpdateMode());
        return AdapterMatch.supported(typed, OPERATION_ID, fingerprint, true, true);
    }
}
