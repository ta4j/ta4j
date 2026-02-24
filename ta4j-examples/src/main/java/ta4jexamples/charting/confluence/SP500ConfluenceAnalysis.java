/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

    private SP500ConfluenceAnalysis() {
    }

    /**
     * Entrypoint for the confluence quick-win workflow.
     *
     * @param args optional arguments: ticker, bars, output directory
     * @since 0.22.3
     */
    public static void main(String[] args) {
        String ticker = args.length > 0 ? args[0] : "^GSPC";
        int bars = args.length > 1 ? Integer.parseInt(args[1]) : 2520;
        Path outputDir = args.length > 2 ? Paths.get(args[2]) : Paths.get("temp", "charts", "confluence");

        System.out.printf("Loading %s daily candles (%d bars)%n", ticker, bars);
        YahooFinanceHttpBarSeriesDataSource dataSource = new YahooFinanceHttpBarSeriesDataSource(true);
        BarSeries series = dataSource.loadSeriesInstance(ticker, YahooFinanceInterval.DAY_1, bars);
        if (series == null || series.getBarCount() == 0) {
            System.err.println("Failed to load series from Yahoo Finance.");
            return;
        }

        ConfluenceReportGenerator generator = new ConfluenceReportGenerator();
        ConfluenceReport report = generator.generate(ticker, series);
        printSummary(report);

        ChartWorkflow workflow = new ChartWorkflow(outputDir.toString());
        ConfluenceChartAdapter chartAdapter = new ConfluenceChartAdapter(workflow);
        String title = "Confluence Report - " + ticker + " (" + report.snapshot().timeframe() + ")";
        ChartPlan plan = chartAdapter.buildPlan(series, report, title);

        String safeTicker = ticker.replaceAll("[^A-Za-z0-9._-]", "_");
        String chartFile = safeTicker + "-confluence.png";
        Optional<Path> chartPath = workflow.save(plan, outputDir, chartFile);
        chartPath.ifPresent(path -> System.out.println("Chart saved: " + path));

        if (!GraphicsEnvironment.isHeadless()) {
            workflow.display(plan, title);
        }

        try {
            Files.createDirectories(outputDir);
            Path jsonPath = outputDir.resolve(safeTicker + "-confluence-report.json");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(jsonPath, gson.toJson(report));
            System.out.println("Report JSON saved: " + jsonPath);
        } catch (IOException e) {
            System.err.println("Failed to write report artifacts: " + e.getMessage());
        }
    }

    private static void printSummary(ConfluenceReport report) {
        ConfluenceReport.Snapshot snapshot = report.snapshot();
        System.out.printf(Locale.US, "Snapshot: %s close %.2f, raw %.1f, decorrelated %.1f%n", snapshot.ticker(),
                snapshot.closePrice(), snapshot.rawConfluenceScore(), snapshot.decorrelatedConfluenceScore());

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
            System.out.printf(Locale.US, "1M: up %.1f%%, down %.1f%%, range %.1f%%%n",
                    oneMonth.upProbability() * 100.0d, oneMonth.downProbability() * 100.0d,
                    oneMonth.rangeProbability() * 100.0d);
        }
        if (threeMonth != null) {
            System.out.printf(Locale.US, "3M: up %.1f%%, down %.1f%%, range %.1f%%%n",
                    threeMonth.upProbability() * 100.0d, threeMonth.downProbability() * 100.0d,
                    threeMonth.rangeProbability() * 100.0d);
        }

        System.out.println("Top supports:");
        for (ConfluenceReport.LevelConfidence level : report.topSupports(3)) {
            System.out.printf(Locale.US, "  %s %.2f (confidence %.1f)%n", level.name(), level.level(),
                    level.confidence());
        }
        System.out.println("Top resistances:");
        for (ConfluenceReport.LevelConfidence level : report.topResistances(3)) {
            System.out.printf(Locale.US, "  %s %.2f (confidence %.1f)%n", level.name(), level.level(),
                    level.confidence());
        }
    }
}
