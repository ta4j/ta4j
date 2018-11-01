package org.ta4j.core.tradereport;

import org.ta4j.core.num.Num;

/**
 * This class represents report which contains performance statistics
 */
public class PerformanceReport {

    private final Num totalProfitLoss;
    private final Num totalProfitLossPercentage;
    private final Num totalProfit;
    private final Num totalLoss;

    public PerformanceReport(Num totalProfitLoss, Num totalProfitLossPercentage, Num totalProfit, Num totalLoss) {
        this.totalProfitLoss = totalProfitLoss;
        this.totalProfitLossPercentage = totalProfitLossPercentage;
        this.totalProfit = totalProfit;
        this.totalLoss = totalLoss;
    }

    public Num getTotalProfitLoss() {
        return totalProfitLoss;
    }

    public Num getTotalProfitLossPercentage() {
        return totalProfitLossPercentage;
    }

    public Num getTotalProfit() {
        return totalProfit;
    }

    public Num getTotalLoss() {
        return totalLoss;
    }

}
