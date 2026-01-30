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
package org.ta4j.core.criteria.drawdown;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.helpers.Statistics;
import org.ta4j.core.num.Num;

/**
 * Estimates the range of maximum drawdowns by randomly re-ordering past trades
 * and stitching their equity paths together.
 * <p>
 * The criterion value is taken from the distribution of simulated drawdowns (by
 * default, the 95th percentile).
 *
 * @since 0.19
 */
public class MonteCarloMaximumDrawdownCriterion extends AbstractAnalysisCriterion {

    private final int iterations;
    private final Integer pathBlocks;
    private final Supplier<RandomGenerator> randomSupplier;
    private final Statistics statistic;

    /**
     * Default constructor returning the 95th percentile.
     *
     * @since 0.19
     */
    public MonteCarloMaximumDrawdownCriterion() {
        this(10_000, null, () -> new SplittableRandom(42L), Statistics.P95);
    }

    /**
     * Constructor.
     *
     * @param iterations number of random simulations to run
     * @param pathBlocks number of trades to include in each simulated path
     *                   ({@code null} = use the number of trades in the sample)
     * @param seed       random seed for reproducibility
     * @param statistic  which summary statistic of the simulated drawdowns to
     *                   return
     *
     * @since 0.19
     */
    public MonteCarloMaximumDrawdownCriterion(int iterations, Integer pathBlocks, long seed, Statistics statistic) {
        this(iterations, pathBlocks, () -> new SplittableRandom(seed), statistic);
    }

    /**
     * Constructor allowing to supply a custom random number generator.
     *
     * @param iterations     number of random simulations to run
     * @param pathBlocks     number of trades to include in each simulated path
     *                       ({@code null} = use the number of trades in the sample)
     * @param randomSupplier supplier of the random generator used for simulations
     * @param statistic      which summary statistic of the simulated drawdowns to
     *                       return
     *
     * @since 0.19
     */
    public MonteCarloMaximumDrawdownCriterion(int iterations, Integer pathBlocks,
            Supplier<RandomGenerator> randomSupplier, Statistics statistic) {
        this.iterations = iterations;
        this.pathBlocks = pathBlocks;
        this.randomSupplier = randomSupplier;
        this.statistic = statistic;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || !position.isClosed()) {
            return series.numFactory().zero();
        }
        return new MaximumDrawdownCriterion().calculate(series, position);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var blocks = buildBlocks(series, tradingRecord);
        if (blocks.size() < 3) {
            return new MaximumDrawdownCriterion().calculate(series, tradingRecord);
        }
        var blocksPerPath = pathBlocks != null ? pathBlocks : blocks.size();
        var random = randomSupplier.get();
        var maxDrawdowns = new double[iterations];
        var numFactory = series.numFactory();
        var one = numFactory.one();
        for (var iteration = 0; iteration < iterations; iteration++) {
            var equity = one;
            var peak = one;
            var maxDrawdown = numFactory.zero();
            for (var blockIndex = 0; blockIndex < blocksPerPath; blockIndex++) {
                var block = blocks.get(random.nextInt(blocks.size()));
                for (var relativeReturn : block) {
                    equity = equity.multipliedBy(one.plus(relativeReturn));
                    if (equity.isGreaterThan(peak)) {
                        peak = equity;
                    } else {
                        var drawdown = peak.minus(equity).dividedBy(peak);
                        if (drawdown.isGreaterThan(maxDrawdown)) {
                            maxDrawdown = drawdown;
                        }
                    }
                }
            }
            maxDrawdowns[iteration] = maxDrawdown.doubleValue();
        }
        var result = Statistics.calculate(maxDrawdowns, statistic);
        return numFactory.numOf(result);
    }

    private List<List<Num>> buildBlocks(BarSeries series, TradingRecord record) {
        var blocks = new ArrayList<List<Num>>();
        var cashFlow = new CashFlow(series, record);
        var one = series.numFactory().one();
        for (var position : record.getPositions()) {
            if (!position.isClosed()) {
                continue;
            }
            var entryIndex = position.getEntry().getIndex();
            var exitIndex = position.getExit().getIndex();
            var block = new ArrayList<Num>();
            var previousEquity = entryIndex > 0 ? cashFlow.getValue(entryIndex - 1) : one;
            for (int i = entryIndex; i <= exitIndex; i++) {
                var currentEquity = cashFlow.getValue(i);
                block.add(currentEquity.dividedBy(previousEquity).minus(one));
                previousEquity = currentEquity;
            }
            if (!block.isEmpty()) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }

}
