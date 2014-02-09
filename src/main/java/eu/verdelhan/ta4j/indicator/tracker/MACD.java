package eu.verdelhan.ta4j.indicator.tracker;

import eu.verdelhan.ta4j.Indicator;

/**
 * Moving average convergence divergence (MACD) indicator.
 */
public class MACD implements Indicator<Double> {

    private final EMA shortTermEma;

    private final EMA longTermEma;

    public MACD(Indicator<? extends Number> indicator, int shortTimeFrame, int longTimeFrame) {
        if (shortTimeFrame > longTimeFrame) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }
        shortTermEma = new EMA(indicator, shortTimeFrame);
        longTermEma = new EMA(indicator, longTimeFrame);
    }

    @Override
    public Double getValue(int index) {
        return shortTermEma.getValue(index).doubleValue() - longTermEma.getValue(index).doubleValue();
    }

}
