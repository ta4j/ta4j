package eu.verdelhan.tailtest.series;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import eu.verdelhan.tailtest.Tick;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.TimeSeriesLoader;

import org.joda.time.Period;

public class SerializableTimeSeries implements TimeSeries {

	private String name;

	private String seriesAdress;

	private transient TimeSeries series;

	private TimeSeriesLoader loader;

	public SerializableTimeSeries(String name, String seriesAdress, TimeSeriesLoader loader) throws FileNotFoundException, IOException {
		this.name = name;
		this.seriesAdress = seriesAdress;
		this.loader = loader;
		this.series = loader.load(new FileInputStream(seriesAdress), name);
	}

	public String getName() {
		return name;
	}

	public String getSeriesAdress() {
		return seriesAdress;
	}
	
	public TimeSeries getSeries()
	{
		if(series == null) reloadSeries();
		return series;
	}
	
	public TimeSeries reloadSeries()
	{
		try {
			this.series = loader.load(new FileInputStream(seriesAdress), name);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return series;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((seriesAdress == null) ? 0 : seriesAdress.hashCode());
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
		final SerializableTimeSeries other = (SerializableTimeSeries) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (seriesAdress == null) {
			if (other.seriesAdress != null)
				return false;
		} else if (!seriesAdress.equals(other.seriesAdress))
			return false;
		return true;
	}

	public int getBegin() {
		return getSeries().getBegin();
	}

	public int getEnd() {
		return getSeries().getEnd();
	}

	public String getPeriodName() {
		return getSeries().getPeriodName();
	}

	public int getSize() {
		return getSeries().getSize();
	}

	public Tick getTick(int i) {
		return getSeries().getTick(i);
	}

	public Period getPeriod() {
		return new Period(Math.min(series.getTick(series.getBegin() + 1).getDate().getMillis() - series.getTick(series.getBegin()).getDate().getMillis(), 
				series.getTick(series.getBegin() + 2).getDate().getMillis() - series.getTick(series.getBegin() + 1).getDate().getMillis()));
	}
}
