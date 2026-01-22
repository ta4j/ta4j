/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.awt.GraphicsEnvironment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.LinearTransactionCostCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.criteria.ValueAtRiskCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitLossCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.criteria.pnl.NetAverageProfitCriterion;
import org.ta4j.core.criteria.pnl.NetAverageLossCriterion;
import org.ta4j.core.criteria.pnl.MaxConsecutiveLossCriterion;
import org.ta4j.core.criteria.pnl.MaxConsecutiveProfitCriterion;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelFacade;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.StopGainRule;
import org.ta4j.core.rules.TrailingStopLossRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.CoinbaseHttpBarSeriesDataSource;

/**
 * Coinbase Data Source Backtest - Advanced Risk Management & Transaction Costs
 * <p>
 * This example demonstrates advanced ta4j features focused on real-world
 * trading considerations:
 * <ul>
 * <li>Loading historical OHLCV data from Coinbase Advanced Trade API</li>
 * <li>MACD (Moving Average Convergence Divergence) with signal line and
 * histogram</li>
 * <li>Keltner Channels (alternative to Bollinger Bands using ATR)</li>
 * <li>VWAP (Volume-Weighted Average Price) - critical for crypto trading</li>
 * <li>Money Flow Index (MFI) - volume-weighted RSI</li>
 * <li>Trailing Stop Loss - dynamic stop that follows price upward</li>
 * <li>Stop Gain - take profit targets</li>
 * <li>Transaction cost analysis - real-world impact of fees</li>
 * <li>Value at Risk (VaR) - risk quantification</li>
 * <li>Gross vs Net metrics - understanding the difference</li>
 * <li>Multiple strategy comparison</li>
 * </ul>
 * <p>
 * <strong>Strategy Concept:</strong> A trend-following strategy using MACD
 * crossovers with Keltner Channel confirmation, VWAP for entry timing, and
 * sophisticated risk management with trailing stops and take-profit targets.
 * <p>
 * <strong>Data Source:</strong> This example uses Coinbase's public market data
 * API to fetch real cryptocurrency data. No API key is required, but be aware
 * of rate limits (350 candles per request, automatically paginated).
 * <p>
 * <strong>Key Learning:</strong> This example emphasizes the critical
 * importance of transaction costs in real trading. Notice how gross returns
 * differ significantly from net returns after accounting for fees!
 */
public class CoinbaseBacktest {

    private static final Logger LOG = LogManager.getLogger(CoinbaseBacktest.class);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     Coinbase Data Source - Advanced Risk Management          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Step 1: Load historical price data from Coinbase
        System.out.println("[1/8] Loading historical price data from Coinbase...");
        System.out.println("   Fetching 1 year of daily data for Bitcoin (BTC-USD)...");
        System.out.println("   (Crypto markets are 24/7 - perfect for trend strategies)");

        // Load 1 year of daily data
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(true);
        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, 365);
        // Alternative methods you can try:
        // BarSeries series = dataSource.loadSeriesInstance("ETH-USD",
        // CoinbaseInterval.ONE_DAY, 500); // 500 bars
        // BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
        // CoinbaseInterval.FOUR_HOUR, 1000); // 4-hour data
        // BarSeries series = dataSource.loadSeriesInstance("ETH-USD",
        // CoinbaseInterval.ONE_DAY,
        // Instant.parse("2023-01-01T00:00:00Z"),
        // Instant.parse("2023-12-31T23:59:59Z")); // Date range

        if (series == null || series.getBarCount() == 0) {
            System.err.println("   [ERROR] Failed to load data from Coinbase");
            System.err.println("   [TIP] Check your internet connection and try again");
            System.err.println("   [TIP] Coinbase API may have rate limits - wait a few minutes and retry");
            return;
        }

        System.out.printf("   [OK] Loaded %d bars of price data%n", series.getBarCount());
        System.out.printf("   [INFO] Date range: %s to %s%n", series.getFirstBar().getEndTime(),
                series.getLastBar().getEndTime());
        System.out.println();

        // Step 2: Create advanced indicators
        System.out.println("[2/8] Creating advanced technical indicators...");
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // MACD: Trend-following momentum indicator
        // MACD = 12-period EMA - 26-period EMA
        // Signal = 9-period EMA of MACD
        // Histogram = MACD - Signal
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        EMAIndicator macdSignal = macd.getSignalLine(9);
        // Histogram: difference between MACD and signal line
        // Positive histogram = bullish momentum, negative = bearish
        org.ta4j.core.indicators.numeric.NumericIndicator macdHistogram = macd.getHistogram(9);

        // Keltner Channels: Volatility-based channels using ATR
        // Similar to Bollinger Bands but uses ATR instead of standard deviation
        // More responsive to volatility changes
        KeltnerChannelFacade keltner = new KeltnerChannelFacade(series, 20, 10, 2.0);
        // keltner.middle() = 20-period EMA
        // keltner.upper() = middle + (2.0 * ATR)
        // keltner.lower() = middle - (2.0 * ATR)

        // VWAP: Volume-Weighted Average Price
        // Critical for crypto trading - shows institutional price levels
        // Often acts as support/resistance
        // Using all available bars for VWAP calculation
        VWAPIndicator vwap = new VWAPIndicator(series, series.getBarCount());

        // Money Flow Index: Volume-weighted RSI (0-100)
        // > 80 = overbought, < 20 = oversold
        // More reliable than RSI because it includes volume
        MoneyFlowIndexIndicator mfi = new MoneyFlowIndexIndicator(series, 14);

        System.out.println("   [OK] Created MACD (12, 26, 9) with signal line and histogram");
        System.out.println("   [OK] Created Keltner Channels (20 EMA, 10 ATR, 2.0 multiplier)");
        System.out.println("   [OK] Created VWAP (Volume-Weighted Average Price)");
        System.out.println("   [OK] Created Money Flow Index (14-period)");
        System.out.println();

        // Step 3: Build trend-following strategy with risk management
        System.out.println("[3/8] Building trend-following strategy with advanced risk management...");
        System.out.println("   Strategy: MACD crossover with Keltner Channel confirmation");

        // Entry rule: Buy when MACD crosses above signal line (bullish crossover)
        // AND price is above VWAP (institutional support)
        // AND price is above Keltner middle (uptrend)
        // AND MFI is not overbought (< 80)
        Rule macdBullishCrossover = new CrossedUpIndicatorRule(macd, macdSignal);
        Rule priceAboveVWAP = new OverIndicatorRule(closePrice, vwap);
        Rule priceAboveKeltnerMiddle = new OverIndicatorRule(closePrice, keltner.middle());
        Rule mfiNotOverbought = new UnderIndicatorRule(mfi, series.numFactory().numOf(80));

        Rule buyingRule = macdBullishCrossover.and(priceAboveVWAP).and(priceAboveKeltnerMiddle).and(mfiNotOverbought);

        // Exit rule: Sell when MACD crosses below signal line (bearish crossover)
        // OR trailing stop loss triggers (protects profits)
        // OR stop gain triggers (take profit at 15%)
        // OR price falls below Keltner lower band (breakdown)
        Rule macdBearishCrossover = new CrossedDownIndicatorRule(macd, macdSignal);
        // Trailing stop: 5% trailing stop loss (follows price upward)
        // This protects profits by moving the stop loss up as price rises
        TrailingStopLossRule trailingStop = new TrailingStopLossRule(closePrice, series.numFactory().numOf(5.0));
        // Stop gain: Take profit at 15% gain
        StopGainRule stopGain = new StopGainRule(closePrice, series.numFactory().numOf(15.0));
        Rule priceBelowKeltnerLower = new UnderIndicatorRule(closePrice, keltner.lower());

        Rule sellingRule = macdBearishCrossover.or(trailingStop).or(stopGain).or(priceBelowKeltnerLower);

        Strategy strategy = new BaseStrategy("MACD Trend Following (Risk Managed)", buyingRule, sellingRule);
        System.out.println("   [OK] Entry: MACD bullish crossover + Price > VWAP + Price > Keltner middle + MFI < 80");
        System.out.println(
                "   [OK] Exit: MACD bearish crossover OR 5% trailing stop OR 15% stop gain OR Price < Keltner lower");
        System.out.println();

        // Step 4: Run backtest
        System.out.println("[4/8] Running backtest on historical data...");
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);
        System.out.printf("   [OK] Backtest complete: %d positions executed%n", tradingRecord.getPositionCount());
        System.out.println();

        // Step 5: Performance analysis with transaction costs
        System.out.println("[5/8] Performance Analysis (WITH Transaction Costs)");
        System.out.println("   ──────────────────────────────────────────");

        // Transaction cost parameters (typical for crypto exchanges)
        // Coinbase Advanced Trade: 0.4% maker fee, 0.6% taker fee
        // Using 0.5% average (0.005) per trade (entry + exit = 1% total per round trip)
        double initialAmount = 10000.0; // $10,000 starting capital
        double feePercentage = 0.005; // 0.5% per trade

        // Gross metrics (before transaction costs)
        AnalysisCriterion grossReturn = new GrossReturnCriterion();
        AnalysisCriterion grossProfitLoss = new GrossProfitLossCriterion();
        Num grossReturnValue = grossReturn.calculate(series, tradingRecord);
        Num grossProfitLossValue = grossProfitLoss.calculate(series, tradingRecord);

        // Net metrics (after transaction costs)
        AnalysisCriterion netReturn = new NetReturnCriterion();
        AnalysisCriterion netProfitLoss = new NetProfitLossCriterion();
        Num netReturnValue = netReturn.calculate(series, tradingRecord);
        Num netProfitLossValue = netProfitLoss.calculate(series, tradingRecord);

        // Transaction costs
        LinearTransactionCostCriterion transactionCosts = new LinearTransactionCostCriterion(initialAmount,
                feePercentage);
        Num totalCosts = transactionCosts.calculate(series, tradingRecord);

        // Average profit/loss per trade
        AnalysisCriterion avgProfit = new NetAverageProfitCriterion();
        AnalysisCriterion avgLoss = new NetAverageLossCriterion();
        Num avgProfitValue = avgProfit.calculate(series, tradingRecord);
        Num avgLossValue = avgLoss.calculate(series, tradingRecord);

        // Consecutive streaks
        AnalysisCriterion maxConsecutiveProfit = new MaxConsecutiveProfitCriterion();
        AnalysisCriterion maxConsecutiveLoss = new MaxConsecutiveLossCriterion();
        Num maxConsecutiveProfitValue = maxConsecutiveProfit.calculate(series, tradingRecord);
        Num maxConsecutiveLossValue = maxConsecutiveLoss.calculate(series, tradingRecord);

        // Risk metrics
        ValueAtRiskCriterion var95 = new ValueAtRiskCriterion(0.95); // 95% confidence level
        Num var95Value = var95.calculate(series, tradingRecord);

        // Display comprehensive results
        System.out.println("   Gross Performance (Before Fees):");
        System.out.printf("      Gross Return:          %.2f%%%n",
                grossReturnValue.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.printf("      Gross Profit/Loss:     $%.2f%n", grossProfitLossValue.doubleValue());
        System.out.println();
        System.out.println("   Transaction Costs:");
        System.out.printf("      Total Fees Paid:       $%.2f (%.2f%% of initial capital)%n", totalCosts.doubleValue(),
                totalCosts.dividedBy(series.numFactory().numOf(initialAmount))
                        .multipliedBy(series.numFactory().numOf(100))
                        .doubleValue());
        System.out.printf("      Fee per Trade:         %.2f%% (entry + exit)%n", feePercentage * 200);
        System.out.println();
        System.out.println("   Net Performance (After Fees):");
        System.out.printf("      Net Return:            %.2f%%%n",
                netReturnValue.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.printf("      Net Profit/Loss:       $%.2f%n", netProfitLossValue.doubleValue());
        System.out.printf("      Impact of Fees:        %.2f%% (difference between gross and net)%n",
                grossReturnValue.minus(netReturnValue).multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.println();
        System.out.println("   Trade Statistics:");
        System.out.printf("      Average Profit:        $%.2f%n", avgProfitValue.doubleValue());
        System.out.printf("      Average Loss:          $%.2f%n", avgLossValue.doubleValue());
        System.out.printf("      Max Consecutive Wins:  %d%n", maxConsecutiveProfitValue.intValue());
        System.out.printf("      Max Consecutive Losses: %d%n", maxConsecutiveLossValue.intValue());
        System.out.println();
        System.out.println("   Risk Metrics:");
        System.out.printf("      Value at Risk (95%%):   %.2f%% (worst expected loss)%n",
                var95Value.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.println();

        // Step 6: Compare strategies (with and without trailing stop)
        System.out.println("[6/8] Strategy Comparison: With vs Without Trailing Stop");
        System.out.println("   ──────────────────────────────────────────");

        // Strategy without trailing stop (only MACD crossover)
        Rule simpleBuyingRule = macdBullishCrossover.and(priceAboveVWAP);
        Rule simpleSellingRule = macdBearishCrossover.or(stopGain).or(priceBelowKeltnerLower);
        Strategy simpleStrategy = new BaseStrategy("MACD Simple (No Trailing Stop)", simpleBuyingRule,
                simpleSellingRule);

        TradingRecord simpleRecord = seriesManager.run(simpleStrategy);
        Num simpleNetReturn = netReturn.calculate(series, simpleRecord);
        Num strategyNetReturn = netReturn.calculate(series, tradingRecord);

        System.out.printf("   Simple Strategy (no trailing stop): %.2f%% net return%n",
                simpleNetReturn.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.printf("   Risk-Managed Strategy (with trailing stop): %.2f%% net return%n",
                strategyNetReturn.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.printf("   Difference: %.2f%%%n",
                strategyNetReturn.minus(simpleNetReturn).multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.println();

        // Step 7: Visualize the strategy
        System.out.println("[7/8] Generating comprehensive strategy visualization...");
        boolean isHeadless = GraphicsEnvironment.isHeadless();

        if (isHeadless) {
            System.out.println("   [WARN] Headless environment detected - skipping chart display");
            System.out.println("   [TIP] Run in a GUI environment to see interactive charts!");
        } else {
            try {
                ChartWorkflow chartWorkflow = new ChartWorkflow();
                JFreeChart chart = chartWorkflow.builder()
                        .withTitle("MACD Trend Following Strategy - Coinbase Data (BTC-USD)")
                        .withSeries(series) // Price bars (candlesticks)
                        .withTradingRecordOverlay(tradingRecord) // Trading positions marked on price chart
                        .withIndicatorOverlay(keltner.middle()) // Keltner middle band
                        .withIndicatorOverlay(keltner.upper()) // Keltner upper band
                        .withIndicatorOverlay(keltner.lower()) // Keltner lower band
                        .withIndicatorOverlay(vwap) // VWAP overlay
                        .withSubChart(macd) // MACD in first subchart
                        .withIndicatorOverlay(macdSignal) // MACD signal line
                        .withSubChart(macdHistogram) // MACD histogram in second subchart
                        .withSubChart(mfi) // Money Flow Index in third subchart
                        .withSubChart(new NetProfitLossCriterion(), tradingRecord) // Net P&L in fourth subchart
                        .toChart();

                chartWorkflow.displayChart(chart, "ta4j Coinbase Backtest - MACD Trend Following with Risk Management");
                System.out.println("   [OK] Multi-subchart displayed in new window");
                System.out.println("   [TIP] Chart shows: Price with Keltner Channels & VWAP, MACD, MFI, and P&L");
            } catch (Exception ex) {
                LOG.warn("Failed to display chart: {}", ex.getMessage(), ex);
                System.out.println("   [WARN] Could not display chart: " + ex.getMessage());
            }
        }
        System.out.println();

        // Step 8: Explain advanced concepts
        System.out.println("[8/8] Advanced Concepts Demonstrated");
        System.out.println("   ──────────────────────────────────────────");
        System.out.println("   ✓ MACD: Trend-following momentum indicator");
        System.out.println("     - Bullish crossover: MACD crosses above signal line");
        System.out.println("     - Histogram shows momentum strength");
        System.out.println();
        System.out.println("   ✓ Keltner Channels: Volatility-based price channels");
        System.out.println("     - Uses ATR instead of standard deviation (more responsive)");
        System.out.println("     - Upper/lower bands adapt to market volatility");
        System.out.println();
        System.out.println("   ✓ VWAP: Volume-Weighted Average Price");
        System.out.println("     - Critical for crypto trading (institutional levels)");
        System.out.println("     - Price above VWAP = bullish, below = bearish");
        System.out.println();
        System.out.println("   ✓ Trailing Stop Loss: Dynamic risk management");
        System.out.println("     - Follows price upward, protecting profits");
        System.out.println("     - More effective than fixed stop loss in trending markets");
        System.out.println();
        System.out.println("   ✓ Stop Gain: Take-profit targets");
        System.out.println("     - Locks in profits at predetermined levels");
        System.out.println("     - Prevents giving back gains in volatile markets");
        System.out.println();
        System.out.println("   ✓ Transaction Costs: Real-world impact");
        System.out.println("     - Gross returns vs Net returns (after fees)");
        System.out.println("     - Fees can significantly impact profitability");
        System.out.println("     - Always account for costs in backtesting!");
        System.out.println();
        System.out.println("   ✓ Value at Risk (VaR): Risk quantification");
        System.out.println("     - Measures worst expected loss at confidence level");
        System.out.println("     - Helps understand downside risk");
        System.out.println();
        System.out.println("   ✓ Gross vs Net Metrics: Understanding the difference");
        System.out.println("     - Gross: Before transaction costs");
        System.out.println("     - Net: After transaction costs (real-world)");
        System.out.println("     - Always use net metrics for real trading decisions");
        System.out.println();

        // Summary
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                         Summary                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("What just happened?");
        System.out.println();
        System.out.println("   1. Loaded 1 year of daily OHLCV data for BTC-USD from Coinbase");
        System.out.println("   2. Created advanced indicators:");
        System.out.println("      - MACD with signal line and histogram (trend momentum)");
        System.out.println("      - Keltner Channels (volatility-based channels)");
        System.out.println("      - VWAP (institutional price levels)");
        System.out.println("      - Money Flow Index (volume-weighted momentum)");
        System.out.println("   3. Built a trend-following strategy with risk management:");
        System.out.println("      - Entry: MACD bullish crossover + Price > VWAP + Price > Keltner middle + MFI < 80");
        System.out.println(
                "      - Exit: MACD bearish crossover OR 5% trailing stop OR 15% stop gain OR Price < Keltner lower");
        System.out.println("   4. Backtested with transaction costs (0.5% per trade)");
        System.out.println("   5. Analyzed gross vs net performance (impact of fees)");
        System.out.println("   6. Compared strategies (with/without trailing stop)");
        System.out.println("   7. Calculated risk metrics (Value at Risk)");
        if (!isHeadless) {
            System.out.println("   8. Visualized with multi-subchart (Price, MACD, MFI, P&L)");
        }
        System.out.println();
        System.out.println("Advanced Features Demonstrated:");
        System.out.println("   ✓ MACD with signal line and histogram");
        System.out.println("   ✓ Keltner Channels (ATR-based volatility bands)");
        System.out.println("   ✓ VWAP for institutional price levels");
        System.out.println("   ✓ Money Flow Index (volume-weighted RSI)");
        System.out.println("   ✓ Trailing Stop Loss (dynamic profit protection)");
        System.out.println("   ✓ Stop Gain (take-profit targets)");
        System.out.println("   ✓ Transaction cost analysis (real-world impact)");
        System.out.println("   ✓ Value at Risk (risk quantification)");
        System.out.println("   ✓ Gross vs Net metrics comparison");
        System.out.println("   ✓ Multiple strategy comparison");
        System.out.println();
        System.out.println("Coinbase Data Source Features:");
        System.out.println("   - Load data by number of days: loadSeries(\"BTC-USD\", 365)");
        System.out.println("   - Load data by bar count: loadSeries(\"BTC-USD\", ONE_DAY, 500)");
        System.out.println("   - Load data by date range: loadSeries(\"BTC-USD\", ONE_DAY, start, end)");
        System.out.println("   - Supports multiple intervals: 1m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 1d");
        System.out.println("   - Works with all Coinbase trading pairs (BTC-USD, ETH-USD, etc.)");
        System.out.println("   - Automatic pagination for large date ranges (350 candles per request)");
        System.out.println();
        System.out.println("Key Takeaways:");
        System.out.println("   • Transaction costs matter! Always use net returns for decisions.");
        System.out.println("   • Trailing stops protect profits better than fixed stops.");
        System.out.println("   • VWAP is critical for crypto trading (institutional levels).");
        System.out.println("   • Risk metrics (VaR) help quantify downside risk.");
        System.out.println("   • Gross vs Net metrics show the real impact of fees.");
        System.out.println();
        System.out.println("Next Steps - Experiment with:");
        System.out.println("   - Different cryptocurrencies: \"ETH-USD\", \"SOL-USD\", \"ADA-USD\"");
        System.out.println("   - Adjust MACD periods (try 8/21 or 19/39)");
        System.out.println("   - Modify trailing stop percentage (try 3% or 7%)");
        System.out.println("   - Change stop gain targets (try 10% or 20%)");
        System.out.println("   - Test different fee structures (0.1%, 0.25%, 1.0%)");
        System.out.println("   - Try different intervals: FOUR_HOUR, SIX_HOUR for shorter timeframes");
        System.out.println("   - Explore other examples in ta4j-examples");
        System.out.println("   - Check out the wiki: https://ta4j.github.io/ta4j-wiki/");
        System.out.println();
        System.out.println("Your turn! Modify this code and see how transaction costs affect profitability.");
        System.out.println();
    }
}
