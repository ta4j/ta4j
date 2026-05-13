/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serializable;

/**
 * A factory that provides a builder for a bar.
 *
 * <p>
 * Pair the factory with the bar semantics you need: time bars for clock-aligned
 * candles, or alternative factories for volume/amount/tick driven aggregation.
 * In most workflows, this is configured once at series construction time.
 * </p>
 */
public interface BarBuilderFactory extends Serializable {

    /**
     * Constructor.
     *
     * @param series the bar series to which the created bar should be added
     * @return the bar builder
     */
    BarBuilder createBarBuilder(BarSeries series);
}
