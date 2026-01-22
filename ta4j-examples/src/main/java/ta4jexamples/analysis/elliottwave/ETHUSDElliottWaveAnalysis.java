/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.time.Instant;

/**
 * Example Elliott Wave analysis for Ethereum (ETH-USD) using Coinbase data.
 * <p>
 * This class demonstrates a simple usage pattern for analyzing Ethereum price
 * data with Elliott Wave indicators. It loads daily (PT1D) data from Coinbase
 * starting from a specific timestamp and performs comprehensive Elliott Wave
 * analysis including:
 * <ul>
 * <li>Swing detection and wave counting</li>
 * <li>Phase identification (impulse and corrective waves)</li>
 * <li>Fibonacci ratio validation</li>
 * <li>Channel projections</li>
 * <li>Scenario-based analysis with confidence scoring</li>
 * <li>Chart visualization with wave pivot labels</li>
 * </ul>
 * <p>
 * The analysis auto-selects an appropriate Elliott degree based on the bar
 * duration and history length.
 * <p>
 * To run this example:
 *
 * <pre>
 * java ETHUSDElliottWaveAnalysis
 * </pre>
 * <p>
 * Charts will be saved to {@code temp/charts/} and displayed if running in a
 * non-headless environment. The analysis results are printed as JSON to
 * standard output.
 *
 * @see ElliottWaveAnalysis
 * @see org.ta4j.core.indicators.elliott.ElliottWaveFacade
 * @since 0.22.0
 */
public class ETHUSDElliottWaveAnalysis {

    /**
     * Main entry point for ETH-USD Elliott Wave analysis.
     * <p>
     * Loads daily Ethereum data from Coinbase starting from the specified epoch
     * timestamp and performs Elliott Wave analysis with auto-selected degree.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        String dataSource = "Coinbase";
        String ticker = "ETH-USD";
        String barDuration = Duration.ofDays(1).toString();
        String startEpoch = String.valueOf(Instant.now().minus(365, ChronoUnit.DAYS).getEpochSecond());

        ElliottWaveAnalysis.main(new String[] { dataSource, ticker, barDuration, startEpoch });
    }

}
