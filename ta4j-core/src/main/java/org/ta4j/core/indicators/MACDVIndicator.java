/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.function.BiFunction;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.VWMAIndicator;
import org.ta4j.core.indicators.macd.MACDHistogramMode;
import org.ta4j.core.indicators.macd.MACDLineValues;
import org.ta4j.core.indicators.macd.MACDVMomentumProfile;
import org.ta4j.core.indicators.macd.MACDVMomentumState;
import org.ta4j.core.indicators.macd.MACDVMomentumStateIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.DeprecationNotifier;

/**
 * @deprecated use {@link org.ta4j.core.indicators.macd.MACDVIndicator}. This
 *             compatibility shim is scheduled for removal in 0.24.0.
 * @since 0.19
 */
@Deprecated(since = "0.22.3", forRemoval = true)
public class MACDVIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> priceIndicator;
    private final int shortBarCount;
    private final int longBarCount;
    private final int defaultSignalBarCount;
    private final transient org.ta4j.core.indicators.macd.MACDVIndicator delegate;

    {
        DeprecationNotifier.warnOnce(MACDVIndicator.class, "org.ta4j.core.indicators.macd.MACDVIndicator", "0.24.0");
    }

    /**
     * Constructor with defaults.
     *
     * @param series the bar series
     * @since 0.19
     */
    public MACDVIndicator(BarSeries series) {
        this(new org.ta4j.core.indicators.macd.MACDVIndicator(series));
    }

    /**
     * Constructor with defaults.
     *
     * @param priceIndicator the price indicator
     * @since 0.19
     */
    public MACDVIndicator(Indicator<Num> priceIndicator) {
        this(new org.ta4j.core.indicators.macd.MACDVIndicator(priceIndicator));
    }

    /**
     * Constructor.
     *
     * @param series        the bar series
     * @param shortBarCount the short time frame (normally 12)
     * @param longBarCount  the long time frame (normally 26)
     * @since 0.19
     */
    public MACDVIndicator(BarSeries series, int shortBarCount, int longBarCount) {
        this(new org.ta4j.core.indicators.macd.MACDVIndicator(series, shortBarCount, longBarCount));
    }

    /**
     * Constructor.
     *
     * @param series         the bar series
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @param signalBarCount the default signal time frame (normally 9)
     * @since 0.22.3
     */
    public MACDVIndicator(BarSeries series, int shortBarCount, int longBarCount, int signalBarCount) {
        this(new org.ta4j.core.indicators.macd.MACDVIndicator(series, shortBarCount, longBarCount, signalBarCount));
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @since 0.19
     */
    public MACDVIndicator(Indicator<Num> priceIndicator, int shortBarCount, int longBarCount) {
        this(new org.ta4j.core.indicators.macd.MACDVIndicator(priceIndicator, shortBarCount, longBarCount));
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @param signalBarCount the default signal time frame (normally 9)
     * @since 0.22.3
     */
    public MACDVIndicator(Indicator<Num> priceIndicator, int shortBarCount, int longBarCount, int signalBarCount) {
        this(new org.ta4j.core.indicators.macd.MACDVIndicator(priceIndicator, shortBarCount, longBarCount,
                signalBarCount));
    }

    private MACDVIndicator(org.ta4j.core.indicators.macd.MACDVIndicator delegate) {
        super(delegate.getPriceIndicator());
        this.delegate = delegate;
        this.priceIndicator = delegate.getPriceIndicator();
        this.shortBarCount = delegate.getShortBarCount();
        this.longBarCount = delegate.getLongBarCount();
        this.defaultSignalBarCount = delegate.getDefaultSignalBarCount();
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
        return delegate.getShortTermVolumeWeightedEma();
    }

    /**
     * @return long-term volume-weighted EMA indicator
     * @since 0.19
     */
    public VWMAIndicator getLongTermVolumeWeightedEma() {
        return delegate.getLongTermVolumeWeightedEma();
    }

    /**
     * @return short-term ATR indicator used by the weighting chain
     * @since 0.22.3
     */
    public ATRIndicator getShortAtrIndicator() {
        return delegate.getShortAtrIndicator();
    }

    /**
     * @return long-term ATR indicator used by the weighting chain
     * @since 0.22.3
     */
    public ATRIndicator getLongAtrIndicator() {
        return delegate.getLongAtrIndicator();
    }

    /**
     * @return signal line for this MACD-V indicator using the configured default
     *         signal period
     * @since 0.22.3
     */
    public EMAIndicator getSignalLine() {
        return delegate.getSignalLine();
    }

    /**
     * @param signalBarCount signal period
     * @return signal line for this MACD-V indicator
     * @since 0.19
     */
    public EMAIndicator getSignalLine(int signalBarCount) {
        return delegate.getSignalLine(signalBarCount);
    }

    /**
     * @param signalLineFactory signal-line factory
     * @return signal line using default signal period and custom factory
     * @since 0.22.3
     */
    public Indicator<Num> getSignalLine(BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return delegate.getSignalLine(signalLineFactory);
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @return signal line using custom factory
     * @since 0.22.3
     */
    public Indicator<Num> getSignalLine(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return delegate.getSignalLine(signalBarCount, signalLineFactory);
    }

    /**
     * @return histogram using default signal period and default polarity
     *         ({@link MACDHistogramMode#MACD_MINUS_SIGNAL})
     * @since 0.22.3
     */
    public NumericIndicator getHistogram() {
        return delegate.getHistogram();
    }

    /**
     * @param signalBarCount signal period
     * @return histogram using default polarity
     *         ({@link MACDHistogramMode#MACD_MINUS_SIGNAL})
     * @since 0.19
     */
    public NumericIndicator getHistogram(int signalBarCount) {
        return delegate.getHistogram(signalBarCount);
    }

    /**
     * @param histogramMode histogram polarity mode
     * @return histogram using default signal period and provided polarity
     * @since 0.22.3
     */
    public NumericIndicator getHistogram(MACDHistogramMode histogramMode) {
        return delegate.getHistogram(histogramMode);
    }

    /**
     * @param signalBarCount signal period
     * @param histogramMode  histogram polarity mode
     * @return histogram for the configured polarity
     * @since 0.22.3
     */
    public NumericIndicator getHistogram(int signalBarCount, MACDHistogramMode histogramMode) {
        return delegate.getHistogram(signalBarCount, histogramMode);
    }

    /**
     * @param signalLineFactory signal-line factory
     * @return histogram using default signal period, default polarity, and custom
     *         signal-line factory
     * @since 0.22.3
     */
    public NumericIndicator getHistogram(BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return delegate.getHistogram(signalLineFactory);
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @return histogram using default polarity and custom signal-line factory
     * @since 0.22.3
     */
    public NumericIndicator getHistogram(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return delegate.getHistogram(signalBarCount, signalLineFactory);
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
        return delegate.getHistogram(signalBarCount, signalLineFactory, histogramMode);
    }

    /**
     * @return moved MACD line implementation with this shim's constructor inputs
     * @since 0.22.3
     */
    public org.ta4j.core.indicators.macd.MACDVIndicator getMacd() {
        return new org.ta4j.core.indicators.macd.MACDVIndicator(priceIndicator, shortBarCount, longBarCount,
                defaultSignalBarCount);
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
        return delegate.getLineValues(index);
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
        return delegate.getLineValues(index, signalBarCount);
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
        return delegate.getLineValues(index, signalBarCount, histogramMode);
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
        return delegate.getLineValues(index, signalBarCount, signalLineFactory, histogramMode);
    }

    /**
     * Classifies the current value using the default momentum profile.
     *
     * @param index bar index
     * @return momentum state
     * @since 0.22.3
     */
    public MACDVMomentumState getMomentumState(int index) {
        return delegate.getMomentumState(index);
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
        return delegate.getMomentumState(index, momentumProfile);
    }

    /**
     * @return momentum-state indicator using default profile
     * @since 0.22.3
     */
    public MACDVMomentumStateIndicator getMomentumStateIndicator() {
        return delegate.getMomentumStateIndicator();
    }

    /**
     * @param momentumProfile momentum profile
     * @return momentum-state indicator using custom profile
     * @since 0.22.3
     */
    public MACDVMomentumStateIndicator getMomentumStateIndicator(MACDVMomentumProfile momentumProfile) {
        return delegate.getMomentumStateIndicator(momentumProfile);
    }

    /**
     * @return rule that is satisfied when MACD crosses above the default signal
     *         line
     * @since 0.22.3
     */
    public Rule crossedUpSignal() {
        return delegate.crossedUpSignal();
    }

    /**
     * @param signalBarCount signal period
     * @return rule that is satisfied when MACD crosses above the signal line
     * @since 0.22.3
     */
    public Rule crossedUpSignal(int signalBarCount) {
        return delegate.crossedUpSignal(signalBarCount);
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @return rule that is satisfied when MACD crosses above a custom signal line
     * @since 0.22.3
     */
    public Rule crossedUpSignal(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return delegate.crossedUpSignal(signalBarCount, signalLineFactory);
    }

    /**
     * @return rule that is satisfied when MACD crosses below the default signal
     *         line
     * @since 0.22.3
     */
    public Rule crossedDownSignal() {
        return delegate.crossedDownSignal();
    }

    /**
     * @param signalBarCount signal period
     * @return rule that is satisfied when MACD crosses below the signal line
     * @since 0.22.3
     */
    public Rule crossedDownSignal(int signalBarCount) {
        return delegate.crossedDownSignal(signalBarCount);
    }

    /**
     * @param signalBarCount    signal period
     * @param signalLineFactory signal-line factory
     * @return rule that is satisfied when MACD crosses below a custom signal line
     * @since 0.22.3
     */
    public Rule crossedDownSignal(int signalBarCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> signalLineFactory) {
        return delegate.crossedDownSignal(signalBarCount, signalLineFactory);
    }

    /**
     * @param expectedState expected momentum state
     * @return rule that is satisfied when the default-profile momentum state
     *         matches the expected state
     * @since 0.22.3
     */
    public Rule inMomentumState(MACDVMomentumState expectedState) {
        return delegate.inMomentumState(expectedState);
    }

    /**
     * @param momentumProfile momentum profile
     * @param expectedState   expected momentum state
     * @return rule that is satisfied when the momentum state matches the expected
     *         state
     * @since 0.22.3
     */
    public Rule inMomentumState(MACDVMomentumProfile momentumProfile, MACDVMomentumState expectedState) {
        return delegate.inMomentumState(momentumProfile, expectedState);
    }

    @Override
    protected Num calculate(int index) {
        return delegate.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return delegate.getCountOfUnstableBars();
    }
}
