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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.Trade;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BacktestStrategy;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.average.SMAIndicator;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.PerformanceReport;
import org.ta4j.core.reports.PositionStatsReport;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import ta4jexamples.loaders.CsvBarsLoader;

public class SimpleMovingAverageRangeBacktest {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleMovingAverageRangeBacktest.class);

    public static void main(final String[] args) throws IOException {

        final int start = 3;
        final int stop = 3;
        final int step = 5;

        final List<Function<BacktestBarSeries, BacktestStrategy>> strategies = new ArrayList<>();
        for (int i = start; i <= stop; i += step) {
            final var p = i;
            strategies.add(x -> new BacktestStrategy("Sma(" + p + ")", createEntryRule(x, p), createExitRule(x, p)));
        }

        final var series = CsvBarsLoader.loadAppleIncSeries();
        final var backtestExecutor = new BacktestExecutor(series);
        final List<TradingStatement> tradingStatements = backtestExecutor.execute(new BacktestStrategy("SMA", createEntryRule(series, 3), createExitRule(series, 3)), DecimalNum.valueOf(50),
                Trade.TradeType.BUY);

        LOG.info(printReport(tradingStatements));
    }

    private static Rule createEntryRule(final BarSeries series, final int barCount) {
        final Indicator<Num> closePrice = new ClosePriceIndicator(series);
        final SMAIndicator sma = new SMAIndicator(closePrice, barCount);
        return new UnderIndicatorRule(sma, closePrice);
    }

    private static Rule createExitRule(final BarSeries series, final int barCount) {
        final Indicator<Num> closePrice = new ClosePriceIndicator(series);
        final SMAIndicator sma = new SMAIndicator(closePrice, barCount);
        return new OverIndicatorRule(sma, closePrice);
    }

    private static String printReport(final List<TradingStatement> tradingStatements) {
        final StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append(System.lineSeparator());
        for (final TradingStatement statement : tradingStatements) {
            resultBuilder.append(printStatementReport(statement));
            resultBuilder.append(System.lineSeparator());
        }

        return resultBuilder.toString();
    }

    private static StringBuilder printStatementReport(final TradingStatement statement) {
        final StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("######### ")
                .append(statement.getStrategy().getName())
                .append(" #########")
                .append(System.lineSeparator())
                .append(printPerformanceReport(statement.getPerformanceReport()))
                .append(System.lineSeparator())
                .append(printPositionStats(statement.getPositionStatsReport()))
                .append(System.lineSeparator())
                .append("###########################");
        return resultBuilder;
    }

    private static StringBuilder printPerformanceReport(final PerformanceReport report) {
        final StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("--------- performance report ---------")
                .append(System.lineSeparator())
                .append("total loss: ")
                .append(report.getTotalLoss())
                .append(System.lineSeparator())
                .append("total profit: ")
                .append(report.getTotalProfit())
                .append(System.lineSeparator())
                .append("total profit loss: " + report.getTotalProfitLoss())
                .append(System.lineSeparator())
                .append("total profit loss percentage: ")
                .append(report.getTotalProfitLossPercentage())
                .append(System.lineSeparator())
                .append("---------------------------");
        return resultBuilder;
    }

    private static StringBuilder printPositionStats(final PositionStatsReport report) {
        final StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("--------- trade statistics report ---------")
                .append(System.lineSeparator())
                .append("loss trade count: ")
                .append(report.getLossCount())
                .append(System.lineSeparator())
                .append("profit trade count: ")
                .append(report.getProfitCount())
                .append(System.lineSeparator())
                .append("break even trade count: ")
                .append(report.getBreakEvenCount())
                .append(System.lineSeparator())
                .append("---------------------------");
        return resultBuilder;
    }
}
