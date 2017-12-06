/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.analysis.criteria;

import org.ta4j.core.*;

/**
 * Versus "buy and hold" criterion.
 * <p></p>
 * Compares the value of a provided {@link AnalysisCriterion criterion} with the value of a {@link BuyAndHoldCriterion "buy and hold" criterion}.
 */
public class VersusBuyAndHoldCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion criterion;

    /**
     * Constructor.
     * @param criterion an analysis criterion to be compared
     */
    public VersusBuyAndHoldCriterion(AnalysisCriterion criterion) {
        this.criterion = criterion;
    }

    /**
     * Calculates the criteria of a trading record relative to a buy-and-hold strategy
     * over the entire series.  Assumes buy-and-hold for the entire range of the series.
     * The trading record may have been generated based on only a portion of the series range.
     * If so, use the version of the method that takes start and finish indices to
     * limit the buy-and-hold range.
     * @param series a time series
     * @param tradingRecord a trading record over a subset of the time series
     * @return the ratio of the trading record criteria to buy-and-hold criteria on the entire series
     */
    @Override
    public double calculate(TimeSeries series, TradingRecord tradingRecord) {
    	return calculate(series, tradingRecord, series.getBeginIndex(), series.getEndIndex());
    }

    /**
     * Calculates the criteria of a trading record relative to a buy-and-hold strategy
     * over a subset of the series.  The subset of the series to use for the buy-and-hold
     * calculation is given by the start and finish indices.  For example, if the trading
     * record is generated with {@link TimeSeriesManager#run(Strategy, int, int)} then
     * this method should be used with the same integer parameters to accurately compare
     * the record with buy-and-hold on the same subset of the series.
     * @param series a time series
     * @param tradingRecord a trading record over a subset of the time series
     * @param startIndex the start index of the subset to use for the buy-and-hold calculation
     * @param finishIndex the finish index of the subset to use for the buy-and-hold calculation
     * @return the ratio of the trading record criteria to buy-and-hold criteria on a subset of the series
     */
    public double calculate(TimeSeries series, TradingRecord tradingRecord, int startIndex, int finishIndex) {
        TradingRecord fakeRecord = new BaseTradingRecord();
        fakeRecord.enter(startIndex);
        fakeRecord.exit(finishIndex);

        return criterion.calculate(series, tradingRecord) / criterion.calculate(series, fakeRecord);
    }

    /**
     * Calculates the criteria of a trade relative to a buy-and-hold strategy over the
     * entire series.  Assumes buy-and-hold for the entire range of the series.  The trade may
     * have been generated based on only a portion of the series range.  If so, use the version
     * of the method that takes start and finish indices to limit the buy-and-hold range.
     * @param series a time series
     * @param trade a trade on a subset of the time series
     * @return the ratio of the trade criteria to buy-and-hold criteria on the entire series
     */
    @Override
    public double calculate(TimeSeries series, Trade trade) {
    	return calculate(series, trade, series.getBeginIndex(), series.getEndIndex());
    }

    /**
     * Calculates the criteria of a trade relative to a buy-and-hold strategy over a subset
     * of the series.  The subset of the series to use for the buy-and-hold calculation is
     * given by the start and finish indices.
     * @param series a time series
     * @param tradingRecord a trading record over a subset of the time series
     * @param startIndex the start index of the subset to use for the buy-and-hold calculation
     * @param finishIndex the finish index of the subset to use for the buy-and-hold calculation
     * @return the ratio of the trading record criteria to buy-and-hold criteria on a subset of the series
     */
    public double calculate(TimeSeries series, Trade trade, int startIndex, int finishIndex) {
        TradingRecord fakeRecord = new BaseTradingRecord();
        fakeRecord.enter(startIndex);
        fakeRecord.exit(finishIndex);

        return criterion.calculate(series, trade) / criterion.calculate(series, fakeRecord);
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return criterionValue1 > criterionValue2;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append(" (").append(criterion).append(')');
        return sb.toString();
    }
}
