/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.ReturnCriterion;
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

        var criterion = new ReturnCriterion();
        Num calculate3DaySma = criterion.calculate(series, tradingRecord3DaySma);
        Num calculate2DaySma = criterion.calculate(series, tradingRecord2DaySma);

        System.out.println(calculate3DaySma);
        System.out.println(calculate2DaySma);
    }

    private static BarSeries createBarSeries() {
        final var series = new BaseBarSeriesBuilder().build();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(createDay(1))
                .openPrice(100.0)
                .highPrice(100.0)
                .lowPrice(100.0)
                .closePrice(100.0)
                .volume(1060)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(createDay(2))
                .openPrice(110.0)
                .highPrice(110.0)
                .lowPrice(110.0)
                .closePrice(110.0)
                .volume(1070)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(createDay(3))
                .openPrice(140.0)
                .highPrice(140.0)
                .lowPrice(140.0)
                .closePrice(140.0)
                .volume(1080)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(createDay(4))
                .openPrice(119.0)
                .highPrice(119.0)
                .lowPrice(119.0)
                .closePrice(119.0)
                .volume(1090)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(createDay(5))
                .openPrice(100.0)
                .highPrice(100.0)
                .lowPrice(100.0)
                .closePrice(100.0)
                .volume(1100)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(createDay(6))
                .openPrice(110.0)
                .highPrice(110.0)
                .lowPrice(110.0)
                .closePrice(110.0)
                .volume(1110)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(createDay(7))
                .openPrice(120.0)
                .highPrice(120.0)
                .lowPrice(120.0)
                .closePrice(120.0)
                .volume(1120)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(createDay(8))
                .openPrice(130.0)
                .highPrice(130.0)
                .lowPrice(130.0)
                .closePrice(130.0)
                .volume(1130)
                .add();
        return series;
    }

    private static Instant createDay(int day) {
        return ZonedDateTime.of(2018, 01, day, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();
    }

    private static Strategy create3DaySmaStrategy(BarSeries series) {
        var closePrice = new ClosePriceIndicator(series);
        var sma = new SMAIndicator(closePrice, 3);
        return new BaseStrategy(new UnderIndicatorRule(sma, closePrice), new OverIndicatorRule(sma, closePrice));
    }

    private static Strategy create2DaySmaStrategy(BarSeries series) {
        var closePrice = new ClosePriceIndicator(series);
        var sma = new SMAIndicator(closePrice, 2);
        return new BaseStrategy(new UnderIndicatorRule(sma, closePrice), new OverIndicatorRule(sma, closePrice));
    }
}
