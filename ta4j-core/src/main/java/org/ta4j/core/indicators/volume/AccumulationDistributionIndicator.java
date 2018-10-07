package org.ta4j.core.indicators.volume;


import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.helpers.CloseLocationValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Accumulation-distribution indicator.
 * </p>
 */
public class AccumulationDistributionIndicator extends RecursiveCachedIndicator<Num> {

    private CloseLocationValueIndicator clvIndicator;

    public AccumulationDistributionIndicator(TimeSeries series) {
        super(series);
        this.clvIndicator = new CloseLocationValueIndicator(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(0);
        }

        // Calculating the money flow multiplier
        Num moneyFlowMultiplier = clvIndicator.getValue(index);

        // Calculating the money flow volume
        Num moneyFlowVolume = moneyFlowMultiplier.multipliedBy(getTimeSeries().getBar(index).getVolume());

        return moneyFlowVolume.plus(getValue(index - 1));
    }
}
