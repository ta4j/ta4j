/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.reports;

import org.ta4j.core.num.Num;

/**
 * Base implementation of {@link PerformanceReport} containing profit/loss
 * statistics.
 *
 * @since 0.19
 */
public class BasePerformanceReport implements PerformanceReport {

    /** The total PnL. */
    public final Num totalProfitLoss;

    /** The total PnL in percent. */
    public final Num totalProfitLossPercentage;

    /** The total profit. */
    public final Num totalProfit;

    /** The total loss. */
    public final Num totalLoss;

    /**
     * Constructs a new performance report with the specified profit/loss metrics.
     *
     * @param totalProfitLoss           the total profit/loss
     * @param totalProfitLossPercentage the total profit/loss as a percentage
     * @param totalProfit               the total profit
     * @param totalLoss                 the total loss
     */
    public BasePerformanceReport(Num totalProfitLoss, Num totalProfitLossPercentage, Num totalProfit, Num totalLoss) {
        this.totalProfitLoss = totalProfitLoss;
        this.totalProfitLossPercentage = totalProfitLossPercentage;
        this.totalProfit = totalProfit;
        this.totalLoss = totalLoss;
    }

    /** @return {@link #totalProfitLoss} */
    @Deprecated
    public Num getTotalProfitLoss() {
        return totalProfitLoss;
    }

    /** @return {@link #totalProfitLossPercentage} */
    @Deprecated
    public Num getTotalProfitLossPercentage() {
        return totalProfitLossPercentage;
    }

    /** @return {@link #totalProfit} */
    @Deprecated
    public Num getTotalProfit() {
        return totalProfit;
    }

    /** @return {@link #totalLoss} */
    @Deprecated
    public Num getTotalLoss() {
        return totalLoss;
    }

    @Override
    public Num getPerformanceMetric() {
        return totalProfitLoss;
    }

    /**
     * Compares this report to another based on total profit/loss.
     *
     * @param o the other performance report to compare
     * @return a negative integer, zero, or a positive integer as this report's
     *         total profit/loss is less than, equal to, or greater than the other
     * @throws ClassCastException if the other report is not a
     *                            {@code BasePerformanceReport}
     */
    @Override
    public int compareTo(PerformanceReport o) {
        if (o instanceof BasePerformanceReport bo) {
            return this.totalProfitLoss.compareTo(bo.totalProfitLoss);
        }
        throw new ClassCastException(this.getClass().getSimpleName() + " instance cannot be compared to object of type "
                + o.getClass().getSimpleName());
    }
}
