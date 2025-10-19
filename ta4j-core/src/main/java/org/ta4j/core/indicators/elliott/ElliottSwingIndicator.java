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
package org.ta4j.core.indicators.elliott;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Detects Elliott swings (alternating pivots) using a symmetric lookback/forward
 * window.
 *
 * @since 0.19
 */
public class ElliottSwingIndicator extends CachedIndicator<List<ElliottSwing>> {

    private final int lookbackLength;
    private final int lookforwardLength;
    private final ElliottDegree degree;

    /**
     * Builds a new indicator with identical lookback/forward lengths.
     *
     * @param series   source bar series
     * @param window   number of bars to inspect before and after a pivot
     * @param degree   swing degree metadata
     * @since 0.19
     */
    public ElliottSwingIndicator(final BarSeries series, final int window, final ElliottDegree degree) {
        this(series, window, window, degree);
    }

    /**
     * Builds a new indicator using dedicated lookback/forward lengths.
     *
     * @param series          source bar series
     * @param lookbackLength  bars inspected before a pivot candidate
     * @param lookforwardLength bars inspected after a pivot candidate
     * @param degree          swing degree metadata
     * @since 0.19
     */
    public ElliottSwingIndicator(final BarSeries series, final int lookbackLength, final int lookforwardLength,
            final ElliottDegree degree) {
        super(series);
        if (lookbackLength < 1 || lookforwardLength < 1) {
            throw new IllegalArgumentException("Window lengths must be positive");
        }
        this.lookbackLength = lookbackLength;
        this.lookforwardLength = lookforwardLength;
        this.degree = Objects.requireNonNull(degree, "degree");
    }

    /**
     * Convenience constructor using another indicator as data source.
     *
     * @param indicator indicator providing the price series
     * @param window    number of bars to inspect before and after a pivot
     * @param degree    swing degree metadata
     * @since 0.19
     */
    public ElliottSwingIndicator(final Indicator<?> indicator, final int window, final ElliottDegree degree) {
        this(indicator.getBarSeries(), window, degree);
    }

    @Override
    public int getCountOfUnstableBars() {
        return lookbackLength + lookforwardLength;
    }

    @Override
    protected List<ElliottSwing> calculate(final int index) {
        final BarSeries series = getBarSeries();
        if (series == null) {
            return List.of();
        }
        if (index < series.getBeginIndex()) {
            return List.of();
        }

        final int firstPivotIndex = Math.max(series.getBeginIndex() + lookbackLength, 0);
        if (index < firstPivotIndex + lookforwardLength) {
            return List.of();
        }

        final List<Pivot> pivots = new ArrayList<>();
        for (int pivotIndex = firstPivotIndex; pivotIndex <= index - lookforwardLength; pivotIndex++) {
            final Num pivotPrice = series.getBar(pivotIndex).getClosePrice();
            if (pivotPrice == null || pivotPrice.isNaN()) {
                continue;
            }
            if (!hasCompleteWindow(series, pivotIndex, index)) {
                continue;
            }

            final PivotClassification classification = classifyPivot(series, pivotIndex, pivotPrice);
            if (classification == PivotClassification.NONE) {
                continue;
            }
            final Pivot pivot = new Pivot(pivotIndex, pivotPrice, classification);
            absorbPivot(pivots, pivot);
        }

        if (pivots.size() < 2) {
            return List.of();
        }

        final List<ElliottSwing> swings = new ArrayList<>(pivots.size() - 1);
        for (int i = 1; i < pivots.size(); i++) {
            final Pivot previous = pivots.get(i - 1);
            final Pivot current = pivots.get(i);
            swings.add(new ElliottSwing(previous.index(), current.index(), previous.price(), current.price(), degree));
        }
        return Collections.unmodifiableList(swings);
    }

    private boolean hasCompleteWindow(final BarSeries series, final int pivotIndex, final int currentIndex) {
        if (pivotIndex - lookbackLength < series.getBeginIndex()) {
            return false;
        }
        if (pivotIndex + lookforwardLength > currentIndex) {
            return false;
        }
        return true;
    }

    private PivotClassification classifyPivot(final BarSeries series, final int pivotIndex, final Num pivotPrice) {
        boolean higherThanAll = true;
        boolean strictlyHigher = false;
        boolean lowerThanAll = true;
        boolean strictlyLower = false;

        for (int i = pivotIndex - lookbackLength; i <= pivotIndex + lookforwardLength; i++) {
            if (i == pivotIndex) {
                continue;
            }
            final Num neighbour = series.getBar(i).getClosePrice();
            if (neighbour == null || neighbour.isNaN()) {
                return PivotClassification.NONE;
            }
            if (neighbour.isGreaterThan(pivotPrice)) {
                higherThanAll = false;
            }
            if (pivotPrice.isGreaterThan(neighbour)) {
                strictlyHigher = true;
            }
            if (neighbour.isLessThan(pivotPrice)) {
                lowerThanAll = false;
            }
            if (pivotPrice.isLessThan(neighbour)) {
                strictlyLower = true;
            }
            if (!higherThanAll && !lowerThanAll) {
                break;
            }
        }

        if (higherThanAll && strictlyHigher) {
            return PivotClassification.HIGH;
        }
        if (lowerThanAll && strictlyLower) {
            return PivotClassification.LOW;
        }
        return PivotClassification.NONE;
    }

    private void absorbPivot(final List<Pivot> pivots, final Pivot pivot) {
        if (pivots.isEmpty()) {
            pivots.add(pivot);
            return;
        }
        final Pivot last = pivots.get(pivots.size() - 1);
        if (last.classification() == pivot.classification()) {
            if (pivot.classification() == PivotClassification.HIGH
                    && pivot.price().isGreaterThanOrEqual(last.price())) {
                pivots.set(pivots.size() - 1, pivot);
            } else if (pivot.classification() == PivotClassification.LOW
                    && pivot.price().isLessThanOrEqual(last.price())) {
                pivots.set(pivots.size() - 1, pivot);
            }
            return;
        }
        pivots.add(pivot);
    }

    private enum PivotClassification {
        HIGH,
        LOW,
        NONE
    }

    private record Pivot(int index, Num price, PivotClassification classification) {
    }
}
