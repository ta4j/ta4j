package org.ta4j.core.tradereport;

import org.ta4j.core.num.Num;

/**
 * This class represents report with statistics for executed trades
 */
public class TradeStatsReport {

    private final Num profitTradeCount;
    private final Num lossTradeCount;
    private final Num breakEvenTradeCount;

    public TradeStatsReport(Num profitTradeCount, Num lossTradeCount, Num breakEvenTradeCount) {
        this.profitTradeCount = profitTradeCount;
        this.lossTradeCount = lossTradeCount;
        this.breakEvenTradeCount = breakEvenTradeCount;
    }

    public Num getProfitTradeCount() {
        return profitTradeCount;
    }

    public Num getLossTradeCount() {
        return lossTradeCount;
    }

    public Num getBreakEvenTradeCount() {
        return breakEvenTradeCount;
    }

}
