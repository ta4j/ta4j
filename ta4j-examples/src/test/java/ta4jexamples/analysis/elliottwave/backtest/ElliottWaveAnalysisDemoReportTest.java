/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ta4jexamples.charting.display.SwingChartDisplayer;
import ta4jexamples.datasources.CoinbaseHttpBarSeriesDataSource;
import ta4jexamples.datasources.CoinbaseHttpBarSeriesDataSource.CoinbaseInterval;

/**
 * Black-box analysis-demo funnel for the live Elliott Wave macro report.
 *
 * <p>
 * Set {@code ta4j.analysisDemoInstrument} to a provider-qualified input such as
 * {@code coinbase:BTC-USD}. Set {@code ta4j.analysisDemoOutputDir} to control
 * where JSON, chart, and cached provider-response artifacts are written.
 */
@Tag("analysis-demo")
class ElliottWaveAnalysisDemoReportTest {

    private static final String INSTRUMENT_PROPERTY = "ta4j.analysisDemoInstrument";
    private static final String OUTPUT_DIRECTORY_PROPERTY = "ta4j.analysisDemoOutputDir";
    private static final String DEFAULT_INSTRUMENT = "coinbase:BTC-USD";
    private static final String COINBASE_PROVIDER = "coinbase";
    private static final Pattern COINBASE_PRODUCT_ID_PATTERN = Pattern.compile("[A-Z0-9]+(?:-[A-Z0-9]+)+");
    private static final int DAILY_BAR_COUNT = 1825;

    @TempDir
    Path tempDirectory;

    @Test
    void liveMacroReportProducesCurrentElliottAnalysisArtifacts() throws IOException {
        String previousDisableDisplay = System.getProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        try {
            System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
            AnalysisInstrument instrument = parseInstrument(
                    System.getProperty(INSTRUMENT_PROPERTY, DEFAULT_INSTRUMENT));
            Path outputDirectory = resolveOutputDirectory();
            Path responseCacheDirectory = outputDirectory.resolve("responses");
            Files.createDirectories(responseCacheDirectory);

            BarSeries series = loadSeries(instrument, responseCacheDirectory);

            assertNotNull(series);
            assertFalse(series.isEmpty());
            assertTrue(series.getBarCount() > 1000,
                    () -> "Expected enough daily bars for live macro analysis but got " + series.getBarCount());

            ElliottWaveMacroCycleDemo.LivePresetReport report = ElliottWaveMacroCycleDemo
                    .generateLivePresetReport(series, outputDirectory);

            Path summaryPath = Path.of(report.summaryPath());
            Path chartPath = Path.of(report.chartPath());
            assertEquals(instrument.productId(), report.seriesName());
            assertTrue(Files.exists(summaryPath), () -> "Missing summary artifact: " + summaryPath);
            assertTrue(Files.size(summaryPath) > 0, () -> "Empty summary artifact: " + summaryPath);
            assertTrue(Files.exists(chartPath), () -> "Missing chart artifact: " + chartPath);
            assertTrue(Files.size(chartPath) > 0, () -> "Empty chart artifact: " + chartPath);
            assertTrue(hasCachedResponses(responseCacheDirectory),
                    () -> "Expected cached provider responses under " + responseCacheDirectory);

            JsonObject summary = JsonParser.parseString(Files.readString(summaryPath)).getAsJsonObject();
            JsonObject currentCycle = summary.getAsJsonObject("currentCycle");
            assertEquals(instrument.productId(), summary.get("seriesName").getAsString());
            assertNotNull(currentCycle);
            assertFalse(currentCycle.get("primaryCount").getAsString().isBlank());
            assertFalse(currentCycle.get("currentWave").getAsString().isBlank());
            assertEquals(chartPath.toAbsolutePath().normalize().toString(),
                    currentCycle.get("chartPath").getAsString());
        } finally {
            restoreDisableDisplayProperty(previousDisableDisplay);
        }
    }

    @Test
    void providerQualifiedInstrumentAcceptsCoinbaseProducts() {
        AnalysisInstrument btcUsd = parseInstrument(" coinbase:btc/usd ");
        AnalysisInstrument ethUsd = parseInstrument("COINBASE:eth-usd");

        assertEquals(COINBASE_PROVIDER, btcUsd.provider());
        assertEquals("BTC-USD", btcUsd.productId());
        assertEquals(COINBASE_PROVIDER, ethUsd.provider());
        assertEquals("ETH-USD", ethUsd.productId());
    }

    @Test
    void providerQualifiedInstrumentRejectsUnsupportedProviders() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> parseInstrument("yahoo:SPY"));

        assertTrue(exception.getMessage().contains("Unsupported analysis demo provider"));
    }

    @Test
    void providerQualifiedInstrumentRejectsInvalidCoinbaseProductIds() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> parseInstrument("coinbase:BTC USD"));

        assertTrue(exception.getMessage().contains("Coinbase analysis demo product id"));
    }

    private static BarSeries loadSeries(final AnalysisInstrument instrument, final Path responseCacheDirectory) {
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(
                responseCacheDirectory.toString());
        BarSeries series = dataSource.loadSeriesInstance(instrument.productId(), CoinbaseInterval.ONE_DAY,
                DAILY_BAR_COUNT, "analysis-demo");
        if (series == null || series.isEmpty()) {
            throw new IllegalStateException("No daily Coinbase candles returned for " + instrument.productId());
        }
        return series;
    }

    private Path resolveOutputDirectory() {
        String configured = System.getProperty(OUTPUT_DIRECTORY_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return tempDirectory.resolve("analysis-demos").resolve("elliott-wave").toAbsolutePath().normalize();
        }

        Path configuredPath = Path.of(configured.trim());
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }

        return repositoryRoot().resolve(configuredPath).toAbsolutePath().normalize();
    }

    private static AnalysisInstrument parseInstrument(final String rawInstrument) {
        String instrument = rawInstrument == null ? "" : rawInstrument.trim();
        int separator = instrument.indexOf(':');
        if (separator <= 0 || separator == instrument.length() - 1) {
            throw new IllegalArgumentException(
                    "Analysis demo instrument must use provider-qualified format, for example coinbase:BTC-USD");
        }

        String provider = instrument.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String productId = instrument.substring(separator + 1).trim().toUpperCase(Locale.ROOT).replace('/', '-');
        if (!COINBASE_PROVIDER.equals(provider)) {
            throw new IllegalArgumentException(
                    "Unsupported analysis demo provider '" + provider + "'. Supported providers: coinbase");
        }
        if (productId.isBlank()) {
            throw new IllegalArgumentException("Coinbase analysis demo product id cannot be blank");
        }
        if (!COINBASE_PRODUCT_ID_PATTERN.matcher(productId).matches()) {
            throw new IllegalArgumentException(
                    "Coinbase analysis demo product id must use Coinbase product format, for example BTC-USD");
        }
        return new AnalysisInstrument(provider, productId);
    }

    private static void restoreDisableDisplayProperty(final String previousDisableDisplay) {
        if (previousDisableDisplay == null) {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        } else {
            System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, previousDisableDisplay);
        }
    }

    private static boolean hasCachedResponses(final Path responseCacheDirectory) throws IOException {
        if (!Files.isDirectory(responseCacheDirectory)) {
            return false;
        }
        try (Stream<Path> responses = Files.list(responseCacheDirectory)) {
            return responses.anyMatch(path -> path.getFileName().toString().endsWith(".json"));
        }
    }

    private static Path repositoryRoot() {
        String rootDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (rootDirectory != null && !rootDirectory.isBlank()) {
            Path candidate = Path.of(rootDirectory).toAbsolutePath().normalize();
            if (isRepositoryRoot(candidate)) {
                return candidate;
            }
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (isRepositoryRoot(candidate)) {
                return candidate;
            }
        }
        return current;
    }

    private static boolean isRepositoryRoot(final Path candidate) {
        return Files.isRegularFile(candidate.resolve("pom.xml")) && Files.isDirectory(candidate.resolve("ta4j-core"))
                && Files.isDirectory(candidate.resolve("ta4j-examples"));
    }

    private record AnalysisInstrument(String provider, String productId) {

        private AnalysisInstrument {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(productId, "productId");
        }
    }
}
