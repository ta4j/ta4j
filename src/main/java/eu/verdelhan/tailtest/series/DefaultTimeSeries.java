package eu.verdelhan.tailtest.series;

import eu.verdelhan.tailtest.Tick;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;
import java.util.List;
import org.joda.time.Period;

/**
 * Default implementation of {@link TimeSeries}.
 */
public class DefaultTimeSeries implements TimeSeries {
    private final List<DefaultTick> ticks;

    private final String name;

	/**
	 * @param name the name of the series
	 * @param ticks the list of ticks of the series
	 */
    public DefaultTimeSeries(String name, List<DefaultTick> ticks) {
        this.name = name;
        this.ticks = ticks;
    }

	/**
	 * @param ticks the list of ticks of the series
	 */
    public DefaultTimeSeries(List<DefaultTick> ticks) {
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
        return ticks.get(0).getDate().toString("hh:mm dd/MM/yyyy - ")
                + ticks.get(getEnd()).getDate().toString("hh:mm dd/MM/yyyy");
    }

    @Override
    public Period getPeriod() {
        return new Period(Math.min(ticks.get(1).getDate().getMillis() - ticks.get(0).getDate().getMillis(), ticks
                .get(2).getDate().getMillis()
                - ticks.get(1).getDate().getMillis()));
    }
}