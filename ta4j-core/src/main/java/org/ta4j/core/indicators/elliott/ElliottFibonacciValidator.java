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

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Validates Elliott swing amplitudes against common Fibonacci retracement and
 * extension ranges.
 *
 * @since 0.22.0
 */
public class ElliottFibonacciValidator {

    private final Num tolerance;

    private final Num waveTwoMinRetracement;
    private final Num waveTwoMaxRetracement;
    private final Num waveThreeMinExtension;
    private final Num waveThreeMaxExtension;
    private final Num waveFourMinRetracement;
    private final Num waveFourMaxRetracement;
    private final Num waveFiveMinProjection;
    private final Num waveFiveMaxProjection;
    private final Num waveBMinRetracement;
    private final Num waveBMaxRetracement;
    private final Num waveCMinExtension;
    private final Num waveCMaxExtension;

    /**
     * Builds a validator with the default {@code 0.05} tolerance.
     *
     * @param numFactory series factory used for all ratio math
     * @since 0.22.0
     */
    public ElliottFibonacciValidator(final NumFactory numFactory) {
        this(numFactory, numFactory.numOf(0.05));
    }

    /**
     * Builds a validator with a caller supplied tolerance.
     *
     * @param numFactory series factory used for all ratio math
     * @param tolerance  symmetric tolerance applied to each Fibonacci band
     * @since 0.22.0
     */
    public ElliottFibonacciValidator(final NumFactory numFactory, final Num tolerance) {
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
        this.waveTwoMinRetracement = numFactory.numOf(0.382);
        this.waveTwoMaxRetracement = numFactory.numOf(0.786);
        this.waveThreeMinExtension = numFactory.numOf(1.0);
        this.waveThreeMaxExtension = numFactory.numOf(2.618);
        this.waveFourMinRetracement = numFactory.numOf(0.236);
        this.waveFourMaxRetracement = numFactory.numOf(0.786);
        this.waveFiveMinProjection = numFactory.numOf(0.618);
        this.waveFiveMaxProjection = numFactory.numOf(1.618);
        this.waveBMinRetracement = numFactory.numOf(0.382);
        this.waveBMaxRetracement = numFactory.numOf(0.886);
        this.waveCMinExtension = numFactory.numOf(1.0);
        this.waveCMaxExtension = numFactory.numOf(1.618);
    }

    /**
     * @param wave1 first impulse swing
     * @param wave2 second impulse swing retracement
     * @return {@code true} when wave two retraces the first swing within the
     *         canonical Fibonacci band
     * @since 0.22.0
     */
    public boolean isWaveTwoRetracementValid(final ElliottSwing wave1, final ElliottSwing wave2) {
        return ratioBetween(wave2.amplitude(), wave1.amplitude(), waveTwoMinRetracement, waveTwoMaxRetracement);
    }

    /**
     * @param wave1 first impulse swing
     * @param wave3 third impulse swing
     * @return {@code true} when wave three extends the first swing by at least one
     *         full unit and stays below common blow-off extremes
     * @since 0.22.0
     */
    public boolean isWaveThreeExtensionValid(final ElliottSwing wave1, final ElliottSwing wave3) {
        return ratioBetween(wave3.amplitude(), wave1.amplitude(), waveThreeMinExtension, waveThreeMaxExtension);
    }

    /**
     * @param wave3 third impulse swing
     * @param wave4 fourth impulse swing
     * @return {@code true} when wave four retraces the third swing within tolerance
     * @since 0.22.0
     */
    public boolean isWaveFourRetracementValid(final ElliottSwing wave3, final ElliottSwing wave4) {
        return ratioBetween(wave4.amplitude(), wave3.amplitude(), waveFourMinRetracement, waveFourMaxRetracement);
    }

    /**
     * @param wave1 first impulse swing
     * @param wave5 final impulse swing
     * @return {@code true} when wave five projects a typical Fibonacci expansion of
     *         wave one
     * @since 0.22.0
     */
    public boolean isWaveFiveProjectionValid(final ElliottSwing wave1, final ElliottSwing wave5) {
        return ratioBetween(wave5.amplitude(), wave1.amplitude(), waveFiveMinProjection, waveFiveMaxProjection);
    }

    /**
     * @param waveA first corrective swing
     * @param waveB second corrective swing
     * @return {@code true} when wave B retraces wave A within the common range
     * @since 0.22.0
     */
    public boolean isWaveBRetracementValid(final ElliottSwing waveA, final ElliottSwing waveB) {
        return ratioBetween(waveB.amplitude(), waveA.amplitude(), waveBMinRetracement, waveBMaxRetracement);
    }

    /**
     * @param waveA first corrective swing
     * @param waveC third corrective swing
     * @return {@code true} when wave C extends beyond wave A by a common Fibonacci
     *         factor
     * @since 0.22.0
     */
    public boolean isWaveCExtensionValid(final ElliottSwing waveA, final ElliottSwing waveC) {
        return ratioBetween(waveC.amplitude(), waveA.amplitude(), waveCMinExtension, waveCMaxExtension);
    }

    private boolean ratioBetween(final Num numerator, final Num denominator, final Num lower, final Num upper) {
        if (!isFinite(numerator) || !isFinite(denominator)) {
            return false;
        }
        if (denominator.isZero()) {
            return false;
        }
        final Num ratio = numerator.dividedBy(denominator).abs();
        final Num lowerBound = lower.minus(tolerance);
        final Num upperBound = upper.plus(tolerance);
        return !ratio.isLessThan(lowerBound) && !ratio.isGreaterThan(upperBound);
    }

    private boolean isFinite(final Num value) {
        return value != null && !value.isNaN();
    }
}
