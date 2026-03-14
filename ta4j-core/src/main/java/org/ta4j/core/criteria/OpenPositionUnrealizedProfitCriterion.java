/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
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
 * @since 0.22.2
 */
public class OpenPositionUnrealizedProfitCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        NumFactory factory = series.numFactory();
        if (!position.isOpened()) {
            return factory.zero();
        }
        int endIndex = series.getEndIndex();
        Num closePrice = series.getBar(endIndex).getClosePrice();
        Num profit = position.getProfit(endIndex, closePrice);
        return toSeriesNum(factory, profit);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory factory = series.numFactory();
        int endIndex = tradingRecord.getEndIndex(series);
        Num closePrice = series.getBar(endIndex).getClosePrice();
        Position current = tradingRecord.getCurrentPosition();
        if (!current.isOpened()) {
            return factory.zero();
        }
        Num profit = current.getProfit(endIndex, closePrice);
        return toSeriesNum(factory, profit);
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isGreaterThan(v2);
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
