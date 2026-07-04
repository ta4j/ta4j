/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.Num;

final class ForecastNumerics {

    private ForecastNumerics() {
    }

    static boolean isInvalid(Num value) {
        if (IndicatorUtils.isInvalid(value)) {
            return true;
        }
        Number delegate = value.getDelegate();
        return delegate instanceof Double doubleValue && Double.isInfinite(doubleValue)
                || delegate instanceof Float floatValue && Float.isInfinite(floatValue);
    }
}
