package org.ta4j.core.indicators.averages;

import java.util.HashMap;
import java.util.Map;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Jurik Moving Average (JMA) Indicator. Smooths price data with minimal lag
 * using an adaptive algorithm.
 */
public class JMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator; // Base indicator (e.g., Close Price)
    private final int barCount; // Period for the JMA calculation
    private final double phase; // Phase adjustment (usually between -100 and +100)
    private final double power; // Smoothing power factor (default is 2)
    private final Map<Integer, JmaData> jmaDataMap;

    private final double beta;
    private final double phaseRatio;
    private final double alpha;

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
        this.indicator = indicator;
        this.barCount = barCount;
        this.phase = Math.min(Math.max(phase, -100), 100); // Clamp phase between -100 and 100
        this.power = Math.max(1, power); // Ensure power is at least 1
        this.jmaDataMap = new HashMap<>();

        // Compute smoothing factor based on phase
        beta = 0.45 * (barCount - 1) / (0.45 * (barCount - 1) + 2);
        phaseRatio = (phase < -100 ? 0.5 : (phase > 100 ? 2.5 : phase / 100 + 1.5));
        alpha = Math.pow(beta, power);

        for (int i = 0; i < indicator.getBarSeries().getBarCount(); i++) {
            calculate(i);
        }
    }

    @Override
    protected Num calculate(int index) {

        NumFactory numFactory = indicator.getBarSeries().numFactory();
        Num currentPrice = indicator.getValue(index);

        if (index <= 0) {
            jmaDataMap.put(index, new JmaData(currentPrice.doubleValue(), 0.0, 0.0, currentPrice.doubleValue()));
            return currentPrice;
        }

        JmaData previousJMA = jmaDataMap.get(index -1 );
        
        // Jurik recursive formula
        double e0 = (1 - alpha) * currentPrice.doubleValue() + alpha * previousJMA.e0;
        double e1 = (currentPrice.doubleValue() - e0) * (1 - beta) + beta * previousJMA.e1;
        double e2 = (e0 + phaseRatio * e1 - previousJMA.jma) * Math.pow(1 - alpha, 2)
                + Math.pow(alpha, 2) * previousJMA.e2();

        double jma = previousJMA.jma + e2;

        if (!jmaDataMap.containsKey(index)) {
            jmaDataMap.put(index, new JmaData(e0, e1, e2, jma));
        }

        return numFactory.numOf(jma);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " phase: " + phase + " power: " + power;
    }

    @Override
    public int getUnstableBars() {
        return barCount;
    }
    
    record JmaData(double e0, double e1, double e2, double jma) {
        
    }
    
}