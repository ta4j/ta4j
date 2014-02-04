package eu.verdelhan.ta4j.indicator.volume;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.cache.CachedIndicator;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class AccumulationDistribution extends CachedIndicator<BigDecimal> {

    private TimeSeries series;

    public AccumulationDistribution(TimeSeries series) {
        this.series = series;
    }

    @Override
    protected BigDecimal calculate(int index) {
        if (index == 0) {
            return BigDecimal.ZERO;
        }
        Tick tick = series.getTick(index);

        // Calculating the money flow multiplier
		BigDecimal moneyFlowMultiplier = tick.getClosePrice().subtract(tick.getMinPrice()).subtract(tick.getMaxPrice().subtract(tick.getClosePrice()))
				.divide(tick.getMaxPrice().subtract(tick.getMinPrice()), RoundingMode.HALF_UP);

		// Calculating the money flow volume
		BigDecimal moneyFlowVolume = moneyFlowMultiplier.multiply(tick.getVolume());

        return moneyFlowVolume.add(getValue(index - 1));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
