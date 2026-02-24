/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.confluence.ConfluenceReport;

import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource;
import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource.YahooFinanceInterval;

/**
 * Runnable quick-win example for generating a daily S&P 500 confluence report
 * with chart and JSON artifacts.
 *
 * <p>
 * Usage:
 *
 * <pre>
 * SP500ConfluenceAnalysis[ticker][bars][outputDir]
 * </pre>
 *
 * @since 0.22.3
 */
public final class SP500ConfluenceAnalysis {

    private static final Logger LOG = LogManager.getLogger(SP500ConfluenceAnalysis.class);

    private SP500ConfluenceAnalysis() {
    }

    /**
     * Entrypoint for the confluence quick-win workflow.
     *
     * @param args optional arguments: ticker, bars, output directory
     * @since 0.22.3
     */
    public static void main(String[] args) {
        YahooFinanceHttpBarSeriesDataSource dataSource = new YahooFinanceHttpBarSeriesDataSource(true);
        run(args, (ticker, bars) -> dataSource.loadSeriesInstance(ticker, YahooFinanceInterval.DAY_1, bars),
                new ConfluenceReportGenerator(), outputDir -> new ChartWorkflow(outputDir.toString()),
                GraphicsEnvironment::isHeadless, SP500ConfluenceAnalysis::writeJsonReport);
    }

    static void run(String[] args, SeriesLoader seriesLoader, ConfluenceReportGenerator generator,
            WorkflowFactory workflowFactory, HeadlessProbe headlessProbe, JsonReportWriter jsonReportWriter) {
        Objects.requireNonNull(args, "args cannot be null");
        Objects.requireNonNull(seriesLoader, "seriesLoader cannot be null");
        Objects.requireNonNull(generator, "generator cannot be null");
        Objects.requireNonNull(workflowFactory, "workflowFactory cannot be null");
        Objects.requireNonNull(headlessProbe, "headlessProbe cannot be null");
        Objects.requireNonNull(jsonReportWriter, "jsonReportWriter cannot be null");

        String ticker = args.length > 0 ? args[0] : "^GSPC";
        int bars = args.length > 1 ? Integer.parseInt(args[1]) : 2520;
        Path outputDir = args.length > 2 ? Paths.get(args[2]) : Paths.get("temp", "charts", "confluence");

        LOG.info("Loading {} daily candles ({} bars)", ticker, bars);
        BarSeries series = seriesLoader.load(ticker, bars);
        if (series == null || series.getBarCount() == 0) {
            LOG.error("Failed to load series from Yahoo Finance");
            return;
        }

        ConfluenceReport report = generator.generate(ticker, series);
        printSummary(report);

        ChartWorkflow workflow = workflowFactory.create(outputDir);
        ConfluenceChartAdapter chartAdapter = new ConfluenceChartAdapter(workflow);
        String title = "Confluence Report - " + ticker + " (" + report.snapshot().timeframe() + ")";
        ChartPlan plan = chartAdapter.buildPlan(series, report, title);

        String safeTicker = sanitizeTicker(ticker);
        String chartFile = safeTicker + "-confluence.png";
        Optional<Path> chartPath = workflow.save(plan, outputDir, chartFile);
        chartPath.ifPresent(path -> LOG.info("Chart saved: {}", path));

        if (!headlessProbe.isHeadless()) {
            LOG.info("Displaying chart in realtime for {}", ticker);
            workflow.display(plan, title);
        } else {
            LOG.warn("Headless environment detected; realtime display skipped");
        }

        try {
            Path jsonPath = jsonReportWriter.write(outputDir, safeTicker, report);
            LOG.info("Report JSON saved: {}", jsonPath);
        } catch (IOException e) {
            LOG.error("Failed to write report artifacts", e);
        }
    }

    private static void printSummary(ConfluenceReport report) {
        ConfluenceReport.Snapshot snapshot = report.snapshot();
        LOG.info(String.format(Locale.US, "Snapshot: %s close %.2f, raw %.1f, decorrelated %.1f", snapshot.ticker(),
                snapshot.closePrice(), snapshot.rawConfluenceScore(), snapshot.decorrelatedConfluenceScore()));

        ConfluenceReport.HorizonProbability oneMonth = report.horizonProbabilities()
                .stream()
                .filter(probability -> probability.horizon() == ConfluenceReport.Horizon.ONE_MONTH)
                .findFirst()
                .orElse(null);
        ConfluenceReport.HorizonProbability threeMonth = report.horizonProbabilities()
                .stream()
                .filter(probability -> probability.horizon() == ConfluenceReport.Horizon.THREE_MONTH)
                .findFirst()
                .orElse(null);

        if (oneMonth != null) {
            LOG.info(String.format(Locale.US, "1M: up %.1f%%, down %.1f%%, range %.1f%%",
                    oneMonth.upProbability() * 100.0d, oneMonth.downProbability() * 100.0d,
                    oneMonth.rangeProbability() * 100.0d));
        }
        if (threeMonth != null) {
            LOG.info(String.format(Locale.US, "3M: up %.1f%%, down %.1f%%, range %.1f%%",
                    threeMonth.upProbability() * 100.0d, threeMonth.downProbability() * 100.0d,
                    threeMonth.rangeProbability() * 100.0d));
        }

        LOG.info("Top supports:");
        for (ConfluenceReport.LevelConfidence level : report.topSupports(3)) {
            LOG.info(String.format(Locale.US, "  %s %.2f (confidence %.1f)", level.name(), level.level(),
                    level.confidence()));
        }
        LOG.info("Top resistances:");
        for (ConfluenceReport.LevelConfidence level : report.topResistances(3)) {
            LOG.info(String.format(Locale.US, "  %s %.2f (confidence %.1f)", level.name(), level.level(),
                    level.confidence()));
        }
    }

    static String sanitizeTicker(String ticker) {
        return ticker.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    static Path writeJsonReport(Path outputDir, String safeTicker, ConfluenceReport report) throws IOException {
        Files.createDirectories(outputDir);
        Path jsonPath = outputDir.resolve(safeTicker + "-confluence-report.json");
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (source, type, context) -> {
                    if (source == null) {
                        return null;
                    }
                    return new JsonPrimitive(source.toString());
                })
                .setPrettyPrinting()
                .create();
        Files.writeString(jsonPath, gson.toJson(report));
        return jsonPath;
    }

    @FunctionalInterface
    interface SeriesLoader {
        BarSeries load(String ticker, int bars);
    }

    @FunctionalInterface
    interface WorkflowFactory {
        ChartWorkflow create(Path outputDir);
    }

    @FunctionalInterface
    interface HeadlessProbe {
        boolean isHeadless();
    }

    @FunctionalInterface
    interface JsonReportWriter {
        Path write(Path outputDir, String safeTicker, ConfluenceReport report) throws IOException;
    }
}
