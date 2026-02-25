/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Live Elliott Wave analysis for the S&amp;P 500 Index (^GSPC) using Yahoo
 * Finance HTTP data.
 *
 * <p>
 * This temporary entry point fetches daily bars up to the current moment using
 * a five-year lookback window, then delegates to
 * {@link ElliottWaveIndicatorSuiteDemo} for analysis, logging, and chart
 * workflow rendering.
 *
 * @since 0.22.3
 */
public class SP500LiveElliottWaveAnalysis {

    private static final String DATA_SOURCE = "YahooFinance";
    private static final String TICKER = "^GSPC";
    private static final String BAR_DURATION = "PT1D";
    private static final long LOOKBACK_DAYS = 1825L;

    /**
     * Runs live S&amp;P 500 Elliott Wave analysis against Yahoo Finance daily bars.
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
