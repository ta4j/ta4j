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
package org.ta4j.core.indicators.volume;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperation;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * VWAP-based bands computed as VWAP +/- multiplier * standard deviation.
 * <p>
 * The bands mirror Bollinger Bands semantics for VWAP and anchored VWAP use
 * cases, providing a quick read on value areas, stretch and potential mean
 * reversion.
 *
 * @since 0.19
 */
public class VWAPBandIndicator extends CachedIndicator<Num> {

    /**
     * Band direction to compute relative to VWAP.
     *
     * @since 0.19
     */
    public enum BandType {
        UPPER, LOWER
    }

    private final AbstractVWAPIndicator vwapIndicator;
    private final Indicator<Num> standardDeviationIndicator;
    private final Indicator<Num> scaledDeviation;
    private final Indicator<Num> band;
    private final BandType bandType;

    /**
     * Constructor.
     *
     * @param vwapIndicator              the VWAP indicator
     * @param standardDeviationIndicator the VWAP standard deviation indicator
     * @param multiplier                 number of standard deviations to offset
     * @param bandType                   upper or lower band selection
     *
     * @since 0.19
     */
    public VWAPBandIndicator(AbstractVWAPIndicator vwapIndicator, Indicator<Num> standardDeviationIndicator,
            Number multiplier, BandType bandType) {
        super(IndicatorSeriesUtils.requireSameSeries(vwapIndicator, standardDeviationIndicator));
        this.vwapIndicator = vwapIndicator;
        this.standardDeviationIndicator = standardDeviationIndicator;
        this.bandType = Objects.requireNonNull(bandType, "bandType must not be null");
        Number validatedMultiplier = Objects.requireNonNull(multiplier, "multiplier must not be null");
        this.scaledDeviation = BinaryOperation.product(standardDeviationIndicator, validatedMultiplier);
        this.band = bandType == BandType.UPPER ? BinaryOperation.sum(vwapIndicator, scaledDeviation)
                : BinaryOperation.difference(vwapIndicator, scaledDeviation);
    }

    @Override
    protected Num calculate(int index) {
        Num vwap = vwapIndicator.getValue(index);
        Num deviation = standardDeviationIndicator.getValue(index);
        if (Num.isNaNOrNull(vwap) || Num.isNaNOrNull(deviation)) {
            return NaN.NaN;
        }
        return band.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(vwapIndicator.getCountOfUnstableBars(), standardDeviationIndicator.getCountOfUnstableBars());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " bandType: " + bandType;
    }
}
