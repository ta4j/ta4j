/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class LiveElliottWaveAnalysisArgumentsTest {

    private static final Instant FIXED_END_TIME = Instant.parse("2026-02-25T00:00:00Z");
    private static final Instant EXPECTED_START_TIME = FIXED_END_TIME.minus(Duration.ofDays(1825L));

    @Test
    void btcLiveAnalysisBuildsCoinbaseDailyArgs() {
        String[] args = BTCUSDLiveElliottWaveAnalysis.buildSuiteArgs(FIXED_END_TIME);
        assertLiveArgs(args, "Coinbase", "BTC-USD");
    }

    @Test
    void ethLiveAnalysisBuildsCoinbaseDailyArgs() {
        String[] args = ETHUSDLiveElliottWaveAnalysis.buildSuiteArgs(FIXED_END_TIME);
        assertLiveArgs(args, "Coinbase", "ETH-USD");
    }

    @Test
    void sp500LiveAnalysisBuildsYahooDailyArgs() {
        String[] args = SP500LiveElliottWaveAnalysis.buildSuiteArgs(FIXED_END_TIME);
        assertLiveArgs(args, "YahooFinance", "^GSPC");
    }

    private static void assertLiveArgs(final String[] args, final String expectedDataSource,
            final String expectedTicker) {
        assertEquals(5, args.length, "Expected ElliottWaveIndicatorSuiteDemo argument count");
        assertEquals(expectedDataSource, args[0], "Unexpected data source");
        assertEquals(expectedTicker, args[1], "Unexpected ticker");
        assertEquals("PT1D", args[2], "Unexpected bar duration");
        assertEquals(Long.toString(EXPECTED_START_TIME.getEpochSecond()), args[3], "Unexpected start epoch");
        assertEquals(Long.toString(FIXED_END_TIME.getEpochSecond()), args[4], "Unexpected end epoch");
    }
}
