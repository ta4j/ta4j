/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Jurik Moving Average (JMA) Indicator.
 *
 * JMA, or Jurik Moving Average, is a type of moving average developed by Mark
 * Jurik. It is known for its ability to respond to price changes more smoothly
 * than traditional moving averages like SMA (Simple Moving Average) or EMA
 * (Exponential Moving Average), while avoiding much of the lag associated with
 * those averages.
 * <p>
 * On a bounded series, retained values keep the full-history recurrence. Read
 * each appended bar to continue the chain at a cost independent of the retained
 * window, including a one-bar window. Finalized input mutations must also be
 * consumed before their bars are evicted: with one-bar retention, read after
 * the final mutation and before appending, or retain at least two bars so the
 * previous bar can be recomputed on the next read. A fresh indicator, or a
 * reader that loses every valid cached predecessor and its required inputs,
 * seeds from the first retained bar instead.
 *
 */
public class JMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator; // Base indicator (e.g., Close Price)
    private final int barCount; // Period for the JMA calculation
    private final Num phase; // Phase adjustment (usually between -100 and +100)
    private final Num power; // Smoothing power factor (default is 2)
    private final transient StateIndicator state;
    private final transient int unstableBars;

    /**
     * Constructor.
     *
     * @param indicator The input indicator (e.g., Close Price).
     * @param barCount  The number of periods for the JMA.
     * @param phase     The phase adjustment (-100 to +100).
     * @param power     The smoothing power factor (default is 2).
     */
    public JMAIndicator(Indicator<Num> indicator, int barCount, double phase, double power) {
        this(new StateIndicator(indicator, barCount, phase, power));
    }

    private JMAIndicator(StateIndicator state) {
        super(state);
        this.state = state;
        this.indicator = state.indicator;
        this.barCount = state.barCount;
        this.phase = state.phase;
        this.power = state.power;
        this.unstableBars = indicator.getCountOfUnstableBars() + barCount;
    }

    @Override
    protected Num calculate(int index) {
        return state.getValue(index).jma;
    }

    /**
     * Retains full-history values carried by the shared recursive state.
     *
     * @return {@code true}
     * @since 0.27.1
     */
    @Override
    protected boolean hasRecursiveDependencies() {
        return true;
    }

    /**
     * Owns all four recurrence components in one cache, including revision
     * invalidation, bounded retention and concurrent last-bar publication.
     */
    private static final class StateIndicator extends RecursiveCachedIndicator<JmaData> {

        private final Indicator<Num> indicator;
        private final int barCount;
        private final Num phase;
        private final Num power;
        private final Num alpha;
        private final Num beta;
        private final Num phaseRatio;
        private final NumFactory numFactory;

        private StateIndicator(Indicator<Num> indicator, int barCount, double phase, double power) {
            super(indicator);
            this.indicator = indicator;
            this.barCount = barCount;
            this.numFactory = indicator.getBarSeries().numFactory();
            this.phase = numFactory.numOf(Math.min(Math.max(phase, -100), 100));
            this.power = numFactory.numOf(Math.max(1, power));
            Num scaledPeriod = numFactory.numOf(0.45).multipliedBy(numFactory.numOf(barCount - 1));
            this.beta = scaledPeriod.dividedBy(scaledPeriod.plus(numFactory.two()));
            this.phaseRatio = this.phase.dividedBy(numFactory.numOf(100)).plus(numFactory.numOf(1.5));
            this.alpha = beta.pow(this.power);
        }

        @Override
        protected JmaData calculate(int index) {
            while (true) {
                int beginIndex = getBarSeries().getBeginIndex();
                if (beginIndex < 0) {
                    return new JmaData(NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN);
                }
                if (index < beginIndex) {
                    return getValue(beginIndex);
                }
                JmaData previous = index == beginIndex ? getCachedValueOrNull(index - 1) : getValue(index - 1);
                Num currentPrice = indicator.getValue(index);
                JmaData result;
                if (previous == null) {
                    Num zero = numFactory.zero();
                    result = new JmaData(currentPrice, zero, zero, currentPrice);
                } else {
                    Num e0 = currentPrice.multipliedBy(numFactory.one().minus(alpha))
                            .plus(previous.e0.multipliedBy(alpha));
                    Num e1 = currentPrice.minus(e0)
                            .multipliedBy(numFactory.one().minus(beta))
                            .plus(previous.e1.multipliedBy(beta));
                    Num e2 = e0.plus(phaseRatio.multipliedBy(e1))
                            .minus(previous.jma)
                            .multipliedBy(numFactory.one().minus(alpha).pow(2))
                            .plus(previous.e2.multipliedBy(alpha.pow(2)));
                    result = new JmaData(e0, e1, e2, previous.jma.plus(e2));
                }
                if (getBarSeries().getBeginIndex() == beginIndex) {
                    return result;
                }
            }
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " phase: " + phase + " power: " + power;
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    record JmaData(Num e0, Num e1, Num e2, Num jma) {

    }

}
