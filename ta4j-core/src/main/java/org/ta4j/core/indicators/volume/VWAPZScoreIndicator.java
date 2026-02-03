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

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperation;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Z-score of price relative to VWAP using volume-weighted standard deviation.
 *
 * @since 0.19
 */
public class VWAPZScoreIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> deviationIndicator;
    private final Indicator<Num> standardDeviationIndicator;
    private final Indicator<Num> ratio;

    /**
     * Constructor.
     *
     * @param deviationIndicator         indicator measuring price - VWAP
     * @param standardDeviationIndicator indicator providing the VWAP standard
     *                                   deviation
     *
     * @since 0.19
     */
    public VWAPZScoreIndicator(Indicator<Num> deviationIndicator, Indicator<Num> standardDeviationIndicator) {
        super(IndicatorSeriesUtils.requireSameSeries(deviationIndicator, standardDeviationIndicator));
        this.deviationIndicator = deviationIndicator;
        this.standardDeviationIndicator = standardDeviationIndicator;
        this.ratio = BinaryOperation.quotient(deviationIndicator, standardDeviationIndicator);
    }

    @Override
    protected Num calculate(int index) {
        Num deviation = deviationIndicator.getValue(index);
        Num std = standardDeviationIndicator.getValue(index);
        if (Num.isNaNOrNull(deviation) || Num.isNaNOrNull(std) || std.isZero()) {
            return NaN.NaN;
        }
        return ratio.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(deviationIndicator.getCountOfUnstableBars(),
                standardDeviationIndicator.getCountOfUnstableBars());
    }
}
