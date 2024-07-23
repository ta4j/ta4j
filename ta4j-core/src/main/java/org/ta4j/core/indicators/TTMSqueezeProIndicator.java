/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.CombineIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

public class TTMSqueezeProIndicator extends CachedIndicator<Num> {

    private final BollingerBandsUpperIndicator upperBandBB;
    private final BollingerBandsLowerIndicator lowerBandBB;

    private final Indicator<Num> upperBandKCHigh;
    private final Indicator<Num> lowerBandKCHigh;
    private final Indicator<Num> upperBandKCLow;
    private final Indicator<Num> lowerBandKCLow;
    private final Indicator<Num> upperBandKCMid;
    private final Indicator<Num> lowerBandKCMid;

    private final int barCount;

    public TTMSqueezeProIndicator(BarSeries series, int barCount, double bbLowerBandK, double bbUpperBandK,
            double shiftFactorHigh, double shiftFactorMid, double shiftFactorLow) {
        super(series);

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        StandardDeviationIndicator closePriceStdDev = new StandardDeviationIndicator(closePrice, barCount);
        CachedIndicator<Num> closePriceSma = new SMAIndicator(closePrice, barCount);
        BollingerBandsMiddleIndicator midLineBB = new BollingerBandsMiddleIndicator(closePriceSma);

        this.upperBandBB = new BollingerBandsUpperIndicator(midLineBB, closePriceStdDev, numOf(bbUpperBandK));
        this.lowerBandBB = new BollingerBandsLowerIndicator(midLineBB, closePriceStdDev, numOf(bbLowerBandK));

        Indicator<Num> shiftHigh = TransformIndicator.multiply(closePriceSma, shiftFactorHigh);
        Indicator<Num> shiftMid = TransformIndicator.multiply(closePriceSma, shiftFactorMid);
        Indicator<Num> shiftLow = TransformIndicator.multiply(closePriceSma, shiftFactorLow);

        this.upperBandKCLow = CombineIndicator.plus(closePriceSma, shiftLow);
        this.lowerBandKCLow = CombineIndicator.minus(closePriceSma, shiftLow);
        this.upperBandKCMid = CombineIndicator.plus(closePriceSma, shiftMid);
        this.lowerBandKCMid = CombineIndicator.minus(closePriceSma, shiftMid);
        this.upperBandKCHigh = CombineIndicator.plus(closePriceSma, shiftHigh);
        this.lowerBandKCHigh = CombineIndicator.minus(closePriceSma, shiftHigh);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        boolean presqueeze = lowerBandBB.getValue(index).isGreaterThan(lowerBandKCLow.getValue(index))
                && upperBandBB.getValue(index).isLessThan(upperBandKCLow.getValue(index));
        boolean originalSqueeze = lowerBandBB.getValue(index).isGreaterThan(lowerBandKCMid.getValue(index))
                && upperBandBB.getValue(index).isLessThan(upperBandKCMid.getValue(index));
        boolean extrSqueeze = lowerBandBB.getValue(index).isGreaterThan(lowerBandKCHigh.getValue(index))
                && upperBandBB.getValue(index).isLessThan(upperBandKCHigh.getValue(index));

        if (presqueeze || originalSqueeze || extrSqueeze) {
            return numOf(1);
        } else {
            return numOf(0);
        }
    }

    @Override
    public int getUnstableBars() {
        return this.barCount;
    }
}
