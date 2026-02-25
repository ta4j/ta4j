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
import org.ta4j.core.indicators.averages.VWMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.MomentumStateRule;

/**
 * Moving average convergence divergence volume (MACD-V) indicator.
 *
 * <p>
 * This implementation is a volume/ATR-weighted MACD variant. It computes short
 * and long variable-weighted EMAs (VWEMAs), where each weight is
 * {@code volume / ATR(period)}, and returns their difference.
 *
 * <p>
 * Unlike volatility-normalized MACD-V formulations that divide the EMA spread
 * by ATR, this indicator uses ATR only inside the weighting term.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/macd-v">
 *      https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/macd-v
 *      </a>
 * @since 0.19
 */
public class MACDVIndicator extends CachedIndicator<Num> {

    private static final int DEFAULT_SHORT_BAR_COUNT = 12;
    private static final int DEFAULT_LONG_BAR_COUNT = 26;
    private static final int DEFAULT_SIGNAL_BAR_COUNT = 9;

    private final Indicator<Num> priceIndicator;
    private final int shortBarCount;
    private final int longBarCount;
    private final int defaultSignalBarCount;

    private transient VWMAIndicator shortTermVwema;
    private transient VWMAIndicator longTermVwema;
    private transient ATRIndicator shortAtrIndicator;
    private transient ATRIndicator longAtrIndicator;

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code shortBarCount} = 12
     * <li>{@code longBarCount} = 26
     * <li>{@code signalBarCount} = 9
     * </ul>
     *
     * @param series the bar series {@link BarSeries}
     * @since 0.19
     */
    public MACDVIndicator(BarSeries series) {
        this(new ClosePriceIndicator(series));
    }

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code shortBarCount} = 12
     * <li>{@code longBarCount} = 26
     * <li>{@code signalBarCount} = 9
     * </ul>
     *
     * @param priceIndicator the price-based {@link Indicator}
     * @since 0.19
     */
    public MACDVIndicator(Indicator<Num> priceIndicator) {
        this(priceIndicator, DEFAULT_SHORT_BAR_COUNT, DEFAULT_LONG_BAR_COUNT, DEFAULT_SIGNAL_BAR_COUNT);
    }

    /**
     * Constructor.
     *
     * @param series        the bar series {@link BarSeries}
     * @param shortBarCount the short time frame (normally 12)
     * @param longBarCount  the long time frame (normally 26)
     * @since 0.19
     */
    public MACDVIndicator(BarSeries series, int shortBarCount, int longBarCount) {
        this(new ClosePriceIndicator(series), shortBarCount, longBarCount, DEFAULT_SIGNAL_BAR_COUNT);
    }

    /**
     * Constructor.
     *
     * @param series         the bar series {@link BarSeries}
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @param signalBarCount the default signal time frame (normally 9)
     * @since 0.22.3
     */
    public MACDVIndicator(BarSeries series, int shortBarCount, int longBarCount, int signalBarCount) {
        this(new ClosePriceIndicator(series), shortBarCount, longBarCount, signalBarCount);
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price-based {@link Indicator}
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @since 0.19
     */
    public MACDVIndicator(Indicator<Num> priceIndicator, int shortBarCount, int longBarCount) {
        this(priceIndicator, shortBarCount, longBarCount, DEFAULT_SIGNAL_BAR_COUNT);
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price-based {@link Indicator}
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @param signalBarCount the default signal time frame (normally 9)
     * @since 0.22.3
     */
    public MACDVIndicator(Indicator<Num> priceIndicator, int shortBarCount, int longBarCount, int signalBarCount) {
        super(priceIndicator);
        validateBarCounts(shortBarCount, longBarCount);
        validateSignalBarCount(signalBarCount);
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
        this.shortBarCount = shortBarCount;
        this.longBarCount = longBarCount;
        this.defaultSignalBarCount = signalBarCount;
        ensureSubIndicatorsInitialized();
    }

    private static void validateBarCounts(int shortBarCount, int longBarCount) {
        if (shortBarCount < 1) {
            throw new IllegalArgumentException("Short term period count must be greater than 0");
        }
        if (longBarCount < 1) {
            throw new IllegalArgumentException("Long term period count must be greater than 0");
        }
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }
    }

    private static void validateSignalBarCount(int signalBarCount) {
        if (signalBarCount < 1) {
            throw new IllegalArgumentException("Signal bar count must be greater than 0");
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
     * @since 0.22.3
     */
    public Indicator<Num> getPriceIndicator() {
        return priceIndicator;
    }

    /**
     * @return short period configured for this indicator
     * @since 0.22.3
     */
    public int getShortBarCount() {
        return shortBarCount;
    }

    /**
     * @return long period configured for this indicator
     * @since 0.22.3
     */
    public int getLongBarCount() {
        return longBarCount;
    }

    /**
     * @return default signal period configured for this indicator
     * @since 0.22.3
     */
    public int getDefaultSignalBarCount() {
        return defaultSignalBarCount;
    }

    /**
     * @return short-term volume-weighted EMA indicator
     * @since 0.19
     */
    public VWMAIndicator getShortTermVolumeWeightedEma() {
        return getShortTermVwema();
    }

    /**
     * @return long-term volume-weighted EMA indicator
     * @since 0.19
     */
    public VWMAIndicator getLongTermVolumeWeightedEma() {
        return getLongTermVwema();
    }

    /**
     * @return short-term ATR indicator used by the weighting chain
     * @since 0.22.3
     */
    public ATRIndicator getShortAtrIndicator() {
        ensureSubIndicatorsInitialized();
        return shortAtrIndicator;
    }

    /**
     * @return long-term ATR indicator used by the weighting chain
     * @since 0.22.3
     */
    public ATRIndicator getLongAtrIndicator() {
        ensureSubIndicatorsInitialized();
        return longAtrIndicator;
    }

    /**
     * @return signal line for this MACD-V indicator using the configured default
     *         signal period
     * @since 0.22.3
     */
    public EMAIndicator getSignalLine() {
        return getSignalLine(defaultSignalBarCount);
    }

    /**
     * @param signalBarCount signal period
     * @return signal line for this MACD-V indicator
     * @since 0.19
     */
    public EMAIndicator getSignalLine(int signalBarCount) {
        validateSignalBarCount(signalBarCount);
        return new EMAIndicator(this, signalBarCount);
    }

    /**
     * @param signalLineFactory signal-line factory
     * @return signal line using default signal period and custom factory
     * @since 0.22.3
     */
    public Indicator<Num> getSignalLine(BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return getSignalLine(defaultSignalBarCount, signalLineFactory);
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @return signal line using custom factory
     * @since 0.22.3
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
     * @return histogram using default signal period and default polarity
     *         ({@link MACDHistogramMode#MACD_MINUS_SIGNAL})
     * @since 0.22.3
     */
    public NumericIndicator getHistogram() {
        return getHistogram(defaultSignalBarCount, MACDHistogramMode.MACD_MINUS_SIGNAL);
    }

    /**
     * @param signalBarCount signal period
     * @return histogram using default polarity
     *         ({@link MACDHistogramMode#MACD_MINUS_SIGNAL})
     * @since 0.19
     */
    public NumericIndicator getHistogram(int signalBarCount) {
        return getHistogram(signalBarCount, MACDHistogramMode.MACD_MINUS_SIGNAL);
    }

    /**
     * @param histogramMode histogram polarity mode
     * @return histogram using default signal period and provided polarity
     * @since 0.22.3
     */
    public NumericIndicator getHistogram(MACDHistogramMode histogramMode) {
        return getHistogram(defaultSignalBarCount, histogramMode);
    }

    /**
     * @param signalBarCount signal period
     * @param histogramMode  histogram polarity mode
     * @return histogram for the configured polarity
     * @since 0.22.3
     */
    public NumericIndicator getHistogram(int signalBarCount, MACDHistogramMode histogramMode) {
        validateSignalBarCount(signalBarCount);
        validateHistogramMode(histogramMode);
        Indicator<Num> signalLine = getSignalLine(signalBarCount);
        return histogramMode == MACDHistogramMode.SIGNAL_MINUS_MACD ? NumericIndicator.of(signalLine).minus(this)
                : NumericIndicator.of(this).minus(signalLine);
    }

    /**
     * @param signalLineFactory signal-line factory
     * @return histogram using default signal period, default polarity, and custom
     *         signal-line factory
     * @since 0.22.3
     */
    public NumericIndicator getHistogram(BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return getHistogram(defaultSignalBarCount, signalLineFactory, MACDHistogramMode.MACD_MINUS_SIGNAL);
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @return histogram using default polarity and custom signal-line factory
     * @since 0.22.3
     */
    public NumericIndicator getHistogram(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return getHistogram(signalBarCount, signalLineFactory, MACDHistogramMode.MACD_MINUS_SIGNAL);
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @param histogramMode     histogram polarity mode
     * @return histogram using custom signal-line factory and polarity
     * @since 0.22.3
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
     * @return this MACD line
     * @since 0.22.3
     */
    public MACDVIndicator getMacd() {
        return this;
    }

    /**
     * Bundles MACD, signal, and histogram values for a bar index using default
     * signal period and default histogram polarity.
     *
     * @param index bar index
     * @return line values bundle
     * @since 0.22.3
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
     * @since 0.22.3
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
     * @since 0.22.3
     */
    public MACDLineValues getLineValues(int index, int signalBarCount, MACDHistogramMode histogramMode) {
        Num macd = getValue(index);
        Num signal = getSignalLine(signalBarCount).getValue(index);
        Num histogram = getHistogram(signalBarCount, histogramMode).getValue(index);
        return new MACDLineValues(macd, signal, histogram);
    }

    /**
     * @param index             bar index
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @param histogramMode     histogram polarity
     * @return line values bundle
     * @since 0.22.3
     */
    public MACDLineValues getLineValues(int index, int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory, MACDHistogramMode histogramMode) {
        validateHistogramMode(histogramMode);
        Indicator<Num> signalLine = getSignalLine(signalBarCount, signalLineFactory);
        Num macd = getValue(index);
        Num signal = signalLine.getValue(index);
        Num histogram = histogramMode.compute(macd, signal);
        return new MACDLineValues(macd, signal, histogram);
    }

    /**
     * Classifies the current value using the default momentum profile.
     *
     * @param index bar index
     * @return momentum state
     * @since 0.22.3
     */
    public MACDVMomentumState getMomentumState(int index) {
        return getMomentumState(index, MACDVMomentumProfile.defaultProfile());
    }

    /**
     * Classifies the current value using a custom momentum profile.
     *
     * @param index           bar index
     * @param momentumProfile momentum profile
     * @return momentum state
     * @since 0.22.3
     */
    public MACDVMomentumState getMomentumState(int index, MACDVMomentumProfile momentumProfile) {
        if (index < getCountOfUnstableBars()) {
            return MACDVMomentumState.UNDEFINED;
        }
        return MACDVMomentumState.fromMacdV(getValue(index), momentumProfile);
    }

    /**
     * @return momentum-state indicator using default profile
     * @since 0.22.3
     */
    public MACDVMomentumStateIndicator getMomentumStateIndicator() {
        return new MACDVMomentumStateIndicator(this);
    }

    /**
     * @param momentumProfile momentum profile
     * @return momentum-state indicator using custom profile
     * @since 0.22.3
     */
    public MACDVMomentumStateIndicator getMomentumStateIndicator(MACDVMomentumProfile momentumProfile) {
        return new MACDVMomentumStateIndicator(this, momentumProfile);
    }

    /**
     * @return rule that is satisfied when MACD crosses above the default signal
     *         line
     * @since 0.22.3
     */
    public Rule crossedUpSignal() {
        return crossedUpSignal(defaultSignalBarCount);
    }

    /**
     * @param signalBarCount signal period
     * @return rule that is satisfied when MACD crosses above the signal line
     * @since 0.22.3
     */
    public Rule crossedUpSignal(int signalBarCount) {
        return new CrossedUpIndicatorRule(this, getSignalLine(signalBarCount));
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @return rule that is satisfied when MACD crosses above a custom signal line
     * @since 0.22.3
     */
    public Rule crossedUpSignal(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return new CrossedUpIndicatorRule(this, getSignalLine(signalBarCount, signalLineFactory));
    }

    /**
     * @return rule that is satisfied when MACD crosses below the default signal
     *         line
     * @since 0.22.3
     */
    public Rule crossedDownSignal() {
        return crossedDownSignal(defaultSignalBarCount);
    }

    /**
     * @param signalBarCount signal period
     * @return rule that is satisfied when MACD crosses below the signal line
     * @since 0.22.3
     */
    public Rule crossedDownSignal(int signalBarCount) {
        return new CrossedDownIndicatorRule(this, getSignalLine(signalBarCount));
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @return rule that is satisfied when MACD crosses below a custom signal line
     * @since 0.22.3
     */
    public Rule crossedDownSignal(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return new CrossedDownIndicatorRule(this, getSignalLine(signalBarCount, signalLineFactory));
    }

    /**
     * @param expectedState expected momentum state
     * @return rule that is satisfied when the default-profile momentum state
     *         matches the expected state
     * @since 0.22.3
     */
    public Rule inMomentumState(MACDVMomentumState expectedState) {
        return inMomentumState(MACDVMomentumProfile.defaultProfile(), expectedState);
    }

    /**
     * @param momentumProfile momentum profile
     * @param expectedState   expected momentum state
     * @return rule that is satisfied when the momentum state matches the expected
     *         state
     * @since 0.22.3
     */
    public Rule inMomentumState(MACDVMomentumProfile momentumProfile, MACDVMomentumState expectedState) {
        return new MomentumStateRule(getMomentumStateIndicator(momentumProfile), expectedState);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num shortValue = getShortTermVwema().getValue(index);
        Num longValue = getLongTermVwema().getValue(index);
        if (Num.isNaNOrNull(shortValue) || Num.isNaNOrNull(longValue)) {
            return NaN.NaN;
        }
        Num macdValue = shortValue.minus(longValue);
        if (Num.isNaNOrNull(macdValue)) {
            return NaN.NaN;
        }
        return macdValue;
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(getShortTermVwema().getCountOfUnstableBars(), getLongTermVwema().getCountOfUnstableBars());
    }

    private VWMAIndicator getShortTermVwema() {
        ensureSubIndicatorsInitialized();
        return shortTermVwema;
    }

    private VWMAIndicator getLongTermVwema() {
        ensureSubIndicatorsInitialized();
        return longTermVwema;
    }

    private void ensureSubIndicatorsInitialized() {
        if (shortTermVwema != null && longTermVwema != null && shortAtrIndicator != null && longAtrIndicator != null) {
            return;
        }

        BarSeries series = priceIndicator.getBarSeries();
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);

        shortAtrIndicator = new ATRIndicator(series, shortBarCount);
        Indicator<Num> shortVolumeWeights = NumericIndicator.of(volumeIndicator).dividedBy(shortAtrIndicator);
        shortTermVwema = new VWMAIndicator(priceIndicator, shortVolumeWeights, shortBarCount, EMAIndicator::new);

        if (shortBarCount == longBarCount) {
            longAtrIndicator = shortAtrIndicator;
            longTermVwema = new VWMAIndicator(priceIndicator, shortVolumeWeights, longBarCount, EMAIndicator::new);
            return;
        }

        longAtrIndicator = new ATRIndicator(series, longBarCount);
        Indicator<Num> longVolumeWeights = NumericIndicator.of(volumeIndicator).dividedBy(longAtrIndicator);
        longTermVwema = new VWMAIndicator(priceIndicator, longVolumeWeights, longBarCount, EMAIndicator::new);
    }
}
