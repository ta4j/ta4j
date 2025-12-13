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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Detects Elliott swings (alternating pivots) using a symmetric
 * lookback/forward window.
 *
 * @since 0.22.0
 */
public class ElliottSwingIndicator extends CachedIndicator<List<ElliottSwing>> {

    private final Indicator<Num> priceIndicator;
    private final int lookbackLength;
    private final int lookforwardLength;
    private final ElliottDegree degree;

    /**
     * Builds a new indicator with identical lookback/forward lengths using close
     * prices.
     *
     * @param series source bar series
     * @param window number of bars to inspect before and after a pivot
     * @param degree swing degree metadata
     * @since 0.22.0
     */
    public ElliottSwingIndicator(final BarSeries series, final int window, final ElliottDegree degree) {
        this(new ClosePriceIndicator(series), window, window, degree);
    }

    /**
     * Builds a new indicator using dedicated lookback/forward lengths and close
     * prices.
     *
     * @param series            source bar series
     * @param lookbackLength    bars inspected before a pivot candidate
     * @param lookforwardLength bars inspected after a pivot candidate
     * @param degree            swing degree metadata
     * @since 0.22.0
     */
    public ElliottSwingIndicator(final BarSeries series, final int lookbackLength, final int lookforwardLength,
            final ElliottDegree degree) {
        this(new ClosePriceIndicator(series), lookbackLength, lookforwardLength, degree);
    }

    /**
     * Builds a new indicator with identical lookback/forward lengths using the
     * provided value source.
     *
     * @param indicator indicator providing the values to analyse
     * @param window    number of bars to inspect before and after a pivot
     * @param degree    swing degree metadata
     * @since 0.22.0
     */
    public ElliottSwingIndicator(final Indicator<Num> indicator, final int window, final ElliottDegree degree) {
        this(indicator, window, window, degree);
    }

    /**
     * Builds a new indicator using dedicated lookback/forward lengths and a custom
     * value source.
     *
     * @param indicator         indicator providing the values to analyse
     * @param lookbackLength    bars inspected before a pivot candidate
     * @param lookforwardLength bars inspected after a pivot candidate
     * @param degree            swing degree metadata
     * @since 0.22.0
     */
    public ElliottSwingIndicator(final Indicator<Num> indicator, final int lookbackLength, final int lookforwardLength,
            final ElliottDegree degree) {
        super(indicator);
        if (lookbackLength < 1 || lookforwardLength < 1) {
            throw new IllegalArgumentException("Window lengths must be positive");
        }
        this.priceIndicator = Objects.requireNonNull(indicator, "indicator");
        this.lookbackLength = lookbackLength;
        this.lookforwardLength = lookforwardLength;
        this.degree = Objects.requireNonNull(degree, "degree");
    }

    @Override
    public int getCountOfUnstableBars() {
        return lookbackLength + lookforwardLength;
    }

    @Override
    protected List<ElliottSwing> calculate(final int index) {
        final BarSeries series = getBarSeries();
        if (series == null || index < series.getBeginIndex()) {
            return List.of();
        }

        final int firstPivotIndex = Math.max(series.getBeginIndex() + lookbackLength, 0);
        if (index < firstPivotIndex) {
            return List.of();
        }

        final List<Pivot> pivots = new ArrayList<>();
        for (int pivotIndex = firstPivotIndex; pivotIndex <= index; pivotIndex++) {
            final Num pivotPrice = priceIndicator.getValue(pivotIndex);
            if (pivotPrice == null || pivotPrice.isNaN()) {
                continue;
            }

            final PivotClassification classification = classifyPivot(pivotIndex, pivotPrice, index);
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

    private PivotClassification classifyPivot(final int pivotIndex, final Num pivotPrice, final int currentIndex) {
        final int beginIndex = getBarSeries().getBeginIndex();
        final int plateauStart = findPlateauStart(pivotIndex, beginIndex, pivotPrice);
        if (plateauStart < 0) {
            return PivotClassification.NONE;
        }
        final int plateauEnd = findPlateauEnd(pivotIndex, currentIndex, pivotPrice);
        if (plateauEnd < 0) {
            return PivotClassification.NONE;
        }
        if (plateauStart - lookbackLength < beginIndex) {
            return PivotClassification.NONE;
        }
        if (plateauEnd + lookforwardLength > currentIndex) {
            return PivotClassification.NONE;
        }

        if (isHigherThanNeighbours(plateauStart, plateauEnd, pivotPrice)) {
            return PivotClassification.HIGH;
        }
        if (isLowerThanNeighbours(plateauStart, plateauEnd, pivotPrice)) {
            return PivotClassification.LOW;
        }
        return PivotClassification.NONE;
    }

    private int findPlateauStart(final int pivotIndex, final int beginIndex, final Num pivotPrice) {
        int start = pivotIndex;
        final int windowStart = Math.max(beginIndex, pivotIndex - lookbackLength);
        while (start > windowStart) {
            final Num previous = priceIndicator.getValue(start - 1);
            if (previous == null || previous.isNaN()) {
                return -1;
            }
            if (!previous.isEqual(pivotPrice)) {
                break;
            }
            start--;
        }
        return start;
    }

    private int findPlateauEnd(final int pivotIndex, final int currentIndex, final Num pivotPrice) {
        int end = pivotIndex;
        final int windowEnd = Math.min(currentIndex, pivotIndex + lookforwardLength);
        while (end < windowEnd) {
            final Num next = priceIndicator.getValue(end + 1);
            if (next == null || next.isNaN()) {
                return -1;
            }
            if (!next.isEqual(pivotPrice)) {
                break;
            }
            end++;
        }
        return end;
    }

    private boolean isHigherThanNeighbours(final int plateauStart, final int plateauEnd, final Num pivotPrice) {
        final int startLookback = plateauStart - lookbackLength;
        for (int i = startLookback; i < plateauStart; i++) {
            final Num value = priceIndicator.getValue(i);
            if (value == null || value.isNaN() || value.isGreaterThanOrEqual(pivotPrice)) {
                return false;
            }
        }
        for (int i = plateauEnd + 1; i <= plateauEnd + lookforwardLength; i++) {
            final Num value = priceIndicator.getValue(i);
            if (value == null || value.isNaN() || value.isGreaterThanOrEqual(pivotPrice)) {
                return false;
            }
        }
        return true;
    }

    private boolean isLowerThanNeighbours(final int plateauStart, final int plateauEnd, final Num pivotPrice) {
        final int startLookback = plateauStart - lookbackLength;
        for (int i = startLookback; i < plateauStart; i++) {
            final Num value = priceIndicator.getValue(i);
            if (value == null || value.isNaN() || value.isLessThanOrEqual(pivotPrice)) {
                return false;
            }
        }
        for (int i = plateauEnd + 1; i <= plateauEnd + lookforwardLength; i++) {
            final Num value = priceIndicator.getValue(i);
            if (value == null || value.isNaN() || value.isLessThanOrEqual(pivotPrice)) {
                return false;
            }
        }
        return true;
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
        HIGH, LOW, NONE
    }

    private record Pivot(int index, Num price, PivotClassification classification) {
    }
}