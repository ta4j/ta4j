/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * Indicator fixture whose constructor dependency can be withheld by its loader.
 */
public class MissingDependencyIndicator extends ClosePriceIndicator {

    public MissingDependencyIndicator(BarSeries series, Dependency dependency) {
        super(series);
    }

    public static class Dependency {
    }
}
