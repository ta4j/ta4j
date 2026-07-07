/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.num.Num;

/**
 * Ultimate Oscillator indicator.
 *
 * <p>
 * The Ultimate Oscillator combines short, medium, and long buying-pressure
 * averages to reduce false divergences from a single look-back period.
 *
 * <pre>
 * BP = close(i) - min(low(i), close(i-1))
 * TR = max(high(i), close(i-1)) - min(low(i), close(i-1))
 *
 * avgShort  = SUM(BP, shortPeriod)  / SUM(TR, shortPeriod)
 * avgMiddle = SUM(BP, middlePeriod) / SUM(TR, middlePeriod)
 * avgLong   = SUM(BP, longPeriod)   / SUM(TR, longPeriod)
 *
 * UO = 100 * (4 * avgShort + 2 * avgMiddle + avgLong) / 7
 * </pre>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Ultimate_oscillator">Wikipedia:
 *      Ultimate Oscillator</a>
 * @since 0.22.3
 */
public class UltimateOscillatorIndicator extends CachedIndicator<Num> {

    /** Default short period. */
    public static final int DEFAULT_SHORT_PERIOD = 7;

    /** Default middle period. */
    public static final int DEFAULT_MIDDLE_PERIOD = 14;

    /** Default long period. */
    public static final int DEFAULT_LONG_PERIOD = 28;

    private final int shortPeriod;
    private final int middlePeriod;
    private final int longPeriod;

    @SuppressWarnings("unused")
    private final Indicator<Num> highPriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> lowPriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> closePriceIndicator;

    private final transient RunningTotalIndicator shortBuyingPressureSumIndicator;
    private final transient RunningTotalIndicator middleBuyingPressureSumIndicator;
    private final transient RunningTotalIndicator longBuyingPressureSumIndicator;

    private final transient RunningTotalIndicator shortTrueRangeSumIndicator;
    private final transient RunningTotalIndicator middleTrueRangeSumIndicator;
    private final transient RunningTotalIndicator longTrueRangeSumIndicator;

    private final Num shortWeight;
    private final Num middleWeight;
    private final Num longWeight;
    private final Num totalWeight;
    private final Num hundred;

    /**
     * Constructor using the canonical (7, 14, 28) periods.
     *
     * @param series the bar series
     * @since 0.22.3
     */
    public UltimateOscillatorIndicator(BarSeries series) {
        this(validatedConfig(series, DEFAULT_SHORT_PERIOD, DEFAULT_MIDDLE_PERIOD, DEFAULT_LONG_PERIOD));
    }

    /**
     * Constructor.
     *
     * @param series       the bar series
     * @param shortPeriod  short look-back period
     * @param middlePeriod middle look-back period
     * @param longPeriod   long look-back period
     * @since 0.22.3
     */
    public UltimateOscillatorIndicator(BarSeries series, int shortPeriod, int middlePeriod, int longPeriod) {
        this(validatedConfig(series, shortPeriod, middlePeriod, longPeriod));
    }

    /**
     * Constructor using the canonical (7, 14, 28) periods.
     *
     * @param highPriceIndicator  high-price indicator
     * @param lowPriceIndicator   low-price indicator
     * @param closePriceIndicator close-price indicator
     * @since 0.22.3
     */
    public UltimateOscillatorIndicator(Indicator<Num> highPriceIndicator, Indicator<Num> lowPriceIndicator,
            Indicator<Num> closePriceIndicator) {
        this(validatedConfig(highPriceIndicator, lowPriceIndicator, closePriceIndicator, DEFAULT_SHORT_PERIOD,
                DEFAULT_MIDDLE_PERIOD, DEFAULT_LONG_PERIOD));
    }

    /**
     * Constructor.
     *
     * @param highPriceIndicator  high-price indicator
     * @param lowPriceIndicator   low-price indicator
     * @param closePriceIndicator close-price indicator
     * @param shortPeriod         short look-back period (must be greater than 0)
     * @param middlePeriod        middle look-back period (must be greater than
     *                            shortPeriod)
     * @param longPeriod          long look-back period (must be greater than
     *                            middlePeriod)
     * @since 0.22.3
     */
    public UltimateOscillatorIndicator(Indicator<Num> highPriceIndicator, Indicator<Num> lowPriceIndicator,
            Indicator<Num> closePriceIndicator, int shortPeriod, int middlePeriod, int longPeriod) {
        this(validatedConfig(highPriceIndicator, lowPriceIndicator, closePriceIndicator, shortPeriod, middlePeriod,
                longPeriod));
    }

    private UltimateOscillatorIndicator(Config config) {
        super(config.series());
        this.highPriceIndicator = config.highPriceIndicator();
        this.lowPriceIndicator = config.lowPriceIndicator();
        this.closePriceIndicator = config.closePriceIndicator();
        this.shortPeriod = config.shortPeriod();
        this.middlePeriod = config.middlePeriod();
        this.longPeriod = config.longPeriod();
        this.shortBuyingPressureSumIndicator = config.shortBuyingPressureSumIndicator();
        this.middleBuyingPressureSumIndicator = config.middleBuyingPressureSumIndicator();
        this.longBuyingPressureSumIndicator = config.longBuyingPressureSumIndicator();
        this.shortTrueRangeSumIndicator = config.shortTrueRangeSumIndicator();
        this.middleTrueRangeSumIndicator = config.middleTrueRangeSumIndicator();
        this.longTrueRangeSumIndicator = config.longTrueRangeSumIndicator();
        this.shortWeight = config.shortWeight();
        this.middleWeight = config.middleWeight();
        this.longWeight = config.longWeight();
        this.totalWeight = config.totalWeight();
        this.hundred = config.hundred();
    }

    private static Config validatedConfig(BarSeries series, int shortPeriod, int middlePeriod, int longPeriod) {
        return validatedConfig(new HighPriceIndicator(series), new LowPriceIndicator(series),
                new ClosePriceIndicator(series), shortPeriod, middlePeriod, longPeriod);
    }

    private static Config validatedConfig(Indicator<Num> highPriceIndicator, Indicator<Num> lowPriceIndicator,
            Indicator<Num> closePriceIndicator, int shortPeriod, int middlePeriod, int longPeriod) {
        BarSeries series = IndicatorUtils.requireSameSeries(highPriceIndicator, lowPriceIndicator, closePriceIndicator);
        validatePeriods(shortPeriod, middlePeriod, longPeriod);

        BuyingPressureIndicator buyingPressureIndicator = new BuyingPressureIndicator(lowPriceIndicator,
                closePriceIndicator);
        TRIndicator trueRangeIndicator = new TRIndicator(highPriceIndicator, lowPriceIndicator, closePriceIndicator);

        RunningTotalIndicator shortBuyingPressureSumIndicator = new RunningTotalIndicator(buyingPressureIndicator,
                shortPeriod);
        RunningTotalIndicator middleBuyingPressureSumIndicator = new RunningTotalIndicator(buyingPressureIndicator,
                middlePeriod);
        RunningTotalIndicator longBuyingPressureSumIndicator = new RunningTotalIndicator(buyingPressureIndicator,
                longPeriod);

        RunningTotalIndicator shortTrueRangeSumIndicator = new RunningTotalIndicator(trueRangeIndicator, shortPeriod);
        RunningTotalIndicator middleTrueRangeSumIndicator = new RunningTotalIndicator(trueRangeIndicator, middlePeriod);
        RunningTotalIndicator longTrueRangeSumIndicator = new RunningTotalIndicator(trueRangeIndicator, longPeriod);

        Num shortWeight = series.numFactory().numOf(4);
        Num middleWeight = series.numFactory().numOf(2);
        Num longWeight = series.numFactory().one();
        Num totalWeight = shortWeight.plus(middleWeight).plus(longWeight);
        Num hundred = series.numFactory().hundred();
        return new Config(series, highPriceIndicator, lowPriceIndicator, closePriceIndicator, shortPeriod, middlePeriod,
                longPeriod, shortBuyingPressureSumIndicator, middleBuyingPressureSumIndicator,
                longBuyingPressureSumIndicator, shortTrueRangeSumIndicator, middleTrueRangeSumIndicator,
                longTrueRangeSumIndicator, shortWeight, middleWeight, longWeight, totalWeight, hundred);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        Num shortAverage = calculateAverage(index, shortBuyingPressureSumIndicator, shortTrueRangeSumIndicator);
        Num middleAverage = calculateAverage(index, middleBuyingPressureSumIndicator, middleTrueRangeSumIndicator);
        Num longAverage = calculateAverage(index, longBuyingPressureSumIndicator, longTrueRangeSumIndicator);

        if (!Num.isFinite(shortAverage) || !Num.isFinite(middleAverage) || !Num.isFinite(longAverage)) {
            return NaN;
        }

        Num weightedAverage = shortAverage.multipliedBy(shortWeight)
                .plus(middleAverage.multipliedBy(middleWeight))
                .plus(longAverage.multipliedBy(longWeight))
                .dividedBy(totalWeight);
        return weightedAverage.multipliedBy(hundred);
    }

    /**
     * Computes one buying-pressure/true-range average for a validated index.
     *
     * <p>
     * Callers are expected to enforce unstable-bar boundaries before invoking this
     * method.
     * </p>
     */
    private Num calculateAverage(int index, RunningTotalIndicator buyingPressureSumIndicator,
            RunningTotalIndicator trueRangeSumIndicator) {
        Num totalTrueRange = trueRangeSumIndicator.getValue(index);
        if (!Num.isFinite(totalTrueRange) || totalTrueRange.isZero()) {
            return NaN;
        }

        Num totalBuyingPressure = buyingPressureSumIndicator.getValue(index);
        if (!Num.isFinite(totalBuyingPressure)) {
            return NaN;
        }

        return totalBuyingPressure.dividedBy(totalTrueRange);
    }

    @Override
    public int getCountOfUnstableBars() {
        int shortUnstableBars = Math.max(shortBuyingPressureSumIndicator.getCountOfUnstableBars(),
                shortTrueRangeSumIndicator.getCountOfUnstableBars());
        int middleUnstableBars = Math.max(middleBuyingPressureSumIndicator.getCountOfUnstableBars(),
                middleTrueRangeSumIndicator.getCountOfUnstableBars());
        int longUnstableBars = Math.max(longBuyingPressureSumIndicator.getCountOfUnstableBars(),
                longTrueRangeSumIndicator.getCountOfUnstableBars());
        return Math.max(shortUnstableBars, Math.max(middleUnstableBars, longUnstableBars));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " shortPeriod: " + shortPeriod + " middlePeriod: " + middlePeriod
                + " longPeriod: " + longPeriod;
    }

    private static void validatePeriods(int shortPeriod, int middlePeriod, int longPeriod) {
        if (shortPeriod <= 0) {
            throw new IllegalArgumentException("Short period must be greater than 0");
        }
        if (middlePeriod <= shortPeriod) {
            throw new IllegalArgumentException("Middle period must be greater than short period");
        }
        if (longPeriod <= middlePeriod) {
            throw new IllegalArgumentException("Long period must be greater than middle period");
        }
    }

    private static final class BuyingPressureIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> lowPriceIndicator;
        private final Indicator<Num> closePriceIndicator;
        private final int unstableBars;

        private BuyingPressureIndicator(Indicator<Num> lowPriceIndicator, Indicator<Num> closePriceIndicator) {
            super(closePriceIndicator);
            this.lowPriceIndicator = lowPriceIndicator;
            this.closePriceIndicator = closePriceIndicator;
            this.unstableBars = Math.max(lowPriceIndicator.getCountOfUnstableBars(),
                    closePriceIndicator.getCountOfUnstableBars()) + 1;
        }

        @Override
        protected Num calculate(int index) {
            int beginIndex = getBarSeries().getBeginIndex();
            if (index <= beginIndex) {
                return NaN;
            }

            Num close = closePriceIndicator.getValue(index);
            Num low = lowPriceIndicator.getValue(index);
            Num previousClose = closePriceIndicator.getValue(index - 1);
            if (!Num.isFinite(close) || !Num.isFinite(low) || !Num.isFinite(previousClose)) {
                return NaN;
            }

            Num minimum = low.min(previousClose);
            return close.minus(minimum);
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }

    private record Config(BarSeries series, Indicator<Num> highPriceIndicator, Indicator<Num> lowPriceIndicator,
            Indicator<Num> closePriceIndicator, int shortPeriod, int middlePeriod, int longPeriod,
            RunningTotalIndicator shortBuyingPressureSumIndicator,
            RunningTotalIndicator middleBuyingPressureSumIndicator,
            RunningTotalIndicator longBuyingPressureSumIndicator, RunningTotalIndicator shortTrueRangeSumIndicator,
            RunningTotalIndicator middleTrueRangeSumIndicator, RunningTotalIndicator longTrueRangeSumIndicator,
            Num shortWeight, Num middleWeight, Num longWeight, Num totalWeight, Num hundred) {
    }
}
