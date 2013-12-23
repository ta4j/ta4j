package eu.verdelhan.tailtest;

import eu.verdelhan.tailtest.tick.DefaultTick;

import org.joda.time.Period;


/**
 * Time Series é um conjunto de {@link DefaultTick} ordenados por um determinado
 * período temporal.
 * 
 * @author Marcio
 * 
 */
public interface TimeSeries {

	Tick getTick(int i);

	int getSize();

	int getBegin();

	int getEnd();

	String getName();

	String getPeriodName();	
	
	Period getPeriod();
}
