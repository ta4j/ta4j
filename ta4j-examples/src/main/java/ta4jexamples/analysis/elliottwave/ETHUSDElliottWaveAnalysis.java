/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

/**
 * Example Elliott Wave analysis for Ethereum (ETH-USD) using an ossified
 * Coinbase dataset.
 * <p>
 * This class demonstrates a simple usage pattern for analyzing Ethereum price
 * data with Elliott Wave indicators. It loads a committed daily (PT1D) dataset
 * from {@code src/main/resources} and performs comprehensive Elliott Wave
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
 * @see ElliottWaveIndicatorSuiteDemo
 * @see org.ta4j.core.indicators.elliott.ElliottWaveFacade
 * @since 0.22.0
 */
public class ETHUSDElliottWaveAnalysis {

    /**
     * Main entry point for ETH-USD Elliott Wave analysis.
     * <p>
     * Loads local daily Ethereum data and performs Elliott Wave analysis with
     * auto-selected degree.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        ElliottWaveIndicatorSuiteDemo.runOssifiedResource(ETHUSDElliottWaveAnalysis.class,
                "Coinbase-ETH-USD-PT1D-20241105_20251020.json", "ETH-USD_PT1D@Coinbase (ossified)", null);
    }

}
