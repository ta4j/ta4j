/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
    static final String WORKBOOK = "portfolio-analysis.xlsx";
    static final String HTML_REPORT = "report.html";
    static final String AI_PROMPT = "ai-analysis-prompt.md";

    private PortfolioAnalysisReport() {
    }

    static void write(Path outputDirectory, PortfolioSeries series, CorrelationMatrix priceMatrix,
            CorrelationMatrix returnMatrix, PortfolioAllocation equalWeight, PortfolioAllocation minimumVariance,
            PortfolioAllocation cappedMinimumVariance, Path aiAnalysisFile) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(priceMatrix, "priceMatrix");
        Objects.requireNonNull(returnMatrix, "returnMatrix");
        Objects.requireNonNull(equalWeight, "equalWeight");
        Objects.requireNonNull(minimumVariance, "minimumVariance");
        Objects.requireNonNull(cappedMinimumVariance, "cappedMinimumVariance");
        Files.createDirectories(outputDirectory);

        PortfolioCorrelationChartFactory chartFactory = new PortfolioCorrelationChartFactory();
        writeChart(outputDirectory.resolve(PRICE_HEATMAP),
                chartFactory.createHeatmap("Adjusted price correlations", priceMatrix));
        writeChart(outputDirectory.resolve(PRICE_DENDROGRAM),
                chartFactory.createDendrogram("Adjusted price correlation hierarchy", priceMatrix.completeLinkage()));
        writeChart(outputDirectory.resolve(RETURN_HEATMAP),
                chartFactory.createHeatmap("Simple-return correlations", returnMatrix));
        writeChart(outputDirectory.resolve(RETURN_DENDROGRAM),
                chartFactory.createDendrogram("Simple-return correlation hierarchy", returnMatrix.completeLinkage()));

        writeWorkbook(outputDirectory.resolve(WORKBOOK), series, priceMatrix, returnMatrix, equalWeight,
                minimumVariance, cappedMinimumVariance);
        Files.writeString(outputDirectory.resolve(AI_PROMPT), aiPrompt(series, returnMatrix, cappedMinimumVariance),
                StandardCharsets.UTF_8);
        String externalAnalysis = aiAnalysisFile == null ? null
                : Files.readString(aiAnalysisFile, StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve(HTML_REPORT),
                htmlReport(series, returnMatrix, equalWeight, minimumVariance, cappedMinimumVariance, externalAnalysis),
                StandardCharsets.UTF_8);
    }

    private static void writeChart(Path path, org.jfree.chart.JFreeChart chart) throws IOException {
        ChartUtils.saveChartAsPNG(path.toFile(), chart, 1200, 900);
    }

    private static void writeWorkbook(Path path, PortfolioSeries series, CorrelationMatrix priceMatrix,
            CorrelationMatrix returnMatrix, PortfolioAllocation equalWeight, PortfolioAllocation minimumVariance,
            PortfolioAllocation cappedMinimumVariance) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(workbook.createDataFormat().getFormat("0.0000"));
            CellStyle percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));

            Sheet summary = workbook.createSheet("Summary");
            addRow(summary, 0, "Generated", Instant.now().toString());
            addRow(summary, 1, "Aligned bars", series.getBarCount());
            addRow(summary, 2, "First date", series.getEndTimes().getFirst().toString());
            addRow(summary, 3, "Last date", series.getEndTimes().getLast().toString());
            addRow(summary, 4, "Assets", String.join(", ", series.getAssets()));
            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            writeMatrix(workbook.createSheet("Price Correlations"), priceMatrix, decimalStyle);
            writeMatrix(workbook.createSheet("Return Correlations"), returnMatrix, decimalStyle);
            writeAllocations(workbook.createSheet("Allocations"), series.getAssets(), equalWeight, minimumVariance,
                    cappedMinimumVariance, percentStyle);
            writeHierarchy(workbook.createSheet("Return Linkage"), returnMatrix.completeLinkage(), decimalStyle);

            try (OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
    }

    private static void writeMatrix(Sheet sheet, CorrelationMatrix matrix, CellStyle decimalStyle) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Asset");
        for (int column = 0; column < matrix.getAssets().size(); column++) {
            header.createCell(column + 1).setCellValue(matrix.getAssets().get(column));
        }
        for (int rowIndex = 0; rowIndex < matrix.getAssets().size(); rowIndex++) {
            String rowAsset = matrix.getAssets().get(rowIndex);
            Row row = sheet.createRow(rowIndex + 1);
            row.createCell(0).setCellValue(rowAsset);
            for (int columnIndex = 0; columnIndex < matrix.getAssets().size(); columnIndex++) {
                Cell cell = row.createCell(columnIndex + 1);
                cell.setCellValue(matrix.getCoefficient(rowAsset, matrix.getAssets().get(columnIndex)).doubleValue());
                cell.setCellStyle(decimalStyle);
            }
        }
        for (int column = 0; column <= matrix.getAssets().size(); column++) {
            sheet.autoSizeColumn(column);
        }
        sheet.createFreezePane(1, 1);
    }

    private static void writeAllocations(Sheet sheet, List<String> assets, PortfolioAllocation equalWeight,
            PortfolioAllocation minimumVariance, PortfolioAllocation cappedMinimumVariance, CellStyle percentStyle) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Asset");
        header.createCell(1).setCellValue("Equal weight");
        header.createCell(2).setCellValue("Minimum variance");
        header.createCell(3).setCellValue("Minimum variance (25% cap)");
        for (int index = 0; index < assets.size(); index++) {
            String asset = assets.get(index);
            Row row = sheet.createRow(index + 1);
            row.createCell(0).setCellValue(asset);
            addAllocationCell(row, 1, equalWeight.getTargetWeight(asset), percentStyle);
            addAllocationCell(row, 2, minimumVariance.getTargetWeight(asset), percentStyle);
            addAllocationCell(row, 3, cappedMinimumVariance.getTargetWeight(asset), percentStyle);
        }
        for (int column = 0; column < 4; column++) {
            sheet.autoSizeColumn(column);
        }
        sheet.createFreezePane(1, 1);
    }

    private static void addAllocationCell(Row row, int column, Num weight, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(weight.doubleValue());
        cell.setCellStyle(style);
    }

    private static void writeHierarchy(Sheet sheet, CorrelationHierarchy hierarchy, CellStyle decimalStyle) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Merge");
        header.createCell(1).setCellValue("Left cluster");
        header.createCell(2).setCellValue("Right cluster");
        header.createCell(3).setCellValue("Distance");
        header.createCell(4).setCellValue("Size");
        for (int index = 0; index < hierarchy.getMerges().size(); index++) {
            ClusterMerge merge = hierarchy.getMerges().get(index);
            Row row = sheet.createRow(index + 1);
            row.createCell(0).setCellValue(index + 1);
            row.createCell(1).setCellValue(merge.getLeftClusterIndex());
            row.createCell(2).setCellValue(merge.getRightClusterIndex());
            Cell distance = row.createCell(3);
            distance.setCellValue(merge.getDistance().doubleValue());
            distance.setCellStyle(decimalStyle);
            row.createCell(4).setCellValue(merge.getSize());
        }
        for (int column = 0; column < 5; column++) {
            sheet.autoSizeColumn(column);
        }
    }

    private static void addRow(Sheet sheet, int rowIndex, String label, Object value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        if (value instanceof Number number) {
            row.createCell(1).setCellValue(number.doubleValue());
        } else {
            row.createCell(1).setCellValue(String.valueOf(value));
        }
    }

    private static String aiPrompt(PortfolioSeries series, CorrelationMatrix returnMatrix,
            PortfolioAllocation recommendation) {
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
        prompt.append("## Recommended allocation (25% maximum per asset)\n\n");
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
            PortfolioAllocation cappedMinimumVariance, String externalAnalysis) {
        PortfolioCorrelations.CorrelationPair strongest = returnMatrix.getPairs()
                .stream()
                .filter(pair -> Num.isFinite(pair.getCoefficient()))
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
                  <p>This report compares equal weight, unconstrained minimum variance, and a practical 25%%-capped
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
                  <p>Raw matrices, allocations, and linkage merges are available in <a href="%s">%s</a>.</p>
                </main></body></html>
                """
                .replace("\n", "%n")
                .formatted(series.getEndTimes().getFirst(), series.getEndTimes().getLast(), series.getBarCount(),
                        allocationTable(series.getAssets(), equalWeight, minimumVariance, cappedMinimumVariance),
                        escapeHtml(strongest.getFirstAsset()), escapeHtml(strongest.getSecondAsset()),
                        strongest.getCoefficient().doubleValue(), PRICE_HEATMAP, PRICE_DENDROGRAM, RETURN_HEATMAP,
                        RETURN_DENDROGRAM, aiSection, WORKBOOK, WORKBOOK);
    }

    private static String allocationTable(List<String> assets, PortfolioAllocation equalWeight,
            PortfolioAllocation minimumVariance, PortfolioAllocation cappedMinimumVariance) {
        StringBuilder html = new StringBuilder(
                "<div class=\"table-wrap\"><table><thead><tr><th>Asset</th><th>Equal</th><th>Minimum variance</th>"
                        + "<th class=\"recommended\">25% capped</th></tr></thead><tbody>");
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
