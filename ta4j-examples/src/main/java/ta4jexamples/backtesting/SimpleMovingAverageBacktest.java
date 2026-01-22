/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class SimpleMovingAverageBacktest {

    private static final Logger LOG = LogManager.getLogger(SimpleMovingAverageBacktest.class);

    public static void main(String[] args) throws InterruptedException {
        BarSeries series = createBarSeries();

        Strategy strategy3DaySma = create3DaySmaStrategy(series);

        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord3DaySma = seriesManager.run(strategy3DaySma, Trade.TradeType.BUY,
                DecimalNum.valueOf(50));
        LOG.debug(tradingRecord3DaySma.toString());

        Strategy strategy2DaySma = create2DaySmaStrategy(series);
        TradingRecord tradingRecord2DaySma = seriesManager.run(strategy2DaySma, Trade.TradeType.BUY,
                DecimalNum.valueOf(50));
        LOG.debug(tradingRecord2DaySma.toString());

        var criterion = new GrossReturnCriterion();
        Num calculate3DaySma = criterion.calculate(series, tradingRecord3DaySma);
        Num calculate2DaySma = criterion.calculate(series, tradingRecord2DaySma);

        LOG.debug(calculate3DaySma.toString());
        LOG.debug(calculate2DaySma.toString());
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
