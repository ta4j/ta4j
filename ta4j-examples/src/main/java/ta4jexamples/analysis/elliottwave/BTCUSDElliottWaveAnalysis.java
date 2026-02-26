/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import org.ta4j.core.indicators.elliott.ElliottDegree;

/**
 * Example Elliott Wave analysis for Bitcoin (BTC-USD) using an ossified
 * Coinbase dataset.
 * <p>
 * This class demonstrates a simple usage pattern for analyzing Bitcoin price
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
 * The analysis uses the PRIMARY Elliott degree, which is suitable for daily
 * bars with approximately one year of history.
 * <p>
 * To run this example:
 *
 * <pre>
 * java BTCUSDElliottWaveAnalysis
 * </pre>
 * <p>
 * Charts will be saved to {@code temp/charts/} and displayed if running in a
 * non-headless environment.
 *
 * @see ElliottWaveIndicatorSuiteDemo
 * @see org.ta4j.core.indicators.elliott.ElliottWaveFacade
 * @since 0.22.0
 */
public class BTCUSDElliottWaveAnalysis {

    /**
     * Main entry point for BTC-USD Elliott Wave analysis.
     * <p>
     * Loads a local daily Bitcoin dataset and performs Elliott Wave analysis using
     * the PRIMARY degree.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        ElliottWaveIndicatorSuiteDemo.runOssifiedResource(BTCUSDElliottWaveAnalysis.class,
                "Coinbase-BTC-USD-PT1D-20230616_20231011.json", "BTC-USD_PT1D@Coinbase (ossified)",
                ElliottDegree.PRIMARY);
    }

}
