package eu.verdelhan.ta4j;

import java.io.InputStream;

/**
 * Loader for a time series
 */
public interface TimeSeriesLoader {

	/**
	 * @param stream the input stream
	 * @param seriesName the name of the series
	 * @return the loaded time series
	 */
	TimeSeries load(InputStream stream, String seriesName);

}
