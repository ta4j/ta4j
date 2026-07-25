/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.WeightedValue;
import org.ta4j.core.num.Num;
import org.ta4j.core.portfolio.MinimumVarianceOptimizer;
import org.ta4j.core.portfolio.PortfolioAllocation;
import org.ta4j.core.portfolio.PortfolioCorrelations;
import org.ta4j.core.portfolio.PortfolioCorrelations.CorrelationMatrix;
import org.ta4j.core.portfolio.PortfolioSeries;

import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource;
import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource.YahooFinanceInterval;

/**
 * Produces a complete anonymous portfolio analytics report from adjusted YTD
 * market data.
 *
 * <p>
 * The report contains annotated price and return heatmaps, complete-linkage
 * dendrograms, equal-weight and minimum-variance allocations, an XLSX workbook,
 * HTML, and a prompt for optional external AI analysis.
 * </p>
 *
 * @since 0.23.1
 */
public final class DiversifiedPortfolioAnalysis {

    private static final List<String> TICKERS = List.of("QQQ", "VWO", "COIN", "FBTC", "IBIT", "ETHW", "RES", "XOM",
            "DOC", "NKE", "ARKK", "TLT");

    private DiversifiedPortfolioAnalysis() {
    }

    public static void main(String[] args) throws IOException {
        Arguments arguments = Arguments.parse(args);
        PortfolioSeries series = loadAdjustedYearToDateSeries();
        PortfolioCorrelations correlations = new PortfolioCorrelations(series);
        CorrelationMatrix priceMatrix = correlations.getPriceMatrix();
        CorrelationMatrix returnMatrix = correlations.getSimpleReturnMatrix();

        List<WeightedValue<String>> equalInputs = new ArrayList<>(series.getAssets().size());
        for (String asset : series.getAssets()) {
            equalInputs.add(new WeightedValue<>(asset, series.numFactory().one()));
        }
        PortfolioAllocation equalWeight = new PortfolioAllocation(equalInputs, series.numFactory());
        PortfolioAllocation minimumVariance = new MinimumVarianceOptimizer(series).optimize();
        PortfolioAllocation cappedMinimumVariance = new MinimumVarianceOptimizer(series,
                series.numFactory().numOf(0.25)).optimize();

        PortfolioAnalysisReport.write(arguments.outputDirectory(), series, priceMatrix, returnMatrix, equalWeight,
                minimumVariance, cappedMinimumVariance, arguments.aiAnalysisFile());
        printSummary(arguments.outputDirectory(), series, minimumVariance, cappedMinimumVariance);
    }

    private static PortfolioSeries loadAdjustedYearToDateSeries() {
        Instant end = Instant.now();
        Instant start = LocalDate.of(Year.now(ZoneOffset.UTC).getValue(), 1, 1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        YahooFinanceHttpBarSeriesDataSource dataSource = new YahooFinanceHttpBarSeriesDataSource(true);
        List<BarSeries> assetSeries = new ArrayList<>(TICKERS.size());
        for (String ticker : TICKERS) {
            BarSeries rawSeries = dataSource.loadAdjustedSeriesInstance(ticker, YahooFinanceInterval.DAY_1, start, end);
            if (rawSeries == null || rawSeries.isEmpty()) {
                throw new IllegalStateException("No adjusted Yahoo Finance data returned for " + ticker);
            }
            assetSeries.add(PortfolioCorrelationAnalysis.normalizeDailySeries(ticker, rawSeries));
        }
        return new PortfolioSeries(assetSeries);
    }

    private static void printSummary(Path outputDirectory, PortfolioSeries series, PortfolioAllocation minimumVariance,
            PortfolioAllocation cappedMinimumVariance) {
        System.out.printf("Portfolio report generated in %s%n", outputDirectory.toAbsolutePath());
        System.out.printf("Common adjusted daily bars: %d%n", series.getBarCount());
        System.out.println("Uncapped minimum-variance allocation:");
        printAllocation(series, minimumVariance);
        System.out.println("Recommended 25%-capped allocation:");
        printAllocation(series, cappedMinimumVariance);
    }

    private static void printAllocation(PortfolioSeries series, PortfolioAllocation allocation) {
        for (String asset : series.getAssets()) {
            Num weight = allocation.getTargetWeight(asset);
            if (weight.isPositive()) {
                System.out.printf(Locale.ROOT, "  %-5s %6.2f%%%n", asset, weight.doubleValue() * 100.0);
            }
        }
    }

    private record Arguments(Path outputDirectory, Path aiAnalysisFile) {

        private static Arguments parse(String[] args) {
            Path outputDirectory = Path.of("target", "portfolio-analysis");
            Path aiAnalysisFile = null;
            for (String argument : args) {
                if (argument.startsWith("--output=")) {
                    outputDirectory = Path.of(argument.substring("--output=".length()));
                } else if (argument.startsWith("--ai-analysis=")) {
                    aiAnalysisFile = Path.of(argument.substring("--ai-analysis=".length()));
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + argument);
                }
            }
            return new Arguments(outputDirectory, aiAnalysisFile);
        }
    }
}
