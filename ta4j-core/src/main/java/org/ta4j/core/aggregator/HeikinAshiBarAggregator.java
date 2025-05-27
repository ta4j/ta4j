/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
