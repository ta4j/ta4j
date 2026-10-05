/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.forecast;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.OnlineChangePointForecastStateIndicator;
import org.ta4j.core.indicators.forecast.RoughVolatilityForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.OnlineChangePointForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.RoughVolatilityForecastState;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Compares default EWMA, rough-volatility, and online change-point state over
 * an ossified BTC-USD daily series.
 *
 * <p>
 * The reservoir phase extends this example again while retaining the same
 * source series and decision index.
 *
 * @since 0.23.1
 */
public final class ForecastStateComparisonExample {

    private static final Logger LOG = LogManager.getLogger(ForecastStateComparisonExample.class);
    private static final String BTC_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231020.json";

    private ForecastStateComparisonExample() {
    }

    /**
     * Loads the committed BTC series and reports the latest estimator state.
     *
     * @param args ignored
     * @since 0.23.1
     */
    public static void main(String[] args) {
        BarSeries series = Objects.requireNonNull(JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(BTC_RESOURCE),
                "BTC resource was not available");
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator ewma = new EwmaReturnForecastStateIndicator(returns);
        RoughVolatilityForecastStateIndicator rough = new RoughVolatilityForecastStateIndicator(returns);
        OnlineChangePointForecastStateIndicator changePoint = new OnlineChangePointForecastStateIndicator(returns);

        int index = series.getEndIndex();
        ReturnForecastState ewmaState = ewma.getValue(index);
        RoughVolatilityForecastState roughState = rough.getValue(index);
        OnlineChangePointForecastState changePointState = changePoint.getValue(index);
        LOG.info("BTC EWMA state index={} stable={} observations={} mean={} volatility={}", index, ewmaState.isStable(),
                ewmaState.observationCount(), ewmaState.mean(), ewmaState.volatility());
        LOG.info(
                "BTC rough state index={} stable={} observations={} mean={} volatility={} hurst={} volOfVol={} horizonVariances={}",
                index, roughState.isStable(), roughState.observationCount(), roughState.mean(), roughState.volatility(),
                roughState.roughnessHurst(), roughState.volOfVol(), roughState.horizonVarianceForecasts());
        LOG.info(
                "BTC change-point state index={} stable={} observations={} mean={} volatility={} recentChangeProbability={} mostLikelyRunLength={} topRunLengths={}",
                index, changePointState.isStable(), changePointState.observationCount(), changePointState.mean(),
                changePointState.volatility(), changePointState.recentChangeProbability(),
                changePointState.mostLikelyRunLength(), changePointState.topRunLengths());
    }
}
