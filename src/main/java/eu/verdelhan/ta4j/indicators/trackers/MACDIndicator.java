package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Indicator;

/**
 * Moving average convergence divergence (MACDIndicator) indicator.
 */
public class MACDIndicator implements Indicator<Double> {

    private final EMAIndicator shortTermEma;

    private final EMAIndicator longTermEma;

    public MACDIndicator(Indicator<? extends Number> indicator, int shortTimeFrame, int longTimeFrame) {
        if (shortTimeFrame > longTimeFrame) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }
        shortTermEma = new EMAIndicator(indicator, shortTimeFrame);
        longTermEma = new EMAIndicator(indicator, longTimeFrame);
    }

    @Override
    public Double getValue(int index) {
        return shortTermEma.getValue(index).doubleValue() - longTermEma.getValue(index).doubleValue();
    }

}
