/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;
import java.util.function.BiFunction;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Volatility-normalized MACD (MACD-V) indicator.
 *
 * <p>
 * This implementation follows the volatility-normalized formulation where the
 * EMA spread is divided by average true range (ATR) and scaled:
 *
 * <pre>
 * MACD-V = ((EMA(fast) - EMA(slow)) / ATR(atrPeriod)) * scale
 * </pre>
 *
 * Typical defaults are {@code fast=12}, {@code slow=26}, {@code atrPeriod=26},
 * {@code signal=9}, and {@code scale=100}.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/macd-v">
 *      https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/macd-v
 *      </a>
 * @since 0.22.2
 */
public class VolatilityNormalizedMACDIndicator extends CachedIndicator<Num> {

    private static final int DEFAULT_FAST_BAR_COUNT = 12;
    private static final int DEFAULT_SLOW_BAR_COUNT = 26;
    private static final int DEFAULT_SIGNAL_BAR_COUNT = 9;
    private static final int DEFAULT_SCALE_FACTOR = 100;

    private final Indicator<Num> priceIndicator;
    private final int fastBarCount;
    private final int slowBarCount;
    private final int atrBarCount;
    private final int defaultSignalBarCount;
    private final Num scaleFactor;

    private transient EMAIndicator fastEma;
    private transient EMAIndicator slowEma;
    private transient ATRIndicator averageTrueRange;

    /**
     * Constructor with defaults:
     *
     * <ul>
     * <li>{@code fastBarCount} = 12
     * <li>{@code slowBarCount} = 26
     * <li>{@code atrBarCount} = 26
     * <li>{@code signalBarCount} = 9
     * <li>{@code scaleFactor} = 100
     * </ul>
     *
     * @param series the bar series
     * @since 0.22.2
     */
    public VolatilityNormalizedMACDIndicator(BarSeries series) {
        this(new ClosePriceIndicator(series));
    }

    /**
     * Constructor with defaults:
     *
     * <ul>
     * <li>{@code fastBarCount} = 12
     * <li>{@code slowBarCount} = 26
     * <li>{@code atrBarCount} = 26
     * <li>{@code signalBarCount} = 9
     * <li>{@code scaleFactor} = 100
     * </ul>
     *
     * @param priceIndicator the price indicator
     * @since 0.22.2
     */
    public VolatilityNormalizedMACDIndicator(Indicator<Num> priceIndicator) {
        this(priceIndicator, DEFAULT_FAST_BAR_COUNT, DEFAULT_SLOW_BAR_COUNT, DEFAULT_SLOW_BAR_COUNT,
                DEFAULT_SIGNAL_BAR_COUNT, DEFAULT_SCALE_FACTOR);
    }

    /**
     * Constructor.
     *
     * @param series         the bar series
     * @param fastBarCount   fast EMA period (normally 12)
     * @param slowBarCount   slow EMA period (normally 26)
     * @param signalBarCount default signal EMA period (normally 9)
     * @since 0.22.2
     */
    public VolatilityNormalizedMACDIndicator(BarSeries series, int fastBarCount, int slowBarCount, int signalBarCount) {
        this(new ClosePriceIndicator(series), fastBarCount, slowBarCount, slowBarCount, signalBarCount,
                DEFAULT_SCALE_FACTOR);
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param fastBarCount   fast EMA period (normally 12)
     * @param slowBarCount   slow EMA period (normally 26)
     * @param signalBarCount default signal EMA period (normally 9)
     * @since 0.22.2
     */
    public VolatilityNormalizedMACDIndicator(Indicator<Num> priceIndicator, int fastBarCount, int slowBarCount,
            int signalBarCount) {
        this(priceIndicator, fastBarCount, slowBarCount, slowBarCount, signalBarCount, DEFAULT_SCALE_FACTOR);
    }

    /**
     * Fully-specified constructor.
     *
     * @param series         the bar series
     * @param fastBarCount   fast EMA period
     * @param slowBarCount   slow EMA period
     * @param atrBarCount    ATR period
     * @param signalBarCount default signal EMA period
     * @param scaleFactor    scalar multiplier (typically 100)
     * @since 0.22.2
     */
    public VolatilityNormalizedMACDIndicator(BarSeries series, int fastBarCount, int slowBarCount, int atrBarCount,
            int signalBarCount, Number scaleFactor) {
        this(new ClosePriceIndicator(series), fastBarCount, slowBarCount, atrBarCount, signalBarCount, scaleFactor);
    }

    /**
     * Fully-specified constructor.
     *
     * @param priceIndicator the price indicator
     * @param fastBarCount   fast EMA period
     * @param slowBarCount   slow EMA period
     * @param atrBarCount    ATR period
     * @param signalBarCount default signal EMA period
     * @param scaleFactor    scalar multiplier (typically 100)
     * @since 0.22.2
     */
    public VolatilityNormalizedMACDIndicator(Indicator<Num> priceIndicator, int fastBarCount, int slowBarCount,
            int atrBarCount, int signalBarCount, Number scaleFactor) {
        super(priceIndicator);
        validatePeriods(fastBarCount, slowBarCount, atrBarCount, signalBarCount);
        if (scaleFactor == null) {
            throw new IllegalArgumentException("Scale factor must not be null");
        }

        Num resolvedScaleFactor = priceIndicator.getBarSeries().numFactory().numOf(scaleFactor);
        if (Num.isNaNOrNull(resolvedScaleFactor) || resolvedScaleFactor.isNegativeOrZero()) {
            throw new IllegalArgumentException("Scale factor must be greater than 0");
        }

        this.priceIndicator = priceIndicator;
        this.fastBarCount = fastBarCount;
        this.slowBarCount = slowBarCount;
        this.atrBarCount = atrBarCount;
        this.defaultSignalBarCount = signalBarCount;
        this.scaleFactor = resolvedScaleFactor;
        ensureSubIndicatorsInitialized();
    }

    private static void validatePeriods(int fastBarCount, int slowBarCount, int atrBarCount, int signalBarCount) {
        if (fastBarCount < 1) {
            throw new IllegalArgumentException("Fast EMA period must be greater than 0");
        }
        if (slowBarCount < 1) {
            throw new IllegalArgumentException("Slow EMA period must be greater than 0");
        }
        if (atrBarCount < 1) {
            throw new IllegalArgumentException("ATR period must be greater than 0");
        }
        if (signalBarCount < 1) {
            throw new IllegalArgumentException("Signal period must be greater than 0");
        }
        if (fastBarCount > slowBarCount) {
            throw new IllegalArgumentException("Slow EMA period must be greater than or equal to fast EMA period");
        }
    }

    /**
     * @return signal line using the configured default signal period
     * @since 0.22.2
     */
    public EMAIndicator getSignalLine() {
        return getSignalLine(defaultSignalBarCount);
    }

    /**
     * @param signalBarCount signal line period
     * @return signal line for this MACD-V indicator
     * @since 0.22.2
     */
    public EMAIndicator getSignalLine(int signalBarCount) {
        if (signalBarCount < 1) {
            throw new IllegalArgumentException("Signal period must be greater than 0");
        }
        return new EMAIndicator(this, signalBarCount);
    }

    /**
     * Returns a signal line created by the provided factory using this indicator
     * and the configured default signal period.
     *
     * @param signalLineFactory factory that creates a signal-line indicator from
     *                          {@code (this, signalBarCount)}
     * @return custom signal line indicator
     * @since 0.22.2
     */
    public Indicator<Num> getSignalLine(BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return getSignalLine(defaultSignalBarCount, signalLineFactory);
    }

    /**
     * Returns a signal line created by the provided factory.
     *
     * @param signalBarCount    signal line period
     * @param signalLineFactory factory that creates a signal-line indicator from
     *                          {@code (this, signalBarCount)}
     * @return custom signal line indicator
     * @since 0.22.2
     */
    public Indicator<Num> getSignalLine(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        if (signalBarCount < 1) {
            throw new IllegalArgumentException("Signal period must be greater than 0");
        }
        if (signalLineFactory == null) {
            throw new IllegalArgumentException("Signal line factory must not be null");
        }
        Indicator<Num> signalLine = signalLineFactory.apply(this, signalBarCount);
        if (signalLine == null) {
            throw new IllegalArgumentException("Signal line factory must not return null");
        }
        if (!Objects.equals(signalLine.getBarSeries(), getBarSeries())) {
            throw new IllegalArgumentException("Signal line must share the same bar series");
        }
        return signalLine;
    }

    /**
     * @return histogram using the configured default signal period
     * @since 0.22.2
     */
    public NumericIndicator getHistogram() {
        return getHistogram(defaultSignalBarCount);
    }

    /**
     * @param signalBarCount signal line period
     * @return histogram as {@code macdV - signal}
     * @since 0.22.2
     */
    public NumericIndicator getHistogram(int signalBarCount) {
        if (signalBarCount < 1) {
            throw new IllegalArgumentException("Signal period must be greater than 0");
        }
        return NumericIndicator.of(this).minus(getSignalLine(signalBarCount));
    }

    /**
     * Returns a histogram built with a custom signal-line factory and the
     * configured default signal period.
     *
     * @param signalLineFactory factory that creates a signal-line indicator
     * @return histogram as {@code macdV - signal}
     * @since 0.22.2
     */
    public NumericIndicator getHistogram(BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return getHistogram(defaultSignalBarCount, signalLineFactory);
    }

    /**
     * Returns a histogram built with a custom signal-line factory.
     *
     * @param signalBarCount    signal line period
     * @param signalLineFactory factory that creates a signal-line indicator
     * @return histogram as {@code macdV - signal}
     * @since 0.22.2
     */
    public NumericIndicator getHistogram(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        if (signalBarCount < 1) {
            throw new IllegalArgumentException("Signal period must be greater than 0");
        }
        return NumericIndicator.of(this).minus(getSignalLine(signalBarCount, signalLineFactory));
    }

    /**
     * @return this MACD-V line
     * @since 0.22.2
     */
    public VolatilityNormalizedMACDIndicator getMacdV() {
        return this;
    }

    /**
     * Classifies the current MACD-V value into a momentum state using the default
     * MACD-V lifecycle thresholds.
     *
     * @param index the bar index
     * @return momentum state for {@code getValue(index)}
     * @since 0.22.2
     */
    public MACDVMomentumState getMomentumState(int index) {
        return MACDVMomentumState.fromMacdV(getValue(index), getBarSeries().numFactory());
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }

        Num fastValue = getFastEma().getValue(index);
        Num slowValue = getSlowEma().getValue(index);
        Num atrValue = getAverageTrueRangeIndicator().getValue(index);
        if (Num.isNaNOrNull(fastValue) || Num.isNaNOrNull(slowValue) || Num.isNaNOrNull(atrValue)) {
            return NaN.NaN;
        }

        Num spread = fastValue.minus(slowValue);
        if (Num.isNaNOrNull(spread)) {
            return NaN.NaN;
        }
        if (atrValue.isZero()) {
            return spread.isZero() ? getBarSeries().numFactory().zero() : NaN.NaN;
        }

        Num macdV = spread.dividedBy(atrValue).multipliedBy(scaleFactor);
        return Num.isNaNOrNull(macdV) ? NaN.NaN : macdV;
    }

    @Override
    public int getCountOfUnstableBars() {
        int emaUnstableBars = Math.max(getFastEma().getCountOfUnstableBars(), getSlowEma().getCountOfUnstableBars());
        int spreadUnstableBars = priceIndicator.getCountOfUnstableBars() + emaUnstableBars;
        return Math.max(spreadUnstableBars, getAverageTrueRangeIndicator().getCountOfUnstableBars());
    }

    private void ensureSubIndicatorsInitialized() {
        getFastEma();
        getSlowEma();
        getAverageTrueRangeIndicator();
    }

    private EMAIndicator getFastEma() {
        if (fastEma == null) {
            fastEma = new EMAIndicator(priceIndicator, fastBarCount);
        }
        return fastEma;
    }

    private EMAIndicator getSlowEma() {
        if (slowEma == null) {
            slowEma = new EMAIndicator(priceIndicator, slowBarCount);
        }
        return slowEma;
    }

    private ATRIndicator getAverageTrueRangeIndicator() {
        if (averageTrueRange == null) {
            averageTrueRange = new ATRIndicator(getBarSeries(), atrBarCount);
        }
        return averageTrueRange;
    }
}
