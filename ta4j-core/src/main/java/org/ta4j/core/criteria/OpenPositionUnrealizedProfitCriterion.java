/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Analysis criterion that returns the unrealized profit/loss for the open
 * position.
 *
 * <p>
 * This marks the current open position to the series end price via
 * {@link Position#getProfit(int, Num)}. Returns zero when no open position
 * exists.
 * </p>
 *
 * <p>
 * A native futures position is marked via
 * {@link Position#getUnrealizedProfit(Num, int)}, which measures the remaining
 * contract exposure in settlement currency and excludes the already settled
 * variation margin, execution fees and funding. An unavailable mark yields NaN
 * for remaining exposure and zero for exhausted exposure. With an empty series
 * and no defined cutoff, all recorded executions determine whether exposure
 * remains.
 * </p>
 *
 * @since 0.22.2
 */
public class OpenPositionUnrealizedProfitCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        NumFactory factory = series.numFactory();
        if (position.getFuturesContract() == null && !position.isOpened()) {
            return factory.zero();
        }
        int endIndex = series.getEndIndex();
        boolean noMark = series.getBarCount() == 0 || endIndex < series.getBeginIndex();
        Num closePrice = noMark ? NaN.NaN : series.getBar(endIndex).getClosePrice();
        int valuationIndex = series.getBarCount() == 0 && endIndex < 0 && position.getFuturesContract() != null
                ? Integer.MAX_VALUE
                : endIndex;
        Num profit = unrealizedProfit(position, closePrice, valuationIndex);
        return toSeriesNum(factory, profit);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory factory = series.numFactory();
        Position current = tradingRecord.getCurrentPosition();
        if (current.getFuturesContract() == null && !current.isOpened()) {
            return factory.zero();
        }
        int endIndex = tradingRecord.getEndIndex(series);
        boolean noMark = series.getBarCount() == 0 || endIndex < series.getBeginIndex();
        Num closePrice = noMark ? NaN.NaN : series.getBar(endIndex).getClosePrice();
        int valuationIndex = series.getBarCount() == 0 && endIndex < 0 && current.getFuturesContract() != null
                ? Integer.MAX_VALUE
                : endIndex;
        Num profit = unrealizedProfit(current, closePrice, valuationIndex);
        return toSeriesNum(factory, profit);
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isGreaterThan(v2);
    }

    private Num unrealizedProfit(Position position, Num closePrice, int endIndex) {
        if (position.getFuturesContract() == null) {
            return position.getProfit(endIndex, closePrice);
        }
        if (closePrice.isNaN()) {
            NumFactory factory = position.getEntry().getAmount().getNumFactory();
            Num entryAmount = executedAmount(position.getEntry(), endIndex, factory);
            Num exitAmount = position.getExit() == null ? factory.zero()
                    : executedAmount(position.getExit(), endIndex, factory);
            return entryAmount.isGreaterThan(exitAmount) ? NaN.NaN : factory.zero();
        }
        return position.getUnrealizedProfit(closePrice, endIndex);
    }

    private Num executedAmount(Trade trade, int endIndex, NumFactory factory) {
        Num amount = factory.zero();
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= endIndex) {
                amount = amount.plus(factory.numOf(fill.amount().getDelegate()));
            }
        }
        return amount;
    }

    private Num toSeriesNum(NumFactory factory, Num value) {
        if (value == null) {
            return factory.zero();
        }
        if (value.isNaN()) {
            return NaN.NaN;
        }
        return factory.numOf(value.getDelegate());
    }
}
