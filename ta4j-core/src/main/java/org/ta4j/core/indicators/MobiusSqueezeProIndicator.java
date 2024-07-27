/**
 * The MIT License (MIT)
 * <p>
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
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
import org.ta4j.core.indicators.bollinger.BollingerBandFacade;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.keltner.KeltnerChannelLowerIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelMiddleIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelUpperIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

public class MobiusSqueezeProIndicator extends CachedIndicator<Boolean> {

    private final KeltnerChannelMiddleIndicator keltnerChannelMidLine;
    private final Indicator<Num> keltnerChannelUpperBandHigh;
    private final Indicator<Num> keltnerChannelLowerBandHigh;
    private final Indicator<Num> keltnerChannelUpperBandLow;
    private final Indicator<Num> keltnerChannelLowerBandLow;
    private final Indicator<Num> keltnerChannelUpperBandMid;
    private final Indicator<Num> keltnerChannelLowerBandMid;

    private final Indicator<Num> bollingerBandUpperLine;
    private final Indicator<Num> bollingerBandLowerLine;
    private final Indicator<Num> bollingerBandMidLine;

    private final Indicator<Num> momentum;

    private final int barCount;

    public MobiusSqueezeProIndicator(BarSeries series, int barCount) {
        this(series, barCount, 2, 1, 1.5, 2);
    }

    public MobiusSqueezeProIndicator(BarSeries series, int barCount, double bollingerBandK,
                                     double keltnerShiftFactorHigh, double keltnerShiftFactorMid, double keltnerShiftFactorLow) {
        super(series);

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator closePriceEma = new EMAIndicator(closePrice, barCount);
        ATRIndicator averageTrueRange = new ATRIndicator(series, barCount);

        BollingerBandFacade bollingerBand = new BollingerBandFacade(series, barCount, bollingerBandK);
        this.bollingerBandMidLine = bollingerBand.middle();
        this.bollingerBandUpperLine = bollingerBand.upper();
        this.bollingerBandLowerLine = bollingerBand.lower();

        this.keltnerChannelMidLine = new KeltnerChannelMiddleIndicator(series, barCount);
        this.keltnerChannelUpperBandLow = new KeltnerChannelUpperIndicator(this.keltnerChannelMidLine, averageTrueRange, keltnerShiftFactorLow);
        this.keltnerChannelLowerBandLow = new KeltnerChannelLowerIndicator(this.keltnerChannelMidLine, averageTrueRange, keltnerShiftFactorLow);
        this.keltnerChannelUpperBandMid = new KeltnerChannelUpperIndicator(this.keltnerChannelMidLine, averageTrueRange, keltnerShiftFactorMid);
        this.keltnerChannelLowerBandMid = new KeltnerChannelLowerIndicator(this.keltnerChannelMidLine, averageTrueRange, keltnerShiftFactorMid);
        this.keltnerChannelUpperBandHigh = new KeltnerChannelUpperIndicator(this.keltnerChannelMidLine, averageTrueRange, keltnerShiftFactorHigh);
        this.keltnerChannelLowerBandHigh = new KeltnerChannelLowerIndicator(this.keltnerChannelMidLine, averageTrueRange, keltnerShiftFactorHigh);

        HighestValueIndicator highestPrice = new HighestValueIndicator(new HighPriceIndicator(series), barCount);
        LowestValueIndicator lowestPrice = new LowestValueIndicator(new LowPriceIndicator(series), barCount);

        Indicator<Num> k1 = CombineIndicator.plus(highestPrice, lowestPrice);
        Indicator<Num> k2 = TransformIndicator.plus(closePriceEma, series.numOf(2).intValue());
        Indicator<Num> k = CombineIndicator.divide(k1, k2);

        Indicator<Num> closePriceMinusK = CombineIndicator.minus(closePrice, k);
        Indicator<Num> closePriceMinusKDivideBy2 = TransformIndicator.divide(closePriceMinusK, series.numOf(2).intValue());
        this.momentum = new EMAIndicator(closePriceMinusKDivideBy2, barCount);

        this.barCount = barCount;
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < this.getUnstableBars()) {
            return false;
        }

        boolean preSqueeze = bollingerBandLowerLine.getValue(index).isGreaterThan(keltnerChannelLowerBandLow.getValue(index))
                && bollingerBandUpperLine.getValue(index).isLessThan(keltnerChannelUpperBandLow.getValue(index));
        boolean originalSqueeze = bollingerBandLowerLine.getValue(index).isGreaterThan(keltnerChannelLowerBandMid.getValue(index))
                && bollingerBandUpperLine.getValue(index).isLessThan(keltnerChannelUpperBandMid.getValue(index));
        boolean extremeSqueeze = bollingerBandLowerLine.getValue(index).isGreaterThan(keltnerChannelLowerBandHigh.getValue(index))
                && bollingerBandUpperLine.getValue(index).isLessThan(keltnerChannelUpperBandHigh.getValue(index));

        return preSqueeze || originalSqueeze || extremeSqueeze;
    }

    @Override
    public int getUnstableBars() {
        return this.barCount;
    }
}
