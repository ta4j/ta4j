package eu.verdelhan.ta4j.indicator.tracker;

import eu.verdelhan.ta4j.TAUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.cache.CachedIndicator;
import eu.verdelhan.ta4j.indicator.helper.HighestValue;
import eu.verdelhan.ta4j.indicator.helper.LowestValue;
import eu.verdelhan.ta4j.indicator.simple.MaxPrice;
import eu.verdelhan.ta4j.indicator.simple.MinPrice;
import java.math.BigDecimal;

public class ParabolicSar extends CachedIndicator<BigDecimal> {

	private double acceleration;

	private final TimeSeries series;

	private BigDecimal extremePoint;

	private final LowestValue<BigDecimal> lowestValueIndicator;

	private final HighestValue<BigDecimal> highestValueIndicator;
	
	private final int timeFrame;
	
	public ParabolicSar(TimeSeries series, int timeFrame) {
		this.acceleration = 0.02d;
		this.series = series;
		this.lowestValueIndicator = new LowestValue<BigDecimal>(new MinPrice(series), timeFrame);
		this.highestValueIndicator = new HighestValue<BigDecimal>(new MaxPrice(series), timeFrame);
		this.timeFrame = timeFrame;
	}

	@Override
	protected BigDecimal calculate(int index) {

		if (index <= 1) {
			extremePoint = series.getTick(index).getClosePrice();
			return extremePoint;
		}

		BigDecimal sar;

		BigDecimal n2ClosePrice = series.getTick(index - 2).getClosePrice();
		BigDecimal n1ClosePrice = series.getTick(index - 1).getClosePrice();
		BigDecimal nClosePrice = series.getTick(index).getClosePrice();

		// trend switch
		if(n2ClosePrice.compareTo(n1ClosePrice) == 1
			&& n1ClosePrice.compareTo(nClosePrice) == -1) {
			sar = extremePoint;
			extremePoint = highestValueIndicator.getValue(index);
			acceleration = 0.02;
		}
		// trend switch
		else if(n2ClosePrice.compareTo(n1ClosePrice) == -1
				&& n1ClosePrice.compareTo(nClosePrice) == 1) {
			
			sar = extremePoint;
			extremePoint = lowestValueIndicator.getValue(index);
			acceleration = 0.02;
			
		}

		//DownTrend
		else if (nClosePrice.compareTo(n1ClosePrice) == -1) {
			BigDecimal lowestValue = lowestValueIndicator.getValue(index);
			if (extremePoint.compareTo(lowestValue) == 1) {
				acceleration = acceleration >= 0.19 ? 0.2 : acceleration + 0.02d;
				extremePoint = lowestValue;
			}
			sar = extremePoint.subtract(getValue(index - 1)).multiply(BigDecimal.valueOf(acceleration), TAUtils.MATH_CONTEXT).add(getValue(index - 1));

			BigDecimal n2MaxPrice = series.getTick(index - 2).getMaxPrice();
			BigDecimal n1MaxPrice = series.getTick(index - 1).getMaxPrice();
			BigDecimal nMaxPrice = series.getTick(index).getMaxPrice();

			if (n1MaxPrice.compareTo(sar) == 1)
				sar = n1MaxPrice;
			else if (n2MaxPrice.compareTo(sar) == 1)
				sar = n2MaxPrice;
			if (nMaxPrice.compareTo(sar) == 1) {
				sar = series.getTick(index).getMinPrice();
			}

		}

		//UpTrend
		else {
			BigDecimal highestValue = highestValueIndicator.getValue(index);
			if (extremePoint.compareTo(highestValue) == -1) {
				acceleration = acceleration >= 0.19 ? 0.2 : acceleration + 0.02;
				extremePoint = highestValue;
			}
			sar = extremePoint.subtract(getValue(index - 1)).multiply(BigDecimal.valueOf(acceleration)).add(getValue(index - 1), TAUtils.MATH_CONTEXT);

			BigDecimal n2MinPrice = series.getTick(index - 2).getMinPrice();
			BigDecimal n1MinPrice = series.getTick(index - 1).getMinPrice();
			BigDecimal nMinPrice = series.getTick(index).getMinPrice();

			if (n1MinPrice.compareTo(sar) == -1)
				sar = n1MinPrice;
			else if (n2MinPrice.compareTo(sar) == -1)
				sar = n2MinPrice;
			if (nMinPrice.compareTo(sar) == -1) {
				sar = series.getTick(index).getMaxPrice();
			}

		}
		return sar;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
