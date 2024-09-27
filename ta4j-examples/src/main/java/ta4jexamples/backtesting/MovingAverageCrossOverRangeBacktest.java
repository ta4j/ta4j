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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.PerformanceReport;
import org.ta4j.core.reports.PositionStatsReport;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

import ta4jexamples.loaders.JsonBarsSerializer;

public class MovingAverageCrossOverRangeBacktest {

    private static final Logger LOG = LoggerFactory.getLogger(MovingAverageCrossOverRangeBacktest.class);

    public static void main(String[] args) {
        Path jsonFilePath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources",
                "ETH-USD-PT5M-2023-3-13_2023-3-15.json");
        if (!Files.exists(jsonFilePath)) {
            LOG.error("File not found: {}", jsonFilePath);
            return;
        }

        BarSeries series = JsonBarsSerializer.loadSeries(jsonFilePath.toAbsolutePath().toString());

        int barCountStart = 3;
        int barCountStop = 200;
        int barCountStep = 3;

        final List<Strategy> strategies = new ArrayList<>();
        for (int shortBarCount = barCountStart; shortBarCount <= barCountStop; shortBarCount += barCountStep) {
            for (int longBarCount = shortBarCount
                    + barCountStep; longBarCount <= barCountStop; longBarCount += barCountStep) {
                String strategyName = String.format("Sma(%d) CrossOver Sma(%d)", shortBarCount, longBarCount);
                strategies.add(
                        new BaseStrategy(strategyName, createSmaCrossEntryRule(series, shortBarCount, longBarCount),
                                createSmaCrossExitRule(series, shortBarCount, longBarCount)));
            }
        }

        Instant startInstant = Instant.now();
        BacktestExecutor backtestExecutor = new BacktestExecutor(series);
        List<TradingStatement> tradingStatements = backtestExecutor.execute(strategies, DecimalNum.valueOf(50),
                Trade.TradeType.BUY);

        LOG.debug("Back-tested {} strategies on {}-bar series in {}", strategies.size(), series.getBarCount(),
                Duration.between(startInstant, Instant.now()));
        LOG.info(printReport(tradingStatements));
    }

    private static Rule createSmaCrossEntryRule(BarSeries series, int shortBarCount, int longBarCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        SMAIndicator smaShort = new SMAIndicator(closePrice, shortBarCount);
        SMAIndicator smaLong = new SMAIndicator(closePrice, longBarCount);

        return new CrossedUpIndicatorRule(smaShort, smaLong);
    }

    private static Rule createSmaCrossExitRule(BarSeries series, int shortBarCount, int longBarCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        SMAIndicator smaShort = new SMAIndicator(closePrice, shortBarCount);
        SMAIndicator smaLong = new SMAIndicator(closePrice, longBarCount);

        return new CrossedDownIndicatorRule(smaShort, smaLong);
    }

    private static String printReport(List<TradingStatement> tradingStatements) {
        StringJoiner resultJoiner = new StringJoiner(System.lineSeparator());
        for (TradingStatement statement : tradingStatements) {
            resultJoiner.add(printStatementReport(statement).toString());
        }

        return resultJoiner.toString();
    }

    private static StringBuilder printStatementReport(TradingStatement statement) {
        StringBuilder resultBuilder = new StringBuilder();
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

    private static StringBuilder printPerformanceReport(PerformanceReport report) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("--------- performance report ---------")
                .append(System.lineSeparator())
                .append("total loss: ")
                .append(report.getTotalLoss())
                .append(System.lineSeparator())
                .append("total profit: ")
                .append(report.getTotalProfit())
                .append(System.lineSeparator())
                .append("total profit loss: ")
                .append(report.getTotalProfitLoss())
                .append(System.lineSeparator())
                .append("total profit loss percentage: ")
                .append(report.getTotalProfitLossPercentage())
                .append(System.lineSeparator())
                .append("---------------------------");
        return resultBuilder;
    }

    private static StringBuilder printPositionStats(PositionStatsReport report) {
        StringBuilder resultBuilder = new StringBuilder();
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
