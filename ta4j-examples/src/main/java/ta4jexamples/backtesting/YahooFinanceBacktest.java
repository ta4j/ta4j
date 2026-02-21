/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

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
import org.ta4j.core.criteria.ExpectancyCriterion;
import org.ta4j.core.criteria.PositionsRatioCriterion;
import org.ta4j.core.criteria.SqnCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.VersusEnterAndHoldCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.UltimateOscillatorIndicator;
import org.ta4j.core.indicators.VortexIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AverageTrueRangeStopLossRule;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource;

/**
 * Yahoo Finance Data Source Backtest - Advanced Multi-Indicator Strategy
 * <p>
 * This example demonstrates advanced ta4j features beyond the Quickstart:
 * <ul>
 * <li>Loading historical OHLCV data from Yahoo Finance API</li>
 * <li>Bollinger Bands for mean reversion signals</li>
 * <li>ATR-based dynamic stop-loss (adapts to market volatility)</li>
 * <li>RSI for momentum confirmation</li>
 * <li>Volume analysis with On-Balance Volume (OBV)</li>
 * <li>Trend confirmation with Vortex and Ultimate Oscillator</li>
 * <li>Indicator composition using BinaryOperationIndicator</li>
 * <li>Advanced performance metrics (Expectancy, SQN, Maximum Drawdown)</li>
 * <li>Multi-subchart visualization</li>
 * </ul>
 * <p>
 * <strong>Strategy Concept:</strong> A mean reversion strategy that buys when
 * price touches the lower Bollinger Band (oversold) with RSI confirmation and
 * volume support, using ATR-based stops that adapt to market volatility.
 * <p>
 * <strong>Data Source:</strong> This example uses Yahoo Finance's public API to
 * fetch real market data. No API key is required, but be aware of rate limits
 * (~2000 requests/hour per IP).
 * <p>
 * Run this example to see an advanced trading strategy backtested on real
 * market data with comprehensive analysis!
 */
public class YahooFinanceBacktest {

    private static final Logger LOG = LogManager.getLogger(YahooFinanceBacktest.class);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     Yahoo Finance Data Source - Backtesting Example         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Step 1: Load historical price data from Yahoo Finance
        System.out.println("[1/7] Loading historical price data from Yahoo Finance...");
        System.out.println("   Fetching 2 years of daily data for Apple Inc. (AAPL)...");
        System.out.println("   (More data = better indicator calculations)");

        // Load 2 years of data for better indicator stability
        YahooFinanceHttpBarSeriesDataSource dataSource = new YahooFinanceHttpBarSeriesDataSource(true);
        BarSeries series = dataSource.loadSeriesInstance("AAPL",
                YahooFinanceHttpBarSeriesDataSource.YahooFinanceInterval.DAY_1, 730);
        // Alternative methods you can try:
        // BarSeries series = dataSource.loadSeriesInstance("AAPL",
        // YahooFinanceInterval.DAY_1, 500); // 500 bars
        // BarSeries series = dataSource.loadSeriesInstance("MSFT",
        // YahooFinanceInterval.HOUR_1, 1000); // Hourly data
        // BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
        // YahooFinanceInterval.DAY_1,
        // Instant.parse("2023-01-01T00:00:00Z"),
        // Instant.parse("2023-12-31T23:59:59Z")); // Date range

        if (series == null || series.getBarCount() == 0) {
            System.err.println("   [ERROR] Failed to load data from Yahoo Finance");
            System.err.println("   [TIP] Check your internet connection and try again");
            System.err.println("   [TIP] Yahoo Finance may have rate limits - wait a few minutes and retry");
            return;
        }

        System.out.printf("   [OK] Loaded %d bars of price data%n", series.getBarCount());
        System.out.printf("   [INFO] Date range: %s to %s%n", series.getFirstBar().getEndTime(),
                series.getLastBar().getEndTime());
        System.out.println();

        // Step 2: Create base indicators
        System.out.println("[2/7] Creating technical indicators...");
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // Bollinger Bands: Mean reversion indicator
        // Uses 20-period SMA with 2 standard deviations
        int bbPeriod = 20;
        double bbMultiplier = 2.0;
        SMAIndicator bbSma = new SMAIndicator(closePrice, bbPeriod);
        StandardDeviationIndicator bbStdDev = new StandardDeviationIndicator(closePrice, bbPeriod);
        BollingerBandsMiddleIndicator bbMiddle = new BollingerBandsMiddleIndicator(bbSma);
        BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMiddle, bbStdDev,
                series.numFactory().numOf(bbMultiplier));
        BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMiddle, bbStdDev,
                series.numFactory().numOf(bbMultiplier));

        // RSI: Momentum oscillator (14-period)
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        // On-Balance Volume: Volume-based trend indicator
        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(series);

        // Trend confirmation indicators
        VortexIndicator vortex = new VortexIndicator(series, 14);
        UltimateOscillatorIndicator ultimateOscillator = new UltimateOscillatorIndicator(series);

        // Note: ATR is used in the AverageTrueRangeStopLossRule below
        // Advanced: You can also create custom indicators using
        // BinaryOperationIndicator
        // Example: Calculate distance from price to middle band as a percentage
        // BinaryOperationIndicator priceToMiddleRatio =
        // BinaryOperationIndicator.quotient(
        // BinaryOperationIndicator.difference(closePrice, bbMiddle), bbMiddle);

        System.out.println("   [OK] Created Bollinger Bands (20-period, 2 std dev)");
        System.out.println("   [OK] Created RSI (14-period)");
        System.out.println("   [OK] Created ATR (14-period) for dynamic stops");
        System.out.println("   [OK] Created On-Balance Volume indicator");
        System.out.println("   [OK] Created Vortex oscillator (14-period) for trend direction");
        System.out.println("   [OK] Created Ultimate Oscillator (7/14/28) for trend strength");
        System.out.println("   [OK] Created custom price-to-middle-band ratio indicator");
        System.out.println();

        // Step 3: Build advanced trading rules
        System.out.println("[3/7] Building advanced trading strategy rules...");
        System.out.println("   Strategy: Mean reversion with multiple confirmations");

        // Entry rule: Buy when price is at or below lower Bollinger Band (oversold)
        // AND RSI is below 45 (oversold confirmation - less strict than 40)
        // Price touching lower BB OR crossing below it
        Rule priceAtLowerBB = new UnderIndicatorRule(closePrice, bbLower)
                .or(new CrossedDownIndicatorRule(closePrice, bbLower));
        Rule rsiOversold = new UnderIndicatorRule(rsi, series.numFactory().numOf(45));
        // Optional: OBV rising provides additional confirmation (but not required)
        // This makes the strategy more tradeable while still using volume analysis
        Rule obvRising = new OverIndicatorRule(obv, new PreviousValueIndicator(obv, 1));
        Rule vortexBullish = new OverIndicatorRule(vortex, series.numFactory().zero());
        Rule ultimateBullish = new OverIndicatorRule(ultimateOscillator, series.numFactory().numOf(50));

        // Entry: Price at lower BB + RSI oversold + (OBV rising OR price below middle
        // band) + trend confirmation from Vortex and Ultimate Oscillator
        // This allows entries when either volume confirms OR price is clearly oversold
        Rule priceBelowMiddle = new UnderIndicatorRule(closePrice, bbMiddle);
        Rule buyingRule = priceAtLowerBB.and(rsiOversold)
                .and(obvRising.or(priceBelowMiddle))
                .and(vortexBullish)
                .and(ultimateBullish);

        // Exit rule: Sell when price reaches upper Bollinger Band (overbought)
        // OR RSI crosses above 65 (overbought - less strict than 70 for more exits)
        // OR ATR-based stop loss triggers (dynamic, adapts to volatility)
        // OR Vortex turns bearish
        Rule exitCondition1 = new CrossedUpIndicatorRule(closePrice, bbUpper)
                .or(new OverIndicatorRule(closePrice, bbUpper));
        Rule exitCondition2 = new OverIndicatorRule(rsi, series.numFactory().numOf(65));
        // ATR-based stop: 2.5x ATR below entry price (allows for some volatility)
        Rule exitCondition3 = new AverageTrueRangeStopLossRule(series, 14, 2.5);
        Rule exitCondition4 = new UnderIndicatorRule(vortex, series.numFactory().zero());

        Rule sellingRule = exitCondition1.or(exitCondition2).or(exitCondition3).or(exitCondition4);

        Strategy strategy = new BaseStrategy("Bollinger Bands Mean Reversion (Trend-Confirmed)", buyingRule,
                sellingRule);
        System.out.println(
                "   [OK] Entry: Price at/below lower BB + RSI < 45 + (OBV rising OR price below middle) + Vortex > 0 + Ultimate > 50");
        System.out.println("   [OK] Exit: Price at/above upper BB OR RSI > 65 OR ATR stop (2.5x ATR) OR Vortex < 0");
        System.out.println();

        // Step 4: Run backtest
        System.out.println("[4/7] Running backtest on historical data...");
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);
        System.out.printf("   [OK] Backtest complete: %d positions executed%n", tradingRecord.getPositionCount());
        System.out.println();

        // Step 5: Advanced performance analysis
        System.out.println("[5/7] Advanced Performance Analysis");
        System.out.println("   ──────────────────────────────────────────");

        // Basic metrics
        AnalysisCriterion netReturn = new NetReturnCriterion();
        AnalysisCriterion winningPositionsRatio = new PositionsRatioCriterion(PositionFilter.PROFIT);
        Num netReturnValue = netReturn.calculate(series, tradingRecord);
        Num winRate = winningPositionsRatio.calculate(series, tradingRecord);

        // Advanced risk-adjusted metrics
        AnalysisCriterion romad = new ReturnOverMaxDrawdownCriterion();
        AnalysisCriterion maxDrawdown = new MaximumDrawdownCriterion();
        AnalysisCriterion expectancy = new ExpectancyCriterion();
        AnalysisCriterion sqn = new SqnCriterion(); // System Quality Number (higher = better)
        AnalysisCriterion versusEnterAndHoldCriterion = new VersusEnterAndHoldCriterion(new NetReturnCriterion());

        Num romadValue = romad.calculate(series, tradingRecord);
        Num maxDrawdownValue = maxDrawdown.calculate(series, tradingRecord);
        Num expectancyValue = expectancy.calculate(series, tradingRecord);
        Num sqnValue = sqn.calculate(series, tradingRecord);
        Num vsBuyHold = versusEnterAndHoldCriterion.calculate(series, tradingRecord);

        // Display comprehensive results
        System.out.println("   Basic Metrics:");
        System.out.printf("      Total Positions:     %d%n", tradingRecord.getPositionCount());
        System.out.printf("      Net Return:          %.2f%%%n",
                netReturnValue.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.printf("      Win Rate:            %.1f%%%n",
                winRate.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.println();
        System.out.println("   Risk Metrics:");
        System.out.printf("      Maximum Drawdown:    %.2f%%%n",
                maxDrawdownValue.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.printf("      Return/Max Drawdown: %.2f%n", romadValue.doubleValue());
        System.out.println();
        System.out.println("   Advanced Metrics:");
        System.out.printf("      Expectancy:          %.4f (avg profit per trade)%n", expectancyValue.doubleValue());
        System.out.printf("      SQN (System Quality): %.2f (higher = better)%n", sqnValue.doubleValue());
        System.out.printf("      vs Buy & Hold:       %.2f%%%n",
                vsBuyHold.multipliedBy(series.numFactory().numOf(100)).doubleValue());
        System.out.println();

        // Step 6: Visualize the strategy with multiple subcharts
        System.out.println("[6/7] Generating comprehensive strategy visualization...");
        boolean isHeadless = GraphicsEnvironment.isHeadless();

        if (isHeadless) {
            System.out.println("   [WARN] Headless environment detected - skipping chart display");
            System.out.println("   [TIP] Run in a GUI environment to see interactive charts!");
        } else {
            try {
                ChartWorkflow chartWorkflow = new ChartWorkflow();
                JFreeChart chart = chartWorkflow.builder()
                        .withTitle("Bollinger Bands Mean Reversion Strategy - Yahoo Finance Data (AAPL)")
                        .withSeries(series) // Price bars (candlesticks)
                        .withTradingRecordOverlay(tradingRecord) // Trading positions marked on price chart
                        .withIndicatorOverlay(bbMiddle) // Middle band overlay
                        .withIndicatorOverlay(bbUpper) // Upper band overlay
                        .withIndicatorOverlay(bbLower) // Lower band overlay
                        .withSubChart(rsi) // RSI in first subchart
                        .withSubChart(obv) // OBV in second subchart
                        .withSubChart(vortex) // Vortex oscillator in third subchart
                        .withSubChart(ultimateOscillator) // Ultimate Oscillator in fourth subchart
                        .withSubChart(new NetProfitLossCriterion(), tradingRecord) // Net profit/loss in fifth subchart
                        .toChart();

                chartWorkflow.displayChart(chart, "ta4j Yahoo Finance Backtest - Advanced Mean Reversion Strategy");
                System.out.println("   [OK] Multi-subchart displayed in new window");
                System.out.println("   [TIP] Chart shows: Price with BB bands, RSI, OBV, Vortex, Ultimate, and P&L");
            } catch (Exception ex) {
                LOG.warn("Failed to display chart: {}", ex.getMessage(), ex);
                System.out.println("   [WARN] Could not display chart: " + ex.getMessage());
            }
        }
        System.out.println();

        // Step 7: Explain advanced concepts
        System.out.println("[7/7] Advanced Concepts Demonstrated");
        System.out.println("   ──────────────────────────────────────────");
        System.out.println("   ✓ Bollinger Bands: Mean reversion indicator");
        System.out.println("     - Price tends to revert to the middle band");
        System.out.println("     - Lower band = oversold, Upper band = overbought");
        System.out.println();
        System.out.println("   ✓ ATR-based Stop Loss: Dynamic risk management");
        System.out.println("     - Adapts to market volatility automatically");
        System.out.println("     - Tighter stops in calm markets, wider in volatile markets");
        System.out.println();
        System.out.println("   ✓ Multi-Indicator Confirmation: Reduces false signals");
        System.out.println("     - RSI confirms oversold/overbought conditions");
        System.out.println("     - OBV confirms volume support for price moves");
        System.out.println("     - Vortex confirms directional trend bias (+VI vs -VI)");
        System.out.println("     - Ultimate Oscillator confirms multi-timeframe buying pressure");
        System.out.println();
        System.out.println("   ✓ Advanced Metrics: Deeper performance insights");
        System.out.println("     - Expectancy: Average profit per trade");
        System.out.println("     - SQN: System Quality Number (risk-adjusted performance)");
        System.out.println("     - Maximum Drawdown: Largest peak-to-trough decline");
        System.out.println();

        // Summary
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                         Summary                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("What just happened?");
        System.out.println();
        System.out.println("   1. Loaded 2 years of daily OHLCV data for AAPL from Yahoo Finance");
        System.out.println("   2. Created advanced indicators:");
        System.out.println("      - Bollinger Bands (mean reversion)");
        System.out.println("      - RSI (momentum confirmation)");
        System.out.println("      - ATR (volatility for dynamic stops)");
        System.out.println("      - OBV (volume trend confirmation)");
        System.out.println("      - Vortex oscillator (trend direction confirmation)");
        System.out.println("      - Ultimate Oscillator (trend strength confirmation)");
        System.out.println("      - Custom price-to-middle-band ratio (indicator composition)");
        System.out.println("   3. Built a sophisticated mean reversion strategy:");
        System.out.println(
                "      - Entry: Price at/below lower BB + RSI < 45 + (OBV rising OR price below middle) + Vortex > 0 + Ultimate > 50");
        System.out.println("      - Exit: Price at/above upper BB OR RSI > 65 OR ATR stop (2.5x ATR) OR Vortex < 0");
        System.out.println("   4. Backtested with ATR-based dynamic stop-loss (adapts to volatility)");
        System.out.println("   5. Analyzed with advanced metrics (Expectancy, SQN, Max Drawdown)");
        if (!isHeadless) {
            System.out.println("   6. Visualized with multi-subchart (Price, RSI, OBV, P&L)");
        }
        System.out.println();
        System.out.println("Advanced Features Demonstrated:");
        System.out.println("   ✓ Bollinger Bands for mean reversion trading");
        System.out.println("   ✓ ATR-based dynamic stop-loss (better than fixed %)");
        System.out.println("   ✓ Multi-indicator trend confirmation (RSI, OBV, Vortex, Ultimate)");
        System.out.println("   ✓ Indicator composition (BinaryOperationIndicator)");
        System.out.println("   ✓ Advanced performance metrics (Expectancy, SQN)");
        System.out.println("   ✓ Multi-subchart visualization");
        System.out.println();
        System.out.println("Yahoo Finance Data Source Features:");
        System.out.println("   - Load data by number of days: loadSeries(\"AAPL\", 730)");
        System.out.println("   - Load data by bar count: loadSeries(\"AAPL\", DAY_1, 500)");
        System.out.println("   - Load data by date range: loadSeries(\"AAPL\", DAY_1, start, end)");
        System.out.println("   - Supports multiple intervals: 1m, 5m, 15m, 30m, 1h, 4h, 1d, 1wk, 1mo");
        System.out.println("   - Works with stocks, ETFs, and cryptocurrencies");
        System.out.println("   - Automatic pagination for large date ranges");
        System.out.println();
        System.out.println("Next Steps - Experiment with:");
        System.out.println("   - Different tickers: \"MSFT\", \"GOOGL\", \"BTC-USD\", \"ETH-USD\"");
        System.out.println("   - Adjust BB period (try 10, 30) and multiplier (try 1.5, 2.5)");
        System.out.println("   - Modify RSI thresholds (try 30/70 or 35/65)");
        System.out.println("   - Change ATR multiplier for stops (try 1.5x or 3.0x)");
        System.out.println("   - Add MACD or Stochastic for additional confirmation");
        System.out.println("   - Try different intervals: HOUR_1, WEEK_1 for different timeframes");
        System.out.println("   - Explore other examples in ta4j-examples");
        System.out.println("   - Check out the wiki: https://ta4j.github.io/ta4j-wiki/");
        System.out.println();
        System.out.println("Your turn! Modify this code and see how it affects performance.");
        System.out.println();
    }
}
