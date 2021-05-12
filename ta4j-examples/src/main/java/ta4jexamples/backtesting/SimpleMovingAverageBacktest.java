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
package ta4jexamples.backtesting;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

public class SimpleMovingAverageBacktest {

    public static void main(String[] args) throws InterruptedException {
        BarSeries series = createBarSeries();

        Strategy strategy3DaySma = create3DaySmaStrategy(series);

        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord3DaySma = seriesManager.run(strategy3DaySma, Trade.TradeType.BUY,
                DecimalNum.valueOf(50));
        System.out.println(tradingRecord3DaySma);

        Strategy strategy2DaySma = create2DaySmaStrategy(series);
        TradingRecord tradingRecord2DaySma = seriesManager.run(strategy2DaySma, Trade.TradeType.BUY,
                DecimalNum.valueOf(50));
        System.out.println(tradingRecord2DaySma);

        AnalysisCriterion criterion = new GrossReturnCriterion();
        Num calculate3DaySma = criterion.calculate(series, tradingRecord3DaySma);
        Num calculate2DaySma = criterion.calculate(series, tradingRecord2DaySma);

        System.out.println(calculate3DaySma);
        System.out.println(calculate2DaySma);
    }

    private static BarSeries createBarSeries() {
        BarSeries series = new BaseBarSeries();
        series.addBar(createBar(CreateDay(1), 100.0, 100.0, 100.0, 100.0, 1060));
        series.addBar(createBar(CreateDay(2), 110.0, 110.0, 110.0, 110.0, 1070));
        series.addBar(createBar(CreateDay(3), 140.0, 140.0, 140.0, 140.0, 1080));
        series.addBar(createBar(CreateDay(4), 119.0, 119.0, 119.0, 119.0, 1090));
        series.addBar(createBar(CreateDay(5), 100.0, 100.0, 100.0, 100.0, 1100));
        series.addBar(createBar(CreateDay(6), 110.0, 110.0, 110.0, 110.0, 1110));
        series.addBar(createBar(CreateDay(7), 120.0, 120.0, 120.0, 120.0, 1120));
        series.addBar(createBar(CreateDay(8), 130.0, 130.0, 130.0, 130.0, 1130));
        return series;
    }

    private static BaseBar createBar(ZonedDateTime endTime, Number openPrice, Number highPrice, Number lowPrice,
            Number closePrice, Number volume) {
        return BaseBar.builder(DecimalNum::valueOf, Number.class)
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime)
                .openPrice(openPrice)
                .highPrice(highPrice)
                .lowPrice(lowPrice)
                .closePrice(closePrice)
                .volume(volume)
                .build();
    }

    private static ZonedDateTime CreateDay(int day) {
        return ZonedDateTime.of(2018, 01, day, 12, 0, 0, 0, ZoneId.systemDefault());
    }

    private static Strategy create3DaySmaStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, 3);
        return new BaseStrategy(new UnderIndicatorRule(sma, closePrice), new OverIndicatorRule(sma, closePrice));
    }

    private static Strategy create2DaySmaStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, 2);
        return new BaseStrategy(new UnderIndicatorRule(sma, closePrice), new OverIndicatorRule(sma, closePrice));
    }
}
