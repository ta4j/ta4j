/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.forecast;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.forecast.AnalogReturnProjectionIndicator;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.RollingConformalForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Runs an analog return forecast and rolling conformal calibration over an
 * ossified BTC-USD daily series.
 *
 * <p>
 * The example keeps modeling and calibration separate: analog neighbors form
 * the empirical base distribution, while the conformal wrapper uses only
 * matured five-day forecast errors to widen its tails.
 *
 * @since 0.23.1
 */
public final class RollingConformalForecastExample {

    private static final Logger LOG = LogManager.getLogger(RollingConformalForecastExample.class);
    private static final String BTC_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231020.json";

    private RollingConformalForecastExample() {
    }

    /**
     * Loads the committed BTC series and reports the latest base and calibrated
     * return forecasts.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        BarSeries series = Objects.requireNonNull(JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(BTC_RESOURCE),
                "BTC resource was not available");
        LogReturnIndicator logReturns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator states = new EwmaReturnForecastStateIndicator(logReturns);
        AnalogReturnProjectionIndicator<ReturnForecastState> analog = AnalogReturnProjectionIndicator.builder(states)
                .horizon(5)
                .lookbackBarCount(90)
                .neighborCount(20)
                .minimumNeighborCount(10)
                .build();
        ReturnForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .cumulativeLogReturnBuilder(analog, logReturns)
                .targetCoverage(0.90)
                .calibrationWindow(60)
                .minimumCalibrationCount(30)
                .build();

        int index = series.getEndIndex();
        Forecast baseForecast = analog.getValue(index);
        Forecast calibratedForecast = calibrated.getValue(index);
        LOG.info("BTC five-day analog forecast index={} support={} median={} q05={} q95={}", index,
                baseForecast.support(), baseForecast.median(), baseForecast.quantile(0.05),
                baseForecast.quantile(0.95));
        LOG.info("BTC five-day conformal forecast index={} support={} median={} q05={} q95={}", index,
                calibratedForecast.support(), calibratedForecast.median(), calibratedForecast.quantile(0.05),
                calibratedForecast.quantile(0.95));
    }
}
