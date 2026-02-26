/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

class OssifiedElliottWaveSeriesLoaderTest {

    private static final Logger LOG = LogManager.getLogger(OssifiedElliottWaveSeriesLoaderTest.class);
    private static final String RESOURCE = "Binance-ETH-USD-PT5M-20230313_20230315.json";

    @Test
    void loadSeries_shouldResolveClasspathRootWithoutLeadingSlash() {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(OssifiedElliottWaveSeriesLoaderTest.class,
                RESOURCE, "ETH-USD_PT5M@Binance (ossified)", LOG);

        assertNotNull(series);
        assertEquals("ETH-USD_PT5M@Binance (ossified)", series.getName());
        assertTrue(series.getBarCount() > 0);
    }

    @Test
    void loadSeries_shouldResolveClasspathRootWithLeadingSlash() {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(OssifiedElliottWaveSeriesLoaderTest.class,
                "/" + RESOURCE, "ETH-USD_PT5M@Binance (ossified)", LOG);

        assertNotNull(series);
        assertEquals("ETH-USD_PT5M@Binance (ossified)", series.getName());
        assertTrue(series.getBarCount() > 0);
    }

    @Test
    void loadSeries_shouldReturnNullWhenResourceIsMissing() {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(OssifiedElliottWaveSeriesLoaderTest.class,
                "does-not-exist.json", "missing", LOG);

        assertNull(series);
    }
}
