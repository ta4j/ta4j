/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingHighIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.num.Num;

/**
 * Detects Elliott swings (alternating pivots) based on confirmed swing highs
 * and lows.
 *
 * <p>
 * This indicator composes two {@link RecentSwingIndicator} instances: one that
 * reports swing highs and one that reports swing lows. Their confirmed swing
 * point indexes are merged into a single alternating pivot sequence, while
 * consecutive pivots of the same type are compressed by retaining the most
 * extreme pivot (higher highs, lower lows).
 *
 * <p>
 * For classic window-based swings use the fractal constructors (backed by
 * {@link RecentFractalSwingHighIndicator} and
 * {@link RecentFractalSwingLowIndicator}). For ZigZag-style swings share a
 * single {@link ZigZagStateIndicator} between high/low swing indicators or use
 * the {@link #zigZag(BarSeries, ElliottDegree)} helpers to avoid mismatched
 * swing state.
 *
 * <p>
 * Swing points are confirmed using lookforward bars, so results are delayed by
 * the chosen lookforward window. Account for this inherent confirmation delay
 * when using the swings in live or latency-sensitive workflows.
 *
 * <p>
 * Use this indicator as the canonical swing source for Elliott Wave analysis.
 * It powers {@link ElliottWaveFacade}, {@link ElliottPhaseIndicator}, and
 * {@link ElliottScenarioIndicator}.
 *
 * @since 0.22.0
 */
public class ElliottSwingIndicator extends CachedIndicator<List<ElliottSwing>> {

    private final RecentSwingIndicator swingHighIndicator;
    private final RecentSwingIndicator swingLowIndicator;
    private final ElliottDegree degree;

    /**
     * Builds a new indicator with identical lookback/forward lengths using
     * {@link HighPriceIndicator}/{@link LowPriceIndicator}.
     *
     * @param series source bar series
     * @param window number of bars to inspect before and after a pivot
     * @param degree swing degree metadata
     * @since 0.22.0
     */
    public ElliottSwingIndicator(final BarSeries series, final int window, final ElliottDegree degree) {
        this(series, window, window, window, degree);
    }

    /**
     * Builds a new indicator using dedicated lookback/forward lengths using
     * {@link HighPriceIndicator}/{@link LowPriceIndicator}.
     *
     * @param series            source bar series
     * @param lookbackLength    bars inspected before a pivot candidate
     * @param lookforwardLength bars inspected after a pivot candidate
     * @param degree            swing degree metadata
     * @since 0.22.0
     */
    public ElliottSwingIndicator(final BarSeries series, final int lookbackLength, final int lookforwardLength,
            final ElliottDegree degree) {
        this(series, lookbackLength, lookforwardLength, Math.min(lookbackLength, lookforwardLength), degree);
    }

    /**
     * Builds a new indicator using dedicated lookback/forward lengths using
     * {@link HighPriceIndicator}/{@link LowPriceIndicator}.
     *
     * @param series            source bar series
     * @param lookbackLength    bars inspected before a pivot candidate
     * @param lookforwardLength bars inspected after a pivot candidate
     * @param allowedEqualBars  number of equal-value bars allowed on each side of a
     *                          candidate pivot (flat tops/bottoms)
     * @param degree            swing degree metadata
     * @since 0.22.0
     */
    public ElliottSwingIndicator(final BarSeries series, final int lookbackLength, final int lookforwardLength,
            final int allowedEqualBars, final ElliottDegree degree) {
        this(fractalHigh(series, lookbackLength, lookforwardLength, allowedEqualBars),
                fractalLow(series, lookbackLength, lookforwardLength, allowedEqualBars), degree);
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
        this(indicator, window, window, window, degree);
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
        this(indicator, lookbackLength, lookforwardLength, Math.min(lookbackLength, lookforwardLength), degree);
    }

    /**
     * Builds a new indicator using dedicated lookback/forward lengths and a custom
     * value source.
     *
     * @param indicator         indicator providing the values to analyse
     * @param lookbackLength    bars inspected before a pivot candidate
     * @param lookforwardLength bars inspected after a pivot candidate
     * @param allowedEqualBars  number of equal-value bars allowed on each side of a
     *                          candidate pivot (flat tops/bottoms)
     * @param degree            swing degree metadata
     * @since 0.22.0
     */
    public ElliottSwingIndicator(final Indicator<Num> indicator, final int lookbackLength, final int lookforwardLength,
            final int allowedEqualBars, final ElliottDegree degree) {
        this(fractalHigh(indicator, lookbackLength, lookforwardLength, allowedEqualBars),
                fractalLow(indicator, lookbackLength, lookforwardLength, allowedEqualBars), degree);
    }

    /**
     * Builds a new indicator from supplied swing high/low detectors.
     *
     * @param swingHighIndicator confirmed swing high detector
     * @param swingLowIndicator  confirmed swing low detector
     * @param degree             swing degree metadata
     * @since 0.22.0
     */
    public ElliottSwingIndicator(final RecentSwingIndicator swingHighIndicator,
            final RecentSwingIndicator swingLowIndicator, final ElliottDegree degree) {
        super(requireSeries(swingHighIndicator, swingLowIndicator));
        this.swingHighIndicator = Objects.requireNonNull(swingHighIndicator, "swingHighIndicator");
        this.swingLowIndicator = Objects.requireNonNull(swingLowIndicator, "swingLowIndicator");
        this.degree = Objects.requireNonNull(degree, "degree");
    }

    private static BarSeries requireSeries(final RecentSwingIndicator swingHighIndicator,
            final RecentSwingIndicator swingLowIndicator) {
        final BarSeries highSeries = Objects.requireNonNull(swingHighIndicator, "swingHighIndicator").getBarSeries();
        final BarSeries lowSeries = Objects.requireNonNull(swingLowIndicator, "swingLowIndicator").getBarSeries();
        if (highSeries == null || lowSeries == null) {
            throw new IllegalArgumentException("Swing indicators must expose a backing series");
        }
        if (highSeries != lowSeries) {
            throw new IllegalArgumentException("Swing indicators must share the same bar series instance");
        }
        return highSeries;
    }

    /**
     * Convenience factory for ZigZag-driven Elliott swings using a shared state
     * indicator.
     *
     * @param stateIndicator ZigZag state indicator providing swing indexes
     * @param price          price indicator used for retrieving swing values
     *                       (should typically match the price indicator used inside
     *                       {@code stateIndicator})
     * @param degree         swing degree metadata
     * @return Elliott swing indicator backed by ZigZag swings
     * @since 0.22.0
     */
    public static ElliottSwingIndicator zigZag(final ZigZagStateIndicator stateIndicator, final Indicator<Num> price,
            final ElliottDegree degree) {
        Objects.requireNonNull(stateIndicator, "stateIndicator");
        Objects.requireNonNull(price, "price");
        return new ElliottSwingIndicator(new RecentZigZagSwingHighIndicator(stateIndicator, price),
                new RecentZigZagSwingLowIndicator(stateIndicator, price), degree);
    }

    /**
     * Convenience factory for ZigZag-driven Elliott swings using close prices and
     * an ATR(14) reversal threshold.
     *
     * @param series source bar series
     * @param degree swing degree metadata
     * @return Elliott swing indicator backed by ZigZag swings
     * @since 0.22.0
     */
    public static ElliottSwingIndicator zigZag(final BarSeries series, final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> reversal = new ATRIndicator(series, 14);
        return zigZag(new ZigZagStateIndicator(price, reversal), price, degree);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(swingHighIndicator.getCountOfUnstableBars(), swingLowIndicator.getCountOfUnstableBars());
    }

    @Override
    protected List<ElliottSwing> calculate(final int index) {
        final BarSeries series = getBarSeries();
        if (series == null || index < series.getBeginIndex() || index > series.getEndIndex()) {
            return List.of();
        }

        final List<Pivot> pivots = pivots(index);
        if (pivots.size() < 2) {
            return List.of();
        }

        final List<ElliottSwing> swings = new ArrayList<>(pivots.size() - 1);
        for (int i = 1; i < pivots.size(); i++) {
            final Pivot previous = pivots.get(i - 1);
            final Pivot current = pivots.get(i);
            swings.add(new ElliottSwing(previous.index, current.index, previous.price, current.price, degree));
        }
        return List.copyOf(swings);
    }

    /**
     * Returns the alternating pivot indexes used to build the swing list at the
     * requested index.
     *
     * @param index bar index
     * @return immutable pivot index list (alternating highs and lows)
     * @since 0.22.0
     */
    public List<Integer> getPivotIndexes(final int index) {
        final List<Pivot> pivots = pivots(index);
        if (pivots.isEmpty()) {
            return List.of();
        }
        final List<Integer> indexes = new ArrayList<>(pivots.size());
        for (Pivot pivot : pivots) {
            indexes.add(pivot.index);
        }
        return List.copyOf(indexes);
    }

    /**
     * @return swing high detector used for pivot collection
     * @since 0.22.0
     */
    public RecentSwingIndicator getSwingHighIndicator() {
        return swingHighIndicator;
    }

    /**
     * @return swing low detector used for pivot collection
     * @since 0.22.0
     */
    public RecentSwingIndicator getSwingLowIndicator() {
        return swingLowIndicator;
    }

    /**
     * @return degree metadata applied to generated swings
     * @since 0.22.0
     */
    public ElliottDegree getDegree() {
        return degree;
    }

    private List<Pivot> pivots(final int index) {
        final List<Integer> highs = swingHighIndicator.getSwingPointIndexesUpTo(index);
        final List<Integer> lows = swingLowIndicator.getSwingPointIndexesUpTo(index);
        if (highs.isEmpty() && lows.isEmpty()) {
            return List.of();
        }

        final List<Pivot> pivots = new ArrayList<>(highs.size() + lows.size());
        int highPointer = 0;
        int lowPointer = 0;

        while (highPointer < highs.size() || lowPointer < lows.size()) {
            final int highIndex = highPointer < highs.size() ? highs.get(highPointer) : Integer.MAX_VALUE;
            final int lowIndex = lowPointer < lows.size() ? lows.get(lowPointer) : Integer.MAX_VALUE;

            if (highIndex == lowIndex) {
                final Num highPrice = swingHighIndicator.getPriceIndicator().getValue(highIndex);
                final Num lowPrice = swingLowIndicator.getPriceIndicator().getValue(lowIndex);

                final PivotType chosen;
                if (pivots.isEmpty()) {
                    if (Num.isNaNOrNull(highPrice)) {
                        chosen = PivotType.LOW;
                    } else if (Num.isNaNOrNull(lowPrice)) {
                        chosen = PivotType.HIGH;
                    } else {
                        chosen = !highPrice.isLessThan(lowPrice) ? PivotType.HIGH : PivotType.LOW;
                    }
                } else {
                    chosen = pivots.get(pivots.size() - 1).type.opposite();
                }

                if (chosen == PivotType.HIGH) {
                    absorbPivot(pivots, new Pivot(highIndex, highPrice, PivotType.HIGH));
                } else {
                    absorbPivot(pivots, new Pivot(lowIndex, lowPrice, PivotType.LOW));
                }
                highPointer++;
                lowPointer++;
                continue;
            }

            if (highIndex < lowIndex) {
                absorbPivot(pivots, new Pivot(highIndex, swingHighIndicator.getPriceIndicator().getValue(highIndex),
                        PivotType.HIGH));
                highPointer++;
            } else {
                absorbPivot(pivots,
                        new Pivot(lowIndex, swingLowIndicator.getPriceIndicator().getValue(lowIndex), PivotType.LOW));
                lowPointer++;
            }
        }

        if (pivots.isEmpty()) {
            return List.of();
        }
        return List.copyOf(pivots);
    }

    private void absorbPivot(final List<Pivot> pivots, final Pivot pivot) {
        if (pivot == null || Num.isNaNOrNull(pivot.price)) {
            return;
        }
        if (pivots.isEmpty()) {
            pivots.add(pivot);
            return;
        }
        final Pivot last = pivots.get(pivots.size() - 1);
        if (last.type == pivot.type) {
            if (pivot.type == PivotType.HIGH && !pivot.price.isLessThan(last.price)) {
                pivots.set(pivots.size() - 1, pivot);
            } else if (pivot.type == PivotType.LOW && !pivot.price.isGreaterThan(last.price)) {
                pivots.set(pivots.size() - 1, pivot);
            }
            return;
        }
        pivots.add(pivot);
    }

    private static RecentSwingIndicator fractalHigh(final BarSeries series, final int lookbackLength,
            final int lookforwardLength, final int allowedEqualBars) {
        Objects.requireNonNull(series, "series");
        return fractalHigh(new HighPriceIndicator(series), lookbackLength, lookforwardLength, allowedEqualBars);
    }

    private static RecentSwingIndicator fractalLow(final BarSeries series, final int lookbackLength,
            final int lookforwardLength, final int allowedEqualBars) {
        Objects.requireNonNull(series, "series");
        return fractalLow(new LowPriceIndicator(series), lookbackLength, lookforwardLength, allowedEqualBars);
    }

    private static RecentSwingIndicator fractalHigh(final Indicator<Num> indicator, final int lookbackLength,
            final int lookforwardLength, final int allowedEqualBars) {
        Objects.requireNonNull(indicator, "indicator");
        if (lookbackLength < 1 || lookforwardLength < 1) {
            throw new IllegalArgumentException("Window lengths must be positive");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be non-negative");
        }
        return new RecentFractalSwingHighIndicator(indicator, lookbackLength, lookforwardLength, allowedEqualBars);
    }

    private static RecentSwingIndicator fractalLow(final Indicator<Num> indicator, final int lookbackLength,
            final int lookforwardLength, final int allowedEqualBars) {
        Objects.requireNonNull(indicator, "indicator");
        if (lookbackLength < 1 || lookforwardLength < 1) {
            throw new IllegalArgumentException("Window lengths must be positive");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be non-negative");
        }
        return new RecentFractalSwingLowIndicator(indicator, lookbackLength, lookforwardLength, allowedEqualBars);
    }

    private enum PivotType {
        HIGH, LOW;

        private PivotType opposite() {
            return this == HIGH ? LOW : HIGH;
        }
    }

    private record Pivot(int index, Num price, PivotType type) {
    }
}
