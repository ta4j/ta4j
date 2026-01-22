/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.BasePerformanceReport;
import org.ta4j.core.reports.PositionStatsReport;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

import java.util.ArrayList;
import java.util.List;

public class SimpleMovingAverageRangeBacktest {

    private static final Logger LOG = LogManager.getLogger(SimpleMovingAverageRangeBacktest.class);

    public static void main(String[] args) {
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();

        int start = 3;
        int stop = 50;
        int step = 5;

        final List<Strategy> strategies = new ArrayList<>();
        for (int i = start; i <= stop; i += step) {
            Strategy strategy = new BaseStrategy("Sma(" + i + ")", createEntryRule(series, i),
                    createExitRule(series, i));
            strategies.add(strategy);
        }
        BacktestExecutor backtestExecutor = new BacktestExecutor(series);
        List<TradingStatement> tradingStatements = backtestExecutor.execute(strategies, DecimalNum.valueOf(50),
                Trade.TradeType.BUY);

        LOG.debug(printReport(tradingStatements));
    }

    private static Rule createEntryRule(BarSeries series, int barCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, barCount);
        return new UnderIndicatorRule(sma, closePrice);
    }

    private static Rule createExitRule(BarSeries series, int barCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, barCount);
        return new OverIndicatorRule(sma, closePrice);
    }

    private static String printReport(List<TradingStatement> tradingStatements) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append(System.lineSeparator());
        for (TradingStatement statement : tradingStatements) {
            resultBuilder.append(printStatementReport(statement));
            resultBuilder.append(System.lineSeparator());
        }

        return resultBuilder.toString();
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

    private static StringBuilder printPerformanceReport(BasePerformanceReport report) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("--------- performance report ---------")
                .append(System.lineSeparator())
                .append("total loss: ")
                .append(report.totalLoss)
                .append(System.lineSeparator())
                .append("total profit: ")
                .append(report.totalProfit)
                .append(System.lineSeparator())
                .append("total profit loss: ")
                .append(report.totalProfitLoss)
                .append(System.lineSeparator())
                .append("total profit loss percentage: ")
                .append(report.totalProfitLossPercentage)
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
