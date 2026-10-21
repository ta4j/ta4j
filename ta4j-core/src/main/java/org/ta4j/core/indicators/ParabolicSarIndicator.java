/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import java.util.function.IntConsumer;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Parabolic SAR indicator.
 *
 * <p>
 * On a moving series, an instance read on every bar continues SAR, trend,
 * extreme and acceleration together across head advances. Normal appends take
 * constant recurrence work and the cached state is bounded by series retention.
 * A fresh instance, or one whose predecessor history is no longer available,
 * seeds at the retained head: its first value is NaN and its second detects the
 * initial trend. It cannot reconstruct history it never observed.
 *
 * @see <a href=
 *      "https://www.investopedia.com/trading/introduction-to-parabolic-sar/">
 *      https://www.investopedia.com/trading/introduction-to-parabolic-sar/</a>
 * @see <a href="https://www.investopedia.com/terms/p/parabolicindicator.asp">
 *      https://www.investopedia.com/terms/p/parabolicindicator.asp</a>
 */
public class ParabolicSarIndicator extends RecursiveCachedIndicator<Num> {

    private final Num maxAcceleration;
    private final Num accelerationStart;
    private final Num accelerationIncrement;
    private final transient SarStateIndicator stateIndicator;

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code accelerationStart} = 0.02
     * <li>{@code maxAcceleration} = 0.2
     * <li>{@code accelerationIncrement} = 0.02
     * </ul>
     *
     * @param series the bar series for this indicator
     */
    public ParabolicSarIndicator(BarSeries series) {
        this(series, series.numFactory().numOf(0.02), series.numFactory().numOf(0.2), series.numFactory().numOf(0.02));
    }

    /**
     * Constructor with {@code increment} = 0.02.
     *
     * @param series            the bar series for this indicator
     * @param accelerationStart acceleration factor
     * @param maxAcceleration   maximum acceleration
     */
    public ParabolicSarIndicator(BarSeries series, Num accelerationStart, Num maxAcceleration) {
        this(series, accelerationStart, maxAcceleration, series.numFactory().numOf(0.02));
    }

    /**
     * Constructor.
     *
     * @param series                the bar series for this indicator
     * @param accelerationStart     acceleration factor (usually 0.02)
     * @param maxAcceleration       maximum acceleration (usually 0.2)
     * @param accelerationIncrement the increment step (usually 0.02)
     */
    public ParabolicSarIndicator(BarSeries series, Num accelerationStart, Num maxAcceleration,
            Num accelerationIncrement) {
        this(series, accelerationStart, maxAcceleration, accelerationIncrement, null);
    }

    // A bounded observer seam lets tests count actual tuple calculations without
    // timing.
    ParabolicSarIndicator(BarSeries series, Num accelerationStart, Num maxAcceleration, Num accelerationIncrement,
            IntConsumer calculationObserver) {
        this(new SarStateIndicator(series, accelerationStart, maxAcceleration, accelerationIncrement,
                calculationObserver), accelerationStart, maxAcceleration, accelerationIncrement);
    }

    private ParabolicSarIndicator(SarStateIndicator stateIndicator, Num accelerationStart, Num maxAcceleration,
            Num accelerationIncrement) {
        super(stateIndicator);
        this.stateIndicator = stateIndicator;
        this.maxAcceleration = maxAcceleration;
        this.accelerationStart = accelerationStart;
        this.accelerationIncrement = accelerationIncrement;
    }

    @Override
    protected Num calculate(int index) {
        return stateIndicator.getValue(index).sar();
    }

    @Override
    public int getCountOfUnstableBars() {
        return stateIndicator.getCountOfUnstableBars();
    }

    /** One revision of the recurrence, including the distinct first-bar seed. */
    private record SarState(Num sar, Num extreme, Num acceleration, boolean upTrend, boolean initialized) {
    }

    private static final class SarStateIndicator extends RecursiveCachedIndicator<SarState> {
        private final LowPriceIndicator lowPriceIndicator;
        private final LowestValueIndicator lowestValueIndicator;
        private final HighPriceIndicator highPriceIndicator;
        private final HighestValueIndicator highestValueIndicator;
        private final Num accelerationStart;
        private final Num maxAcceleration;
        private final Num accelerationIncrement;

        private final transient IntConsumer calculationObserver;

        private SarStateIndicator(BarSeries series, Num accelerationStart, Num maxAcceleration,
                Num accelerationIncrement, IntConsumer calculationObserver) {
            super(series);
            this.lowPriceIndicator = new LowPriceIndicator(series);
            this.lowestValueIndicator = new LowestValueIndicator(lowPriceIndicator, 2);
            this.highPriceIndicator = new HighPriceIndicator(series);
            this.highestValueIndicator = new HighestValueIndicator(highPriceIndicator, 2);
            this.accelerationStart = accelerationStart;
            this.maxAcceleration = maxAcceleration;
            this.accelerationIncrement = accelerationIncrement;
            this.calculationObserver = calculationObserver;
        }

        @Override
        protected SarState calculate(int index) {
            if (calculationObserver != null) {
                calculationObserver.accept(index);
            }
            while (true) {
                int beginIndex = getBarSeries().getBeginIndex();
                if (beginIndex < 0) {
                    return new SarState(NaN, NaN, getBarSeries().numFactory().zero(), false, false);
                }
                if (index < beginIndex) {
                    return getValue(beginIndex);
                }
                SarState state;
                if (index == beginIndex) {
                    state = new SarState(NaN, getBarSeries().getBar(index).getClosePrice(),
                            getBarSeries().numFactory().zero(), false, false);
                } else {
                    // Resolve the complete predecessor before reading current inputs.
                    // A NaN SAR can still be the valid first initialization phase.
                    SarState previous = getValue(index - 1);
                    state = advance(index, previous);
                }
                if (getBarSeries().getBeginIndex() == beginIndex) {
                    return state;
                }
                // A removed input/predecessor must not publish a mixed-head tuple.
            }
        }

        private SarState advance(int index, SarState previous) {
            if (!previous.initialized()) {
                boolean upTrend = getBarSeries().getBar(index - 1)
                        .getClosePrice()
                        .isLessThan(getBarSeries().getBar(index).getClosePrice());
                Num sar = upTrend ? lowestValueIndicator.getValue(index - 1)
                        : highestValueIndicator.getValue(index - 1);
                Num extreme = upTrend ? highestValueIndicator.getValue(index - 1)
                        : lowestValueIndicator.getValue(index - 1);
                return new SarState(sar, extreme, accelerationStart, upTrend, true);
            }

            Num priorSar = previous.sar();
            boolean upTrend = previous.upTrend();
            Num extreme = previous.extreme();
            Num high = highPriceIndicator.getValue(index);
            Num low = lowPriceIndicator.getValue(index);
            Num acceleration = previous.acceleration();
            Num sar = priorSar.plus(acceleration.multipliedBy(extreme.minus(priorSar)));

            if (upTrend) {
                if (low.isLessThan(sar)) {
                    sar = extreme;
                    acceleration = accelerationStart;
                    extreme = low;
                    upTrend = false;
                } else if (high.isGreaterThan(extreme)) {
                    extreme = high;
                    acceleration = acceleration.plus(accelerationIncrement);
                    if (acceleration.isGreaterThan(maxAcceleration)) {
                        acceleration = maxAcceleration;
                    }
                }
            } else {
                if (high.isGreaterThanOrEqual(sar)) {
                    sar = extreme;
                    acceleration = accelerationStart;
                    extreme = high;
                    upTrend = true;
                } else if (low.isLessThan(extreme)) {
                    extreme = low;
                    acceleration = acceleration.plus(accelerationIncrement);
                    if (acceleration.isGreaterThan(maxAcceleration)) {
                        acceleration = maxAcceleration;
                    }
                }
            }

            if (upTrend) {
                Num lowestPriceOfTwoPreviousBars = lowestValueIndicator.getValue(index - 1);
                if (sar.isGreaterThan(lowestPriceOfTwoPreviousBars)) {
                    sar = lowestPriceOfTwoPreviousBars;
                }
            } else {
                Num highestPriceOfTwoPreviousBars = highestValueIndicator.getValue(index - 1);
                if (sar.isLessThan(highestPriceOfTwoPreviousBars)) {
                    sar = highestPriceOfTwoPreviousBars;
                }
            }
            return new SarState(sar, extreme, acceleration, upTrend, true);
        }

        @Override
        public int getCountOfUnstableBars() {
            return Math.max(lowestValueIndicator.getCountOfUnstableBars(),
                    highestValueIndicator.getCountOfUnstableBars());
        }
    }
}
