/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

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
    public void writesChartsCsvHtmlAndEscapedExternalAnalysis() throws Exception {
        PortfolioSeries series = new PortfolioSeries(series("ALPHA", 100, 110, 99, 120, 115),
                series("BETA", 100, 105, 101, 111, 109), series("GAMMA", 100, 95, 101, 90, 92));
        PortfolioCorrelations correlations = new PortfolioCorrelations(series);
        Map<String, Num> equalWeights = new LinkedHashMap<>();
        for (String asset : series.getAssets()) {
            equalWeights.put(asset, series.numFactory().one().dividedBy(series.numFactory().numOf(3)));
        }
        PortfolioAllocation equal = new PortfolioAllocation(equalWeights, series.numFactory());
        PortfolioAllocation minimumVariance = new MinimumVarianceOptimizer(series).optimize();
        Num maximumAssetWeight = series.numFactory().numOf(0.5);
        PortfolioAllocation capped = new MinimumVarianceOptimizer(series, maximumAssetWeight).optimize();
        Path externalAnalysis = temporaryDirectory.resolve("analysis.txt");
        Files.writeString(externalAnalysis, "<script>alert(\"unsafe\")</script> & review", StandardCharsets.UTF_8);

        PortfolioAnalysisReport.write(temporaryDirectory, series, correlations.getPriceMatrix(),
                correlations.getSimpleReturnMatrix(), equal, minimumVariance, capped, maximumAssetWeight,
                externalAnalysis);

        assertChart(PortfolioAnalysisReport.PRICE_HEATMAP);
        assertChart(PortfolioAnalysisReport.PRICE_DENDROGRAM);
        assertChart(PortfolioAnalysisReport.RETURN_HEATMAP);
        assertChart(PortfolioAnalysisReport.RETURN_DENDROGRAM);
        assertCsvTables();

        String html = Files.readString(temporaryDirectory.resolve(PortfolioAnalysisReport.HTML_REPORT));
        assertTrue(html.contains("&lt;script&gt;alert(&quot;unsafe&quot;)&lt;/script&gt; &amp; review"));
        assertFalse(html.contains("<script>alert"));
        assertTrue(html.contains("50.00%-capped"));
        String prompt = Files.readString(temporaryDirectory.resolve(PortfolioAnalysisReport.AI_PROMPT));
        assertTrue(prompt.contains("50.00% maximum per asset"));
        assertTrue(prompt.contains("ALPHA / BETA"));
    }

    private void assertChart(String fileName) throws Exception {
        BufferedImage image = ImageIO.read(temporaryDirectory.resolve(fileName).toFile());
        assertNotNull(image);
        assertEquals(1200, image.getWidth());
        assertEquals(900, image.getHeight());
        assertTrue(Files.size(temporaryDirectory.resolve(fileName)) > 10_000);
    }

    private void assertCsvTables() throws Exception {
        List<String> allocations = Files
                .readAllLines(temporaryDirectory.resolve(PortfolioAnalysisReport.ALLOCATIONS_CSV));
        assertEquals("asset,equal_weight,minimum_variance,minimum_variance_capped_0.5", allocations.get(0));
        assertTrue(allocations.get(1).startsWith("ALPHA,"));
        assertEquals(4, allocations.size());
        List<String> returns = Files
                .readAllLines(temporaryDirectory.resolve(PortfolioAnalysisReport.RETURN_CORRELATIONS_CSV));
        assertEquals("asset,ALPHA,BETA,GAMMA", returns.get(0));
        assertEquals(4, returns.size());
        assertEquals(4,
                Files.readAllLines(temporaryDirectory.resolve(PortfolioAnalysisReport.PRICE_CORRELATIONS_CSV)).size());
        assertEquals("merge,left_cluster,right_cluster,distance,size",
                Files.readAllLines(temporaryDirectory.resolve(PortfolioAnalysisReport.RETURN_LINKAGE_CSV)).get(0));
    }

    @Test
    public void csvFieldsQuoteSeparatorsQuotesAndLineBreaks() {
        assertEquals("SPY", PortfolioAnalysisReport.csvField("SPY"));
        assertEquals("\"A,B\"", PortfolioAnalysisReport.csvField("A,B"));
        assertEquals("\"say \"\"hi\"\"\"", PortfolioAnalysisReport.csvField("say \"hi\""));
        assertEquals("\"two\nlines\"", PortfolioAnalysisReport.csvField("two\nlines"));
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
