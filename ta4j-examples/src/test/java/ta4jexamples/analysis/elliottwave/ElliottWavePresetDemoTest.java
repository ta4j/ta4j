/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.elliott.ElliottDegree;

class ElliottWavePresetDemoTest {

    private static final Instant FIXED_END_TIME = Instant.parse("2026-02-25T00:00:00Z");
    private static final Instant EXPECTED_START_TIME = FIXED_END_TIME.minus(Duration.ofDays(1825L));

    @Test
    void buildLiveSuiteArgsWithoutDegreeUsesExpectedLayout() {
        String[] args = ElliottWavePresetDemo.buildLiveSuiteArgs("Coinbase", "BTC-USD", "PT1D", 1825L, FIXED_END_TIME,
                null);

        assertEquals(5, args.length, "Expected auto-degree argument count");
        assertEquals("Coinbase", args[0], "Unexpected data source");
        assertEquals("BTC-USD", args[1], "Unexpected ticker");
        assertEquals("PT1D", args[2], "Unexpected bar duration");
        assertEquals(Long.toString(EXPECTED_START_TIME.getEpochSecond()), args[3], "Unexpected start epoch");
        assertEquals(Long.toString(FIXED_END_TIME.getEpochSecond()), args[4], "Unexpected end epoch");
    }

    @Test
    void buildLiveSuiteArgsWithDegreeUsesExplicitDegreeLayout() {
        String[] args = ElliottWavePresetDemo.buildLiveSuiteArgs("YahooFinance", "AAPL", "PT1D", 1825L, FIXED_END_TIME,
                ElliottDegree.PRIMARY);

        assertEquals(6, args.length, "Expected explicit-degree argument count");
        assertEquals("YahooFinance", args[0], "Unexpected data source");
        assertEquals("AAPL", args[1], "Unexpected ticker");
        assertEquals("PT1D", args[2], "Unexpected bar duration");
        assertEquals("PRIMARY", args[3], "Unexpected degree");
        assertEquals(Long.toString(EXPECTED_START_TIME.getEpochSecond()), args[4], "Unexpected start epoch");
        assertEquals(Long.toString(FIXED_END_TIME.getEpochSecond()), args[5], "Unexpected end epoch");
    }

    @Test
    void shouldUseBtcMacroPresetForDailyBitcoin() {
        assertTrue(ElliottWavePresetDemo.shouldUseBtcMacroPreset("BTC-USD", "PT1D"));
        assertTrue(ElliottWavePresetDemo.shouldUseBtcMacroPreset("btc-usd", "PT24H"));
    }

    @Test
    void shouldNotUseBtcMacroPresetForOtherAssetsOrIntervals() {
        assertFalse(ElliottWavePresetDemo.shouldUseBtcMacroPreset("ETH-USD", "PT1D"));
        assertFalse(ElliottWavePresetDemo.shouldUseBtcMacroPreset("BTC-USD", "PT4H"));
    }
}
