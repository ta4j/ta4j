/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.reports;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.NetLossCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossPercentageCriterion;
import org.ta4j.core.num.Num;

/**
 * Generates a {@link BasePerformanceReport} based on the provided trading
 * record and bar series.
 */
public class PerformanceReportGenerator implements ReportGenerator<BasePerformanceReport> {

    @Override
    public BasePerformanceReport generate(Strategy strategy, TradingRecord tradingRecord, BarSeries series) {
        final Num pnl = new NetProfitLossCriterion().calculate(series, tradingRecord);
        final Num pnlPercentage = new NetProfitLossPercentageCriterion().calculate(series, tradingRecord);
        final Num netProfit = new NetProfitCriterion().calculate(series, tradingRecord);
        final Num netLoss = new NetLossCriterion().calculate(series, tradingRecord);
        return new BasePerformanceReport(pnl, pnlPercentage, netProfit, netLoss);
    }
}
