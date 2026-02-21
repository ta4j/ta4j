/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.MACDHistogramMode;
import org.ta4j.core.indicators.MACDVMomentumProfile;
import org.ta4j.core.indicators.MACDVMomentumState;
import org.ta4j.core.indicators.VolatilityNormalizedMACDIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;

/**
 * Strategy showing the volatility-normalized MACD-V workflow:
 *
 * <ul>
 * <li>custom signal-line injection ({@link SMAIndicator})</li>
 * <li>histogram polarity configuration</li>
 * <li>momentum-state filtering via {@link MACDVMomentumProfile}</li>
 * </ul>
 */
public class MACDVMomentumStateStrategy {

    private static final Logger LOG = LogManager.getLogger(MACDVMomentumStateStrategy.class);

    private static final int FAST_EMA = 12;
    private static final int SLOW_EMA = 26;
    private static final int ATR_PERIOD = 26;
    private static final int SIGNAL_PERIOD = 9;

    private static final MACDVMomentumProfile MOMENTUM_PROFILE = new MACDVMomentumProfile(25, 80, -25, -80);

    /**
     * @param series bar series
     * @return strategy based on volatility-normalized MACD-V momentum-state helpers
     */
    public static Strategy buildStrategy(BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolatilityNormalizedMACDIndicator macdV = new VolatilityNormalizedMACDIndicator(closePrice, FAST_EMA, SLOW_EMA,
                ATR_PERIOD, SIGNAL_PERIOD, 100);
        NumericIndicator histogram = macdV.getHistogram(SIGNAL_PERIOD, SMAIndicator::new,
                MACDHistogramMode.MACD_MINUS_SIGNAL);

        Rule bullishMomentum = macdV.inMomentumState(MOMENTUM_PROFILE, MACDVMomentumState.RALLYING_OR_RETRACING)
                .or(macdV.inMomentumState(MOMENTUM_PROFILE, MACDVMomentumState.HIGH_RISK));
        Rule bearishMomentum = macdV.inMomentumState(MOMENTUM_PROFILE, MACDVMomentumState.REBOUNDING_OR_REVERSING)
                .or(macdV.inMomentumState(MOMENTUM_PROFILE, MACDVMomentumState.LOW_RISK));

        Rule entryRule = macdV.crossedUpSignal(SIGNAL_PERIOD, SMAIndicator::new)
                .and(new OverIndicatorRule(histogram, 0))
                .and(bullishMomentum);
        Rule exitRule = macdV.crossedDownSignal(SIGNAL_PERIOD, SMAIndicator::new)
                .or(new UnderIndicatorRule(histogram, 0))
                .or(bearishMomentum);

        return new BaseStrategy(MACDVMomentumStateStrategy.class.getSimpleName(), entryRule, exitRule,
                macdV.getCountOfUnstableBars());
    }

    public static void main(String[] args) {

        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();
        Strategy strategy = buildStrategy(series);

        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);
        LOG.debug(() -> strategy.toJson());
        LOG.debug("{}'s number of positions: {}", strategy.getName(), tradingRecord.getPositionCount());

        Num grossReturn = new GrossReturnCriterion().calculate(series, tradingRecord);
        LOG.debug("{}'s gross return: {}", strategy.getName(), grossReturn);

        VolatilityNormalizedMACDIndicator macdV = new VolatilityNormalizedMACDIndicator(series, FAST_EMA, SLOW_EMA,
                ATR_PERIOD, SIGNAL_PERIOD, 100);
        Indicator<Num> signal = macdV.getSignalLine(SIGNAL_PERIOD, SMAIndicator::new);
        NumericIndicator histogram = macdV.getHistogram(SIGNAL_PERIOD, SMAIndicator::new,
                MACDHistogramMode.MACD_MINUS_SIGNAL);

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withTradingRecordOverlay(tradingRecord)
                .withSubChart(macdV)
                .withIndicatorOverlay(signal)
                .withSubChart(histogram)
                .withAnalysisCriterionOverlay(new GrossReturnCriterion(), tradingRecord)
                .toChart();
        chartWorkflow.displayChart(chart);
        chartWorkflow.saveChartImage(chart, series, "macdv-momentum-state-strategy", "temp/charts");
    }
}
