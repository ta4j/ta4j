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
package ta4jexamples.backtesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.reports.TradingStatement;
import ta4jexamples.loaders.AdaptiveJsonBarsSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A class to perform backtesting of multiple trading strategies
 */
public class MultiStrategyBacktest {

    private static final Logger LOG = LoggerFactory.getLogger(MultiStrategyBacktest.class);
    private static final int DEFAULT_DECIMAL_PRECISION = 16;

    /**
     * Entry point of the application that configures the decimal precision and runs
     * a multi-strategy backtest. The method sets the default decimal precision for
     * calculations and executes a backtest using OHLC data loaded from a specified
     * JSON resource file.
     *
     * @param args command-line arguments passed to the application (not used in
     *             this implementation)
     */
    public static void main(String[] args) {
        DecimalNum.configureDefaultPrecision(DEFAULT_DECIMAL_PRECISION);

        String jsonOhlcResourceFile = "Coinbase-ETH-USD-PT1D-2024-11-06_2025-10-21.json";
        new MultiStrategyBacktest().runBacktest(jsonOhlcResourceFile);
    }

    /**
     * Executes a backtest using OHLC data loaded from a JSON resource file. The
     * method loads the bar series from the specified resource file, validates its
     * content, and then generates multiple strategies based on SMA crossover rules
     * with varying parameters. Each strategy is executed against the loaded bar
     * series, and the results are logged.
     *
     * @param jsonOHLCResourceFile the path to the JSON resource file containing
     *                             OHLC data
     */
    public void runBacktest(String jsonOHLCResourceFile) {
        BarSeries series = null;
        try (InputStream resourceStream = MultiStrategyBacktest.class.getClassLoader()
                .getResourceAsStream(jsonOHLCResourceFile)) {
            series = AdaptiveJsonBarsSerializer.loadSeries(resourceStream);
        } catch (IOException ex) {
            LOG.error("IOException while loading resource: {} - {}", jsonOHLCResourceFile, ex.getMessage());
        }

        Objects.requireNonNull(series, "Bar series was null");

        List<Strategy> strategies = new ArrayList<>();

        Instant startInstant = Instant.now();
        BacktestExecutor backtestExecutor = new BacktestExecutor(series);
        List<TradingStatement> tradingStatements = backtestExecutor.execute(strategies, DecimalNum.valueOf(50),
                Trade.TradeType.BUY);

        LOG.debug("Back-tested {} strategies on {}-bar series using decimal precision of {} in {}", strategies.size(),
                series.getBarCount(), DEFAULT_DECIMAL_PRECISION, Duration.between(startInstant, Instant.now()));
    }
}
