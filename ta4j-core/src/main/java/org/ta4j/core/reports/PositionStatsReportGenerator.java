/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.reports;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.NumberOfBreakEvenPositionsCriterion;
import org.ta4j.core.analysis.criteria.NumberOfLosingPositionsCriterion;
import org.ta4j.core.analysis.criteria.NumberOfWinningPositionsCriterion;
import org.ta4j.core.num.Num;

/**
 * This class generates PositionStatsReport based on provided trading record and
 * bar series.
 *
 * @see PositionStatsReport
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
