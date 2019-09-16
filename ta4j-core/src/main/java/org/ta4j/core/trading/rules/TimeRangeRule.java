package org.ta4j.core.trading.rules;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.DateTimeIndicator;
import org.ta4j.core.trading.rules.AbstractRule;

public class TimeRangeRule extends AbstractRule {

	private List<TimeRange> timeRanges;
	private DateTimeIndicator timeIndicator;
	
	public TimeRangeRule(List<TimeRange> timeRanges, DateTimeIndicator beginTimeIndicator) {
		this.timeRanges = timeRanges;
		this.timeIndicator = beginTimeIndicator;
	}
	
	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
		boolean satisfied = false;
		ZonedDateTime dateTime = this.timeIndicator.getValue(index);
    	LocalTime localTime = LocalTime.of(dateTime.getHour(), dateTime.getMinute());
		satisfied = this.timeRanges.stream()
				.anyMatch(timeRange -> 
					localTime.equals(timeRange.getFrom()) || 
					localTime.equals(timeRange.getTo()) ||
					localTime.isAfter(timeRange.getFrom()) && localTime.isBefore(timeRange.getTo())
					);
		traceIsSatisfied(index, satisfied);
		return satisfied;
	}
	
	public static class TimeRange {
		
		private LocalTime from;
		private LocalTime to;
		
		public TimeRange(LocalTime from, LocalTime to) {
			this.from = from;
			this.to = to;
		}
		
		public LocalTime getFrom() {
			return from;
		}
		
		public LocalTime getTo() {
			return to;
		}
	}
}
