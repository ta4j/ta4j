package net.sf.tail.sample;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.TimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.joda.time.DateTime;
import org.joda.time.Period;

public class SampleTimeSeries implements TimeSeries {

	private List<DefaultTick> ticks;

	public SampleTimeSeries(double... data) {
		ticks = new ArrayList<DefaultTick>();
		for (int i = 0; i < data.length; i++) {
			ticks.add(new DefaultTick(new DateTime().withMillisOfSecond(i),data[i]));
		}
	}

	public SampleTimeSeries(List<DefaultTick> ticks) {
		this.ticks = ticks;
	}

	public SampleTimeSeries(double[] data, DateTime[] times) {
		if (data.length != times.length) {
			throw new IllegalArgumentException();
		}
		ticks = new ArrayList<DefaultTick>();
		for (int i = 0; i < data.length; i++) {
			ticks.add(new DefaultTick(data[i], times[i]));
		}
	}

	public SampleTimeSeries(DateTime... dates) {
		ticks = new ArrayList<DefaultTick>();
		int i = 1;
		for (DateTime date : dates) {
			ticks.add(new DefaultTick(date, i++));
		}
	}

	public SampleTimeSeries() {
		ticks = new ArrayList<DefaultTick>();
		for (double i = 0d; i < 10; i++) {
			DefaultTick tick = new DefaultTick(new DateTime(0), i, i + 1, i + 2, i + 3, i + 4, i + 5, i + 6, i + 7, (int) (i + 8));
			ticks.add(tick);
		}
	}

	public DefaultTick getTick(int i) {
		return ticks.get(i);
	}

	public int getSize() {
		return ticks.size();
	}

	public int getBegin() {
		return 0;
	}

	public int getEnd() {
		return ticks.size() - 1;
	}

	public String getName() {
		return "SampleTimeSeries";
	}

	public String getPeriodName() {
		return ticks.get(0).getDate().toString("hh:mm dd/MM/yyyy - ")
				+ ticks.get(this.getEnd()).getDate().toString("hh:mm dd/MM/yyyy");
	}

	public Period getPeriod() {
		return new Period(Math.min(ticks.get(1).getDate().getMillis() - ticks.get(0).getDate().getMillis(), 
				ticks.get(2).getDate().getMillis()- ticks.get(1).getDate().getMillis()));
	}
}
