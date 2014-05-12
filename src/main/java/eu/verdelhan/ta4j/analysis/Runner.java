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
package eu.verdelhan.ta4j.analysis;

import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import java.util.ArrayList;
import java.util.List;

/**
 * History runner.
 * <p>
 * The runner object is used to run {@link Strategy trading strategies} over a {@link TimeSeries time series}.
 */
public class Runner {

    /** Operation type */
    private OperationType operationType;
    /** Time series slicer */
    private TimeSeriesSlicer slicer;
    /** Run/executed strategy */
    private Strategy strategy;
    /** Cached trade results */
    private ArrayList<List<Trade>> listTradesResults;

    /**
     * Constructor.
     * @param type the initial {@link OperationType operation type} of new {@link Trade trades}
     * @param slicer a {@link TimeSeriesSlicer time series slicer}
     * @param strategy a {@link Strategy strategy} to be run
     */
    public Runner(OperationType type, TimeSeriesSlicer slicer, Strategy strategy) {
        if ((type == null) || (slicer == null) || (strategy == null)) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        this.slicer = slicer;
        this.strategy = strategy;
        operationType = type;
        listTradesResults = new ArrayList<List<Trade>>();
    }

    /**
     * Constructor.
     * @param slicer a {@link TimeSeriesSlicer time series slicer}
     * @param strategy a {@link Strategy strategy} to be run
     */
    public Runner(TimeSeriesSlicer slicer, Strategy strategy) {
        this(OperationType.BUY, slicer, strategy);
    }

    /**
     * Executes the runner.
     * @param sliceIndex the series slice index
     * @return the list of trades corresponding to sliceIndex
     */
    public List<Trade> run(int sliceIndex) {
        if (listTradesResults.size() < sliceIndex) {
            // The slice index is over the cached trades results
            // --> Running strategy on the previous slice
            listTradesResults.add(run(sliceIndex - 1));
        } else if (listTradesResults.size() > sliceIndex) {
            // The slice index is in the cached results.
            // --> Returning them
            return listTradesResults.get(sliceIndex);
        }

        int beginIndex = 0;
        int endIndex = 0;

        if (listTradesResults.isEmpty()) {
            beginIndex = slicer.getSlice(sliceIndex).getBegin();
            endIndex = slicer.getSlice(sliceIndex).getEnd();
        } else {

            endIndex = slicer.getSlice(sliceIndex).getEnd();

            int i = listTradesResults.size() - 1;
            List<Trade> lastTrades = listTradesResults.get(i);
            while ((lastTrades.isEmpty()) && (i > 0)) {
                i--;
                lastTrades = listTradesResults.get(i);
            }

            if (i > 0) {
                Trade lastTrade = lastTrades.get(lastTrades.size() - 1);
                beginIndex = lastTrade.getExit().getIndex() + 1;

                if (beginIndex > endIndex) {
                    return new ArrayList<Trade>();
                }
            } else {
                beginIndex = slicer.getSlice(sliceIndex).getBegin();
            }
        }
        List<Trade> trades = runStrategy(beginIndex, endIndex, sliceIndex);
        listTradesResults.add(trades);
        return trades;
    }

    /**
     * Runs the strategy.
     * @param beginIndex the begin index of the series
     * @param endIndex the end index of the series
     * @param sliceIndex the slice index
     * @return a list of trades
     */
    private List<Trade> runStrategy(int beginIndex, int endIndex, int sliceIndex) {

        List<Trade> trades = new ArrayList<Trade>();
        Trade lastTrade = new Trade(operationType);
        for (int i = Math.max(beginIndex, 0); i <= endIndex; i++) {
            if (strategy.shouldOperate(lastTrade, i)) {
                lastTrade.operate(i);
                if (lastTrade.isClosed()) {
                    trades.add(lastTrade);
                    lastTrade = new Trade(operationType);
                }
            }
        }

        if (lastTrade.isOpened()) {
            int j = 1;
            while (slicer.getNumberOfSlices() > (sliceIndex + j)) {
                int start = Math.max(slicer.getSlice(sliceIndex + j).getBegin(), endIndex);
                int last = slicer.getSlice(sliceIndex + j).getEnd();

                for (int i = start; i <= last; i++) {
                    if (strategy.shouldOperate(lastTrade, i)) {
                        lastTrade.operate(i);
                        break;
                    }
                }
                if (lastTrade.isClosed()) {
                    trades.add(lastTrade);
                    break;
                }
                j++;
            }
        }
        return trades;
    }
}
