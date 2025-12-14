/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ta4jexamples.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottFibonacciValidator;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottPhaseIndicator;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;

import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

class ElliottWaveAnalysisTest {

    private static final String OSSIFIED_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";
    private static final double FIB_TOLERANCE = 0.25;

    @Test
    void ossifiedDatasetProducesImpulseAndCorrection() {
        BarSeries series = loadOssifiedSeries();
        ElliottSwingIndicator swingIndicator = ElliottSwingIndicator.zigZag(series, ElliottDegree.PRIMARY);
        ElliottFibonacciValidator validator = new ElliottFibonacciValidator(series.numFactory(),
                series.numFactory().numOf(FIB_TOLERANCE));
        ElliottPhaseIndicator phaseIndicator = new ElliottPhaseIndicator(swingIndicator, validator);

        int endIndex = series.getEndIndex();
        assertEquals(ElliottPhase.CORRECTIVE_C, phaseIndicator.getValue(endIndex));
        assertTrue(phaseIndicator.isImpulseConfirmed(endIndex));
        assertTrue(phaseIndicator.isCorrectiveConfirmed(endIndex));

        assertEquals(5, phaseIndicator.impulseSwings(endIndex).size());
        assertEquals(3, phaseIndicator.correctiveSwings(endIndex).size());
    }

    @Test
    void rendersWaveLabelsOnChart() {
        BarSeries series = loadOssifiedSeries();
        ElliottSwingIndicator swingIndicator = ElliottSwingIndicator.zigZag(series, ElliottDegree.PRIMARY);
        ElliottFibonacciValidator validator = new ElliottFibonacciValidator(series.numFactory(),
                series.numFactory().numOf(FIB_TOLERANCE));
        ElliottPhaseIndicator phaseIndicator = new ElliottPhaseIndicator(swingIndicator, validator);

        BarSeriesLabelIndicator waveLabels = buildWaveLabels(series, phaseIndicator);

        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(waveLabels)
                .withLabel("Wave labels")
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot pricePlot = combinedPlot.getSubplots().get(0);

        List<String> annotationTexts = pricePlot.getAnnotations()
                .stream()
                .filter(XYTextAnnotation.class::isInstance)
                .map(annotation -> ((XYTextAnnotation) annotation).getText())
                .toList();

        assertTrue(annotationTexts.containsAll(List.of("1", "2", "3", "4", "5", "A", "B", "C")));
    }

    private static BarSeries loadOssifiedSeries() {
        try (InputStream stream = ElliottWaveAnalysisTest.class.getClassLoader()
                .getResourceAsStream(OSSIFIED_OHLCV_RESOURCE)) {
            assertNotNull(stream, "Missing resource: " + OSSIFIED_OHLCV_RESOURCE);

            BarSeries loaded = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(stream);
            assertNotNull(loaded, "Failed to deserialize BarSeries from resource");

            BarSeries series = new BaseBarSeriesBuilder().withName("BTC-USD_PT1D@Coinbase (ossified)").build();
            for (int i = 0; i < loaded.getBarCount(); i++) {
                series.addBar(loaded.getBar(i));
            }
            return series;
        } catch (Exception ex) {
            throw new AssertionError("Failed to load dataset", ex);
        }
    }

    private static BarSeriesLabelIndicator buildWaveLabels(BarSeries series, ElliottPhaseIndicator phaseIndicator) {
        int endIndex = series.getEndIndex();
        List<ElliottSwing> impulse = phaseIndicator.impulseSwings(endIndex);
        List<ElliottSwing> correction = phaseIndicator.correctiveSwings(endIndex);

        List<BarLabel> labels = new ArrayList<>();
        if (!impulse.isEmpty()) {
            ElliottSwing first = impulse.get(0);
            labels.add(new BarLabel(first.fromIndex(), first.fromPrice(), "", placementForPivot(!first.isRising())));
        }

        for (int i = 0; i < impulse.size(); i++) {
            ElliottSwing swing = impulse.get(i);
            labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), String.valueOf(i + 1),
                    placementForPivot(swing.isRising())));
        }

        for (int i = 0; i < correction.size(); i++) {
            ElliottSwing swing = correction.get(i);
            String label = switch (i) {
            case 0 -> "A";
            case 1 -> "B";
            default -> "C";
            };
            labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), label, placementForPivot(swing.isRising())));
        }

        return new BarSeriesLabelIndicator(series, labels);
    }

    private static LabelPlacement placementForPivot(boolean isHighPivot) {
        return isHighPivot ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
    }
}
