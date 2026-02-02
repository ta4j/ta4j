/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.bars.HeikinAshiBarBuilder;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates a list of {@link BaseBar bars} into another one by following
 * Heikin-Ashi logic
 */
public class HeikinAshiBarAggregator implements BarAggregator {

    @Override
    public List<Bar> aggregate(List<Bar> ohlcBars) {
        var heikinAshiBars = new ArrayList<Bar>();
        var haBuilder = new HeikinAshiBarBuilder();
        Num previousOpen = null;
        Num previousClose = null;

        for (Bar ohlcBar : ohlcBars) {
            haBuilder.timePeriod(ohlcBar.getTimePeriod())
                    .endTime(ohlcBar.getEndTime())
                    .openPrice(ohlcBar.getOpenPrice())
                    .highPrice(ohlcBar.getHighPrice())
                    .lowPrice(ohlcBar.getLowPrice())
                    .closePrice(ohlcBar.getClosePrice())
                    .volume(ohlcBar.getVolume())
                    .amount(ohlcBar.getAmount())
                    .trades(ohlcBar.getTrades());

            if (previousOpen != null && previousClose != null) {
                haBuilder.previousHeikinAshiOpenPrice(previousOpen).previousHeikinAshiClosePrice(previousClose);
            } else {
                haBuilder.previousHeikinAshiOpenPrice(null).previousHeikinAshiClosePrice(null);
            }

            var haBar = haBuilder.build();
            heikinAshiBars.add(haBar);

            previousOpen = haBar.getOpenPrice();
            previousClose = haBar.getClosePrice();
        }
        return heikinAshiBars;
    }

}
