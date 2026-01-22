/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.AnalysisCriterion.PositionFilter;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.AverageReturnPerBarCriterion;
import org.ta4j.core.criteria.EnterAndHoldCriterion;
import org.ta4j.core.criteria.LinearTransactionCostCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.NumberOfBarsCriterion;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.PositionsRatioCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.VersusEnterAndHoldCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;

import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;
import ta4jexamples.strategies.MovingMomentumStrategy;

/**
 * This class displays analysis criterion values after running a trading
 * strategy over a bar series.
 */
public class StrategyAnalysis {

    private static final Logger LOG = LogManager.getLogger(StrategyAnalysis.class);

    public static void main(String[] args) {

        // Getting the bar series
        var series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();
        // Building the trading strategy
        var strategy = MovingMomentumStrategy.buildStrategy(series);
        // Running the strategy
        var seriesManager = new BarSeriesManager(series);
        var tradingRecord = seriesManager.run(strategy);

        /*
         * Analysis criteria
         */

        var grossReturn = new GrossReturnCriterion().calculate(series, tradingRecord);
        LOG.debug("Gross return: {}", grossReturn);

        var netReturnCriterion = new NetReturnCriterion();
        var netReturn = netReturnCriterion.calculate(series, tradingRecord);
        LOG.debug("Net return: {}", netReturn);

        var numberOfBars = new NumberOfBarsCriterion().calculate(series, tradingRecord);
        LOG.debug("Number of bars: {}", numberOfBars);

        var AverageReturnPerBar = new AverageReturnPerBarCriterion().calculate(series, tradingRecord);
        LOG.debug("Average return per bar: {}", AverageReturnPerBar);

        var numberOfPositions = new NumberOfPositionsCriterion().calculate(series, tradingRecord);
        LOG.debug("Number of positions: {}", numberOfPositions);

        var positionsRatio = new PositionsRatioCriterion(PositionFilter.PROFIT).calculate(series, tradingRecord);
        LOG.debug("Winning positions ratio: {}", positionsRatio);

        var maximumDrawdown = new MaximumDrawdownCriterion().calculate(series, tradingRecord);
        LOG.debug("Maximum drawdown: {}", maximumDrawdown);

        var returnOverMaxDrawdown = new ReturnOverMaxDrawdownCriterion().calculate(series, tradingRecord);
        LOG.debug("Return over maximum drawdown: {}", returnOverMaxDrawdown);

        var linearTransactionCost = new LinearTransactionCostCriterion(1000, 0.005).calculate(series, tradingRecord);
        LOG.debug("Total transaction cost (from $1000): {}", linearTransactionCost);

        var enterAndHold = EnterAndHoldCriterion.EnterAndHoldReturnCriterion().calculate(series, tradingRecord);
        LOG.debug("Buy-and-hold return: {}", enterAndHold);

        var versusEnterAndHold = new VersusEnterAndHoldCriterion(netReturnCriterion).calculate(series, tradingRecord);
        LOG.debug("Custom strategy return vs buy-and-hold strategy return: {}", versusEnterAndHold);
    }

}
