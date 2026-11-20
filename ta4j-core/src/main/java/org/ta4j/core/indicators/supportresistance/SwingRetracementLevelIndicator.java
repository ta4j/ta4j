/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supportresistance;

import static org.ta4j.core.num.NaN.NaN;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.indicators.RecentSwingIndicators;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Swing retracement level indicator.
 *
 * <p>
 * Returns the price level that sits at a fixed fraction of the range between
 * the latest confirmed swing low and swing high:
 *
 * <pre>
 * level(i) = low(i) + ratio * (high(i) - low(i))
 * </pre>
 *
 * where {@code high(i)} and {@code low(i)} are the values of the supplied swing
 * high and swing low indicators at {@code i}, that is the prices of the latest
 * swings each of them has confirmed with the bars up to {@code i}. A ratio of
 * {@code 0} returns the swing low, {@code 1} the swing high and {@code 0.5} the
 * midpoint. The level is always measured from the low: the pair is not
 * reordered by which swing came last, so a 38.2% pullback after a rally from
 * the low to the high is {@code ratio = 0.618}.
 *
 * <p>
 * The indicator is causal. The value at {@code i} reads the swing indicators
 * only at {@code i}, and a {@link RecentSwingIndicator} reports at {@code i}
 * only swings confirmed by bar {@code i}; values therefore do not change when
 * later bars arrive, and each bar costs two cached swing reads plus constant
 * arithmetic. The value is {@code NaN} until both a swing high and a swing low
 * have been confirmed, and before {@link #getCountOfUnstableBars()}, which is
 * the larger unstable-bar count of the two swing indicators.
 *
 * <p>
 * Example, the 61.8% level between the latest seven-bar fractal swings:
 *
 * <pre>{@code
 * RecentSwingIndicators.Pair swings = RecentSwingIndicators.fractal(series);
 * Indicator<Num> level = new SwingRetracementLevelIndicator(swings.highs(), swings.lows(), 0.618);
 * }</pre>
 *
 * @see RecentSwingIndicators
 * @see <a href=
 *      "https://www.investopedia.com/terms/f/fibonacciretracement.asp">Investopedia:
 *      Fibonacci Retracement Levels</a>
 * @since 0.29.1
 */
public class SwingRetracementLevelIndicator extends CachedIndicator<Num> {

    private final RecentSwingIndicator swingHighIndicator;
    private final RecentSwingIndicator swingLowIndicator;
    private final Num ratio;

    /**
     * Constructor.
     *
     * @param swingHighIndicator indicator of the latest confirmed swing high
     * @param swingLowIndicator  indicator of the latest confirmed swing low, on the
     *                           same series
     * @param ratio              position of the level between the swing low
     *                           ({@code 0}) and the swing high ({@code 1}), for
     *                           example {@code 0.382}, {@code 0.5} or {@code 0.618}
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the swing indicators use different
     *                                  series, or {@code ratio} is not a finite
     *                                  value in {@code [0, 1]}
     * @since 0.29.1
     */
    public SwingRetracementLevelIndicator(RecentSwingIndicator swingHighIndicator,
            RecentSwingIndicator swingLowIndicator, Number ratio) {
        this(validatedConfig(swingHighIndicator, swingLowIndicator, ratio));
    }

    /**
     * Constructor using the canonical {@link RecentSwingIndicators#defaultFor}
     * swing pair (ATR-scaled ZigZag).
     *
     * @param series the bar series
     * @param ratio  position of the level between the swing low ({@code 0}) and the
     *               swing high ({@code 1})
     * @throws IllegalArgumentException if {@code ratio} is not a finite value in
     *                                  {@code [0, 1]}
     * @since 0.29.1
     */
    public SwingRetracementLevelIndicator(BarSeries series, Number ratio) {
        this(validatedConfig(RecentSwingIndicators.defaultFor(series), ratio));
    }

    private SwingRetracementLevelIndicator(Config config) {
        super(config.swingHighIndicator(), config.swingLowIndicator());
        this.swingHighIndicator = config.swingHighIndicator();
        this.swingLowIndicator = config.swingLowIndicator();
        this.ratio = config.ratio();
    }

    private static Config validatedConfig(RecentSwingIndicators.Pair swings, Number ratio) {
        return validatedConfig(swings.highs(), swings.lows(), ratio);
    }

    private static Config validatedConfig(RecentSwingIndicator swingHighIndicator,
            RecentSwingIndicator swingLowIndicator, Number ratio) {
        Objects.requireNonNull(swingHighIndicator, "swingHighIndicator");
        Objects.requireNonNull(swingLowIndicator, "swingLowIndicator");
        Objects.requireNonNull(ratio, "ratio");
        final NumFactory numFactory = Objects
                .requireNonNull(swingHighIndicator.getBarSeries(), "swingHighIndicator must reference a bar series")
                .numFactory();
        // A decimal factory cannot represent a raw NaN or infinity, so screen the
        // primitive value before converting it.
        final Num value = Double.isFinite(ratio.doubleValue()) ? numFactory.numOf(ratio) : NaN;
        if (!Num.isFinite(value) || value.isNegative() || value.isGreaterThan(numFactory.one())) {
            throw new IllegalArgumentException("ratio must be a finite value between 0 and 1, got " + ratio);
        }
        return new Config(swingHighIndicator, swingLowIndicator, value);
    }

    private record Config(RecentSwingIndicator swingHighIndicator, RecentSwingIndicator swingLowIndicator, Num ratio) {
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        final Num high = swingHighIndicator.getValue(index);
        final Num low = swingLowIndicator.getValue(index);
        if (Num.isNaNOrNull(high) || Num.isNaNOrNull(low)) {
            return NaN;
        }
        return low.plus(ratio.multipliedBy(high.minus(low)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(swingHighIndicator.getCountOfUnstableBars(), swingLowIndicator.getCountOfUnstableBars());
    }
}
