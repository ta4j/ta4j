/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.backtest.BacktestBar;
import org.ta4j.core.backtest.BacktestBarBuilder;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.backtest.BacktestBarSeriesBuilder;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BacktestStrategy;
import org.ta4j.core.criteria.pnl.ReturnCriterion;
import org.ta4j.core.indicators.IndicatorContext;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.reports.TradingStatement;

public class SimpleMovingAverageBacktest {

  public static void main(final String[] args) throws InterruptedException {
    final var series = createBarSeries();

    final BacktestExecutor backtestExecutor = new BacktestExecutor(series);
    final List<TradingStatement> tradingStatements = backtestExecutor.execute(
        List.of(
            create2DaySmaStrategy(series),
            create3DaySmaStrategy(series)
        ),
        DecimalNum.valueOf(50),
        Trade.TradeType.BUY
    );
    System.out.println(tradingStatements);

    final var criterion = new ReturnCriterion();
    tradingStatements.stream()
        .map(tradingStatement -> criterion.calculate(series, tradingStatement.getStrategy().getTradeRecord()))
        .forEach(sum -> System.out.println(sum));
  }


  private static BacktestBarSeries createBarSeries() {
    final var series = new BacktestBarSeriesBuilder().build();
    series.addBar(createBar(series.barBuilder(), createDay(1), 100.0, 100.0, 100.0, 100.0, 1060));
    series.addBar(createBar(series.barBuilder(), createDay(2), 110.0, 110.0, 110.0, 110.0, 1070));
    series.addBar(createBar(series.barBuilder(), createDay(3), 140.0, 140.0, 140.0, 140.0, 1080));
    series.addBar(createBar(series.barBuilder(), createDay(4), 119.0, 119.0, 119.0, 119.0, 1090));
    series.addBar(createBar(series.barBuilder(), createDay(5), 100.0, 100.0, 100.0, 100.0, 1100));
    series.addBar(createBar(series.barBuilder(), createDay(6), 110.0, 110.0, 110.0, 110.0, 1110));
    series.addBar(createBar(series.barBuilder(), createDay(7), 120.0, 120.0, 120.0, 120.0, 1120));
    series.addBar(createBar(series.barBuilder(), createDay(8), 130.0, 130.0, 130.0, 130.0, 1130));
    return series;
  }


  private static BacktestBar createBar(
      final BacktestBarBuilder barBuilder,
      final Instant endTime,
      final Number openPrice,
      final Number highPrice,
      final Number lowPrice,
      final Number closePrice,
      final Number volume
  ) {
    return barBuilder.timePeriod(Duration.ofDays(1))
        .endTime(endTime)
        .openPrice(openPrice)
        .highPrice(highPrice)
        .lowPrice(lowPrice)
        .closePrice(closePrice)
        .volume(volume)
        .build();
  }


  private static Instant createDay(final int day) {
    return Instant.EPOCH.plus(Duration.ofDays(day));
  }


  private static BacktestStrategy create3DaySmaStrategy(final BacktestBarSeries series) {
    final var closePrice = NumericIndicator.closePrice(series);
    final var sma = closePrice.sma(3);
    return new BacktestStrategy(
        "",
        sma.isLessThan(closePrice),
        sma.isGreaterThan(closePrice),
        IndicatorContext.of(sma)
    );
  }


  private static BacktestStrategy create2DaySmaStrategy(final BarSeries series) {
    final var closePrice = NumericIndicator.closePrice(series);
    final var sma = closePrice.sma(2);
    return new BacktestStrategy(
        "",
        sma.isLessThan(closePrice),
        sma.isGreaterThan(closePrice),
        IndicatorContext.of(sma)
    );
  }
}
