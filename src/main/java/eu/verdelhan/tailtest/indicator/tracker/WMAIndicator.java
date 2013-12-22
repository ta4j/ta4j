package net.sf.tail.indicator.tracker;

import net.sf.tail.Indicator;

public class WMAIndicator implements Indicator<Double> {

	private int timeFrame;

	private Indicator<? extends Number> indicator;

	public WMAIndicator(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	public String getName() {
		return String.format(getClass().getSimpleName() + " timeFrame: %s", timeFrame);
	}

	public Double getValue(int index) {
		if(index == 0) return indicator.getValue(0).doubleValue();
		double value = 0;
		if(index - timeFrame < 0) {
			
			for(int i = index + 1; i > 0; i--) {
				value += i * indicator.getValue(i-1).doubleValue();
				
			}
			 return value / (((index + 1) * (index + 2)) / 2);
		}
		
		int actualIndex = index;
		for(int i = timeFrame; i > 0; i--) {
			value += i * indicator.getValue(actualIndex).doubleValue();
			actualIndex--;
		}
		return value / ((timeFrame * (timeFrame + 1)) / 2);
	}

}
