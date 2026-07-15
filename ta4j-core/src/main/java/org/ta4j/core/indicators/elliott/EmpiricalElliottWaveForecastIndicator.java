/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Forecasts the next intraday Elliott impulse phase from causally available,
 * empirically observed analogs.
 *
 * <p>
 * The built-in pipeline uses the deterministic Elliott phase tracker to label
 * prior bars, then requires a same-phase bullish scenario to corroborate each
 * label at the configured confidence. This avoids treating the scenario set's
 * completeness-biased base ranking as the historical phase. The indicator then
 * compares the current bar's ATR-normalized one-, three-, and five-bar returns,
 * range, and relative volume with earlier feature observations and the phase
 * observed one bar after each observation. The nearest qualifying observations
 * form an empirical next-phase distribution and a standard {@link Forecast}
 * summary of wave numbers. Labels at or after the decision index never enter
 * the training set.
 *
 * <p>
 * No forecast is emitted until the historical window contains the configured
 * minimum number of bullish impulse observations. This makes the absence of a
 * previously observed wave structure an explicit non-signal rather than a
 * synthetic low-confidence count. The defaults target one-minute and
 * five-minute series and use ATR-scaled {@link ElliottDegree#SUB_MINUETTE}
 * swings.
 *
 * @since 0.23.1
 */
public final class EmpiricalElliottWaveForecastIndicator
        extends CachedIndicator<EmpiricalElliottWaveForecastIndicator.WaveForecast> {

    private static final int ATR_BAR_COUNT = 14;
    private static final int FEATURE_LOOKBACK = 20;
    private static final int FORECAST_HORIZON = 1;

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final Indicator<ElliottPhase> phaseIndicator;
    private final ATRIndicator atr;
    private final Settings settings;

    /**
     * Builds an intraday forecast using the default settings.
     *
     * @param series one-minute or five-minute bar series
     * @since 0.23.1
     */
    public EmpiricalElliottWaveForecastIndicator(final BarSeries series) {
        this(series, Settings.intradayDefaults());
    }

    /**
     * Builds an intraday forecast using a custom empirical window.
     *
     * @param series   one-minute or five-minute bar series
     * @param settings empirical matching settings
     * @since 0.23.1
     */
    public EmpiricalElliottWaveForecastIndicator(final BarSeries series, final Settings settings) {
        this(series, builtInSources(series, Objects.requireNonNull(settings, "settings").degree()), settings);
    }

    private EmpiricalElliottWaveForecastIndicator(final BarSeries series, final BuiltInSources sources,
            final Settings settings) {
        this(series, sources.scenarioIndicator(), sources.phaseIndicator(), settings);
    }

    /**
     * Builds a forecast from a supplied scenario source. This overload is useful
     * when an application already owns the scenario pipeline or needs a controlled
     * historical label source. Supplied scenario sources use their base scenario's
     * phase as the label because they do not expose a corresponding deterministic
     * phase tracker.
     *
     * @param series            backing bar series
     * @param scenarioIndicator causal historical scenario source
     * @param settings          empirical matching settings
     * @since 0.23.1
     */
    public EmpiricalElliottWaveForecastIndicator(final BarSeries series,
            final Indicator<ElliottScenarioSet> scenarioIndicator, final Settings settings) {
        this(series, scenarioIndicator, null, settings);
    }

    private EmpiricalElliottWaveForecastIndicator(final BarSeries series,
            final Indicator<ElliottScenarioSet> scenarioIndicator, final Indicator<ElliottPhase> phaseIndicator,
            final Settings settings) {
        super(Objects.requireNonNull(series, "series"));
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        if (!IndicatorUtils.isSameSeries(series, scenarioIndicator.getBarSeries())) {
            throw new IllegalArgumentException("scenarioIndicator must use the same BarSeries instance");
        }
        if (phaseIndicator != null && !IndicatorUtils.isSameSeries(series, phaseIndicator.getBarSeries())) {
            throw new IllegalArgumentException("phaseIndicator must use the same BarSeries instance");
        }
        this.phaseIndicator = phaseIndicator;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.atr = new ATRIndicator(series, ATR_BAR_COUNT);
    }

    private static BuiltInSources builtInSources(final BarSeries series, final ElliottDegree degree) {
        ElliottSwingIndicator swings = ElliottSwingIndicator.zigZag(Objects.requireNonNull(series, "series"), degree);
        return new BuiltInSources(new ElliottScenarioIndicator(swings), new ElliottPhaseIndicator(swings));
    }

    @Override
    protected WaveForecast calculate(final int index) {
        if (index < getCountOfUnstableBars()) {
            return WaveForecast.unstable(index);
        }
        FeatureVector current = features(index);
        if (current == null) {
            return WaveForecast.unstable(index);
        }

        int firstCandidate = Math.max(getBarSeries().getBeginIndex() + FEATURE_LOOKBACK,
                index - settings.trainingLookbackBars());
        List<Analog> analogs = new ArrayList<>();
        for (int candidateIndex = firstCandidate; candidateIndex < index - FORECAST_HORIZON; candidateIndex++) {
            int labelIndex = candidateIndex + FORECAST_HORIZON;
            ElliottScenarioSet scenarioSet = scenarioIndicator.getValue(labelIndex);
            ElliottPhase phase = phaseIndicator == null
                    ? scenarioSet.base().map(ElliottScenario::currentPhase).orElse(ElliottPhase.NONE)
                    : phaseIndicator.getValue(labelIndex);
            ElliottScenario scenario = phaseIndicator == null ? scenarioSet.base().orElse(null)
                    : scenarioSet.byPhase(phase).base().orElse(null);
            if (!qualifies(scenario)) {
                continue;
            }
            if (!phase.isImpulse()) {
                continue;
            }
            FeatureVector candidate = features(candidateIndex);
            if (candidate == null) {
                continue;
            }
            double distance = current.distance(candidate);
            if (distance <= settings.maximumAnalogDistance()) {
                analogs.add(new Analog(distance, phase));
            }
        }
        analogs.sort(Comparator.comparingDouble(Analog::distance));
        if (analogs.size() > settings.neighborCount()) {
            analogs = new ArrayList<>(analogs.subList(0, settings.neighborCount()));
        }
        if (analogs.size() < settings.minimumSamples()) {
            return WaveForecast.unstable(index);
        }
        return summarize(index, analogs);
    }

    private boolean qualifies(final ElliottScenario scenario) {
        return scenario != null && scenario.type().isImpulse() && scenario.hasKnownDirection() && scenario.isBullish()
                && scenario.currentPhase().isImpulse() && scenario.confidenceScore()
                        .isGreaterThanOrEqual(getBarSeries().numFactory().numOf(settings.minimumScenarioConfidence()));
    }

    private WaveForecast summarize(final int index, final List<Analog> analogs) {
        NumFactory numFactory = getBarSeries().numFactory();
        EnumMap<ElliottPhase, Integer> counts = new EnumMap<>(ElliottPhase.class);
        List<Num> waveNumbers = new ArrayList<>(analogs.size());
        for (Analog analog : analogs) {
            counts.merge(analog.phase(), 1, Integer::sum);
            waveNumbers.add(numFactory.numOf(analog.phase().impulseIndex()));
        }

        EnumMap<ElliottPhase, Num> probabilities = new EnumMap<>(ElliottPhase.class);
        ElliottPhase mostLikely = ElliottPhase.NONE;
        int highestCount = 0;
        for (ElliottPhase phase : List.of(ElliottPhase.WAVE1, ElliottPhase.WAVE2, ElliottPhase.WAVE3,
                ElliottPhase.WAVE4, ElliottPhase.WAVE5)) {
            int count = counts.getOrDefault(phase, 0);
            probabilities.put(phase, numFactory.numOf(count).dividedBy(numFactory.numOf(analogs.size())));
            if (count > highestCount) {
                mostLikely = phase;
                highestCount = count;
            }
        }
        Forecast waveNumberForecast = Forecast.ofSamples(index, FORECAST_HORIZON, waveNumbers);
        return new WaveForecast(waveNumberForecast, probabilities, mostLikely,
                probabilities.getOrDefault(mostLikely, numFactory.zero()));
    }

    private FeatureVector features(final int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (index - 5 < beginIndex || index < atr.getCountOfUnstableBars()) {
            return null;
        }
        Num atrValue = atr.getValue(index);
        if (Num.isNaNOrNull(atrValue) || atrValue.isZero()) {
            return null;
        }
        Bar bar = getBarSeries().getBar(index);
        double atrAmount = atrValue.doubleValue();
        double range = bar.getHighPrice().minus(bar.getLowPrice()).doubleValue() / atrAmount;
        double volumeTotal = 0.0d;
        int volumeCount = 0;
        for (int i = Math.max(beginIndex, index - FEATURE_LOOKBACK); i < index; i++) {
            volumeTotal += getBarSeries().getBar(i).getVolume().doubleValue();
            volumeCount++;
        }
        double averageVolume = volumeCount == 0 ? 0.0d : volumeTotal / volumeCount;
        double volumeRatio = averageVolume > 0.0d ? bar.getVolume().doubleValue() / averageVolume : 1.0d;
        return new FeatureVector(returnInAtr(index, 1, atrAmount), returnInAtr(index, 3, atrAmount),
                returnInAtr(index, 5, atrAmount), range, volumeRatio);
    }

    private double returnInAtr(final int index, final int bars, final double atrAmount) {
        Num current = getBarSeries().getBar(index).getClosePrice();
        Num previous = getBarSeries().getBar(index - bars).getClosePrice();
        return current.minus(previous).doubleValue() / atrAmount;
    }

    @Override
    public int getCountOfUnstableBars() {
        int structuralUnstableBars = scenarioIndicator.getCountOfUnstableBars();
        if (phaseIndicator != null) {
            structuralUnstableBars = Math.max(structuralUnstableBars, phaseIndicator.getCountOfUnstableBars());
        }
        return Math.max(atr.getCountOfUnstableBars(), structuralUnstableBars) + FEATURE_LOOKBACK
                + settings.minimumSamples();
    }

    /**
     * Configuration for causal empirical phase matching.
     *
     * @param degree                    Elliott degree used to label historical bars
     * @param trainingLookbackBars      maximum historical training window
     * @param neighborCount             maximum number of nearest analogs
     * @param minimumSamples            minimum analogs required for a forecast
     * @param maximumAnalogDistance     maximum Euclidean feature distance
     * @param minimumScenarioConfidence minimum historical scenario confidence
     * @since 0.23.1
     */
    public record Settings(ElliottDegree degree, int trainingLookbackBars, int neighborCount, int minimumSamples,
            double maximumAnalogDistance, double minimumScenarioConfidence) {

        public Settings {
            Objects.requireNonNull(degree, "degree");
            if (trainingLookbackBars <= 0) {
                throw new IllegalArgumentException("trainingLookbackBars must be > 0");
            }
            if (neighborCount <= 0) {
                throw new IllegalArgumentException("neighborCount must be > 0");
            }
            if (minimumSamples <= 0 || minimumSamples > neighborCount) {
                throw new IllegalArgumentException("minimumSamples must be in [1, neighborCount]");
            }
            if (trainingLookbackBars < minimumSamples) {
                throw new IllegalArgumentException("trainingLookbackBars must be >= minimumSamples");
            }
            if (!Double.isFinite(maximumAnalogDistance) || maximumAnalogDistance <= 0.0d) {
                throw new IllegalArgumentException("maximumAnalogDistance must be finite and > 0");
            }
            if (!Double.isFinite(minimumScenarioConfidence) || minimumScenarioConfidence < 0.0d
                    || minimumScenarioConfidence > 1.0d) {
                throw new IllegalArgumentException("minimumScenarioConfidence must be in [0, 1]");
            }
        }

        /**
         * Returns defaults suitable for rolling one-minute or five-minute crypto bars.
         *
         * @return intraday empirical settings
         * @since 0.23.1
         */
        public static Settings intradayDefaults() {
            return new Settings(ElliottDegree.SUB_MINUETTE, 2_880, 48, 12, 5.0d, 0.20d);
        }
    }

    /**
     * Empirical Elliott phase forecast for one decision bar.
     *
     * <p>
     * Stable instances require a normalized impulse-phase distribution whose modal
     * entry matches {@code mostLikelyPhase} and {@code probability}. The weighted
     * wave number must also match {@link Forecast#mean()}. Unstable instances carry
     * no phase distribution.
     *
     * @param waveNumberForecast numeric summary of sampled impulse wave numbers
     * @param phaseProbabilities empirical probability for each impulse phase
     * @param mostLikelyPhase    modal impulse phase
     * @param probability        probability of the modal phase
     * @since 0.23.1
     */
    public record WaveForecast(Forecast waveNumberForecast, Map<ElliottPhase, Num> phaseProbabilities,
            ElliottPhase mostLikelyPhase, Num probability) {

        public WaveForecast {
            Objects.requireNonNull(waveNumberForecast, "waveNumberForecast");
            phaseProbabilities = Map.copyOf(Objects.requireNonNull(phaseProbabilities, "phaseProbabilities"));
            Objects.requireNonNull(mostLikelyPhase, "mostLikelyPhase");
            Objects.requireNonNull(probability, "probability");
            if (!waveNumberForecast.isStable()) {
                if (!phaseProbabilities.isEmpty() || mostLikelyPhase != ElliottPhase.NONE
                        || !Num.isNaNOrNull(probability)) {
                    throw new IllegalArgumentException(
                            "an unstable wave forecast must have no phase probabilities or most likely phase");
                }
            } else {
                if (!mostLikelyPhase.isImpulse()) {
                    throw new IllegalArgumentException(
                            "mostLikelyPhase must be an impulse phase for a stable forecast");
                }
                Num modalProbability = phaseProbabilities.get(mostLikelyPhase);
                if (!Num.isFinite(modalProbability)) {
                    throw new IllegalArgumentException("mostLikelyPhase must have a finite probability");
                }
                NumFactory numFactory = waveNumberForecast.mean().getNumFactory();
                Num probabilityMass = numFactory.zero();
                Num expectedWaveNumber = numFactory.zero();
                for (Map.Entry<ElliottPhase, Num> entry : phaseProbabilities.entrySet()) {
                    if (!entry.getKey().isImpulse()) {
                        throw new IllegalArgumentException("phaseProbabilities must contain impulse phases only");
                    }
                    Num candidateProbability = entry.getValue();
                    if (!Num.isFinite(candidateProbability) || candidateProbability.isNegative()
                            || candidateProbability.isGreaterThan(candidateProbability.getNumFactory().one())) {
                        throw new IllegalArgumentException("phase probabilities must be finite and in [0, 1]");
                    }
                    if (!numFactory.produces(candidateProbability)) {
                        throw new IllegalArgumentException("phase probabilities must use the forecast NumFactory");
                    }
                    probabilityMass = probabilityMass.plus(candidateProbability);
                    expectedWaveNumber = expectedWaveNumber
                            .plus(candidateProbability.multipliedBy(numFactory.numOf(entry.getKey().impulseIndex())));
                    if (candidateProbability.isGreaterThan(modalProbability)) {
                        throw new IllegalArgumentException("mostLikelyPhase must identify a modal phase");
                    }
                }
                if (!Num.isFinite(probability) || probability.compareTo(modalProbability) != 0) {
                    throw new IllegalArgumentException("probability must match the mostLikelyPhase probability");
                }
                Num tolerance = numFactory.numOf(1.0e-9d);
                if (probabilityMass.minus(numFactory.one()).abs().isGreaterThan(tolerance)) {
                    throw new IllegalArgumentException("phase probabilities must sum to 1");
                }
                if (expectedWaveNumber.minus(waveNumberForecast.mean()).abs().isGreaterThan(tolerance)) {
                    throw new IllegalArgumentException("phase probabilities must match the numeric forecast mean");
                }
            }
        }

        /**
         * Returns an unstable forecast when no qualifying empirical structure exists.
         *
         * @param index decision index
         * @return unstable forecast
         * @since 0.23.1
         */
        public static WaveForecast unstable(final int index) {
            return new WaveForecast(Forecast.unstable(index, FORECAST_HORIZON), Map.of(), ElliottPhase.NONE,
                    org.ta4j.core.num.NaN.NaN);
        }

        /**
         * Returns the empirical probability for a phase.
         *
         * @param phase impulse phase
         * @return probability, or the forecast numeric zero when absent and stable
         * @since 0.23.1
         */
        public Num probability(final ElliottPhase phase) {
            Objects.requireNonNull(phase, "phase");
            Num value = phaseProbabilities.get(phase);
            if (value != null || !waveNumberForecast.isStable()) {
                return value == null ? org.ta4j.core.num.NaN.NaN : value;
            }
            return waveNumberForecast.mean().getNumFactory().zero();
        }

        /**
         * @return whether enough qualifying historical structures support the forecast
         * @since 0.23.1
         */
        public boolean isStable() {
            return waveNumberForecast.isStable();
        }
    }

    private record Analog(double distance, ElliottPhase phase) {
    }

    private record BuiltInSources(Indicator<ElliottScenarioSet> scenarioIndicator,
            Indicator<ElliottPhase> phaseIndicator) {
    }

    private record FeatureVector(double oneBarReturn, double threeBarReturn, double fiveBarReturn, double range,
            double volumeRatio) {

        double distance(final FeatureVector other) {
            double one = oneBarReturn - other.oneBarReturn;
            double three = threeBarReturn - other.threeBarReturn;
            double five = fiveBarReturn - other.fiveBarReturn;
            double barRange = range - other.range;
            double volume = volumeRatio - other.volumeRatio;
            return Math.sqrt(one * one + three * three + five * five + barRange * barRange + volume * volume);
        }
    }
}
