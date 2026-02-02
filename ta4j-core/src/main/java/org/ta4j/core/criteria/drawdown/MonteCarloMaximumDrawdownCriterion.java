/*
 * SPDX-License-Identifier: MIT
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
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.AbstractEquityCurveSettingsCriterion;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.Statistics;
import org.ta4j.core.num.Num;

/**
 * Estimates the range of maximum drawdowns by randomly re-ordering past trades
 * and stitching their equity paths together.
 * <p>
 * The criterion value is taken from the distribution of simulated drawdowns (by
 * default, the 95th percentile).
 *
 * <p>
 * <b>Open positions:</b> When using {@link EquityCurveMode#MARK_TO_MARKET}, the
 * {@link OpenPositionHandling} setting controls whether open positions
 * contribute to the simulated drawdowns. {@link EquityCurveMode#REALIZED}
 * always ignores open positions regardless of the requested handling.
 *
 * <pre>{@code
 * var markToMarket = new MonteCarloMaximumDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET,
 *         OpenPositionHandling.MARK_TO_MARKET);
 * var ignoreOpen = new MonteCarloMaximumDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);
 * }</pre>
 *
 * @since 0.19
 */
public class MonteCarloMaximumDrawdownCriterion extends AbstractEquityCurveSettingsCriterion {

    private final int iterations;
    private final Integer pathBlocks;
    private final Supplier<RandomGenerator> randomSupplier;
    private final Statistics statistics;
    private final MaximumDrawdownCriterion maximumDrawdownCriterion;

    /**
     * Default constructor returning the 95th percentile.
     *
     * @since 0.19
     */
    public MonteCarloMaximumDrawdownCriterion() {
        this(10_000, null, () -> new SplittableRandom(42L), Statistics.P95, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Default constructor returning the 95th percentile using the given equity
     * curve mode.
     *
     * @param equityCurveMode the equity curve mode to use for drawdown simulation
     * @since 0.22.2
     */
    public MonteCarloMaximumDrawdownCriterion(EquityCurveMode equityCurveMode) {
        this(10_000, null, () -> new SplittableRandom(42L), Statistics.P95, equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Default constructor returning the 95th percentile using the given open
     * position handling.
     *
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public MonteCarloMaximumDrawdownCriterion(OpenPositionHandling openPositionHandling) {
        this(10_000, null, () -> new SplittableRandom(42L), Statistics.P95, EquityCurveMode.MARK_TO_MARKET,
                openPositionHandling);
    }

    /**
     * Constructor.
     *
     * @param iterations number of random simulations to run
     * @param pathBlocks number of trades to include in each simulated path
     *                   ({@code null} = use the number of trades in the sample)
     * @param seed       random seed for reproducibility
     * @param statistics which summary statistics of the simulated drawdowns to
     *                   return
     * @since 0.19
     */
    public MonteCarloMaximumDrawdownCriterion(int iterations, Integer pathBlocks, long seed, Statistics statistics) {
        this(iterations, pathBlocks, () -> new SplittableRandom(seed), statistics, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor.
     *
     * @param iterations      number of random simulations to run
     * @param pathBlocks      number of trades to include in each simulated path
     *                        ({@code null} = use the number of trades in the
     *                        sample)
     * @param seed            random seed for reproducibility
     * @param statistics      which summary statistics of the simulated drawdowns to
     *                        return
     * @param equityCurveMode the equity curve mode to use for drawdown simulation
     * @since 0.22.2
     */
    public MonteCarloMaximumDrawdownCriterion(int iterations, Integer pathBlocks, long seed, Statistics statistics,
            EquityCurveMode equityCurveMode) {
        this(iterations, pathBlocks, () -> new SplittableRandom(seed), statistics, equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor.
     *
     * @param iterations           number of random simulations to run
     * @param pathBlocks           number of trades to include in each simulated
     *                             path ({@code null} = use the number of trades in
     *                             the sample)
     * @param seed                 random seed for reproducibility
     * @param statistics           which summary statistics of the simulated
     *                             drawdowns to return
     * @param equityCurveMode      the equity curve mode to use for drawdown
     *                             simulation
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public MonteCarloMaximumDrawdownCriterion(int iterations, Integer pathBlocks, long seed, Statistics statistics,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(iterations, pathBlocks, () -> new SplittableRandom(seed), statistics, equityCurveMode,
                openPositionHandling);
    }

    /**
     * Constructor allowing to supply a custom random number generator.
     *
     * @param iterations     number of random simulations to run
     * @param pathBlocks     number of trades to include in each simulated path
     *                       ({@code null} = use the number of trades in the sample)
     * @param randomSupplier supplier of the random generator used for simulations
     * @param statistics     which summary statistics of the simulated drawdowns to
     *                       return
     * @since 0.19
     */
    public MonteCarloMaximumDrawdownCriterion(int iterations, Integer pathBlocks,
            Supplier<RandomGenerator> randomSupplier, Statistics statistics) {
        this(iterations, pathBlocks, randomSupplier, statistics, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor allowing to supply a custom random number generator.
     *
     * @param iterations           number of random simulations to run
     * @param pathBlocks           number of trades to include in each simulated
     *                             path ({@code null} = use the number of trades in
     *                             the sample)
     * @param randomSupplier       supplier of the random generator used for
     *                             simulations
     * @param statistics           which summary statistics of the simulated
     *                             drawdowns to return
     * @param equityCurveMode      the equity curve mode to use for drawdown
     *                             simulation
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public MonteCarloMaximumDrawdownCriterion(int iterations, Integer pathBlocks,
            Supplier<RandomGenerator> randomSupplier, Statistics statistics, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        super(equityCurveMode, openPositionHandling);
        this.iterations = iterations;
        this.pathBlocks = pathBlocks;
        this.randomSupplier = randomSupplier;
        this.statistics = statistics;
        this.maximumDrawdownCriterion = new MaximumDrawdownCriterion(this.equityCurveMode, this.openPositionHandling);
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
        return maximumDrawdownCriterion.calculate(series, position);
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
            return maximumDrawdownCriterion.calculate(series, tradingRecord);
        }
        var blocksPerPath = pathBlocks != null ? pathBlocks : blocks.size();
        var random = randomSupplier.get();
        var maxDrawdowns = new Num[iterations];
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
            maxDrawdowns[iteration] = maxDrawdown;
        }
        return statistics.calculate(numFactory, maxDrawdowns);
    }

    private List<List<Num>> buildBlocks(BarSeries series, TradingRecord record) {
        var blocks = new ArrayList<List<Num>>();
        var cashFlow = new CashFlow(series, record, equityCurveMode, openPositionHandling);
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
