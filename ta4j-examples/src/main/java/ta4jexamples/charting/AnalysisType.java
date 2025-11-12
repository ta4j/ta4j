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

/**
 * Enumeration of predefined analysis types for chart visualization.
 *
 * <p>
 * This enum provides commonly used technical analysis overlays that can be
 * applied to candlestick charts. Each analysis type is backed by an
 * {@link AnalysisDataSet} implementation.
 * </p>
 *
 * @see AnalysisDataSet
 * @see MovingAverageAnalysisDataSet
 */
public enum AnalysisType {

    /** 10-period moving average analysis. */
    MOVING_AVERAGE_10(new MovingAverageAnalysisDataSet(10)),

    /** 20-period moving average analysis. */
    MOVING_AVERAGE_20(new MovingAverageAnalysisDataSet(20)),

    /** 50-period moving average analysis. */
    MOVING_AVERAGE_50(new MovingAverageAnalysisDataSet(50)),

    /** 100-period moving average analysis. */
    MOVING_AVERAGE_100(new MovingAverageAnalysisDataSet(100)),

    /** 200-period moving average analysis. */
    MOVING_AVERAGE_200(new MovingAverageAnalysisDataSet(200));

    /** The analysis dataset implementation for this analysis type. */
    public final AnalysisDataSet dataSet;

    /**
     * Constructs an analysis type with the specified dataset.
     *
     * @param dataSet the analysis dataset implementation
     */
    AnalysisType(AnalysisDataSet dataSet) {
        this.dataSet = dataSet;
    }
}
