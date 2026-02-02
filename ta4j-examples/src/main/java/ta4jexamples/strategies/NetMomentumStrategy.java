/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.indicators.NetMomentumIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import java.awt.Color;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class NetMomentumStrategy {

    private static final Logger LOG = LogManager.getLogger(NetMomentumStrategy.class);

    private static final int DEFAULT_OVERBOUGHT_THRESHOLD = 900;
    private static final int DEFAULT_MOMENTUM_TIMEFRAME = 200;
    private static final int DEFAULT_OVERSOLD_THRESHOLD = -200;
    private static final int DEFAULT_RSI_BARCOUNT = 14;
    private static final double DEFAULT_DECAY_FACTOR = 1;

    public static void main(String[] args) {
        String jsonOhlcResourceFile = "Coinbase-ETH-USD-PT1D-20160517_20251028.json";

        BarSeries series = null;
        try (InputStream resourceStream = NetMomentumStrategy.class.getClassLoader()
                .getResourceAsStream(jsonOhlcResourceFile)) {
            series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(resourceStream);
        } catch (IOException ex) {
            LOG.error("IOException while loading resource: {} - {}", jsonOhlcResourceFile, ex.getMessage());
        }

        Objects.requireNonNull(series, "Bar series was null");

        // Running the strategy
        runSingleStrategy(series);
    }

    private static void runSingleStrategy(BarSeries series) {
        BarSeriesManager seriesManager = new BarSeriesManager(series);

        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePriceIndicator, DEFAULT_RSI_BARCOUNT);
        NetMomentumIndicator rsiM = NetMomentumIndicator.forRsiWithDecay(rsiIndicator, DEFAULT_MOMENTUM_TIMEFRAME,
                DEFAULT_DECAY_FACTOR);
        Strategy strategy = createStrategy(rsiM);

        TradingRecord tradingRecord = seriesManager.run(strategy);
        LOG.debug(() -> strategy.toJson());
        LOG.debug("{}'s number of positions: {}", strategy.getName(), tradingRecord.getPositionCount());

        var netProfitLoss = new NetProfitLossCriterion().calculate(series, tradingRecord);
        LOG.debug("{}'s net profit/loss: {}", strategy.getName(), netProfitLoss);

        // Charting
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withTradingRecordOverlay(tradingRecord)
                .withAnalysisCriterionOverlay(new NetProfitCriterion(), tradingRecord)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50)
                .withLineColor(Color.GRAY)
                .withOpacity(0.3f)
                .withHorizontalMarker(70)
                .withLineColor(Color.RED)
                .withOpacity(0.3f)
                .withHorizontalMarker(30)
                .withLineColor(Color.GREEN)
                .withOpacity(0.3f)
                .withSubChart(rsiM)
                .withHorizontalMarker(0)
                .withLineColor(Color.GRAY)
                .withOpacity(0.3f)
                .toChart();
        chartWorkflow.displayChart(chart);
        chartWorkflow.saveChartImage(chart, series, "net-momentum-strategy", "temp/charts");
    }

    private static Strategy createStrategy(NetMomentumIndicator rsiM) {
        Rule entryRule = new CrossedUpIndicatorRule(rsiM, DEFAULT_OVERSOLD_THRESHOLD);
        Rule exitRule = new CrossedDownIndicatorRule(rsiM, DEFAULT_OVERBOUGHT_THRESHOLD);

        return new BaseStrategy(NetMomentumStrategy.class.getSimpleName(), entryRule, exitRule);
    }

}
