/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

/**
 * Example Elliott Wave analysis for S&P 500 Index (^GSPC) using Yahoo Finance
 * data from an ossified local dataset.
 * <p>
 * This class demonstrates a simple usage pattern for analyzing S&P 500 index
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
 * java SP500ElliottWaveAnalysis
 * </pre>
 * <p>
 * Charts will be saved to {@code temp/charts/} and displayed if running in a
 * non-headless environment.
 *
 * @see ElliottWaveIndicatorSuiteDemo
 * @see org.ta4j.core.indicators.elliott.ElliottWaveFacade
 * @since 0.22.0
 */
public class SP500ElliottWaveAnalysis {

    /**
     * Main entry point for S&P 500 Elliott Wave analysis.
     * <p>
     * Loads local daily S&P 500 index data and performs Elliott Wave analysis with
     * auto-selected degree.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        ElliottWaveIndicatorSuiteDemo.runOssifiedResource(SP500ElliottWaveAnalysis.class,
                "YahooFinance-SP500-PT1D-20230616_20231011.json", "^GSPC_PT1D@YahooFinance (ossified)", null);
    }
}
