/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.reports;

import org.ta4j.core.num.Num;

/**
 * Represents a report with statistics for positions.
 */
public class PositionStatsReport {

    /** The number of positions making a profit. */
    private final Num profitCount;

    /** The number of positions making a loss. */
    private final Num lossCount;

    /** The number of positions with a break even. */
    private final Num breakEvenCount;

    /**
     * Constructor.
     *
     * @param profitCount    the number of positions making a profit
     * @param lossCount      the number of positions making a loss
     * @param breakEvenCount the number of positions with a break even
     */
    public PositionStatsReport(Num profitCount, Num lossCount, Num breakEvenCount) {
        this.profitCount = profitCount;
        this.lossCount = lossCount;
        this.breakEvenCount = breakEvenCount;
    }

    /** @return {@link #profitCount} */
    public Num getProfitCount() {
        return profitCount;
    }

    /** @return {@link #lossCount} */
    public Num getLossCount() {
        return lossCount;
    }

    /** @return {@link #breakEvenCount} */
    public Num getBreakEvenCount() {
        return breakEvenCount;
    }
}
