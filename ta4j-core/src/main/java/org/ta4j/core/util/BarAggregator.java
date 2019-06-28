package org.ta4j.core;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.num.Num;

public class BarAggregator {
	
	private BarAggregator() {}

	/**
	 * Aggregates the bars of the <code>timeSeries</code> by
	 * <code>timePeriod</code>. The new <code>timePeriod</code> must be a
	 * multiplication of the actual time period.
	 * 
	 * @param timeSeries the actual TimeSeries
	 * @param timePeriod the actual timePeriod
	 * @return the aggregated TimeSeries
	 */
	public static TimeSeries aggregateBarSeries(TimeSeries timeSeries, Duration timePeriod) {
		String name = timeSeries.getName() + "_upscaled_to_" + timePeriod;
		return aggregateTimeSeries(name, timeSeries, timePeriod);
	}

	/**
	 * Aggregates the bars of the <code>timeSeries</code> by
	 * <code>timePeriod</code>. The new <code>timePeriod</code> must be a
	 * multiplication of the actual time period.
	 * 
	 * @param name       the name of the returned TimeSeries
	 * @param timeSeries the actual TimeSeries
	 * @param timePeriod the actual timePeriod
	 * @return the aggregated TimeSeries
	 */
	public static TimeSeries aggregateTimeSeries(String name, TimeSeries timeSeries, Duration timePeriod) {
		List<Bar> sumBars = aggregateBars(timeSeries.getBarData(), timePeriod);
		return new BaseTimeSeries(name, sumBars);
	}

	/**
	 * Aggregates a list of bars by <code>timePeriod</code>.The new
	 * <code>timePeriod</code> must be a multiplication of the actual time period.
	 * 
	 * @param bars       the actual bars
	 * @param timePeriod the new timePeriod
	 * @return the aggregated bars with new <code>timePeriod</code>
	 */
	public static List<Bar> aggregateBars(List<Bar> bars, Duration timePeriod) {
		List<Bar> sumBars = new ArrayList<>();
		if (bars.isEmpty())
			return sumBars;
		// get the actual time period
		Duration actualDur = bars.iterator().next().getTimePeriod();
		// check if new timePeriod is a multiplication of actual time period
		boolean isNotMultiplication = timePeriod.getSeconds() % actualDur.getSeconds() != 0;

		if (isNotMultiplication) {
			throw new IllegalArgumentException(
					"Cannot aggregate bars: the new timePeriod must be a multiplication of the actual timePeriod.");
		}

		int i = 0;
		Num zero = bars.iterator().next().getOpenPrice().numOf(0);
		while (i < bars.size()) {
			Bar b1 = bars.get(i);
			ZonedDateTime beginTime = b1.getBeginTime();
			Num open = b1.getOpenPrice();
			Num high = b1.getHighPrice();
			Num low = b1.getLowPrice();

			// set to ZERO
			Num close = zero;
			Num volume = zero;
			Num amount = zero;
			Duration sumDur = Duration.ZERO;

			while (sumDur.compareTo(timePeriod) < 0) {
				if (i < bars.size()) {
					Bar b2 = bars.get(i);

					if (b2.getHighPrice().isGreaterThan(high)) {
						high = b2.getHighPrice();
					}
					if (b2.getLowPrice().isLessThan(low)) {
						low = b2.getLowPrice();
					}
					close = b2.getClosePrice();
					volume = volume.plus(b2.getVolume());
					amount = amount.plus(b2.getAmount());
				}
				sumDur = sumDur.plus(actualDur);
				i++;
			}

			// add only bars with elapsed timePeriod
			if (i <= bars.size()) {
				Bar b = new BaseBar(timePeriod, beginTime.plus(timePeriod), open, high, low, close, volume, amount);
				sumBars.add(b);
			}
		}

		return sumBars;
	}

}
