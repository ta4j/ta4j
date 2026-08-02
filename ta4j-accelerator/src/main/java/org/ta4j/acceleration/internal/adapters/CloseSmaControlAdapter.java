/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.adapters;

import org.ta4j.acceleration.spi.AdapterMatch;
import org.ta4j.acceleration.spi.IndicatorAccelerationAdapter;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * CPU-planned Close/SMA control adapter.
 *
 * @since 0.23.1
 */
public final class CloseSmaControlAdapter implements IndicatorAccelerationAdapter<Num> {

    /**
     * Operation ID for the control graph.
     *
     * @since 0.23.1
     */
    public static final String OPERATION_ID = "ta4j.control.close-sma.v1";

    @Override
    public String operationId() {
        return OPERATION_ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AdapterMatch<Num> match(Indicator<?> indicator) {
        if (indicator instanceof ClosePriceIndicator || indicator instanceof SMAIndicator) {
            Indicator<Num> typed = (Indicator<Num>) indicator;
            return AdapterMatch.supported(typed, OPERATION_ID, indicator.toDescriptor().toString(), false, false);
        }
        return AdapterMatch.unsupported("not a ClosePriceIndicator or SMAIndicator control graph");
    }
}
