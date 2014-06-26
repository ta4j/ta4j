/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ta4jexamples.slicers;

import eu.verdelhan.ta4j.TimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;
import ta4jexamples.loaders.CsvTicksLoader;

/**
 * This class splits a time series into sub-series (slices).
 */
public class SeriesSlicer {

    /**
     * Splits a time series into subseries containing nbTicks ticks each.<br>
     * The last subseries may have less ticks than nbTicks.
     * @param series the time series
     * @param nbTicks the number of ticks of each subseries
     * @return a list of subseries
     */
    public static List<TimeSeries> split(TimeSeries series, int nbTicks) {
        ArrayList<TimeSeries> subseries = new ArrayList<TimeSeries>();
        if (series != null) {
            for (int i = series.getBegin(); i <= series.getEnd(); i += nbTicks) {
                // For each nbTicks ticks
                int subseriesBegin = i;
                int subseriesEnd = Math.min(subseriesBegin + nbTicks, series.getEnd());
                subseries.add(series.subseries(subseriesBegin, subseriesEnd));
            }
        }
        return subseries;
    }

    /**
     * Splits a time series into subseries lasting duration.<br>
     * The last subseries may last less than duration.
     * @param series the time series
     * @param duration the duration of each subseries
     * @return a list of subseries
     */
    public static List<TimeSeries> split(TimeSeries series, Period duration) {
        ArrayList<TimeSeries> subseries = new ArrayList<TimeSeries>();
        if (series != null && duration != null && !duration.equals(Period.ZERO)) {

            // Building the interval of the first subseries
            DateTime beginInterval = series.getTick(series.getBegin()).getEndTime();
            DateTime endInterval = beginInterval.plus(duration);
            Interval subseriesInterval = new Interval(beginInterval, endInterval);

            // Subseries begin and end indexes
            int subseriesBegin = series.getBegin();
            int subseriesNbTicks = 0;

            for (int i = series.getBegin(); i <= series.getEnd(); i++) {
                // For each tick...
                DateTime tickTime = series.getTick(i).getEndTime();
                if (subseriesInterval.contains(tickTime)) {
                    // Tick in the interval
                    // --> Incrementing the number of ticks in the subseries
                    subseriesNbTicks++;
                } else {
                    // Tick out of the interval
                    if (!endInterval.isAfter(tickTime)) {
                        // Tick after the interval
                        // --> Building and adding the previous subseries
                        subseries.add(series.subseries(subseriesBegin, subseriesBegin + subseriesNbTicks - 1));
                        // --> Clearing counters for new subseries
                        subseriesBegin = i;
                        subseriesNbTicks = 1;
                    }

                    // Building the next interval
                    beginInterval = endInterval;
                    endInterval = beginInterval.plus(duration);
                    subseriesInterval = new Interval(beginInterval, endInterval);
                }

            }
            // Building and adding the last subseries
            subseries.add(series.subseries(subseriesBegin, subseriesBegin + subseriesNbTicks - 1));
        }
        return subseries;
    }

    public static void main(String[] args) {

        TimeSeries series = CsvTicksLoader.loadAppleIncSeries();

        // Splitting into 1-month duration sub-series
        List<TimeSeries> monthSeries = split(series, Period.months(1));
        System.out.println("Number of sub-series: " + monthSeries.size());
        System.out.println("First sub-series: " + monthSeries.get(0).getPeriodName());
        System.out.println("Last sub-series: " + monthSeries.get(monthSeries.size() - 1).getPeriodName());

        // Splitting into 20-ticks sub-series
        List<TimeSeries> subseries = split(series, 20);
        System.out.println("Number of sub-series: " + subseries.size());
        System.out.println("First sub-series: " + subseries.get(0).getPeriodName());
        System.out.println("Last sub-series: " + subseries.get(subseries.size() - 1).getPeriodName());
    }
}
