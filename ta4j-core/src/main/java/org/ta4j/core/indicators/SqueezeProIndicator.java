/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

/**
 * Mobius/TTM Squeeze Pro momentum indicator (LazyBear/TradingView parity).
 *
 * <p>
 * The "Pro" variant exposes three compression levels, measured by how tightly
 * the Bollinger Bands fit inside progressively wider Keltner Channels:
 * <ul>
 * <li>HIGH: Bollinger Bands fully inside KC using {@code kcHigh}</li>
 * <li>MID: Bollinger Bands fully inside KC using {@code kcMid}</li>
 * <li>LOW: Bollinger Bands fully inside KC using {@code kcLow}</li>
 * <li>NONE: no squeeze</li>
 * </ul>
 * The indicator value itself is the squeeze momentum histogram from the
 * original LazyBear implementation: a linear regression of the detrended price
 * series ({@code close - avg(avg(highest(high), lowest(low)), sma(close))})
 * over {@code barCount}. Compression state can be queried via
 * {@link #getSqueezeLevel(int)} or the convenience {@link #isInSqueeze(int)}.
 *
 * <p>
 * To mirror TradingView defaults this implementation uses SMA-based Bollinger
 * Bands and a simple moving average of True Range for the Keltner channel width
 * (not Wilder's ATR smoothing).
 */
public class SqueezeProIndicator extends CachedIndicator<Num> {

    public enum SqueezeLevel {
        NONE, LOW, MID, HIGH
    }

    private final Indicator<Num> closePrice;
    private final Indicator<Num> priceSma;
    private final Indicator<Num> priceStdDev;
    private final Indicator<Num> trueRangeSma;
    private final Indicator<Num> detrendedPrice;
    private final Indicator<Num> momentum;

    private final Num bollingerBandK;
    private final Num keltnerShiftFactorHigh;
    private final Num keltnerShiftFactorMid;
    private final Num keltnerShiftFactorLow;

    private final int barCount;

    /**
     * Constructor with default parameters.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public SqueezeProIndicator(BarSeries series, int barCount) {
        this(series, barCount, 2, 1, 1.5, 2);
    }

    /**
     * Constructor with custom parameters.
     *
     * @param series                 the bar series
     * @param barCount               the time frame
     * @param bollingerBandK         the Bollinger Band multiplier
     * @param keltnerShiftFactorHigh the Keltner Channel high band multiplier
     * @param keltnerShiftFactorMid  the Keltner Channel middle band multiplier
     * @param keltnerShiftFactorLow  the Keltner Channel low band multiplier
     */
    public SqueezeProIndicator(BarSeries series, int barCount, double bollingerBandK, double keltnerShiftFactorHigh,
            double keltnerShiftFactorMid, double keltnerShiftFactorLow) {
        super(series);

        this.bollingerBandK = series.numFactory().numOf(bollingerBandK);
        this.keltnerShiftFactorHigh = series.numFactory().numOf(keltnerShiftFactorHigh);
        this.keltnerShiftFactorMid = series.numFactory().numOf(keltnerShiftFactorMid);
        this.keltnerShiftFactorLow = series.numFactory().numOf(keltnerShiftFactorLow);
        this.closePrice = new ClosePriceIndicator(series);
        this.priceSma = new SMAIndicator(closePrice, barCount);
        this.priceStdDev = new StandardDeviationIndicator(closePrice, barCount);
        this.trueRangeSma = new SMAIndicator(new TRIndicator(series), barCount);

        Indicator<Num> highestHigh = new HighestValueIndicator(new HighPriceIndicator(series), barCount);
        Indicator<Num> lowestLow = new LowestValueIndicator(new LowPriceIndicator(series), barCount);
        Indicator<Num> averageHighLow = NumericIndicator.of(highestHigh).plus(lowestLow).dividedBy(2);
        Indicator<Num> averageHighLowAndSma = NumericIndicator.of(averageHighLow).plus(priceSma).dividedBy(2);
        this.detrendedPrice = NumericIndicator.of(closePrice).minus(averageHighLowAndSma);

        this.momentum = new SimpleLinearRegressionIndicator(detrendedPrice, barCount);
        this.barCount = barCount;
    }

    /**
     * Calculates the Squeeze Pro histogram value for a specific index.
     *
     * @param index the index
     * @return squeeze momentum histogram value, or NaN during the unstable period
     */
    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        Num value = momentum.getValue(index);
        Num bbLower = bollingerBandLowerLine(index);
        Num bbUpper = bollingerBandUpperLine(index);
        if (isNaN(value) || isNaN(bbLower) || isNaN(bbUpper)) {
            return NaN;
        }

        return value;
    }

    /**
     * Returns the squeeze level at the given index.
     *
     * @param index the index to check
     * @return squeeze level, or {@link SqueezeLevel#NONE} if no compression is
     *         detected
     */
    public SqueezeLevel getSqueezeLevel(int index) {
        if (index < getCountOfUnstableBars()) {
            return SqueezeLevel.NONE;
        }
        Num bbLower = bollingerBandLowerLine(index);
        Num bbUpper = bollingerBandUpperLine(index);
        if (isNaN(bbLower) || isNaN(bbUpper)) {
            return SqueezeLevel.NONE;
        }

        if (isSqueezeCondition(bbLower, bbUpper, keltnerShiftFactorHigh, index)) {
            return SqueezeLevel.HIGH;
        }
        if (isSqueezeCondition(bbLower, bbUpper, keltnerShiftFactorMid, index)) {
            return SqueezeLevel.MID;
        }
        if (isSqueezeCondition(bbLower, bbUpper, keltnerShiftFactorLow, index)) {
            return SqueezeLevel.LOW;
        }
        return SqueezeLevel.NONE;
    }

    /**
     * Returns whether the Bollinger Bands are inside any of the configured Keltner
     * channels at the given index.
     *
     * @param index the index to check
     * @return true when any squeeze level is active
     */
    public boolean isInSqueeze(int index) {
        return getSqueezeLevel(index) != SqueezeLevel.NONE;
    }

    private Num bollingerBandUpperLine(int index) {
        Num basis = priceSma.getValue(index);
        Num stdDev = priceStdDev.getValue(index);
        if (isNaN(basis) || isNaN(stdDev)) {
            return NaN;
        }
        return basis.plus(stdDev.multipliedBy(bollingerBandK));
    }

    private Num bollingerBandLowerLine(int index) {
        Num basis = priceSma.getValue(index);
        Num stdDev = priceStdDev.getValue(index);
        if (isNaN(basis) || isNaN(stdDev)) {
            return NaN;
        }
        return basis.minus(stdDev.multipliedBy(bollingerBandK));
    }

    private boolean isSqueezeCondition(Num bbLower, Num bbUpper, Num keltnerShiftFactor, int index) {
        Num basis = priceSma.getValue(index);
        Num atrWidth = trueRangeSma.getValue(index);
        if (isNaN(basis) || isNaN(atrWidth)) {
            return false;
        }
        Num upperKeltner = basis.plus(atrWidth.multipliedBy(keltnerShiftFactor));
        Num lowerKeltner = basis.minus(atrWidth.multipliedBy(keltnerShiftFactor));

        return bbLower.isGreaterThan(lowerKeltner) && bbUpper.isLessThan(upperKeltner);
    }

    private boolean isNaN(Num value) {
        return value.isNaN() || Double.isNaN(value.doubleValue());
    }

    @Override
    public int getCountOfUnstableBars() {
        int unstableBars = Math.max(priceSma.getCountOfUnstableBars(), priceStdDev.getCountOfUnstableBars());
        unstableBars = Math.max(unstableBars, trueRangeSma.getCountOfUnstableBars());
        return Math.max(unstableBars, momentum.getCountOfUnstableBars());
    }
}
