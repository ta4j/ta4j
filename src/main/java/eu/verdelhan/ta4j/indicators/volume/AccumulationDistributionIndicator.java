package eu.verdelhan.ta4j.indicators.volume;


import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;


public class AccumulationDistributionIndicator extends CachedIndicator<Double> {

    private TimeSeries series;

    public AccumulationDistributionIndicator(TimeSeries series) {
        this.series = series;
    }

    @Override
    protected Double calculate(int index) {
        if (index == 0) {
            return 0d;
        }
        Tick tick = series.getTick(index);

        // Calculating the money flow multiplier
		double moneyFlowMultiplier = ((tick.getClosePrice() - tick.getMinPrice()) - (tick.getMaxPrice() - tick.getClosePrice()))
				 / (tick.getMaxPrice() - tick.getMinPrice());

		// Calculating the money flow volume
		double moneyFlowVolume = moneyFlowMultiplier * tick.getVolume();

        return moneyFlowVolume + getValue(index - 1);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
