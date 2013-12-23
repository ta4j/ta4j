package eu.verdelhan.tailtest.series;

import java.util.List;

import eu.verdelhan.tailtest.Tick;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.joda.time.Period;

/**
 * Implementação default da interface {@link TimeSeries}.
 * 
 * @author Marcio
 * 
 */
public class DefaultTimeSeries implements TimeSeries {
	transient private final List<DefaultTick> ticks;

	private final String name;

	public DefaultTimeSeries(String name, List<DefaultTick> ticks) {
		this.name = name;
		this.ticks = ticks;
	}

	public DefaultTimeSeries(List<DefaultTick> ticks) {
		this(null, ticks);
	}

	public String getName() {
		return name == null ? "not named" : name;
	}

	public Tick getTick(int i) {
		return ticks.get(i);
	}

	public int getSize() {
		return ticks.size();
	}

	public int getBegin() {
		return 0;
	}

	public int getEnd() {
		return getSize() - 1;
	}

	public String getPeriodName() {
		return ticks.get(0).getDate().toString("hh:mm dd/MM/yyyy - ")
				+ ticks.get(this.getEnd()).getDate().toString("hh:mm dd/MM/yyyy");
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((ticks == null) ? 0 : ticks.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final DefaultTimeSeries other = (DefaultTimeSeries) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (ticks == null) {
			if (other.ticks != null)
				return false;
		} else if (!ticks.equals(other.ticks))
			return false;
		return true;
	}

	public Period getPeriod() {
		return new Period(Math.min(ticks.get(1).getDate().getMillis() - ticks.get(0).getDate().getMillis(), ticks
				.get(2).getDate().getMillis()
				- ticks.get(1).getDate().getMillis()));
	}

}
