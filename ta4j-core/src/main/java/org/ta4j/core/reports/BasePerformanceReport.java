/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.reports;

import java.util.Objects;

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
     * Compares this report to another based first on total profit/loss, then the
     * remaining profit/loss metrics as deterministic tie breakers.
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
            int result = this.totalProfitLoss.compareTo(bo.totalProfitLoss);
            if (result != 0) {
                return result;
            }
            result = this.totalProfitLossPercentage.compareTo(bo.totalProfitLossPercentage);
            if (result != 0) {
                return result;
            }
            result = this.totalProfit.compareTo(bo.totalProfit);
            if (result != 0) {
                return result;
            }
            return this.totalLoss.compareTo(bo.totalLoss);
        }
        throw new ClassCastException(this.getClass().getSimpleName() + " instance cannot be compared to object of type "
                + o.getClass().getSimpleName());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BasePerformanceReport other)) {
            return false;
        }
        return Objects.equals(totalProfitLoss, other.totalProfitLoss)
                && Objects.equals(totalProfitLossPercentage, other.totalProfitLossPercentage)
                && Objects.equals(totalProfit, other.totalProfit) && Objects.equals(totalLoss, other.totalLoss);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalProfitLoss, totalProfitLossPercentage, totalProfit, totalLoss);
    }
}
