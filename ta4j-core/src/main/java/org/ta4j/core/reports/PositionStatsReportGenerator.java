/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.reports;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.NumberOfBreakEvenPositionsCriterion;
import org.ta4j.core.criteria.NumberOfLosingPositionsCriterion;
import org.ta4j.core.criteria.NumberOfWinningPositionsCriterion;
import org.ta4j.core.num.Num;

/**
 * Generates a {@link PositionStatsReport} based on provided trading record and
 * bar series.
 */
public class PositionStatsReportGenerator implements ReportGenerator<PositionStatsReport> {

    @Override
    public PositionStatsReport generate(Strategy strategy, TradingRecord tradingRecord, BarSeries series) {
        final Num winningPositions = new NumberOfWinningPositionsCriterion().calculate(series, tradingRecord);
        final Num losingPositions = new NumberOfLosingPositionsCriterion().calculate(series, tradingRecord);
        final Num breakEvenPositions = new NumberOfBreakEvenPositionsCriterion().calculate(series, tradingRecord);
        return new PositionStatsReport(winningPositions, losingPositions, breakEvenPositions);
    }
}
