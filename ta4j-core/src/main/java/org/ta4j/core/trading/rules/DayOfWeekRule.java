package org.ta4j.core.trading.rules;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.DateTimeIndicator;
import org.ta4j.core.trading.rules.AbstractRule;

public class DayOfWeekRule extends AbstractRule {

	private Set<DayOfWeek> daysOfWeekSet;
	private DateTimeIndicator timeIndicator;
	
	public DayOfWeekRule(DateTimeIndicator timeIndicator, DayOfWeek... daysOfWeek) {
		this.daysOfWeekSet = new HashSet<>(Arrays.asList(daysOfWeek));
		this.timeIndicator = timeIndicator;
	}

	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
		ZonedDateTime dateTime = this.timeIndicator.getValue(index);
		boolean satisfied = daysOfWeekSet.contains(dateTime.getDayOfWeek());

		traceIsSatisfied(index, satisfied);
		return satisfied;
	}
}