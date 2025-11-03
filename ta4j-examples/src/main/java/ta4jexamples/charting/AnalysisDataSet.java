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

import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.XYDataset;

/**
 * Interface for creating analysis datasets from OHLC data for chart
 * visualization.
 *
 * <p>
 * Implementations of this interface should provide methods to transform OHLC
 * (Open, High, Low, Close) data into datasets suitable for charting technical
 * analysis indicators or overlays.
 * </p>
 *
 * @see MovingAverageAnalysisDataSet
 */
public interface AnalysisDataSet {

    /**
     * Creates an analysis dataset from the provided OHLC data.
     *
     * @param data the OHLC dataset to analyze
     * @return a XYDataset containing the analysis data
     * @throws IllegalArgumentException if data is null
     */
    XYDataset getAnalysis(DefaultOHLCDataset data);

}
