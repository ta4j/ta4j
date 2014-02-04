package eu.verdelhan.ta4j.series;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import java.util.List;
import org.joda.time.Period;

/**
 * Default implementation of {@link TimeSeries}.
 */
public class DefaultTimeSeries implements TimeSeries {
    private final List<? extends Tick> ticks;

    private final String name;

	/**
	 * @param name the name of the series
	 * @param ticks the list of ticks of the series
	 */
    public DefaultTimeSeries(String name, List<? extends Tick> ticks) {
        this.name = name;
        this.ticks = ticks;
    }

	/**
	 * @param ticks the list of ticks of the series
	 */
    public DefaultTimeSeries(List<? extends Tick> ticks) {
        this("unnamed", ticks);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Tick getTick(int i) {
        return ticks.get(i);
    }

    @Override
    public int getSize() {
        return ticks.size();
    }

    @Override
    public int getBegin() {
        return 0;
    }

    @Override
    public int getEnd() {
        return getSize() - 1;
    }

    @Override
    public String getPeriodName() {
        return ticks.get(0).getEndTime().toString("hh:mm dd/MM/yyyy - ")
                + ticks.get(getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy");
    }

    @Override
    public Period getPeriod() {
        return new Period(Math.min(ticks.get(1).getEndTime().getMillis() - ticks.get(0).getEndTime().getMillis(), ticks
                .get(2).getEndTime().getMillis()
                - ticks.get(1).getEndTime().getMillis()));
    }
}