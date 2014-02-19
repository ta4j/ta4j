package eu.verdelhan.ta4j.indicators.trackers;


import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.helpers.HighestValueIndicator;
import eu.verdelhan.ta4j.indicators.helpers.LowestValueIndicator;
import eu.verdelhan.ta4j.indicators.simple.MaxPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MinPriceIndicator;


public class ParabolicSarIndicator extends CachedIndicator<Double> {

	private double acceleration;

	private final TimeSeries series;

	private double extremePoint;

	private final LowestValueIndicator lowestValueIndicator;

	private final HighestValueIndicator highestValueIndicator;
	
	private final int timeFrame;
	
	public ParabolicSarIndicator(TimeSeries series, int timeFrame) {
		this.acceleration = 0.02d;
		this.series = series;
		this.lowestValueIndicator = new LowestValueIndicator(new MinPriceIndicator(series), timeFrame);
		this.highestValueIndicator = new HighestValueIndicator(new MaxPriceIndicator(series), timeFrame);
		this.timeFrame = timeFrame;
	}

	@Override
	protected Double calculate(int index) {

		if (index <= 1) {
			extremePoint = series.getTick(index).getClosePrice();
			return extremePoint;
		}

		double sar;

		double n2ClosePrice = series.getTick(index - 2).getClosePrice();
		double n1ClosePrice = series.getTick(index - 1).getClosePrice();
		double nClosePrice = series.getTick(index).getClosePrice();

		// trend switch
		if(n2ClosePrice > n1ClosePrice && n1ClosePrice < nClosePrice) {
			sar = extremePoint;
			extremePoint = highestValueIndicator.getValue(index);
			acceleration = 0.02;
		}
		// trend switch
		else if(n2ClosePrice < n1ClosePrice && n1ClosePrice > nClosePrice) {
			
			sar = extremePoint;
			extremePoint = lowestValueIndicator.getValue(index);
			acceleration = 0.02;
			
		}

		//DownTrend
		else if (nClosePrice < n1ClosePrice) {
			double lowestValue = lowestValueIndicator.getValue(index);
			if (extremePoint > lowestValue) {
				acceleration = acceleration >= 0.19 ? 0.2 : acceleration + 0.02d;
				extremePoint = lowestValue;
			}
			sar = (extremePoint - getValue(index - 1)) * acceleration + getValue(index - 1);

			double n2MaxPrice = series.getTick(index - 2).getMaxPrice();
			double n1MaxPrice = series.getTick(index - 1).getMaxPrice();
			double nMaxPrice = series.getTick(index).getMaxPrice();

			if (n1MaxPrice > sar)
				sar = n1MaxPrice;
			else if (n2MaxPrice > sar)
				sar = n2MaxPrice;
			if (nMaxPrice > sar) {
				sar = series.getTick(index).getMinPrice();
			}

		}

		//UpTrend
		else {
			double highestValue = highestValueIndicator.getValue(index);
			if (extremePoint < highestValue) {
				acceleration = acceleration >= 0.19 ? 0.2 : acceleration + 0.02;
				extremePoint = highestValue;
			}
			sar = (extremePoint - getValue(index - 1)) * acceleration + getValue(index - 1);

			double n2MinPrice = series.getTick(index - 2).getMinPrice();
			double n1MinPrice = series.getTick(index - 1).getMinPrice();
			double nMinPrice = series.getTick(index).getMinPrice();

			if (n1MinPrice < sar)
				sar = n1MinPrice;
			else if (n2MinPrice < sar)
				sar = n2MinPrice;
			if (nMinPrice < sar) {
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
