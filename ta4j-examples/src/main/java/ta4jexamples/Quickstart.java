/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples;

import java.awt.GraphicsEnvironment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.AnalysisCriterion.PositionFilter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.PositionsRatioCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.VersusEnterAndHoldCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.StopGainRule;
import org.ta4j.core.rules.StopLossRule;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;

/**
 * Quickstart for ta4j - Your first trading strategy!
 * <p>
 * This example demonstrates:
 * <ul>
 * <li>Loading historical price data</li>
 * <li>Creating technical indicators (Simple Moving Averages)</li>
 * <li>Building entry and exit rules</li>
 * <li>Running a backtest</li>
 * <li>Analyzing performance metrics</li>
 * <li>Visualizing the strategy with charts</li>
 * </ul>
 * <p>
 * Run this example to see a complete trading strategy in action with visual
 * output!
 */
public class Quickstart {

    private static final Logger LOG = LogManager.getLogger(Quickstart.class);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          Welcome to ta4j - Your First Trading Strategy       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Step 1: Load historical price data
        System.out.println("[1/6] Loading historical Bitcoin price data from Bitstamp...");
        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();
        if (series == null || series.isEmpty()) {
            System.err.println(
                    "   [ERROR] Failed to load price data. The Bitstamp CSV file may be missing from the classpath.");
            System.err.println(
                    "   [TIP] Ensure the file 'Bitstamp-BTC-USD-PT5M-20131125_20131201.csv' exists in src/main/resources");
            return;
        }
        System.out.printf("   [OK] Loaded %d bars of price data%n", series.getBarCount());
        System.out.println();

        // Step 2: Create indicators
        System.out.println("[2/6] Creating technical indicators...");
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(closePrice, 50); // 50-period SMA
        SMAIndicator longSma = new SMAIndicator(closePrice, 200); // 200-period SMA
        System.out.println("   [OK] Created 50-period and 200-period Simple Moving Averages");
        System.out.println();

        // Step 3: Build trading rules
        System.out.println("[3/6] Building trading strategy rules...");
        // Entry rule: Buy when fast SMA crosses above slow SMA (golden cross)
        Rule buyingRule = new CrossedUpIndicatorRule(shortSma, longSma);

        // Exit rule: Sell when fast SMA crosses below slow SMA (death cross)
        // OR take profit at +6% OR cut losses at -5%
        Rule sellingRule = new CrossedDownIndicatorRule(shortSma, longSma)
                .or(new StopLossRule(closePrice, series.numFactory().numOf(5)))
                .or(new StopGainRule(closePrice, series.numFactory().numOf(6)));

        Strategy strategy = new BaseStrategy("SMA Crossover Strategy", buyingRule, sellingRule);
        System.out.println("   [OK] Strategy: SMA Crossover with stop-loss and take-profit");
        System.out.println();

        // Step 4: Run backtest
        System.out.println("[4/6] Running backtest on historical data...");
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);
        System.out.printf("   [OK] Backtest complete: %d trades executed%n", tradingRecord.getPositionCount());
        System.out.println();

        // Step 5: Analyze results
        System.out.println("[5/6] Performance Analysis");
        System.out.println("   ──────────────────────────────────────────");

        // Calculate key metrics
        AnalysisCriterion netReturn = new NetReturnCriterion();
        AnalysisCriterion winningPositionsRatio = new PositionsRatioCriterion(PositionFilter.PROFIT);
        AnalysisCriterion romad = new ReturnOverMaxDrawdownCriterion();
        AnalysisCriterion versusEnterAndHoldCriterion = new VersusEnterAndHoldCriterion(new NetReturnCriterion());

        Num netReturnValue = netReturn.calculate(series, tradingRecord);
        Num winRate = winningPositionsRatio.calculate(series, tradingRecord);
        Num romadValue = romad.calculate(series, tradingRecord);
        Num vsBuyHold = versusEnterAndHoldCriterion.calculate(series, tradingRecord);

        // Display formatted results
        System.out.printf("   Total Trades:        %d%n", tradingRecord.getPositionCount());
        System.out.printf("   Net Return:          %.2f%%%n",
                netReturnValue.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.printf("   Win Rate:            %.1f%%%n",
                winRate.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.printf("   Return/Max Drawdown: %.2f%n", romadValue.doubleValue());
        System.out.printf("   vs Buy & Hold:       %.2f%%%n",
                vsBuyHold.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.println();

        // Step 6: Visualize the strategy
        System.out.println("[6/6] Generating strategy visualization...");
        boolean isHeadless = GraphicsEnvironment.isHeadless();

        if (isHeadless) {
            System.out.println("   [WARN] Headless environment detected - skipping chart display");
            System.out.println("   [TIP] Run in a GUI environment to see interactive charts!");
        } else {
            try {
                ChartWorkflow chartWorkflow = new ChartWorkflow();
                JFreeChart chart = chartWorkflow.builder()
                        .withTitle("SMA Crossover Strategy - Quickstart Example")
                        .withSeries(series) // Price bars (candlesticks)
                        .withTradingRecordOverlay(tradingRecord) // Trading positions in subchart
                        .withIndicatorOverlay(shortSma) // Fast SMA overlay
                        .withIndicatorOverlay(longSma) // Slow SMA overlay
                        .withSubChart(new NetProfitLossCriterion(), tradingRecord) // Net profit/loss in subchart
                        .toChart();

                chartWorkflow.displayChart(chart, "ta4j Quickstart - SMA Crossover Strategy");
                System.out.println("   [OK] Chart displayed in new window");
                System.out.println("   [TIP] Net profit/loss shown in subchart below price chart");
            } catch (Exception ex) {
                LOG.warn("Failed to display chart: {}", ex.getMessage(), ex);
                System.out.println("   [WARN] Could not display chart: " + ex.getMessage());
            }
        }
        System.out.println();

        // Summary
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                         Summary                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("What just happened?");
        System.out.println();
        System.out.println("   1. We loaded historical Bitcoin price data");
        System.out.println("   2. Created two moving averages (50-period and 200-period)");
        System.out.println("   3. Built a strategy that:");
        System.out.println("      - Buys when the fast MA crosses above the slow MA");
        System.out.println("      - Sells when the fast MA crosses below the slow MA");
        System.out.println("      - Uses stop-loss (-5%) and take-profit (+6%) rules");
        System.out.println("   4. Backtested the strategy on historical data");
        System.out.println("   5. Analyzed the performance metrics");
        if (!isHeadless) {
            System.out.println("   6. Visualized the strategy with a chart");
        }
        System.out.println();
        System.out.println("Next Steps:");
        System.out.println("   - Modify the indicator periods (try 20/100 for more frequent trades)");
        System.out.println("   - Adjust stop-loss and take-profit percentages");
        System.out.println("   - Add more indicators (RSI, MACD, etc.)");
        System.out.println("   - Explore other examples in ta4j-examples");
        System.out.println("   - Check out the wiki: https://ta4j.github.io/ta4j-wiki/");
        System.out.println();
        System.out.println("Your turn! Modify this code and see how it affects performance.");
        System.out.println();
    }
}
