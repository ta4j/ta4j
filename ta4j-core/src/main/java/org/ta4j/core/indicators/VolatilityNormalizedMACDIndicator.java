/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;
import java.util.function.BiFunction;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.MomentumStateRule;

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

        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
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

    private static void validateSignalBarCount(int signalBarCount) {
        if (signalBarCount < 1) {
            throw new IllegalArgumentException("Signal period must be greater than 0");
        }
    }

    private static void validateHistogramMode(MACDHistogramMode histogramMode) {
        if (histogramMode == null) {
            throw new IllegalArgumentException("Histogram mode must not be null");
        }
    }

    private static void validateSignalLineFactory(
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        if (signalLineFactory == null) {
            throw new IllegalArgumentException("Signal line factory must not be null");
        }
    }

    /**
     * @return source price indicator
     * @since 0.22.2
     */
    public Indicator<Num> getPriceIndicator() {
        return priceIndicator;
    }

    /**
     * @return configured fast EMA period
     * @since 0.22.2
     */
    public int getFastBarCount() {
        return fastBarCount;
    }

    /**
     * @return configured slow EMA period
     * @since 0.22.2
     */
    public int getSlowBarCount() {
        return slowBarCount;
    }

    /**
     * @return configured ATR period
     * @since 0.22.2
     */
    public int getAtrBarCount() {
        return atrBarCount;
    }

    /**
     * @return configured default signal period
     * @since 0.22.2
     */
    public int getDefaultSignalBarCount() {
        return defaultSignalBarCount;
    }

    /**
     * @return configured scale factor
     * @since 0.22.2
     */
    public Num getScaleFactor() {
        return scaleFactor;
    }

    /**
     * @return fast EMA indicator
     * @since 0.22.2
     */
    public EMAIndicator getFastEma() {
        if (fastEma == null) {
            fastEma = new EMAIndicator(priceIndicator, fastBarCount);
        }
        return fastEma;
    }

    /**
     * @return slow EMA indicator
     * @since 0.22.2
     */
    public EMAIndicator getSlowEma() {
        if (slowEma == null) {
            slowEma = new EMAIndicator(priceIndicator, slowBarCount);
        }
        return slowEma;
    }

    /**
     * @return ATR indicator used for normalization
     * @since 0.22.2
     */
    public ATRIndicator getAtrIndicator() {
        if (averageTrueRange == null) {
            averageTrueRange = new ATRIndicator(getBarSeries(), atrBarCount);
        }
        return averageTrueRange;
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
        validateSignalBarCount(signalBarCount);
        return new EMAIndicator(this, signalBarCount);
    }

    /**
     * @param signalLineFactory signal-line factory
     * @return signal line using default signal period and custom factory
     * @since 0.22.2
     */
    public Indicator<Num> getSignalLine(BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return getSignalLine(defaultSignalBarCount, signalLineFactory);
    }

    /**
     * @param signalBarCount    signal line period
     * @param signalLineFactory factory that creates a signal-line indicator from
     *                          {@code (this, signalBarCount)}
     * @return custom signal line indicator
     * @since 0.22.2
     */
    public Indicator<Num> getSignalLine(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        validateSignalBarCount(signalBarCount);
        validateSignalLineFactory(signalLineFactory);
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
     * @return histogram using the configured default signal period and default
     *         polarity ({@link MACDHistogramMode#MACD_MINUS_SIGNAL})
     * @since 0.22.2
     */
    public NumericIndicator getHistogram() {
        return getHistogram(defaultSignalBarCount, MACDHistogramMode.MACD_MINUS_SIGNAL);
    }

    /**
     * @param signalBarCount signal line period
     * @return histogram as {@code macdV - signal}
     * @since 0.22.2
     */
    public NumericIndicator getHistogram(int signalBarCount) {
        return getHistogram(signalBarCount, MACDHistogramMode.MACD_MINUS_SIGNAL);
    }

    /**
     * @param histogramMode histogram polarity mode
     * @return histogram using default signal period and provided polarity
     * @since 0.22.2
     */
    public NumericIndicator getHistogram(MACDHistogramMode histogramMode) {
        return getHistogram(defaultSignalBarCount, histogramMode);
    }

    /**
     * @param signalBarCount signal line period
     * @param histogramMode  histogram polarity mode
     * @return histogram for the configured polarity
     * @since 0.22.2
     */
    public NumericIndicator getHistogram(int signalBarCount, MACDHistogramMode histogramMode) {
        validateSignalBarCount(signalBarCount);
        validateHistogramMode(histogramMode);
        Indicator<Num> signalLine = getSignalLine(signalBarCount);
        return histogramMode == MACDHistogramMode.SIGNAL_MINUS_MACD ? NumericIndicator.of(signalLine).minus(this)
                : NumericIndicator.of(this).minus(signalLine);
    }

    /**
     * @param signalLineFactory factory that creates a signal-line indicator
     * @return histogram using default signal period, default polarity, and custom
     *         signal-line factory
     * @since 0.22.2
     */
    public NumericIndicator getHistogram(BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return getHistogram(defaultSignalBarCount, signalLineFactory, MACDHistogramMode.MACD_MINUS_SIGNAL);
    }

    /**
     * @param signalBarCount    signal line period
     * @param signalLineFactory factory that creates a signal-line indicator
     * @return histogram using custom signal-line factory and default polarity
     * @since 0.22.2
     */
    public NumericIndicator getHistogram(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return getHistogram(signalBarCount, signalLineFactory, MACDHistogramMode.MACD_MINUS_SIGNAL);
    }

    /**
     * @param signalBarCount    signal line period
     * @param signalLineFactory factory that creates a signal-line indicator
     * @param histogramMode     histogram polarity mode
     * @return histogram with custom signal-line factory and polarity
     * @since 0.22.2
     */
    public NumericIndicator getHistogram(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory, MACDHistogramMode histogramMode) {
        validateSignalBarCount(signalBarCount);
        validateHistogramMode(histogramMode);
        Indicator<Num> signalLine = getSignalLine(signalBarCount, signalLineFactory);
        return histogramMode == MACDHistogramMode.SIGNAL_MINUS_MACD ? NumericIndicator.of(signalLine).minus(this)
                : NumericIndicator.of(this).minus(signalLine);
    }

    /**
     * @return this MACD-V line
     * @since 0.22.2
     */
    public VolatilityNormalizedMACDIndicator getMacdV() {
        return this;
    }

    /**
     * Bundles MACD, signal, and histogram values for a bar index using default
     * signal period and default histogram polarity.
     *
     * @param index bar index
     * @return line values bundle
     * @since 0.22.2
     */
    public MACDLineValues getLineValues(int index) {
        return getLineValues(index, defaultSignalBarCount, MACDHistogramMode.MACD_MINUS_SIGNAL);
    }

    /**
     * Bundles MACD, signal, and histogram values for a bar index using default
     * histogram polarity.
     *
     * @param index          bar index
     * @param signalBarCount signal period
     * @return line values bundle
     * @since 0.22.2
     */
    public MACDLineValues getLineValues(int index, int signalBarCount) {
        return getLineValues(index, signalBarCount, MACDHistogramMode.MACD_MINUS_SIGNAL);
    }

    /**
     * Bundles MACD, signal, and histogram values for a bar index.
     *
     * @param index          bar index
     * @param signalBarCount signal period
     * @param histogramMode  histogram polarity
     * @return line values bundle
     * @since 0.22.2
     */
    public MACDLineValues getLineValues(int index, int signalBarCount, MACDHistogramMode histogramMode) {
        Num macd = getValue(index);
        Num signal = getSignalLine(signalBarCount).getValue(index);
        Num histogram = getHistogram(signalBarCount, histogramMode).getValue(index);
        return new MACDLineValues(macd, signal, histogram);
    }

    /**
     * Bundles MACD, signal, and histogram values for a bar index with a custom
     * signal-line factory.
     *
     * @param index             bar index
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @param histogramMode     histogram polarity
     * @return line values bundle
     * @since 0.22.2
     */
    public MACDLineValues getLineValues(int index, int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory, MACDHistogramMode histogramMode) {
        Num macd = getValue(index);
        Num signal = getSignalLine(signalBarCount, signalLineFactory).getValue(index);
        Num histogram = getHistogram(signalBarCount, signalLineFactory, histogramMode).getValue(index);
        return new MACDLineValues(macd, signal, histogram);
    }

    /**
     * Classifies the current MACD-V value using the default momentum profile.
     *
     * @param index bar index
     * @return momentum state for {@code getValue(index)}
     * @since 0.22.2
     */
    public MACDVMomentumState getMomentumState(int index) {
        return getMomentumState(index, MACDVMomentumProfile.defaultProfile());
    }

    /**
     * Classifies the current MACD-V value using a custom momentum profile.
     *
     * @param index           bar index
     * @param momentumProfile momentum profile
     * @return momentum state for {@code getValue(index)}
     * @since 0.22.2
     */
    public MACDVMomentumState getMomentumState(int index, MACDVMomentumProfile momentumProfile) {
        if (index < getCountOfUnstableBars()) {
            return MACDVMomentumState.UNDEFINED;
        }
        return MACDVMomentumState.fromMacdV(getValue(index), momentumProfile);
    }

    /**
     * @return momentum-state indicator using the default profile
     * @since 0.22.2
     */
    public MACDVMomentumStateIndicator getMomentumStateIndicator() {
        return new MACDVMomentumStateIndicator(this);
    }

    /**
     * @param momentumProfile momentum profile
     * @return momentum-state indicator using a custom profile
     * @since 0.22.2
     */
    public MACDVMomentumStateIndicator getMomentumStateIndicator(MACDVMomentumProfile momentumProfile) {
        return new MACDVMomentumStateIndicator(this, momentumProfile);
    }

    /**
     * @return rule that is satisfied when MACD-V crosses above the default signal
     *         line
     * @since 0.22.2
     */
    public Rule crossedUpSignal() {
        return crossedUpSignal(defaultSignalBarCount);
    }

    /**
     * @param signalBarCount signal period
     * @return rule that is satisfied when MACD-V crosses above the signal line
     * @since 0.22.2
     */
    public Rule crossedUpSignal(int signalBarCount) {
        return new CrossedUpIndicatorRule(this, getSignalLine(signalBarCount));
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @return rule that is satisfied when MACD-V crosses above a custom signal line
     * @since 0.22.2
     */
    public Rule crossedUpSignal(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return new CrossedUpIndicatorRule(this, getSignalLine(signalBarCount, signalLineFactory));
    }

    /**
     * @return rule that is satisfied when MACD-V crosses below the default signal
     *         line
     * @since 0.22.2
     */
    public Rule crossedDownSignal() {
        return crossedDownSignal(defaultSignalBarCount);
    }

    /**
     * @param signalBarCount signal period
     * @return rule that is satisfied when MACD-V crosses below the signal line
     * @since 0.22.2
     */
    public Rule crossedDownSignal(int signalBarCount) {
        return new CrossedDownIndicatorRule(this, getSignalLine(signalBarCount));
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @return rule that is satisfied when MACD-V crosses below a custom signal line
     * @since 0.22.2
     */
    public Rule crossedDownSignal(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return new CrossedDownIndicatorRule(this, getSignalLine(signalBarCount, signalLineFactory));
    }

    /**
     * @param expectedState expected momentum state
     * @return rule that is satisfied when the default-profile momentum state
     *         matches the expected state
     * @since 0.22.2
     */
    public Rule inMomentumState(MACDVMomentumState expectedState) {
        return inMomentumState(MACDVMomentumProfile.defaultProfile(), expectedState);
    }

    /**
     * @param momentumProfile momentum profile
     * @param expectedState   expected momentum state
     * @return rule that is satisfied when the momentum state matches the expected
     *         state
     * @since 0.22.2
     */
    public Rule inMomentumState(MACDVMomentumProfile momentumProfile, MACDVMomentumState expectedState) {
        return new MomentumStateRule(getMomentumStateIndicator(momentumProfile), expectedState);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }

        Num fastValue = getFastEma().getValue(index);
        Num slowValue = getSlowEma().getValue(index);
        Num atrValue = getAtrIndicator().getValue(index);
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
        return Math.max(spreadUnstableBars, getAtrIndicator().getCountOfUnstableBars());
    }

    private void ensureSubIndicatorsInitialized() {
        getFastEma();
        getSlowEma();
        getAtrIndicator();
    }
}
