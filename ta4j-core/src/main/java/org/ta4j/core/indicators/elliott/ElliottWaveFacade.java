/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.elliott;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

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
 * Example usage:
 *
 * <pre>
 * ElliottWaveFacade facade = ElliottWaveFacade.fractal(series, 5, ElliottDegree.INTERMEDIATE);
 * ElliottPhase phase = facade.phase().getValue(index);
 * ElliottRatio ratio = facade.ratio().getValue(index);
 * boolean confluent = facade.confluence().isConfluent(index);
 * </pre>
 *
 * @since 0.22.0
 */
public final class ElliottWaveFacade {

    private final BarSeries series;
    private final ElliottSwingIndicator swingIndicator;
    private final Indicator<Num> priceIndicator;

    private ElliottPhaseIndicator phaseIndicator;
    private ElliottRatioIndicator ratioIndicator;
    private ElliottChannelIndicator channelIndicator;
    private ElliottWaveCountIndicator waveCountIndicator;
    private ElliottConfluenceIndicator confluenceIndicator;
    private ElliottInvalidationIndicator invalidationIndicator;

    private ElliottWaveFacade(final BarSeries series, final ElliottSwingIndicator swingIndicator,
            final Indicator<Num> priceIndicator) {
        this.series = Objects.requireNonNull(series, "series");
        this.swingIndicator = Objects.requireNonNull(swingIndicator, "swingIndicator");
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
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
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        var swingIndicator = new ElliottSwingIndicator(series, window, degree);
        var priceIndicator = new ClosePriceIndicator(series);
        return new ElliottWaveFacade(series, swingIndicator, priceIndicator);
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
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        var swingIndicator = new ElliottSwingIndicator(series, lookbackLength, lookforwardLength, degree);
        var priceIndicator = new ClosePriceIndicator(series);
        return new ElliottWaveFacade(series, swingIndicator, priceIndicator);
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
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        var swingIndicator = ElliottSwingIndicator.zigZag(series, degree);
        var priceIndicator = new ClosePriceIndicator(series);
        return new ElliottWaveFacade(series, swingIndicator, priceIndicator);
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
        Objects.requireNonNull(swingIndicator, "swingIndicator");
        Objects.requireNonNull(priceIndicator, "priceIndicator");
        return new ElliottWaveFacade(swingIndicator.getBarSeries(), swingIndicator, priceIndicator);
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
     * @return the phase indicator (lazily created)
     * @since 0.22.0
     */
    public ElliottPhaseIndicator phase() {
        if (phaseIndicator == null) {
            phaseIndicator = new ElliottPhaseIndicator(swingIndicator);
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
     * @return the wave count indicator (lazily created)
     * @since 0.22.0
     */
    public ElliottWaveCountIndicator waveCount() {
        if (waveCountIndicator == null) {
            waveCountIndicator = new ElliottWaveCountIndicator(swingIndicator);
        }
        return waveCountIndicator;
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
}
