/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import java.util.HashMap;
import java.util.Map;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
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
 *
 */
public class JMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator; // Base indicator (e.g., Close Price)
    private final int barCount; // Period for the JMA calculation
    private final Num phase; // Phase adjustment (usually between -100 and +100)
    private final Num power; // Smoothing power factor (default is 2)
    private final Map<Integer, JmaData> jmaDataMap;
    private final NumFactory numFactory;
    private final Num beta;
    private final Num phaseRatio;
    private final Num alpha;

    /**
     * Constructor.
     *
     * @param indicator The input indicator (e.g., Close Price).
     * @param barCount  The number of periods for the JMA.
     * @param phase     The phase adjustment (-100 to +100).
     * @param power     The smoothing power factor (default is 2).
     */
    public JMAIndicator(Indicator<Num> indicator, int barCount, double phase, double power) {
        super(indicator.getBarSeries());
        this.numFactory = indicator.getBarSeries().numFactory();
        this.indicator = indicator;
        this.barCount = barCount;
        this.phase = numFactory.numOf(Math.min(Math.max(phase, -100), 100)); // Clamp phase between -100 and 100
        this.power = numFactory.numOf(Math.max(1, power)); // Ensure power is at least 1
        this.jmaDataMap = new HashMap<>();

        // Compute smoothing factor based on phase
        beta = numFactory.numOf(0.45)
                .multipliedBy(numFactory.numOf(barCount - 1))
                .dividedBy(numFactory.numOf(0.45).multipliedBy(numFactory.numOf(barCount - 1)).plus(numFactory.two()));

        phaseRatio = this.phase.isLessThan(numFactory.numOf(-100)) ? numFactory.numOf(0.5)
                : (this.phase.isGreaterThan(numFactory.numOf(100)) ? numFactory.numOf(2.5)
                        : this.phase.dividedBy(numFactory.numOf(100)).plus(numFactory.numOf(1.5)));

        alpha = beta.pow(this.power);

        for (int i = indicator.getBarSeries().getBeginIndex(); i < indicator.getBarSeries().getBarCount(); i++) {
            calculate(i);
        }
    }

    @Override
    protected Num calculate(int index) {

        NumFactory numFactory = indicator.getBarSeries().numFactory();
        Num currentPrice = indicator.getValue(index);
        Num zero = numFactory.zero();

        if (index <= 0) {
            jmaDataMap.put(index, new JmaData(currentPrice, zero, zero, currentPrice));
            return currentPrice;
        }

        JmaData previousJMA = jmaDataMap.get(index - 1);

        Num e0 = calculateE0(numFactory, currentPrice, previousJMA);
        Num e1 = calculateE1(numFactory, currentPrice, previousJMA, e0);
        Num e2 = calculateE2(numFactory, previousJMA, e0, e1);

        Num jma = previousJMA.jma.plus(e2);

        if (!jmaDataMap.containsKey(index)) {
            jmaDataMap.put(index, new JmaData(e0, e1, e2, jma));
        }

        return jma;
    }

    private Num calculateE0(NumFactory numFactory, Num currentPrice, JmaData previousJMA) {
        Num e0 = currentPrice.multipliedBy(numFactory.one().minus(alpha)).plus(previousJMA.e0.multipliedBy(alpha));
        return e0;
    }

    private Num calculateE1(NumFactory numFactory, Num currentPrice, JmaData previousJMA, Num e0) {
        Num e1 = currentPrice.minus(e0)
                .multipliedBy(numFactory.one().minus(beta))
                .plus(previousJMA.e1.multipliedBy(beta));
        return e1;
    }

    /*
     * Since previous e2 and previous JMA has to be used to calculate e2 I didn't
     * see a way to abstract this out to a separate indicator which would have made
     * JMA so much simpler to calculate and we wouldn't need the internal cache.
     */
    private Num calculateE2(NumFactory numFactory, JmaData previousJMA, Num e0, Num e1) {
        Num e2 = e0.plus(phaseRatio.multipliedBy(e1))
                .minus(previousJMA.jma)
                .multipliedBy(numFactory.one().minus(alpha).pow(2))
                .plus(previousJMA.e2.multipliedBy(alpha.pow(2)));
        return e2;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " phase: " + phase + " power: " + power;
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    record JmaData(Num e0, Num e1, Num e2, Num jma) {

    }

}