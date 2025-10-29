/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package ta4jexamples.charting;

import org.jfree.data.time.MovingAverage;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.XYDataset;

/**
 * Implementation of {@link AnalysisDataSet} that creates a moving average
 * analysis for chart visualization.
 *
 * <p>
 * This class generates a moving average dataset from OHLC data using
 * JFreeChart's MovingAverage utility. The moving average period is specified in
 * days.
 * </p>
 *
 * @see AnalysisDataSet
 * @see MovingAverage
 */
public class MovingAverageAnalysisDataSet implements AnalysisDataSet {

    /** Number of milliseconds in a day. */
    public static final long MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000L;

    /** The time period for the moving average in days. */
    public final int timePeriod;

    /**
     * Constructs a new MovingAverageAnalysisDataSet.
     *
     * @param timePeriod the time period for the moving average in days
     * @throws IllegalArgumentException if timePeriod is not positive
     */
    public MovingAverageAnalysisDataSet(int timePeriod) {
        if (timePeriod <= 0) {
            throw new IllegalArgumentException("Time period must be positive, got: " + timePeriod);
        }
        this.timePeriod = timePeriod;
    }

    /**
     * Creates a moving average dataset from the provided OHLC data.
     *
     * @param data the OHLC dataset to create moving average from
     * @return a XYDataset containing the moving average data
     * @throws IllegalArgumentException if data is null
     */
    @Override
    public XYDataset getAnalysis(DefaultOHLCDataset data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        Long movingAveragePeriod = this.timePeriod * MILLISECONDS_PER_DAY;
        String label = "-" + this.timePeriod + "MA";
        XYDataset dataSet = MovingAverage.createMovingAverage(data, label, movingAveragePeriod, 0L);
        return dataSet;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MovingAverageAnalysisDataSet that = (MovingAverageAnalysisDataSet) obj;
        return this.timePeriod == that.timePeriod;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(this.timePeriod);
    }

    @Override
    public String toString() {
        return "MovingAverageAnalysisDataSet{" + "timePeriod=" + timePeriod + '}';
    }
}
