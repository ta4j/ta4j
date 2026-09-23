/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.forecast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.KalmanNoiseIndicator;
import org.ta4j.core.indicators.KinematicKalmanFilterIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.forecast.KinematicKalmanForecastStateIndicator;
import org.ta4j.core.indicators.forecast.KinematicKalmanPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.state.KinematicKalmanForecastState;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.numeric.UnaryOperationIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Demonstrates opt-in ATR/relative-volume Kalman noise compositions.
 *
 * <p>
 * ATR squared supplies a price-variance scale. Higher relative volume reduces
 * measurement noise under an explicit, uncalibrated confidence hypothesis.
 * These are adaptive proxies, not estimates of the true noise variances or new
 * Kalman defaults. See the companion adaptive-kalman-noise.md walkthrough.
 *
 * @since 0.25.1
 */
public final class AdaptiveKalmanNoiseExample {

    static final double MINIMUM_VARIANCE = 1e-8;
    static final double PROCESS_SCALE = 0.01;
    static final double MEASUREMENT_SCALE = 1;
    static final double MINIMUM_RELATIVE_VOLUME = 0.25;
    static final double MAXIMUM_RELATIVE_VOLUME = 4;
    static final double VOLUME_EXPONENT = 0.5;

    private static final Logger LOG = LogManager.getLogger(AdaptiveKalmanNoiseExample.class);

    private AdaptiveKalmanNoiseExample() {
    }

    /**
     * Runs an offline, common-sample one-step forecast comparison.
     *
     * @param args optionally {@code --lag-noise} to use prior-bar dynamic noise
     * @since 0.25.1
     */
    public static void main(String[] args) {
        if (args.length > 1 || args.length == 1 && !"--lag-noise".equals(args[0])) {
            throw new IllegalArgumentException("Usage: AdaptiveKalmanNoiseExample [--lag-noise]");
        }
        boolean lagNoise = args.length == 1;
        BarSeries snapshot = KinematicKalmanForecastExample.loadSeries();
        // The bundled snapshot ends with an as-of partial week, not a mature target.
        BarSeries series = snapshot.getSubSeries(snapshot.getBeginIndex(), snapshot.getEndIndex());
        List<Model> models = createModels(series, 14, 20, lagNoise);
        Evaluation evaluation = evaluate(models, new ClosePriceIndicator(series), 520);

        LOG.info("Offline weekly comparison: prior-bar noise={}, common samples={}, skipped origins={}", lagNoise,
                evaluation.sampleCount(), evaluation.skippedCount());
        for (Score score : evaluation.scores()) {
            LOG.info("{}: one-step MAE={}, RMSE={}", score.name(), score.meanAbsoluteError(),
                    score.rootMeanSquaredError());
        }
        for (Model model : models) {
            KinematicKalmanForecastState state = model.state().getValue(series.getEndIndex());
            LOG.info("{}: corrected price={}, velocity={}, Q={}, R={}", model.name(), state.position(),
                    state.velocity(), state.processNoise(), state.measurementNoise());
        }
        LOG.info("Parameters are illustrative and were not fitted. Evaluate other periods before accepting a recipe.");
        LOG.info("Scaling both Q and R by ATR squared does not, by itself, change their ratio.");
    }

    static NoiseInputs createNoise(Indicator<Num> atr, Indicator<Num> volume, int volumeWindow) {
        NumericIndicator variance = NumericIndicator.of(atr).squared().max(MINIMUM_VARIANCE);
        NumericIndicator relativeVolume = NumericIndicator.of(new RelativeVolumeIndicator(volume, volumeWindow))
                .max(MINIMUM_RELATIVE_VOLUME)
                .min(MAXIMUM_RELATIVE_VOLUME);
        Indicator<Num> volumeConfidence = UnaryOperationIndicator.pow(relativeVolume, VOLUME_EXPONENT);
        KalmanNoiseIndicator processNoise = new KalmanNoiseIndicator(variance, PROCESS_SCALE);
        KalmanNoiseIndicator measurementNoise = new KalmanNoiseIndicator(variance.dividedBy(volumeConfidence),
                MEASUREMENT_SCALE);
        return new NoiseInputs(variance, relativeVolume, processNoise, measurementNoise);
    }

    static List<Model> createModels(BarSeries series, int atrWindow, int volumeWindow, boolean lagNoise) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        NoiseInputs noise = createNoise(new ATRIndicator(series, atrWindow), new VolumeIndicator(series), volumeWindow);
        KalmanNoiseIndicator fixedQ = KalmanNoiseIndicator.constant(series, PROCESS_SCALE);
        KalmanNoiseIndicator fixedR = KalmanNoiseIndicator.constant(series, MEASUREMENT_SCALE);
        KalmanNoiseIndicator dynamicQ = atDecisionTime(noise.processNoise(), lagNoise);
        KalmanNoiseIndicator dynamicR = atDecisionTime(noise.measurementNoise(), lagNoise);
        return List.of(model("Fixed Q/R", close, fixedQ, fixedR),
                model("ATR squared Q / fixed R", close, dynamicQ, fixedR),
                model("ATR squared Q / volume-adjusted R", close, dynamicQ, dynamicR));
    }

    private static KalmanNoiseIndicator atDecisionTime(KalmanNoiseIndicator noise, boolean lagNoise) {
        return lagNoise ? new KalmanNoiseIndicator(new PreviousValueIndicator(noise)) : noise;
    }

    private static Model model(String name, ClosePriceIndicator close, KalmanNoiseIndicator processNoise,
            KalmanNoiseIndicator measurementNoise) {
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(close, processNoise,
                measurementNoise);
        return new Model(name, state, new KinematicKalmanFilterIndicator(state).forecast());
    }

    static Evaluation evaluate(List<Model> models, ClosePriceIndicator close, int decisionCount) {
        if (decisionCount < 1) {
            throw new IllegalArgumentException("decisionCount must be positive");
        }
        BarSeries series = close.getBarSeries();
        NumFactory numFactory = series.numFactory();
        int baseline = models.size();
        Num[] absoluteErrors = new Num[baseline + 1];
        Num[] squaredErrors = new Num[baseline + 1];
        Arrays.fill(absoluteErrors, numFactory.zero());
        Arrays.fill(squaredErrors, numFactory.zero());
        int sampleCount = 0;
        int skippedCount = 0;
        int firstOrigin = Math.max(series.getBeginIndex(), series.getEndIndex() - decisionCount);
        for (int origin = firstOrigin; origin < series.getEndIndex(); origin++) {
            Num[] predictions = new Num[baseline + 1];
            boolean available = true;
            for (int modelIndex = 0; modelIndex < models.size(); modelIndex++) {
                Forecast forecast = models.get(modelIndex).forecast().getValue(origin);
                if (!forecast.isStable()) {
                    available = false;
                    continue;
                }
                predictions[modelIndex] = forecast.mean();
                available &= Num.isFinite(predictions[modelIndex]);
            }
            predictions[baseline] = close.getValue(origin);
            Num realized = close.getValue(origin + 1);
            if (!available || !Num.isFinite(realized) || !Num.isFinite(predictions[baseline])) {
                skippedCount++;
                continue;
            }
            // Every model and the last-close benchmark use the same accepted origins.
            for (int modelIndex = 0; modelIndex < predictions.length; modelIndex++) {
                Num error = realized.minus(predictions[modelIndex]);
                absoluteErrors[modelIndex] = absoluteErrors[modelIndex].plus(error.abs());
                squaredErrors[modelIndex] = squaredErrors[modelIndex].plus(error.multipliedBy(error));
            }
            sampleCount++;
        }
        List<Score> scores = new ArrayList<>();
        for (int modelIndex = 0; modelIndex <= baseline; modelIndex++) {
            String name = modelIndex == baseline ? "Last close" : models.get(modelIndex).name();
            Num mae = sampleCount == 0 ? NaN.NaN : absoluteErrors[modelIndex].dividedBy(numFactory.numOf(sampleCount));
            Num rmse = sampleCount == 0 ? NaN.NaN
                    : squaredErrors[modelIndex].dividedBy(numFactory.numOf(sampleCount)).sqrt();
            scores.add(new Score(name, mae, rmse));
        }
        return new Evaluation(sampleCount, skippedCount, List.copyOf(scores));
    }

    // The only custom policy: missing volume is neutral, actual zero volume is not.
    // Keep this example-specific choice out of KalmanNoiseIndicator and core
    // defaults.
    private static final class RelativeVolumeIndicator extends AbstractIndicator<Num> {

        private final Indicator<Num> volume;
        private final SMAIndicator averageVolume;

        private RelativeVolumeIndicator(Indicator<Num> volume, int volumeWindow) {
            super(volume.getBarSeries());
            if (volumeWindow < 1) {
                throw new IllegalArgumentException("volumeWindow must be positive");
            }
            this.volume = volume;
            this.averageVolume = new SMAIndicator(volume, volumeWindow);
        }

        @Override
        public Num getValue(int index) {
            Num current = volume.getValue(index);
            if (Num.isFinite(current) && current.isNegative()) {
                return NaN.NaN;
            }
            // SMA exposes partial windows; deliberately wait for its declared warm-up.
            if (!Num.isFinite(current)
                    || index - getBarSeries().getBeginIndex() < averageVolume.getCountOfUnstableBars()) {
                return getBarSeries().numFactory().one();
            }
            Num average = averageVolume.getValue(index);
            if (!Num.isFinite(average) || !average.isPositive()) {
                return getBarSeries().numFactory().one();
            }
            return current.dividedBy(average);
        }

        @Override
        public List<Indicator<?>> getDependencies() {
            return List.of(volume, averageVolume);
        }

        @Override
        public int getCountOfUnstableBars() {
            // Neutral confidence is defined during volume warm-up, unlike ATR variance.
            return 0;
        }
    }

    record NoiseInputs(Indicator<Num> priceVariance, Indicator<Num> relativeVolume, KalmanNoiseIndicator processNoise,
            KalmanNoiseIndicator measurementNoise) {
    }

    record Model(String name, KinematicKalmanForecastStateIndicator state,
            KinematicKalmanPriceForecastIndicator forecast) {
    }

    record Score(String name, Num meanAbsoluteError, Num rootMeanSquaredError) {
    }

    record Evaluation(int sampleCount, int skippedCount, List<Score> scores) {
    }
}
