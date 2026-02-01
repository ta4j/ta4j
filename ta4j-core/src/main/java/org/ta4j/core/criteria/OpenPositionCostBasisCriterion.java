/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.OpenPosition;
import org.ta4j.core.Position;
import org.ta4j.core.TradeView;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Analysis criterion that returns the cost basis of the open position.
 *
 * <p>
 * For {@link LiveTradingRecord} this returns the net open position's entry
 * value plus any remaining entry fees. For other trading records, it uses the
 * current position's entry value plus entry transaction cost. Returns zero when
 * no open position exists.
 * </p>
 *
 * @since 0.22.2
 */
public class OpenPositionCostBasisCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        NumFactory factory = series.numFactory();
        if (!position.isOpened()) {
            return factory.zero();
        }
        TradeView entry = position.getEntry();
        Num entryPrice = entry.getPricePerAsset(series);
        Num entryCost = entryPrice.multipliedBy(entry.getAmount()).plus(entry.getCost());
        return toSeriesNum(factory, entryCost);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory factory = series.numFactory();
        if (tradingRecord instanceof LiveTradingRecord liveRecord) {
            OpenPosition open = liveRecord.getNetOpenPosition();
            if (open == null || open.amount() == null || open.amount().isZero()) {
                return factory.zero();
            }
            Num entryCost = toSeriesNum(factory, open.totalEntryCost());
            Num fees = toSeriesNum(factory, open.totalFees());
            return entryCost.plus(fees);
        }
        Position current = tradingRecord.getCurrentPosition();
        if (!current.isOpened()) {
            return factory.zero();
        }
        TradeView entry = current.getEntry();
        Num entryPrice = entry.getPricePerAsset(series);
        Num entryCost = entryPrice.multipliedBy(entry.getAmount()).plus(entry.getCost());
        return toSeriesNum(factory, entryCost);
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isLessThan(v2);
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
