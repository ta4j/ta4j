package net.sf.tail;

import org.joda.time.DateTime;

public interface Tick {
	public DateTime getDate();
	public String getDateName();
	public String getSimpleDateName();
	public double getClosePrice();
	public double getOpenPrice();
	public int getTrades();
	public double getMaxPrice();
	public double getAmount();
	public double getVolume();
	public double getVariation();
	public double getMinPrice();
	public double getPreviousPrice();
}
