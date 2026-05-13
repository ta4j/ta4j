/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class IndicatorFamilyManagerTest {

    @Test
    public void analyzesNamedIndicatorsInCallerOrder() {
        BarSeries series = seriesOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator smoothed = new SMAIndicator(close, 3);
        Indicator<Num> closeInverse = BinaryOperationIndicator.product(close, -1);

        IndicatorFamilyResult result = new IndicatorFamilyManager(series)
                .analyze(namedIndicators(testIndicator("close", close), testIndicator("sma", smoothed),
                        testIndicator("closeInverse", closeInverse)), 0.93);

        assertThat(result.similarityThreshold()).isEqualTo(0.93);
        assertThat(result.stableIndex()).isEqualTo(121);
        assertThat(result.families()).extracting(IndicatorFamilyResult.Family::indicatorNames)
                .containsExactly(List.of("close"), List.of("sma"), List.of("closeInverse"));
        assertThat(result.familyByIndicator().keySet()).containsExactly("close", "sma", "closeInverse");
        assertThat(result.pairSimilarities())
                .extracting(IndicatorFamilyResult.PairSimilarity::firstIndicatorName,
                        IndicatorFamilyResult.PairSimilarity::secondIndicatorName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("close", "sma"),
                        org.assertj.core.groups.Tuple.tuple("close", "closeInverse"),
                        org.assertj.core.groups.Tuple.tuple("sma", "closeInverse"));
    }

    @Test
    public void thresholdControlsFamilyMerging() {
        BarSeries series = increasingSeries(180);
        Indicator<Num> base = mockIndicator(series, index -> index);
        Indicator<Num> noisyTrend = mockIndicator(series, index -> index + (20.0 * Math.sin(index / 3.0)));
        Indicator<Num> alternating = mockIndicator(series, index -> index % 2 == 0 ? 1.0 : -1.0);
        Map<String, Indicator<Num>> indicators = namedIndicators(testIndicator("base", base),
                testIndicator("noisyTrend", noisyTrend), testIndicator("alternating", alternating));
        IndicatorFamilyManager manager = new IndicatorFamilyManager(series);

        IndicatorFamilyResult loose = manager.analyze(indicators, 0.80);
        IndicatorFamilyResult strict = manager.analyze(indicators, 0.97);

        assertThat(loose.familyByIndicator().get("base")).isEqualTo(loose.familyByIndicator().get("noisyTrend"));
        assertThat(strict.familyByIndicator().get("base")).isNotEqualTo(strict.familyByIndicator().get("noisyTrend"));
        assertThat(loose.families()).hasSize(2);
        assertThat(strict.families()).hasSize(3);
    }

    @Test
    public void usesAbsoluteCorrelationForInverseIndicators() {
        BarSeries series = increasingSeries(180);
        Indicator<Num> base = mockIndicator(series, index -> index);
        Indicator<Num> inverse = mockIndicator(series, index -> -index);

        IndicatorFamilyResult result = new IndicatorFamilyManager(series)
                .analyze(namedIndicators(testIndicator("base", base), testIndicator("inverse", inverse)), 0.99);

        assertThat(result.families()).hasSize(1);
        assertThat(result.familyByIndicator().values()).containsOnly("family-001");
        assertThat(result.pairSimilarities().get(0).similarity()).isEqualTo(1.0);
    }

    @Test
    public void clampsStableIndexToRollingSeriesBeginIndex() {
        BarSeries series = increasingSeries(180);
        series.setMaximumBarCount(30);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Indicator<Num> inverse = BinaryOperationIndicator.product(close, -1);

        IndicatorFamilyResult result = new IndicatorFamilyManager(series)
                .analyze(namedIndicators(testIndicator("close", close), testIndicator("inverse", inverse)), 0.99);

        assertThat(series.getBeginIndex()).isEqualTo(150);
        assertThat(result.stableIndex()).isEqualTo(series.getBeginIndex());
        assertThat(result.families()).hasSize(1);
        assertThat(result.pairSimilarities().get(0).similarity()).isEqualTo(1.0);
    }

    @Test
    public void returnsZeroSimilarityWhenNoStableSamplesExist() {
        BarSeries series = seriesOf(1, 2, 3);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Indicator<Num> inverse = BinaryOperationIndicator.product(close, -1);

        IndicatorFamilyResult result = new IndicatorFamilyManager(series)
                .analyze(namedIndicators(testIndicator("close", close), testIndicator("inverse", inverse)), 0.01);

        assertThat(result.pairSimilarities()).hasSize(1);
        assertThat(result.pairSimilarities().get(0).similarity()).isEqualTo(0.0);
        assertThat(result.families()).hasSize(2);
        assertThat(result.stableIndex()).isEqualTo(119);
    }

    @Test
    public void resultCollectionsAreImmutable() {
        BarSeries series = increasingSeries(180);
        Indicator<Num> base = mockIndicator(series, index -> index);
        IndicatorFamilyResult result = new IndicatorFamilyManager(series)
                .analyze(namedIndicators(testIndicator("base", base)), 0.90);

        assertThrows(UnsupportedOperationException.class, () -> result.familyByIndicator().put("other", "family-999"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.families().add(new IndicatorFamilyResult.Family("family-999", List.of("other"))));
        assertThrows(UnsupportedOperationException.class, () -> result.families().get(0).indicatorNames().add("other"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.pairSimilarities().add(new IndicatorFamilyResult.PairSimilarity("a", "b", 0.5)));
    }

    @Test
    public void validatesInputs() {
        BarSeries series = increasingSeries(180);
        BarSeries otherSeries = increasingSeries(180);
        Indicator<Num> base = mockIndicator(series, index -> index);
        Indicator<Num> other = mockIndicator(otherSeries, index -> index);
        IndicatorFamilyManager manager = new IndicatorFamilyManager(series);

        assertThrows(NullPointerException.class, () -> new IndicatorFamilyManager(null));
        assertThrows(IllegalArgumentException.class, () -> new IndicatorFamilyManager(
                new BaseBarSeriesBuilder().withBarBuilderFactory(new MockBarBuilderFactory()).build()));
        assertThrows(IllegalArgumentException.class, () -> manager.analyze(Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> manager.analyze(namedIndicators(testIndicator("base", base)), Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> manager.analyze(namedIndicators(testIndicator("base", base)), -0.01));
        assertThrows(IllegalArgumentException.class,
                () -> manager.analyze(namedIndicators(testIndicator("base", base)), 1.01));
        assertThrows(IllegalArgumentException.class,
                () -> manager.analyze(namedIndicators(testIndicator("base", other)), 0.90));

        LinkedHashMap<String, Indicator<Num>> nullIndicator = new LinkedHashMap<>();
        nullIndicator.put("base", null);
        assertThrows(IllegalArgumentException.class, () -> manager.analyze(nullIndicator));

        LinkedHashMap<String, Indicator<Num>> blankName = new LinkedHashMap<>();
        blankName.put(" ", base);
        assertThrows(IllegalArgumentException.class, () -> manager.analyze(blankName));
    }

    private static BarSeries increasingSeries(int barCount) {
        BarSeries series = new BaseBarSeriesBuilder().withBarBuilderFactory(new MockBarBuilderFactory()).build();
        for (int index = 0; index < barCount; index++) {
            series.barBuilder().closePrice(index).add();
        }
        return series;
    }

    private static Indicator<Num> mockIndicator(BarSeries series, ValueFactory valueFactory) {
        NumFactory numFactory = series.numFactory();
        List<Num> values = new ArrayList<>(series.getBarCount());
        for (int index = 0; index < series.getBarCount(); index++) {
            values.add(numFactory.numOf(valueFactory.valueAt(index)));
        }
        return new MockIndicator(series, values);
    }

    private static BarSeries seriesOf(double... values) {
        BarSeries series = new BaseBarSeriesBuilder().withBarBuilderFactory(new MockBarBuilderFactory()).build();
        for (double value : values) {
            series.barBuilder().closePrice(value).add();
        }
        return series;
    }

    private static Map<String, Indicator<Num>> namedIndicators(TestIndicator... entries) {
        LinkedHashMap<String, Indicator<Num>> indicators = new LinkedHashMap<>();
        for (TestIndicator entry : entries) {
            indicators.put(entry.name(), entry.indicator());
        }
        return indicators;
    }

    private static TestIndicator testIndicator(String name, Indicator<Num> indicator) {
        return new TestIndicator(name, indicator);
    }

    private record TestIndicator(String name, Indicator<Num> indicator) {
    }

    @FunctionalInterface
    private interface ValueFactory {
        double valueAt(int index);
    }
}
