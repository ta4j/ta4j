/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

/**
 * Anchored volume-weighted average price (AVWAP) indicator.
 * <p>
 * The indicator resets its VWAP window whenever an anchor condition evaluates
 * to {@code true}. Anchors typically correspond to notable events such as
 * earnings reports, major swing highs/lows or policy announcements.
 *
 * @since 0.19
 */
public class AnchoredVWAPIndicator extends AbstractVWAPIndicator {

    private final Indicator<Boolean> anchorSignal;
    private final int defaultAnchorIndex;
    private final transient int baseAnchorIndex;
    private final transient int baseIndex;
    private final transient List<Integer> anchorIndexCache = new ArrayList<>();

    /**
     * Creates an anchored VWAP using typical price and volume from the provided
     * series and a fixed anchor index.
     *
     * @param series      the bar series
     * @param anchorIndex the inclusive index to start accumulation from
     *
     * @since 0.19
     */
    public AnchoredVWAPIndicator(BarSeries series, int anchorIndex) {
        this(new TypicalPriceIndicator(series), new VolumeIndicator(series), anchorIndex);
    }

    /**
     * Creates an anchored VWAP using custom price and volume indicators and a fixed
     * anchor index.
     *
     * @param priceIndicator  the price indicator
     * @param volumeIndicator the volume indicator
     * @param anchorIndex     the inclusive index to start accumulation from
     *
     * @since 0.19
     */
    public AnchoredVWAPIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator, int anchorIndex) {
        this(priceIndicator, volumeIndicator, null, anchorIndex);
    }

    /**
     * Creates an anchored VWAP whose anchor resets whenever the
     * {@code anchorSignal} is {@code true}.
     *
     * @param series       the bar series
     * @param anchorSignal indicator returning {@code true} on anchor bars
     *
     * @since 0.19
     */
    public AnchoredVWAPIndicator(BarSeries series, Indicator<Boolean> anchorSignal) {
        this(new TypicalPriceIndicator(series), new VolumeIndicator(series), anchorSignal, series.getBeginIndex());
    }

    /**
     * Creates an anchored VWAP whose anchor resets whenever the
     * {@code anchorSignal} evaluates to {@code true}. Anchoring begins at
     * {@code defaultAnchorIndex} if no anchor has fired yet.
     *
     * @param priceIndicator     the price indicator
     * @param volumeIndicator    the volume indicator
     * @param anchorSignal       indicator returning {@code true} on anchor bars
     * @param defaultAnchorIndex the default anchor index if the signal has not
     *                           fired yet
     *
     * @since 0.19
     */
    public AnchoredVWAPIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator,
            Indicator<Boolean> anchorSignal, int defaultAnchorIndex) {
        super(priceIndicator, volumeIndicator);
        if (anchorSignal != null) {
            IndicatorUtils.requireSameSeries(priceIndicator, anchorSignal);
        }
        this.anchorSignal = anchorSignal;
        this.defaultAnchorIndex = defaultAnchorIndex;
        this.baseIndex = getBarSeries().getBeginIndex();
        int clampedDefault = Math.max(defaultAnchorIndex, baseIndex);
        this.baseAnchorIndex = clampedDefault;
    }

    /**
     * Resolves window start index.
     */
    @Override
    protected int resolveWindowStartIndex(int index) {
        return resolveAnchorIndex(index);
    }

    /**
     * Returns the currently active anchor index for the provided bar.
     *
     * @param index the bar index
     * @return anchor index for the bar
     *
     * @since 0.19
     */
    public int getAnchorIndex(int index) {
        return resolveAnchorIndex(index);
    }

    /**
     * Resolves anchor index.
     */
    private int resolveAnchorIndex(int index) {
        synchronized (anchorIndexCache) {
            ensureAnchorCacheLocked(index);
            int offset = index - baseIndex;
            if (offset < 0) {
                return baseAnchorIndex;
            }
            return anchorIndexCache.get(offset);
        }
    }

    /**
     * Ensures anchor cache locked.
     */
    private void ensureAnchorCacheLocked(int index) {
        int offset = index - baseIndex;
        if (offset < 0) {
            return;
        }
        int currentSize = anchorIndexCache.size();
        if (offset < currentSize) {
            return;
        }

        int lastAnchor = currentSize == 0 ? baseAnchorIndex : anchorIndexCache.get(currentSize - 1);
        for (int i = currentSize; i <= offset; i++) {
            int seriesIndex = baseIndex + i;
            int anchor = lastAnchor;
            if (anchorSignal != null) {
                Boolean signal = anchorSignal.getValue(seriesIndex);
                if (Boolean.TRUE.equals(signal)) {
                    anchor = seriesIndex;
                }
            }
            anchor = Math.max(anchor, baseAnchorIndex);
            anchorIndexCache.add(anchor);
            lastAnchor = anchor;
        }
    }

    /**
     * Returns the number of unstable bars required before values become reliable.
     */
    @Override
    public int getCountOfUnstableBars() {
        int unstableBars = Math.max(priceIndicator.getCountOfUnstableBars(), volumeIndicator.getCountOfUnstableBars());
        if (anchorSignal != null) {
            unstableBars = Math.max(unstableBars, anchorSignal.getCountOfUnstableBars());
        }
        return unstableBars;
    }

    /**
     * Implements to descriptor.
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("defaultAnchorIndex", defaultAnchorIndex);
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder()
                .withType(getClass().getSimpleName())
                .withParameters(parameters);
        builder.addComponent(priceIndicator.toDescriptor());
        builder.addComponent(volumeIndicator.toDescriptor());
        if (anchorSignal != null) {
            builder.addComponent(anchorSignal.toDescriptor());
        }
        return builder.build();
    }

    /**
     * Returns the JSON representation of this component.
     */
    @Override
    public String toJson() {
        return ComponentSerialization.toJson(toDescriptor());
    }

    /**
     * Returns a string representation of this component.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " defaultAnchorIndex: " + defaultAnchorIndex;
    }
}
