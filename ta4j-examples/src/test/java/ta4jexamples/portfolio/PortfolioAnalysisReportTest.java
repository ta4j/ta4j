/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.portfolio.MinimumVarianceOptimizer;
import org.ta4j.core.portfolio.PortfolioAllocation;
import org.ta4j.core.portfolio.PortfolioCorrelations;
import org.ta4j.core.portfolio.PortfolioSeries;

public class PortfolioAnalysisReportTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    public void writesChartsWorkbookHtmlAndEscapedExternalAnalysis() throws Exception {
        PortfolioSeries series = new PortfolioSeries(series("ALPHA", 100, 110, 99, 120, 115),
                series("BETA", 100, 105, 101, 111, 109), series("GAMMA", 100, 95, 101, 90, 92));
        PortfolioCorrelations correlations = new PortfolioCorrelations(series);
        Map<String, Num> equalWeights = new LinkedHashMap<>();
        for (String asset : series.getAssets()) {
            equalWeights.put(asset, series.numFactory().one().dividedBy(series.numFactory().numOf(3)));
        }
        PortfolioAllocation equal = new PortfolioAllocation(equalWeights, series.numFactory());
        PortfolioAllocation minimumVariance = new MinimumVarianceOptimizer(series).optimize();
        PortfolioAllocation capped = new MinimumVarianceOptimizer(series, series.numFactory().numOf(0.5)).optimize();
        Path externalAnalysis = temporaryDirectory.resolve("analysis.txt");
        Files.writeString(externalAnalysis, "<script>alert(\"unsafe\")</script> & review", StandardCharsets.UTF_8);

        PortfolioAnalysisReport.write(temporaryDirectory, series, correlations.getPriceMatrix(),
                correlations.getSimpleReturnMatrix(), equal, minimumVariance, capped, externalAnalysis);

        assertChart(PortfolioAnalysisReport.PRICE_HEATMAP);
        assertChart(PortfolioAnalysisReport.PRICE_DENDROGRAM);
        assertChart(PortfolioAnalysisReport.RETURN_HEATMAP);
        assertChart(PortfolioAnalysisReport.RETURN_DENDROGRAM);
        assertWorkbook();

        String html = Files.readString(temporaryDirectory.resolve(PortfolioAnalysisReport.HTML_REPORT));
        assertTrue(html.contains("&lt;script&gt;alert(&quot;unsafe&quot;)&lt;/script&gt; &amp; review"));
        assertFalse(html.contains("<script>alert"));
        String prompt = Files.readString(temporaryDirectory.resolve(PortfolioAnalysisReport.AI_PROMPT));
        assertTrue(prompt.contains("25% maximum per asset"));
        assertTrue(prompt.contains("ALPHA / BETA"));
    }

    private void assertChart(String fileName) throws Exception {
        BufferedImage image = ImageIO.read(temporaryDirectory.resolve(fileName).toFile());
        assertNotNull(image);
        assertEquals(1200, image.getWidth());
        assertEquals(900, image.getHeight());
        assertTrue(Files.size(temporaryDirectory.resolve(fileName)) > 10_000);
    }

    private void assertWorkbook() throws Exception {
        try (InputStream input = Files.newInputStream(temporaryDirectory.resolve(PortfolioAnalysisReport.WORKBOOK));
                XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            assertNotNull(workbook.getSheet("Summary"));
            assertNotNull(workbook.getSheet("Price Correlations"));
            assertNotNull(workbook.getSheet("Return Correlations"));
            assertNotNull(workbook.getSheet("Allocations"));
            assertNotNull(workbook.getSheet("Return Linkage"));
            assertEquals("ALPHA", workbook.getSheet("Allocations").getRow(1).getCell(0).getStringCellValue());
        }
    }

    private static BarSeries series(String name, double... closes) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Num zero = series.numFactory().zero();
        for (int index = 0; index < closes.length; index++) {
            Num close = series.numFactory().numOf(closes[index]);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(zero)
                    .add();
        }
        return series;
    }
}
