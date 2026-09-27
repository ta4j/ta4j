/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jfree.chart.ChartUtils;
import org.ta4j.core.num.Num;
import org.ta4j.core.portfolio.PortfolioAllocation;
import org.ta4j.core.portfolio.PortfolioCorrelations;
import org.ta4j.core.portfolio.PortfolioCorrelations.ClusterMerge;
import org.ta4j.core.portfolio.PortfolioCorrelations.CorrelationHierarchy;
import org.ta4j.core.portfolio.PortfolioCorrelations.CorrelationMatrix;
import org.ta4j.core.portfolio.PortfolioSeries;

import ta4jexamples.charting.compose.PortfolioCorrelationChartFactory;

final class PortfolioAnalysisReport {

    static final String PRICE_HEATMAP = "price-correlation-heatmap.png";
    static final String PRICE_DENDROGRAM = "price-correlation-dendrogram.png";
    static final String RETURN_HEATMAP = "return-correlation-heatmap.png";
    static final String RETURN_DENDROGRAM = "return-correlation-dendrogram.png";
    static final String PRICE_CORRELATIONS_CSV = "price-correlations.csv";
    static final String RETURN_CORRELATIONS_CSV = "return-correlations.csv";
    static final String ALLOCATIONS_CSV = "allocations.csv";
    static final String RETURN_LINKAGE_CSV = "return-linkage.csv";
    static final String HTML_REPORT = "report.html";
    static final String AI_PROMPT = "ai-analysis-prompt.md";

    private PortfolioAnalysisReport() {
    }

    static void write(Path outputDirectory, PortfolioSeries series, CorrelationMatrix priceMatrix,
            CorrelationMatrix returnMatrix, PortfolioAllocation equalWeight, PortfolioAllocation minimumVariance,
            PortfolioAllocation cappedMinimumVariance, double maximumAssetWeight, Path aiAnalysisFile)
            throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(priceMatrix, "priceMatrix");
        Objects.requireNonNull(returnMatrix, "returnMatrix");
        Objects.requireNonNull(equalWeight, "equalWeight");
        Objects.requireNonNull(minimumVariance, "minimumVariance");
        Objects.requireNonNull(cappedMinimumVariance, "cappedMinimumVariance");
        if (!(maximumAssetWeight > 0 && maximumAssetWeight <= 1)) {
            throw new IllegalArgumentException("maximumAssetWeight must be finite and in (0, 1]");
        }
        Num cap = series.numFactory().numOf(maximumAssetWeight);
        // Validate before writing so an undefined coefficient never leaves a partial
        // report behind.
        requireDefinedCorrelations("priceMatrix", priceMatrix);
        requireDefinedCorrelations("returnMatrix", returnMatrix);
        CorrelationHierarchy returnLinkage = returnMatrix.completeLinkage();
        Files.createDirectories(outputDirectory);

        PortfolioCorrelationChartFactory chartFactory = new PortfolioCorrelationChartFactory();
        writeChart(outputDirectory.resolve(PRICE_HEATMAP),
                chartFactory.createHeatmap("Adjusted price correlations", priceMatrix));
        writeChart(outputDirectory.resolve(PRICE_DENDROGRAM),
                chartFactory.createDendrogram("Adjusted price correlation hierarchy", priceMatrix.completeLinkage()));
        writeChart(outputDirectory.resolve(RETURN_HEATMAP),
                chartFactory.createHeatmap("Simple-return correlations", returnMatrix));
        writeChart(outputDirectory.resolve(RETURN_DENDROGRAM),
                chartFactory.createDendrogram("Simple-return correlation hierarchy", returnLinkage));

        writeCsv(outputDirectory.resolve(PRICE_CORRELATIONS_CSV), matrixRows(priceMatrix));
        writeCsv(outputDirectory.resolve(RETURN_CORRELATIONS_CSV), matrixRows(returnMatrix));
        writeCsv(outputDirectory.resolve(ALLOCATIONS_CSV),
                allocationRows(series.getAssets(), equalWeight, minimumVariance, cappedMinimumVariance, cap));
        writeCsv(outputDirectory.resolve(RETURN_LINKAGE_CSV), linkageRows(returnLinkage));
        Files.writeString(outputDirectory.resolve(AI_PROMPT),
                aiPrompt(series, returnMatrix, cappedMinimumVariance, cap), StandardCharsets.UTF_8);
        String externalAnalysis = aiAnalysisFile == null ? null
                : Files.readString(aiAnalysisFile, StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve(HTML_REPORT), htmlReport(series, returnMatrix, equalWeight,
                minimumVariance, cappedMinimumVariance, cap, externalAnalysis), StandardCharsets.UTF_8);
    }

    private static void requireDefinedCorrelations(String name, CorrelationMatrix matrix) {
        for (PortfolioCorrelations.CorrelationPair pair : matrix.getPairs()) {
            if (!Num.isFinite(pair.getCoefficient())) {
                throw new IllegalArgumentException(name + " has an undefined correlation for " + pair.getFirstAsset()
                        + " / " + pair.getSecondAsset() + "; remove assets whose prices are constant in the window");
            }
        }
    }

    private static void writeChart(Path path, org.jfree.chart.JFreeChart chart) throws IOException {
        ChartUtils.saveChartAsPNG(path.toFile(), chart, 1200, 900);
    }

    /*
     * CSV keeps the report dependency-free: spreadsheets open it directly, and the
     * HTML report carries the formatted view. Values are written as plain
     * fractions.
     */
    private static void writeCsv(Path path, List<List<String>> rows) throws IOException {
        StringBuilder csv = new StringBuilder();
        for (List<String> row : rows) {
            for (int column = 0; column < row.size(); column++) {
                if (column > 0) {
                    csv.append(',');
                }
                csv.append(csvField(row.get(column)));
            }
            csv.append("\r\n");
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    static String csvField(String value) {
        Objects.requireNonNull(value, "value");
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static List<List<String>> matrixRows(CorrelationMatrix matrix) {
        List<String> assets = matrix.getAssets();
        List<List<String>> rows = new ArrayList<>();
        List<String> header = new ArrayList<>();
        header.add("asset");
        header.addAll(assets);
        rows.add(header);
        for (String rowAsset : assets) {
            List<String> row = new ArrayList<>();
            row.add(rowAsset);
            for (String columnAsset : assets) {
                row.add(matrix.getCoefficient(rowAsset, columnAsset).toString());
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<List<String>> allocationRows(List<String> assets, PortfolioAllocation equalWeight,
            PortfolioAllocation minimumVariance, PortfolioAllocation cappedMinimumVariance, Num maximumAssetWeight) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("asset", "equal_weight", "minimum_variance",
                "minimum_variance_capped_" + maximumAssetWeight.toString()));
        for (String asset : assets) {
            rows.add(List.of(asset, equalWeight.getTargetWeight(asset).toString(),
                    minimumVariance.getTargetWeight(asset).toString(),
                    cappedMinimumVariance.getTargetWeight(asset).toString()));
        }
        return rows;
    }

    private static List<List<String>> linkageRows(CorrelationHierarchy hierarchy) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("merge", "left_cluster", "right_cluster", "distance", "size"));
        for (int index = 0; index < hierarchy.getMerges().size(); index++) {
            ClusterMerge merge = hierarchy.getMerges().get(index);
            rows.add(List.of(String.valueOf(index + 1), String.valueOf(merge.getLeftClusterIndex()),
                    String.valueOf(merge.getRightClusterIndex()), merge.getDistance().toString(),
                    String.valueOf(merge.getSize())));
        }
        return rows;
    }

    private static String aiPrompt(PortfolioSeries series, CorrelationMatrix returnMatrix,
            PortfolioAllocation recommendation, Num maximumAssetWeight) {
        StringBuilder prompt = new StringBuilder("""
                # Portfolio analysis review

                Review this anonymous, long-only portfolio analysis. Explain diversification strengths, concentration
                risks, highly correlated holdings, and practical limitations. Do not infer an account owner and do not
                present the output as personalized financial advice.

                ## Data window

                """);
        prompt.append("- Assets: ").append(String.join(", ", series.getAssets())).append('\n');
        prompt.append("- Adjusted daily bars: ").append(series.getBarCount()).append('\n');
        prompt.append("- Start: ").append(series.getEndTimes().getFirst()).append('\n');
        prompt.append("- End: ").append(series.getEndTimes().getLast()).append("\n\n");
        prompt.append("## Recommended allocation (")
                .append(percent(maximumAssetWeight))
                .append(" maximum per asset)\n\n");
        appendMarkdownAllocation(prompt, series.getAssets(), recommendation);
        prompt.append("\n## Simple-return correlations\n\n");
        for (PortfolioCorrelations.CorrelationPair pair : returnMatrix.getPairs()) {
            prompt.append(String.format(Locale.ROOT, "- %s / %s: %.4f%n", pair.getFirstAsset(), pair.getSecondAsset(),
                    pair.getCoefficient().doubleValue()));
        }
        return prompt.toString();
    }

    private static String htmlReport(PortfolioSeries series, CorrelationMatrix returnMatrix,
            PortfolioAllocation equalWeight, PortfolioAllocation minimumVariance,
            PortfolioAllocation cappedMinimumVariance, Num maximumAssetWeight, String externalAnalysis) {
        PortfolioCorrelations.CorrelationPair strongest = returnMatrix.getPairs()
                .stream()
                .max((first, second) -> first.getAbsoluteCoefficient().compareTo(second.getAbsoluteCoefficient()))
                .orElseThrow();
        String aiSection = externalAnalysis == null
                ? "<p>No external AI response was supplied. Use <code>ai-analysis-prompt.md</code> with your preferred"
                        + " model, then rerun with <code>--ai-analysis=&lt;file&gt;</code>.</p>"
                : "<pre class=\"ai-response\">" + escapeHtml(externalAnalysis) + "</pre>";
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Diversified Portfolio Analysis</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 0; color: #202124; background: #fafaf8; }
                    main { max-width: 1180px; margin: auto; padding: 28px; }
                    h1, h2 { color: #17252a; }
                    .meta { color: #5f6368; }
                    .charts { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%%, 460px), 1fr)); gap: 18px; }
                    figure { margin: 0; }
                    img { width: 100%%; height: auto; border: 1px solid #d9d9d4; }
                    .table-wrap { overflow-x: auto; }
                    table { width: 100%%; min-width: 640px; border-collapse: collapse; margin: 12px 0 24px; }
                    th, td { padding: 7px 10px; border-bottom: 1px solid #ddd; text-align: right; }
                    th:first-child, td:first-child { text-align: left; }
                    .recommended { background: #e8f4ef; }
                    .ai-response { white-space: pre-wrap; overflow-wrap: anywhere; padding: 16px; background: #f0f2f1; }
                    code { font-family: ui-monospace, monospace; }
                  </style>
                </head>
                <body><main>
                  <h1>Diversified Portfolio Analysis</h1>
                  <p class="meta">Adjusted daily data from %s through %s, %d common observations.</p>
                  <p>This report compares equal weight, unconstrained minimum variance, and a practical %s-capped
                  minimum-variance allocation. The capped result is the recommendation shown here; it is analytical
                  research, not personalized financial advice.</p>
                  <h2>Allocation comparison</h2>
                  %s
                  <h2>Correlation highlights</h2>
                  <p>The largest absolute simple-return relationship is %s / %s at %.3f. Review both the heatmap and
                  hierarchy before treating apparently distinct tickers as independent risk sources.</p>
                  <div class="charts">
                    <figure><img src="%s" alt="Annotated adjusted-price correlation heatmap"></figure>
                    <figure><img src="%s" alt="Adjusted-price complete-linkage dendrogram"></figure>
                    <figure><img src="%s" alt="Annotated simple-return correlation heatmap"></figure>
                    <figure><img src="%s" alt="Simple-return complete-linkage dendrogram"></figure>
                  </div>
                  <h2>External AI analysis</h2>
                  %s
                  <p>Raw data as CSV: <a href="%s">price correlations</a>, <a href="%s">return correlations</a>,
                  <a href="%s">allocations</a>, and <a href="%s">return linkage merges</a>.</p>
                </main></body></html>
                """
                .replace("\n", "%n")
                .formatted(series.getEndTimes().getFirst(), series.getEndTimes().getLast(), series.getBarCount(),
                        percent(maximumAssetWeight),
                        allocationTable(series.getAssets(), equalWeight, minimumVariance, cappedMinimumVariance,
                                maximumAssetWeight),
                        escapeHtml(strongest.getFirstAsset()), escapeHtml(strongest.getSecondAsset()),
                        strongest.getCoefficient().doubleValue(), PRICE_HEATMAP, PRICE_DENDROGRAM, RETURN_HEATMAP,
                        RETURN_DENDROGRAM, aiSection, PRICE_CORRELATIONS_CSV, RETURN_CORRELATIONS_CSV, ALLOCATIONS_CSV,
                        RETURN_LINKAGE_CSV);
    }

    private static String allocationTable(List<String> assets, PortfolioAllocation equalWeight,
            PortfolioAllocation minimumVariance, PortfolioAllocation cappedMinimumVariance, Num maximumAssetWeight) {
        StringBuilder html = new StringBuilder(
                "<div class=\"table-wrap\"><table><thead><tr><th>Asset</th><th>Equal</th><th>Minimum variance</th>"
                        + "<th class=\"recommended\">" + percent(maximumAssetWeight)
                        + " capped</th></tr></thead><tbody>");
        for (String asset : assets) {
            html.append("<tr><td>")
                    .append(escapeHtml(asset))
                    .append("</td><td>")
                    .append(percent(equalWeight.getTargetWeight(asset)))
                    .append("</td><td>")
                    .append(percent(minimumVariance.getTargetWeight(asset)))
                    .append("</td><td class=\"recommended\">")
                    .append(percent(cappedMinimumVariance.getTargetWeight(asset)))
                    .append("</td></tr>");
        }
        return html.append("</tbody></table></div>").toString();
    }

    private static void appendMarkdownAllocation(StringBuilder prompt, List<String> assets,
            PortfolioAllocation allocation) {
        for (String asset : assets) {
            prompt.append("- ")
                    .append(asset)
                    .append(": ")
                    .append(percent(allocation.getTargetWeight(asset)))
                    .append('\n');
        }
    }

    private static String percent(Num value) {
        return String.format(Locale.ROOT, "%.2f%%", value.doubleValue() * 100.0);
    }

    static String escapeHtml(String value) {
        return Objects.requireNonNull(value, "value")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
