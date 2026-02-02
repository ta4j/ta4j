/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
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

    /**
     * Defines which summary value to return from the simulated drawdowns.
     *
     * @since 0.19
     */
    public enum Statistic {
        MEDIAN, P95, P99, MEAN, MIN, MAX
    }

    private final int iterations;
    private final Integer pathBlocks;
    private final Supplier<RandomGenerator> randomSupplier;
    private final Statistic statistic;

    /**
     * Default constructor returning the 95th percentile.
     *
     * @since 0.19
     */
    public MonteCarloMaximumDrawdownCriterion() {
        this(10_000, null, () -> new SplittableRandom(42L), Statistic.P95);
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
    public MonteCarloMaximumDrawdownCriterion(int iterations, Integer pathBlocks, long seed, Statistic statistic) {
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
            Supplier<RandomGenerator> randomSupplier, Statistic statistic) {
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
        var result = switch (statistic) {
        case MEDIAN -> percentile(maxDrawdowns, 0.5);
        case P95 -> percentile(maxDrawdowns, 0.95);
        case P99 -> percentile(maxDrawdowns, 0.99);
        case MEAN -> Arrays.stream(maxDrawdowns).average().orElse(0);
        case MIN -> Arrays.stream(maxDrawdowns).min().orElse(0);
        case MAX -> Arrays.stream(maxDrawdowns).max().orElse(0);
        };
        return numFactory.numOf(result);
    }

    private static double percentile(double[] values, double level) {
        if (values.length == 0) {
            return 0;
        }
        var sorted = values.clone();
        Arrays.sort(sorted);
        var index = (int) Math.ceil(level * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index];
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