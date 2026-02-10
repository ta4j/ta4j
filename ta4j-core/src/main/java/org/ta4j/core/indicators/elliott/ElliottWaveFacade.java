/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Facade class that creates and coordinates a complete set of Elliott Wave
 * indicators from a single configuration.
 *
 * <p>
 * This class simplifies the creation of Elliott Wave analysis indicators by
 * providing factory methods that set up all the necessary components with
 * consistent configuration. All indicators share the same underlying swing
 * detector, ensuring they analyze the same wave structure.
 *
 * <p>
 * <b>Entry point</b>: Start here when you want indicator-style, per-bar access
 * to Elliott Wave outputs (phase, ratios, scenario sets, invalidation levels,
 * and projections). If you need a one-shot analysis pipeline with pluggable
 * swing detectors and confidence profiles, prefer {@link ElliottWaveAnalysis}.
 *
 * <p>
 * Basic usage:
 *
 * <pre>
 * ElliottWaveFacade facade = ElliottWaveFacade.fractal(series, 5, ElliottDegree.INTERMEDIATE);
 * ElliottPhase phase = facade.phase().getValue(index);
 * ElliottRatio ratio = facade.ratio().getValue(index);
 * boolean confluent = facade.confluence().isConfluent(index);
 * </pre>
 *
 * <p>
 * Advanced usage with custom configuration:
 *
 * <pre>
 * // Custom Fibonacci tolerance and swing compressor
 * Num customTolerance = series.numFactory().numOf(0.25);
 * // Convenience constructor: 1% of current price, 2 bars minimum
 * ElliottSwingCompressor compressor = new ElliottSwingCompressor(series);
 * // Or use main constructor with explicit values:
 * // ElliottSwingCompressor compressor = new ElliottSwingCompressor(
 * //         closePrice.getValue(endIndex).multipliedBy(series.numFactory().numOf(0.01)), 2);
 *
 * ElliottWaveFacade facade = ElliottWaveFacade.zigZag(series, degree, Optional.of(customTolerance),
 *         Optional.of(compressor));
 *
 * // Phase indicator uses custom validator with custom tolerance
 * ElliottPhase phase = facade.phase().getValue(index);
 *
 * // Filtered wave count uses compressor
 * int filteredCount = facade.filteredWaveCount().getValue(index);
 *
 * // Basic wave count (no compression)
 * int basicCount = facade.waveCount().getValue(index);
 * </pre>
 *
 * @since 0.22.0
 */
public final class ElliottWaveFacade {

    private final BarSeries series;
    private final ElliottSwingIndicator swingIndicator;
    private final Indicator<Num> priceIndicator;
    private final Optional<Num> fibTolerance;
    private final Optional<ElliottSwingCompressor> compressor;

    private ElliottPhaseIndicator phaseIndicator;
    private ElliottRatioIndicator ratioIndicator;
    private ElliottChannelIndicator channelIndicator;
    private ElliottWaveCountIndicator waveCountIndicator;
    private ElliottWaveCountIndicator filteredWaveCountIndicator;
    private ElliottConfluenceIndicator confluenceIndicator;
    private ElliottInvalidationIndicator invalidationIndicator;
    private ElliottScenarioIndicator scenarioIndicator;
    private ElliottProjectionIndicator projectionIndicator;
    private ElliottInvalidationLevelIndicator invalidationLevelIndicator;
    private ElliottTrendBiasIndicator trendBiasIndicator;

    private ElliottWaveFacade(final BarSeries series, final ElliottSwingIndicator swingIndicator,
            final Indicator<Num> priceIndicator, final Optional<Num> fibTolerance,
            final Optional<ElliottSwingCompressor> compressor) {
        this.series = Objects.requireNonNull(series, "series cannot be null");
        this.swingIndicator = Objects.requireNonNull(swingIndicator, "swingIndicator cannot be null");
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator cannot be null");
        this.fibTolerance = Objects.requireNonNull(fibTolerance, "fibTolerance cannot be null");
        this.compressor = Objects.requireNonNull(compressor, "compressor cannot be null");
    }

    /**
     * Creates an Elliott Wave facade using fractal-based swing detection.
     *
     * @param series source bar series
     * @param window number of bars to inspect before and after a pivot
     * @param degree swing degree metadata
     * @return configured Elliott Wave facade
     * @since 0.22.0
     */
    public static ElliottWaveFacade fractal(final BarSeries series, final int window, final ElliottDegree degree) {
        return fractal(series, window, degree, Optional.empty(), Optional.empty());
    }

    /**
     * Creates an Elliott Wave facade using fractal-based swing detection with
     * optional custom configuration.
     *
     * @param series       source bar series
     * @param window       number of bars to inspect before and after a pivot
     * @param degree       swing degree metadata
     * @param fibTolerance optional custom Fibonacci tolerance for phase validation
     *                     (default: 0.05). When provided, the phase indicator will
     *                     use a custom {@link ElliottFibonacciValidator} with this
     *                     tolerance instead of the default validator.
     * @param compressor   optional swing compressor for filtered wave counting.
     *                     When provided, {@link #filteredWaveCount()} will use this
     *                     compressor to filter swings before counting. If empty,
     *                     {@link #filteredWaveCount()} returns the same as
     *                     {@link #waveCount()}.
     * @return configured Elliott Wave facade
     * @since 0.22.0
     */
    public static ElliottWaveFacade fractal(final BarSeries series, final int window, final ElliottDegree degree,
            final Optional<Num> fibTolerance, final Optional<ElliottSwingCompressor> compressor) {
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(degree, "degree cannot be null");
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, window, degree);
        ClosePriceIndicator priceIndicator = new ClosePriceIndicator(series);
        return new ElliottWaveFacade(series, swingIndicator, priceIndicator, fibTolerance, compressor);
    }

    /**
     * Creates an Elliott Wave facade using fractal-based swing detection with
     * asymmetric lookback/lookforward.
     *
     * @param series            source bar series
     * @param lookbackLength    bars inspected before a pivot candidate
     * @param lookforwardLength bars inspected after a pivot candidate
     * @param degree            swing degree metadata
     * @return configured Elliott Wave facade
     * @since 0.22.0
     */
    public static ElliottWaveFacade fractal(final BarSeries series, final int lookbackLength,
            final int lookforwardLength, final ElliottDegree degree) {
        return fractal(series, lookbackLength, lookforwardLength, degree, Optional.empty(), Optional.empty());
    }

    /**
     * Creates an Elliott Wave facade using fractal-based swing detection with
     * asymmetric lookback/lookforward and optional custom configuration.
     *
     * @param series            source bar series
     * @param lookbackLength    bars inspected before a pivot candidate
     * @param lookforwardLength bars inspected after a pivot candidate
     * @param degree            swing degree metadata
     * @param fibTolerance      optional custom Fibonacci tolerance for phase
     *                          validation (default: 0.05). When provided, the phase
     *                          indicator will use a custom
     *                          {@link ElliottFibonacciValidator} with this
     *                          tolerance instead of the default validator.
     * @param compressor        optional swing compressor for filtered wave
     *                          counting. When provided,
     *                          {@link #filteredWaveCount()} will use this
     *                          compressor to filter swings before counting. If
     *                          empty, {@link #filteredWaveCount()} returns the same
     *                          as {@link #waveCount()}.
     * @return configured Elliott Wave facade
     * @since 0.22.0
     */
    public static ElliottWaveFacade fractal(final BarSeries series, final int lookbackLength,
            final int lookforwardLength, final ElliottDegree degree, final Optional<Num> fibTolerance,
            final Optional<ElliottSwingCompressor> compressor) {
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(degree, "degree cannot be null");
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, lookbackLength, lookforwardLength,
                degree);
        ClosePriceIndicator priceIndicator = new ClosePriceIndicator(series);
        return new ElliottWaveFacade(series, swingIndicator, priceIndicator, fibTolerance, compressor);
    }

    /**
     * Creates an Elliott Wave facade using ZigZag-based swing detection with
     * ATR(14) reversal threshold.
     *
     * @param series source bar series
     * @param degree swing degree metadata
     * @return configured Elliott Wave facade
     * @since 0.22.0
     */
    public static ElliottWaveFacade zigZag(final BarSeries series, final ElliottDegree degree) {
        return zigZag(series, degree, Optional.empty(), Optional.empty());
    }

    /**
     * Creates an Elliott Wave facade using ZigZag-based swing detection with
     * ATR(14) reversal threshold and optional custom configuration.
     *
     * @param series       source bar series
     * @param degree       swing degree metadata
     * @param fibTolerance optional custom Fibonacci tolerance for phase validation
     *                     (default: 0.05). When provided, the phase indicator will
     *                     use a custom {@link ElliottFibonacciValidator} with this
     *                     tolerance instead of the default validator.
     * @param compressor   optional swing compressor for filtered wave counting.
     *                     When provided, {@link #filteredWaveCount()} will use this
     *                     compressor to filter swings before counting. If empty,
     *                     {@link #filteredWaveCount()} returns the same as
     *                     {@link #waveCount()}.
     * @return configured Elliott Wave facade
     * @since 0.22.0
     */
    public static ElliottWaveFacade zigZag(final BarSeries series, final ElliottDegree degree,
            final Optional<Num> fibTolerance, final Optional<ElliottSwingCompressor> compressor) {
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(degree, "degree cannot be null");
        ElliottSwingIndicator swingIndicator = ElliottSwingIndicator.zigZag(series, degree);
        ClosePriceIndicator priceIndicator = new ClosePriceIndicator(series);
        return new ElliottWaveFacade(series, swingIndicator, priceIndicator, fibTolerance, compressor);
    }

    /**
     * Creates an Elliott Wave facade from a custom swing indicator.
     *
     * @param swingIndicator custom swing indicator
     * @param priceIndicator price reference for confluence analysis
     * @return configured Elliott Wave facade
     * @since 0.22.0
     */
    public static ElliottWaveFacade from(final ElliottSwingIndicator swingIndicator,
            final Indicator<Num> priceIndicator) {
        return from(swingIndicator, priceIndicator, Optional.empty(), Optional.empty());
    }

    /**
     * Creates an Elliott Wave facade from a custom swing indicator with optional
     * custom configuration.
     *
     * @param swingIndicator custom swing indicator
     * @param priceIndicator price reference for confluence analysis
     * @param fibTolerance   optional custom Fibonacci tolerance for phase
     *                       validation (default: 0.05). When provided, the phase
     *                       indicator will use a custom
     *                       {@link ElliottFibonacciValidator} with this tolerance
     *                       instead of the default validator.
     * @param compressor     optional swing compressor for filtered wave counting.
     *                       When provided, {@link #filteredWaveCount()} will use
     *                       this compressor to filter swings before counting. If
     *                       empty, {@link #filteredWaveCount()} returns the same as
     *                       {@link #waveCount()}.
     * @return configured Elliott Wave facade
     * @since 0.22.0
     */
    public static ElliottWaveFacade from(final ElliottSwingIndicator swingIndicator,
            final Indicator<Num> priceIndicator, final Optional<Num> fibTolerance,
            final Optional<ElliottSwingCompressor> compressor) {
        Objects.requireNonNull(swingIndicator, "swingIndicator cannot be null");
        Objects.requireNonNull(priceIndicator, "priceIndicator cannot be null");
        return new ElliottWaveFacade(swingIndicator.getBarSeries(), swingIndicator, priceIndicator, fibTolerance,
                compressor);
    }

    /**
     * @return the underlying bar series
     * @since 0.22.0
     */
    public BarSeries series() {
        return series;
    }

    /**
     * @return the swing indicator used by all facade components
     * @since 0.22.0
     */
    public ElliottSwingIndicator swing() {
        return swingIndicator;
    }

    /**
     * Returns the phase indicator, lazily created on first access.
     * <p>
     * If a custom Fibonacci tolerance was provided during facade construction, the
     * phase indicator will use a custom {@link ElliottFibonacciValidator} with that
     * tolerance. Otherwise, it uses the default validator with a tolerance of 0.05.
     *
     * @return the phase indicator (lazily created)
     * @since 0.22.0
     */
    public ElliottPhaseIndicator phase() {
        if (phaseIndicator == null) {
            if (fibTolerance.isPresent()) {
                final NumFactory numFactory = series.numFactory();
                final ElliottFibonacciValidator validator = new ElliottFibonacciValidator(numFactory,
                        fibTolerance.get());
                phaseIndicator = new ElliottPhaseIndicator(swingIndicator, validator);
            } else {
                phaseIndicator = new ElliottPhaseIndicator(swingIndicator);
            }
        }
        return phaseIndicator;
    }

    /**
     * @return the ratio indicator (lazily created)
     * @since 0.22.0
     */
    public ElliottRatioIndicator ratio() {
        if (ratioIndicator == null) {
            ratioIndicator = new ElliottRatioIndicator(swingIndicator);
        }
        return ratioIndicator;
    }

    /**
     * @return the channel indicator (lazily created)
     * @since 0.22.0
     */
    public ElliottChannelIndicator channel() {
        if (channelIndicator == null) {
            channelIndicator = new ElliottChannelIndicator(swingIndicator);
        }
        return channelIndicator;
    }

    /**
     * @return the wave count indicator (lazily created, without compression)
     * @since 0.22.0
     */
    public ElliottWaveCountIndicator waveCount() {
        if (waveCountIndicator == null) {
            waveCountIndicator = new ElliottWaveCountIndicator(swingIndicator);
        }
        return waveCountIndicator;
    }

    /**
     * Returns the filtered wave count indicator, lazily created on first access.
     * <p>
     * If a swing compressor was provided during facade construction, this method
     * returns a wave count indicator that filters swings using the compressor
     * before counting. The compressor removes swings that don't meet minimum
     * amplitude and/or length thresholds.
     * <p>
     * If no compressor was provided, this method returns the same indicator as
     * {@link #waveCount()}, which counts all swings without filtering.
     *
     * @return the filtered wave count indicator (lazily created, with compression
     *         if compressor is configured)
     * @see ElliottSwingCompressor
     * @since 0.22.0
     */
    public ElliottWaveCountIndicator filteredWaveCount() {
        if (filteredWaveCountIndicator == null) {
            if (compressor.isPresent()) {
                filteredWaveCountIndicator = new ElliottWaveCountIndicator(swingIndicator, compressor.get());
            } else {
                // If no compressor configured, return basic wave count
                filteredWaveCountIndicator = waveCount();
            }
        }
        return filteredWaveCountIndicator;
    }

    /**
     * @return the confluence indicator (lazily created)
     * @since 0.22.0
     */
    public ElliottConfluenceIndicator confluence() {
        if (confluenceIndicator == null) {
            confluenceIndicator = new ElliottConfluenceIndicator(priceIndicator, ratio(), channel());
        }
        return confluenceIndicator;
    }

    /**
     * @return the invalidation indicator (lazily created)
     * @since 0.22.0
     */
    public ElliottInvalidationIndicator invalidation() {
        if (invalidationIndicator == null) {
            invalidationIndicator = new ElliottInvalidationIndicator(phase());
        }
        return invalidationIndicator;
    }

    /**
     * @return the scenario indicator providing alternative wave interpretations
     *         (lazily created)
     * @since 0.22.0
     */
    public ElliottScenarioIndicator scenarios() {
        if (scenarioIndicator == null) {
            scenarioIndicator = new ElliottScenarioIndicator(swingIndicator, channel());
        }
        return scenarioIndicator;
    }

    /**
     * @return the projection indicator for Fibonacci targets (lazily created)
     * @since 0.22.0
     */
    public ElliottProjectionIndicator projection() {
        if (projectionIndicator == null) {
            projectionIndicator = new ElliottProjectionIndicator(scenarios());
        }
        return projectionIndicator;
    }

    /**
     * @return the invalidation level indicator (lazily created)
     * @since 0.22.0
     */
    public ElliottInvalidationLevelIndicator invalidationLevel() {
        if (invalidationLevelIndicator == null) {
            invalidationLevelIndicator = new ElliottInvalidationLevelIndicator(scenarios());
        }
        return invalidationLevelIndicator;
    }

    /**
     * @return the trend bias indicator derived from scenario direction
     * @since 0.22.2
     */
    public ElliottTrendBiasIndicator trendBias() {
        if (trendBiasIndicator == null) {
            trendBiasIndicator = new ElliottTrendBiasIndicator(scenarios());
        }
        return trendBiasIndicator;
    }

    /**
     * Gets the base case (highest confidence) scenario at the specified index.
     *
     * @param index bar index
     * @return base case scenario, or empty if no scenarios exist
     * @since 0.22.0
     */
    public Optional<ElliottScenario> primaryScenario(final int index) {
        return scenarios().primaryScenario(index);
    }

    /**
     * Gets alternative scenarios (excluding base case) at the specified index.
     *
     * @param index bar index
     * @return list of alternative scenarios sorted by confidence
     * @since 0.22.0
     */
    public List<ElliottScenario> alternativeScenarios(final int index) {
        return scenarios().alternatives(index);
    }

    /**
     * Gets the confidence score for a specific phase at the specified index.
     *
     * @param index bar index
     * @param phase the phase to check confidence for
     * @return confidence score (0.0 - 1.0), or 0.0 if no matching scenario
     * @since 0.22.0
     */
    public Num confidenceForPhase(final int index, final ElliottPhase phase) {
        final ElliottScenarioSet scenarioSet = scenarios().getValue(index);
        return scenarioSet.byPhase(phase)
                .base()
                .map(ElliottScenario::confidenceScore)
                .orElse(series.numFactory().zero());
    }

    /**
     * Checks whether all high-confidence scenarios agree on the current phase.
     *
     * @param index bar index
     * @return {@code true} if scenarios show strong consensus
     * @since 0.22.0
     */
    public boolean hasScenarioConsensus(final int index) {
        return scenarios().hasStrongConsensus(index);
    }

    /**
     * Gets the consensus phase across high-confidence scenarios.
     *
     * @param index bar index
     * @return consensus phase, or NONE if no agreement
     * @since 0.22.0
     */
    public ElliottPhase scenarioConsensus(final int index) {
        return scenarios().consensus(index);
    }

    /**
     * Gets a summary description of scenarios at the specified index.
     *
     * @param index bar index
     * @return human-readable scenario summary
     * @since 0.22.0
     */
    public String scenarioSummary(final int index) {
        return scenarios().getValue(index).summary();
    }
}
