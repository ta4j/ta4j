/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.forecast;

import static org.ta4j.core.criteria.ReturnRepresentation.DECIMAL;

import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.ChopIndicator;
import org.ta4j.core.indicators.KalmanNoiseIndicator;
import org.ta4j.core.indicators.KinematicKalmanFilterIndicator;
import org.ta4j.core.indicators.forecast.KinematicKalmanForecastStateIndicator;
import org.ta4j.core.indicators.forecast.KinematicKalmanPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.RollingConformalForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.state.KinematicKalmanForecastState;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Demonstrates regime-aware kinematic Kalman price forecasts over an ossified
 * S&amp;P 500 weekly series.
 *
 * <p>
 * The example keeps the noise inputs in squared-price units. ATR supplies the
 * local price scale for both variances, while inverted CHOP changes how much
 * the filter trusts its constant-velocity path. In directional regimes, larger
 * measurement noise favors continuation; in choppy regimes, smaller measurement
 * noise lets new observations extinguish stale velocity.
 *
 * <p>
 * These scales are illustrative rather than calibrated trading parameters.
 * Useful experiments include replacing inverted CHOP with direct CHOP, a
 * liquidity or volume transformation, or a separately calibrated noise model.
 *
 * @since 0.23.1
 */
public final class KinematicKalmanForecastExample {

    static final String SP500_RESOURCE = "YahooFinance-SP500-PT7D-19500103_20260730.json";
    static final int ATR_BAR_COUNT = 14;
    static final int CHOP_BAR_COUNT = 14;
    static final int WALK_FORWARD_DECISIONS = 520;
    static final double MINIMUM_VARIANCE = 1e-8;
    static final double PROCESS_ATR_VARIANCE_SCALE = 0.0625;
    static final double MEASUREMENT_VARIANCE_FLOOR = 0.25;
    static final double MEASUREMENT_TREND_VARIANCE_SCALE = 1.75;

    private static final Logger LOG = LogManager.getLogger(KinematicKalmanForecastExample.class);
    private static final List<Integer> HORIZONS = List.of(1, 4, 13);

    private KinematicKalmanForecastExample() {
    }

    /**
     * Runs the offline S&amp;P 500 forecast walkthrough.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        BarSeries series = loadSeries();
        ForecastModel model = createModel(series);
        int endIndex = series.getEndIndex();
        KinematicKalmanForecastState state = model.state().getValue(endIndex);

        LOG.info("Ossified ^GSPC weekly series: bars={}, first={}, last={}, resource={}", series.getBarCount(),
                series.getFirstBar().getEndTime(), series.getLastBar().getEndTime(), SP500_RESOURCE);
        LOG.info("Latest state: close={}, correctedPosition={}, weeklyVelocity={}, ATR(14)={}, CHOP(14)={}, Q={}, R={}",
                model.close().getValue(endIndex), state.position(), state.velocity(), model.atr().getValue(endIndex),
                model.chop().getValue(endIndex), model.processNoise().getValue(endIndex),
                model.measurementNoise().getValue(endIndex));
        LOG.info("Noise recipe: Q=max({}, {}*ATR^2); R=max({}, ATR^2*({}+{}*(1-CHOP)^2)). CHOP uses decimal output.",
                MINIMUM_VARIANCE, PROCESS_ATR_VARIANCE_SCALE, MINIMUM_VARIANCE, MEASUREMENT_VARIANCE_FLOOR,
                MEASUREMENT_TREND_VARIANCE_SCALE);

        for (KinematicKalmanPriceForecastIndicator forecastIndicator : model.forecasts()) {
            Forecast forecast = forecastIndicator.getValue(endIndex);
            LOG.info("{}-week forecast: mean={}, q05={}, q95={}, standardDeviation={}, support={}", forecast.horizon(),
                    forecast.mean(), forecast.quantile(0.05), forecast.quantile(0.95), forecast.standardDeviation(),
                    forecast.support());
        }
        Forecast calibrated = model.conformalFourWeek().getValue(endIndex);
        LOG.info("4-week conformal interval: median={}, q05={}, q95={}, targetCoverage=90%", calibrated.median(),
                calibrated.quantile(0.05), calibrated.quantile(0.95));

        LOG.info("Ten-year walk-forward comparison ({} decisions per horizon where available):",
                WALK_FORWARD_DECISIONS);
        for (ForecastEvaluation evaluation : evaluate(model, WALK_FORWARD_DECISIONS)) {
            LOG.info(
                    "{} weeks: samples={}, Kalman MAE={}, last-close MAE={}, analytic coverage={}%, analytic width={}%",
                    evaluation.horizon(), evaluation.sampleCount(), evaluation.kalmanMeanAbsoluteError(),
                    evaluation.lastCloseMeanAbsoluteError(), evaluation.analyticCoverage() * 100,
                    evaluation.analyticNormalizedWidth() * 100);
            if (Double.isFinite(evaluation.conformalCoverage())) {
                LOG.info("{} weeks conformal: coverage={}%, width={}% (90% target)", evaluation.horizon(),
                        evaluation.conformalCoverage() * 100, evaluation.conformalNormalizedWidth() * 100);
            }
        }
        LOG.info(
                "Springboard: try direct CHOP for conventional choppy-market measurement noise, volume-derived liquidity noise, other horizons, or a different conformal window.");
        LOG.info(
                "Interpretation: this untuned wiring example does not assume Kalman beats last-close; use the reported benchmark and coverage to calibrate or reject a proposed noise recipe.");
    }

    static BarSeries loadSeries() {
        return Objects.requireNonNull(JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(SP500_RESOURCE),
                "S&P 500 resource was not available");
    }

    static ForecastModel createModel(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ATRIndicator atr = new ATRIndicator(series, ATR_BAR_COUNT);
        ChopIndicator chop = new ChopIndicator(series, CHOP_BAR_COUNT, DECIMAL);

        NumericIndicator atrVariance = NumericIndicator.of(atr).squared();
        NumericIndicator processVariance = atrVariance.multipliedBy(PROCESS_ATR_VARIANCE_SCALE).max(MINIMUM_VARIANCE);
        NumericIndicator trendStrength = NumericIndicator.of(chop).multipliedBy(-1).plus(1).max(0).min(1);
        NumericIndicator measurementVarianceMultiplier = trendStrength.squared()
                .multipliedBy(MEASUREMENT_TREND_VARIANCE_SCALE)
                .plus(MEASUREMENT_VARIANCE_FLOOR);
        NumericIndicator measurementVariance = atrVariance.multipliedBy(measurementVarianceMultiplier)
                .max(MINIMUM_VARIANCE);

        KalmanNoiseIndicator processNoise = new KalmanNoiseIndicator(processVariance);
        KalmanNoiseIndicator measurementNoise = new KalmanNoiseIndicator(measurementVariance);
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(close, processNoise,
                measurementNoise);
        KinematicKalmanFilterIndicator filter = new KinematicKalmanFilterIndicator(state);
        List<KinematicKalmanPriceForecastIndicator> forecasts = HORIZONS.stream().map(filter::forecast).toList();
        KinematicKalmanPriceForecastIndicator fourWeek = forecasts.get(1);
        ForecastProjectionIndicator conformalFourWeek = RollingConformalForecastProjectionIndicator
                .builder(fourWeek, close)
                .targetCoverage(0.90)
                .calibrationWindow(104)
                .minimumCalibrationCount(52)
                .build();
        return new ForecastModel(close, atr, chop, processNoise, measurementNoise, state, forecasts, conformalFourWeek);
    }

    static List<ForecastEvaluation> evaluate(ForecastModel model, int decisionCount) {
        if (decisionCount < 1) {
            throw new IllegalArgumentException("decisionCount must be > 0");
        }
        return model.forecasts().stream().map(forecast -> evaluate(model, forecast, decisionCount)).toList();
    }

    private static ForecastEvaluation evaluate(ForecastModel model,
            KinematicKalmanPriceForecastIndicator forecastIndicator, int decisionCount) {
        int horizon = forecastIndicator.getHorizon();
        int endIndex = model.close().getBarSeries().getEndIndex();
        int lastDecisionIndex = endIndex - horizon;
        int firstDecisionIndex = Math.max(forecastIndicator.getCountOfUnstableBars(),
                lastDecisionIndex - decisionCount + 1);
        double kalmanAbsoluteError = 0;
        double lastCloseAbsoluteError = 0;
        double analyticHits = 0;
        double analyticNormalizedWidth = 0;
        double conformalHits = 0;
        double conformalNormalizedWidth = 0;
        int sampleCount = 0;
        int conformalSampleCount = 0;

        for (int decisionIndex = firstDecisionIndex; decisionIndex <= lastDecisionIndex; decisionIndex++) {
            Forecast forecast = forecastIndicator.getValue(decisionIndex);
            Num decisionClose = model.close().getValue(decisionIndex);
            Num realized = model.close().getValue(decisionIndex + horizon);
            if (!forecast.isStable() || !Num.isFinite(decisionClose) || !Num.isFinite(realized)) {
                continue;
            }
            double realizedValue = realized.doubleValue();
            double decisionCloseValue = decisionClose.doubleValue();
            kalmanAbsoluteError += Math.abs(realizedValue - forecast.mean().doubleValue());
            lastCloseAbsoluteError += Math.abs(realizedValue - decisionCloseValue);
            analyticHits += contains(forecast, realizedValue) ? 1 : 0;
            analyticNormalizedWidth += normalizedWidth(forecast, decisionCloseValue);
            sampleCount++;

            if (horizon == 4) {
                Forecast conformal = model.conformalFourWeek().getValue(decisionIndex);
                if (conformal.isStable()) {
                    conformalHits += contains(conformal, realizedValue) ? 1 : 0;
                    conformalNormalizedWidth += normalizedWidth(conformal, decisionCloseValue);
                    conformalSampleCount++;
                }
            }
        }

        if (sampleCount == 0) {
            return new ForecastEvaluation(horizon, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN);
        }
        double conformalCoverage = conformalSampleCount == 0 ? Double.NaN : conformalHits / conformalSampleCount;
        double conformalWidth = conformalSampleCount == 0 ? Double.NaN
                : conformalNormalizedWidth / conformalSampleCount;
        return new ForecastEvaluation(horizon, sampleCount, kalmanAbsoluteError / sampleCount,
                lastCloseAbsoluteError / sampleCount, analyticHits / sampleCount, analyticNormalizedWidth / sampleCount,
                conformalCoverage, conformalWidth);
    }

    private static boolean contains(Forecast forecast, double realizedValue) {
        return forecast.quantile(0.05).doubleValue() <= realizedValue
                && realizedValue <= forecast.quantile(0.95).doubleValue();
    }

    private static double normalizedWidth(Forecast forecast, double decisionClose) {
        return (forecast.quantile(0.95).doubleValue() - forecast.quantile(0.05).doubleValue()) / decisionClose;
    }

    record ForecastModel(ClosePriceIndicator close, ATRIndicator atr, ChopIndicator chop,
            KalmanNoiseIndicator processNoise, KalmanNoiseIndicator measurementNoise,
            KinematicKalmanForecastStateIndicator state, List<KinematicKalmanPriceForecastIndicator> forecasts,
            ForecastProjectionIndicator conformalFourWeek) {
    }

    record ForecastEvaluation(int horizon, int sampleCount, double kalmanMeanAbsoluteError,
            double lastCloseMeanAbsoluteError, double analyticCoverage, double analyticNormalizedWidth,
            double conformalCoverage, double conformalNormalizedWidth) {
    }
}
