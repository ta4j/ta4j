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
package ta4jexamples.charting;

import java.util.Objects;

import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.PriceChannel;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Wraps channel boundary values for chart overlays, extracting the selected
 * line from a {@link PriceChannel} when available.
 *
 * <p>
 * When the wrapped indicator yields {@link Num} values, this indicator forwards
 * them unchanged. When the wrapped indicator yields {@link PriceChannel}
 * values, it extracts the configured boundary (upper, lower, or median).
 * </p>
 *
 * @since 0.22.0
 */
public final class ChannelBoundaryIndicator extends CachedIndicator<Num> {

    private final PriceChannel.Boundary boundary;
    private final Indicator<?> indicator;
    private final String label;

    /**
     * Constructs a channel boundary indicator.
     *
     * @param indicator the indicator providing boundary values or channels
     * @param boundary  the boundary role to extract or label
     * @throws IllegalArgumentException if the indicator is not attached to a bar
     *                                  series
     * @throws NullPointerException     if indicator or boundary is null
     */
    public ChannelBoundaryIndicator(Indicator<?> indicator, PriceChannel.Boundary boundary) {
        this(indicator, boundary, null);
    }

    /**
     * Constructs a channel boundary indicator with a custom label.
     *
     * @param indicator the indicator providing boundary values or channels
     * @param boundary  the boundary role to extract or label
     * @param label     the label to expose in legends (uses a default if blank)
     * @throws IllegalArgumentException if the indicator is not attached to a bar
     *                                  series
     * @throws NullPointerException     if indicator or boundary is null
     */
    public ChannelBoundaryIndicator(Indicator<?> indicator, PriceChannel.Boundary boundary, String label) {
        super(requireSeries(indicator));
        this.indicator = indicator;
        this.boundary = Objects.requireNonNull(boundary, "Boundary cannot be null");
        this.label = resolveLabel(label, indicator, boundary);
    }

    /**
     * Returns the boundary role configured for this indicator.
     *
     * @return the channel boundary role
     */
    public PriceChannel.Boundary boundary() {
        return boundary;
    }

    @Override
    protected Num calculate(int index) {
        Object value = indicator.getValue(index);
        if (value == null) {
            return NaN.NaN;
        }
        if (value instanceof Num num) {
            return num;
        }
        if (value instanceof PriceChannel channel) {
            return switch (boundary) {
            case UPPER -> channel.upper();
            case LOWER -> channel.lower();
            case MEDIAN -> channel.median();
            };
        }
        throw new IllegalStateException("ChannelBoundaryIndicator expects Num or PriceChannel values, but received "
                + value.getClass().getName());
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return label;
    }

    private static BarSeries requireSeries(Indicator<?> indicator) {
        Objects.requireNonNull(indicator, "Indicator cannot be null");
        BarSeries series = indicator.getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("Indicator " + indicator + " is not attached to a BarSeries");
        }
        return series;
    }

    private static String resolveLabel(String label, Indicator<?> indicator, PriceChannel.Boundary boundary) {
        if (label != null && !label.isBlank()) {
            return label;
        }
        String baseLabel = indicator.toString();
        String boundaryLabel = boundary.label();
        if (baseLabel == null || baseLabel.isBlank()) {
            return boundaryLabel;
        }
        return baseLabel + " (" + boundaryLabel + ")";
    }
}
