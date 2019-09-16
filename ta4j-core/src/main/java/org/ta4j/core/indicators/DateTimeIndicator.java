package org.ta4j.core.indicators;

import java.time.ZonedDateTime;
import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class DateTimeIndicator extends CachedIndicator<ZonedDateTime> {

	private Function<Bar, ZonedDateTime> action;
	
    public DateTimeIndicator(BarSeries barSeries, Function<Bar, ZonedDateTime> action) {
        super(barSeries);
        this.action = action;
    }

    @Override
    protected ZonedDateTime calculate(int index) {
    	Bar bar = getBarSeries().getBar(index);
    	return this.action.apply(bar);
    }
}
