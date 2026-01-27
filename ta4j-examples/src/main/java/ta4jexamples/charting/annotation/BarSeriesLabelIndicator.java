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
package ta4jexamples.charting.annotation;

import static org.ta4j.core.num.NaN.NaN;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator overlay that exposes sparse bar-index labels for chart annotations.
 *
 * <p>
 * The primary {@link #getValue(int)} output returns the label Y-value
 * (typically a price) at labeled indices and {@code NaN} elsewhere. Chart
 * renderers can additionally consume {@link #labels()} to attach text
 * annotations at the labeled indices.
 */
public class BarSeriesLabelIndicator extends CachedIndicator<Num> {

    public enum LabelPlacement {
        ABOVE, BELOW, CENTER
    }

    public record BarLabel(int barIndex, Num yValue, String text, LabelPlacement placement) {

        public BarLabel {
            if (barIndex < 0) {
                throw new IllegalArgumentException("barIndex must be non-negative");
            }
            Objects.requireNonNull(yValue, "yValue");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(placement, "placement");
        }
    }

    private final Map<Integer, BarLabel> labelsByIndex;
    private final List<BarLabel> labels;

    public BarSeriesLabelIndicator(final BarSeries series, final List<BarLabel> labels) {
        super(Objects.requireNonNull(series, "series"));
        Objects.requireNonNull(labels, "labels");
        this.labels = labels.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(BarLabel::barIndex))
                .collect(Collectors.toUnmodifiableList());
        this.labelsByIndex = this.labels.stream()
                .collect(Collectors.toUnmodifiableMap(BarLabel::barIndex, label -> label, (left, right) -> {
                    // Keep the last (right) value for duplicate indices
                    return right;
                }));
    }

    @Override
    protected Num calculate(final int index) {
        final BarLabel label = labelsByIndex.get(index);
        return label != null ? label.yValue() : NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    /**
     * @return ordered, immutable label list
     */
    public List<BarLabel> labels() {
        return List.copyOf(this.labels);
    }
}
