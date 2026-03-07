/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;

import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo;

/**
 * Consolidated preset launcher for Elliott Wave demos.
 *
 * <p>
 * This entry point replaces multiple thin wrappers by supporting:
 * <ul>
 * <li>Ossified presets mapped to bundled datasets</li>
 * <li>Live runs where users can specify any ticker</li>
 * </ul>
 *
 * <p>
 * Usage:
 * <ul>
 * <li>{@code ossified <btc|eth|sp500>}</li>
 * <li>{@code live <Coinbase|YahooFinance> <ticker> [barDuration] [lookbackDays] [degree]}</li>
 * </ul>
 *
 * <p>
 * Examples:
 * <ul>
 * <li>{@code ossified btc}</li>
 * <li>{@code live Coinbase BTC-USD PT1D 1825}</li>
 * <li>{@code live YahooFinance AAPL PT1D 1460 PRIMARY}</li>
 * </ul>
 *
 * @since 0.22.4
 */
public class ElliottWavePresetDemo {

    private static final Logger LOG = LogManager.getLogger(ElliottWavePresetDemo.class);
    private static final String DEFAULT_BAR_DURATION = "PT1D";
    private static final long DEFAULT_LOOKBACK_DAYS = 1825L;
    private static final Path DEFAULT_BTC_MACRO_CHART_DIRECTORY = Path.of("temp", "charts");

    /**
     * Runs a preset Elliott Wave demo.
     *
     * @param args command-line arguments; see class-level usage documentation
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            logUsage();
            return;
        }

        final String mode = normalize(args[0]);
        switch (mode) {
        case "ossified":
            runOssified(args);
            return;
        case "live":
            runLive(args);
            return;
        default:
            LOG.error("Unknown mode '{}'. Expected 'ossified' or 'live'.", args[0]);
            logUsage();
        }
    }

    private static void runOssified(String[] args) {
        if (args.length < 2) {
            LOG.error("Missing ossified preset. Expected one of: btc, eth, sp500");
            logUsage();
            return;
        }

        Optional<OssifiedPreset> preset = OssifiedPreset.fromToken(args[1]);
        if (preset.isEmpty()) {
            LOG.error("Unknown ossified preset '{}'. Expected one of: btc, eth, sp500", args[1]);
            logUsage();
            return;
        }

        OssifiedPreset selected = preset.orElseThrow();
        ElliottWaveIndicatorSuiteDemo.runOssifiedResource(ElliottWavePresetDemo.class, selected.resource(),
                selected.seriesName(), selected.degreeOverride().orElse(null));
    }

    private static void runLive(String[] args) {
        if (args.length < 3) {
            LOG.error("Live mode expects at least dataSource and ticker");
            logUsage();
            return;
        }

        final String dataSource = Objects.requireNonNull(args[1], "dataSource");
        final String ticker = Objects.requireNonNull(args[2], "ticker");
        final String barDuration = args.length > 3 ? args[3] : DEFAULT_BAR_DURATION;

        long lookbackDays = DEFAULT_LOOKBACK_DAYS;
        if (args.length > 4) {
            try {
                lookbackDays = Long.parseLong(args[4]);
            } catch (NumberFormatException ex) {
                LOG.error("Invalid lookbackDays '{}': expected a positive integer", args[4]);
                return;
            }
        }
        if (lookbackDays <= 0) {
            LOG.error("Invalid lookbackDays '{}': must be > 0", lookbackDays);
            return;
        }

        ElliottDegree degree = null;
        if (args.length > 5) {
            try {
                degree = ElliottDegree.valueOf(args[5].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                LOG.error("Invalid degree '{}'", args[5]);
                return;
            }
        }

        if (shouldUseBtcMacroPreset(ticker, barDuration)) {
            if (degree != null) {
                LOG.info(
                        "Ignoring explicit degree '{}' for BTC daily macro preset; the validated macro profile selects its own structural interpretation.",
                        degree);
            }
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(Duration.ofDays(lookbackDays));
            Duration parsedDuration = parseBarDuration(barDuration);
            BarSeries series = ElliottWaveIndicatorSuiteDemo.loadSeriesFromDataSource(dataSource, ticker,
                    parsedDuration, startTime, endTime);
            if (series == null || series.isEmpty()) {
                LOG.error("Unable to load live BTC series for macro preset from {} {} {}", dataSource, ticker,
                        barDuration);
                return;
            }
            ElliottWaveBtcMacroCycleDemo.runLivePreset(series, DEFAULT_BTC_MACRO_CHART_DIRECTORY);
            return;
        }

        String[] suiteArgs = buildLiveSuiteArgs(dataSource, ticker, barDuration, lookbackDays, Instant.now(), degree);
        ElliottWaveIndicatorSuiteDemo.main(suiteArgs);
    }

    static boolean shouldUseBtcMacroPreset(String ticker, String barDuration) {
        if (ticker == null || barDuration == null) {
            return false;
        }
        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        String normalizedDuration = barDuration.trim().toUpperCase(Locale.ROOT);
        return "BTC-USD".equals(normalizedTicker)
                && ("PT1D".equals(normalizedDuration) || "PT24H".equals(normalizedDuration));
    }

    static String[] buildLiveSuiteArgs(String dataSource, String ticker, String barDuration, long lookbackDays,
            Instant endTime, ElliottDegree degree) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(barDuration, "barDuration");
        Objects.requireNonNull(endTime, "endTime");
        if (lookbackDays <= 0) {
            throw new IllegalArgumentException("lookbackDays must be > 0");
        }

        Instant startTime = endTime.minus(Duration.ofDays(lookbackDays));
        if (degree == null) {
            return new String[] { dataSource, ticker, barDuration, Long.toString(startTime.getEpochSecond()),
                    Long.toString(endTime.getEpochSecond()) };
        }
        return new String[] { dataSource, ticker, barDuration, degree.name(), Long.toString(startTime.getEpochSecond()),
                Long.toString(endTime.getEpochSecond()) };
    }

    private static void logUsage() {
        LOG.info("Usage:");
        LOG.info("  ossified <btc|eth|sp500>");
        LOG.info("  live <Coinbase|YahooFinance> <ticker> [barDuration] [lookbackDays] [degree]");
    }

    private static Duration parseBarDuration(String barDuration) {
        Objects.requireNonNull(barDuration, "barDuration");
        String normalized = barDuration.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("PT") && normalized.endsWith("D")) {
            int days = Integer.parseInt(normalized.substring(2, normalized.length() - 1));
            return Duration.ofHours(days * 24L);
        }
        return Duration.parse(normalized);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private enum OssifiedPreset {
        BTC("Coinbase-BTC-USD-PT1D-20230616_20231011.json", "BTC-USD_PT1D@Coinbase (ossified)", Optional.empty()),
        ETH("Coinbase-ETH-USD-PT1D-20241105_20251020.json", "ETH-USD_PT1D@Coinbase (ossified)", Optional.empty()),
        SP500("YahooFinance-SP500-PT1D-20230616_20231011.json", "^GSPC_PT1D@YahooFinance (ossified)", Optional.empty());

        private final String resource;
        private final String seriesName;
        private final Optional<ElliottDegree> degreeOverride;

        OssifiedPreset(String resource, String seriesName, Optional<ElliottDegree> degreeOverride) {
            this.resource = resource;
            this.seriesName = seriesName;
            this.degreeOverride = degreeOverride;
        }

        static Optional<OssifiedPreset> fromToken(String token) {
            if (token == null) {
                return Optional.empty();
            }
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
            case "btc" -> Optional.of(BTC);
            case "eth" -> Optional.of(ETH);
            case "sp500", "s&p500", "gspc", "^gspc" -> Optional.of(SP500);
            default -> Optional.empty();
            };
        }

        String resource() {
            return resource;
        }

        String seriesName() {
            return seriesName;
        }

        Optional<ElliottDegree> degreeOverride() {
            return degreeOverride;
        }
    }
}
