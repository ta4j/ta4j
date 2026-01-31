/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.builder;

/**
 * Controls how charts interpret gaps between bars on the domain axis.
 *
 * <p>
 * Use {@link #REAL_TIME} to preserve actual time gaps (weekends, holidays, or
 * missing bars). Use {@link #BAR_INDEX} to compress the domain axis so bars are
 * evenly spaced by index, eliminating visual gaps while keeping the underlying
 * data unchanged.
 * </p>
 *
 * @since 0.23
 */
public enum TimeAxisMode {

    /**
     * Plot bars using their real timestamps. Missing bars appear as gaps on the
     * time axis.
     */
    REAL_TIME,

    /**
     * Plot bars using their index positions, producing evenly spaced candles and
     * removing time gaps (e.g., weekends, holidays).
     */
    BAR_INDEX
}
