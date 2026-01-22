/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.indicators.KalmanFilterIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.BasePerformanceReport;
import org.ta4j.core.reports.PositionStatsReport;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class MovingAverageCrossOverRangeBacktest {

    private static final Logger LOG = LogManager.getLogger(MovingAverageCrossOverRangeBacktest.class);
    private static final int DEFAULT_DECIMAL_PRECISION = 16;

    public static void main(String[] args) {
        DecimalNum.configureDefaultPrecision(DEFAULT_DECIMAL_PRECISION);

        String resourceName = "Binance-ETH-USD-PT5M-20230313_20230315.json";
        InputStream resourceStream = MovingAverageCrossOverRangeBacktest.class.getClassLoader()
                .getResourceAsStream(resourceName);
        if (resourceStream == null) {
            LOG.error("File not found in classpath: {}", resourceName);
            return;
        }

        BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(resourceStream);
        if (series == null || series.isEmpty()) {
            LOG.error("Bar series was null or empty: {}", series);
            return;
        }

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

        LOG.debug("Back-tested {} strategies on {}-bar series using decimal precision of {} in {}", strategies.size(),
                series.getBarCount(), DEFAULT_DECIMAL_PRECISION, Duration.between(startInstant, Instant.now()));
        LOG.debug(printReport(tradingStatements));
    }

    private static Rule createSmaCrossEntryRule(BarSeries series, int shortBarCount, int longBarCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        KalmanFilterIndicator kalmanFilteredClosePrice = new KalmanFilterIndicator(closePrice);
        SMAIndicator smaShort = new SMAIndicator(kalmanFilteredClosePrice, shortBarCount);
        SMAIndicator smaLong = new SMAIndicator(kalmanFilteredClosePrice, longBarCount);

        return new CrossedUpIndicatorRule(smaShort, smaLong);
    }

    private static Rule createSmaCrossExitRule(BarSeries series, int shortBarCount, int longBarCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        KalmanFilterIndicator kalmanFilteredClosePrice = new KalmanFilterIndicator(closePrice);
        SMAIndicator smaShort = new SMAIndicator(kalmanFilteredClosePrice, shortBarCount);
        SMAIndicator smaLong = new SMAIndicator(kalmanFilteredClosePrice, longBarCount);

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
