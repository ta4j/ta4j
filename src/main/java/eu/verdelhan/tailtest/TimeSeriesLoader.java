package net.sf.tail;

import java.io.InputStream;

public interface TimeSeriesLoader {
	TimeSeries load(InputStream stream, String seriesName);
}
