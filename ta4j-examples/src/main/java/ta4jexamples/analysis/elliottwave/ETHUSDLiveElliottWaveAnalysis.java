/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Live Elliott Wave analysis for ETH-USD using Coinbase HTTP data.
 *
 * <p>
 * This temporary entry point fetches daily bars up to the current moment using
 * a five-year lookback window, then delegates to
 * {@link ElliottWaveIndicatorSuiteDemo} for analysis, logging, and chart
 * workflow rendering.
 *
 * @since 0.22.3
 */
public class ETHUSDLiveElliottWaveAnalysis {

    private static final String DATA_SOURCE = "Coinbase";
    private static final String TICKER = "ETH-USD";
    private static final String BAR_DURATION = "PT1D";
    private static final long LOOKBACK_DAYS = 1825L;

    /**
     * Runs live ETH-USD Elliott Wave analysis against Coinbase daily bars.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        ElliottWaveIndicatorSuiteDemo.main(buildSuiteArgs(Instant.now()));
    }

    static String[] buildSuiteArgs(final Instant endTime) {
        Objects.requireNonNull(endTime, "endTime");
        final Instant startTime = endTime.minus(Duration.ofDays(LOOKBACK_DAYS));
        return new String[] { DATA_SOURCE, TICKER, BAR_DURATION, Long.toString(startTime.getEpochSecond()),
                Long.toString(endTime.getEpochSecond()) };
    }
}
