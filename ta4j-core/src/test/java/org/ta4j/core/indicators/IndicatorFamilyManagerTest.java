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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator;
import org.ta4j.core.indicators.statistics.SampleType;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.NaN;
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

        assertThat(result.similarityThreshold()).isEqualByComparingTo(series.numFactory().numOf("0.93"));
        assertThat(series.numFactory().produces(result.similarityThreshold())).isTrue();
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
        assertThat(result.families()).extracting(IndicatorFamilyResult.Family::representativeIndicatorName)
                .containsExactly("close", "sma", "closeInverse");
        assertThat(result.families()).extracting(IndicatorFamilyResult.Family::averageInternalSimilarity)
                .allSatisfy(similarity -> assertThat(similarity).isEqualByComparingTo(series.numFactory().one()));
        assertThat(result.families()).extracting(IndicatorFamilyResult.Family::minimumInternalSimilarity)
                .allSatisfy(similarity -> assertThat(similarity).isEqualByComparingTo(series.numFactory().one()));
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
        assertThat(result.pairSimilarities().get(0).similarity()).isEqualByComparingTo(series.numFactory().one());
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
        assertThat(result.pairSimilarities().get(0).similarity()).isEqualByComparingTo(series.numFactory().one());
    }

    @Test
    public void returnsZeroSimilarityWhenNoStableSamplesExist() {
        BarSeries series = seriesOf(1, 2, 3);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Indicator<Num> inverse = BinaryOperationIndicator.product(close, -1);

        IndicatorFamilyResult result = new IndicatorFamilyManager(series)
                .analyze(namedIndicators(testIndicator("close", close), testIndicator("inverse", inverse)), 0.01);

        assertThat(result.pairSimilarities()).hasSize(1);
        IndicatorFamilyResult.PairSimilarity pair = result.pairSimilarities().get(0);
        assertThat(pair.similarity()).isEqualByComparingTo(series.numFactory().zero());
        assertThat(pair.signedAverageSimilarity()).isEqualByComparingTo(series.numFactory().zero());
        assertThat(pair.latestSignedSimilarity()).isEqualByComparingTo(series.numFactory().zero());
        assertThat(pair.sampleCount()).isZero();
        assertThat(pair.minimumSignedSimilarity()).isEqualByComparingTo(series.numFactory().zero());
        assertThat(pair.maximumSignedSimilarity()).isEqualByComparingTo(series.numFactory().zero());
        assertThat(result.families()).hasSize(2);
        assertThat(result.stableIndex()).isEqualTo(119);
    }

    @Test
    public void customCorrelationWindowControlsStableSamples() {
        BarSeries series = seriesOf(1, 2, 3);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Indicator<Num> inverse = BinaryOperationIndicator.product(close, -1);

        IndicatorFamilyResult result = new IndicatorFamilyManager(series, 3)
                .analyze(namedIndicators(testIndicator("close", close), testIndicator("inverse", inverse)), 0.99);

        assertThat(result.pairSimilarities()).hasSize(1);
        assertThat(result.pairSimilarities().get(0).similarity()).isEqualByComparingTo(series.numFactory().one());
        assertThat(result.families()).hasSize(1);
        assertThat(result.stableIndex()).isEqualTo(2);
    }

    @Test
    public void exposesSignedPairSimilarityStatistics() {
        BarSeries series = seriesOf(1, 2, 3, 4, 5);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Indicator<Num> inverse = BinaryOperationIndicator.product(close, -1);

        IndicatorFamilyResult result = new IndicatorFamilyManager(series, 3)
                .analyze(namedIndicators(testIndicator("close", close), testIndicator("inverse", inverse)), 0.99);

        IndicatorFamilyResult.PairSimilarity pair = result.pairSimilarities().get(0);
        assertThat(pair.similarity()).isEqualByComparingTo(series.numFactory().one());
        assertThat(pair.signedAverageSimilarity()).isEqualByComparingTo(series.numFactory().minusOne());
        assertThat(pair.latestSignedSimilarity()).isEqualByComparingTo(series.numFactory().minusOne());
        assertThat(pair.sampleCount()).isEqualTo(3);
        assertThat(pair.minimumSignedSimilarity()).isEqualByComparingTo(series.numFactory().minusOne());
        assertThat(pair.maximumSignedSimilarity()).isEqualByComparingTo(series.numFactory().minusOne());
    }

    @Test
    public void acceptsCustomMetricFactories() {
        BarSeries series = increasingSeries(5);
        Indicator<Num> first = mockIndicator(series, index -> index);
        Indicator<Num> second = mockIndicator(series, index -> index * 2.0);
        AtomicInteger factoryCalls = new AtomicInteger();
        BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> metricFactory = (left, right) -> {
            factoryCalls.incrementAndGet();
            return constantIndicator(series, "0.75");
        };

        IndicatorFamilyResult result = new IndicatorFamilyManager(series, metricFactory)
                .analyze(namedIndicators(testIndicator("first", first), testIndicator("second", second)), 0.80);

        assertThat(factoryCalls).hasValue(1);
        assertThat(result.families()).hasSize(2);
        IndicatorFamilyResult.PairSimilarity pair = result.pairSimilarities().get(0);
        assertThat(pair.similarity()).isEqualByComparingTo(series.numFactory().numOf("0.75"));
        assertThat(pair.signedAverageSimilarity()).isEqualByComparingTo(series.numFactory().numOf("0.75"));
        assertThat(pair.latestSignedSimilarity()).isEqualByComparingTo(series.numFactory().numOf("0.75"));
        assertThat(pair.sampleCount()).isEqualTo(5);
    }

    @Test
    public void keepsDefaultMetricEquivalentToPopulationCorrelationIndicator() {
        BarSeries series = increasingSeries(12);
        Indicator<Num> first = mockIndicator(series, index -> index + Math.sin(index));
        Indicator<Num> second = mockIndicator(series, index -> (2.0 * index) + Math.cos(index));
        Map<String, Indicator<Num>> indicators = namedIndicators(testIndicator("first", first),
                testIndicator("second", second));
        BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> explicitPopulationCorrelation = (left,
                right) -> new CorrelationCoefficientIndicator(left, right, 4, SampleType.POPULATION);

        IndicatorFamilyResult defaultResult = new IndicatorFamilyManager(series, 4).analyze(indicators, 0.10);
        IndicatorFamilyResult explicitResult = new IndicatorFamilyManager(series, explicitPopulationCorrelation)
                .analyze(indicators, 0.10);

        assertPairSimilaritiesEqual(defaultResult.pairSimilarities(), explicitResult.pairSimilarities());
        assertThat(defaultResult.families()).extracting(IndicatorFamilyResult.Family::indicatorNames)
                .containsExactlyElementsOf(
                        explicitResult.families().stream().map(IndicatorFamilyResult.Family::indicatorNames).toList());
    }

    @Test
    public void preservesDeterministicPairOrderWithParallelAnalysis() {
        BarSeries series = increasingSeries(180);
        Map<String, Indicator<Num>> indicators = namedIndicators(
                testIndicator("base", mockIndicator(series, index -> index)),
                testIndicator("scaled", mockIndicator(series, index -> index * 2.0)),
                testIndicator("inverse", mockIndicator(series, index -> -index)),
                testIndicator("wave", mockIndicator(series, index -> Math.sin(index / 3.0))),
                testIndicator("alternating", mockIndicator(series, index -> index % 2 == 0 ? 1.0 : -1.0)));

        IndicatorFamilyResult sequential = new IndicatorFamilyManager(series, 20).analyze(indicators, 0.80);
        IndicatorFamilyResult parallel = new IndicatorFamilyManager(series, 20, 4).analyze(indicators, 0.80);

        assertPairSimilaritiesEqual(sequential.pairSimilarities(), parallel.pairSimilarities());
        assertThat(parallel.familyByIndicator()).containsExactlyEntriesOf(sequential.familyByIndicator());
        assertThat(parallel.families()).extracting(IndicatorFamilyResult.Family::indicatorNames)
                .containsExactlyElementsOf(
                        sequential.families().stream().map(IndicatorFamilyResult.Family::indicatorNames).toList());
    }

    @Test
    public void maxParallelismOneForcesSequentialPairAnalysis() {
        BarSeries series = increasingSeries(5);
        Indicator<Num> first = mockIndicator(series, index -> index);
        Indicator<Num> second = mockIndicator(series, index -> index * 2.0);
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> factoryThread = new AtomicReference<>();
        BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> metricFactory = (left, right) -> {
            factoryThread.set(Thread.currentThread());
            return constantIndicator(series, "0.50");
        };

        new IndicatorFamilyManager(series, metricFactory, 1)
                .analyze(namedIndicators(testIndicator("first", first), testIndicator("second", second)), 0.40);

        assertThat(factoryThread).hasValue(callerThread);
    }

    @Test
    public void exposesFamilyRepresentativeAndCohesionMetrics() {
        BarSeries series = increasingSeries(5);
        Indicator<Num> a = mockIndicator(series, index -> index);
        Indicator<Num> b = mockIndicator(series, index -> index + 1.0);
        Indicator<Num> c = mockIndicator(series, index -> index + 2.0);
        BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> metricFactory = mappedConstantMetric(series, a, b,
                c);

        IndicatorFamilyResult result = new IndicatorFamilyManager(series, metricFactory)
                .analyze(namedIndicators(testIndicator("a", a), testIndicator("b", b), testIndicator("c", c)), 0.90);

        assertThat(result.families()).hasSize(1);
        IndicatorFamilyResult.Family family = result.families().get(0);
        assertThat(family.indicatorNames()).containsExactly("a", "b", "c");
        assertThat(family.representativeIndicatorName()).isEqualTo("b");
        Num expectedAverage = series.numFactory().numOf("2.30").dividedBy(series.numFactory().three());
        assertThat(family.averageInternalSimilarity()).isEqualByComparingTo(expectedAverage);
        assertThat(family.minimumInternalSimilarity()).isEqualByComparingTo(series.numFactory().numOf("0.40"));
    }

    @Test
    public void skipsInvalidMetricSamples() {
        BarSeries series = increasingSeries(4);
        Indicator<Num> first = mockIndicator(series, index -> index);
        Indicator<Num> second = mockIndicator(series, index -> index * 2.0);
        BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> metricFactory = (left, right) -> new MockIndicator(
                series,
                List.of(NaN.NaN, series.numFactory().numOf("0.50"), NaN.NaN, series.numFactory().numOf("-0.25")));

        IndicatorFamilyResult result = new IndicatorFamilyManager(series, metricFactory)
                .analyze(namedIndicators(testIndicator("first", first), testIndicator("second", second)), 0.30);

        IndicatorFamilyResult.PairSimilarity pair = result.pairSimilarities().get(0);
        assertThat(pair.sampleCount()).isEqualTo(2);
        assertThat(pair.similarity()).isEqualByComparingTo(series.numFactory().numOf("0.375"));
        assertThat(pair.signedAverageSimilarity()).isEqualByComparingTo(series.numFactory().numOf("0.125"));
        assertThat(pair.latestSignedSimilarity()).isEqualByComparingTo(series.numFactory().numOf("-0.25"));
        assertThat(pair.minimumSignedSimilarity()).isEqualByComparingTo(series.numFactory().numOf("-0.25"));
        assertThat(pair.maximumSignedSimilarity()).isEqualByComparingTo(series.numFactory().numOf("0.50"));
    }

    @Test
    public void resultCollectionsAreImmutable() {
        BarSeries series = increasingSeries(180);
        Indicator<Num> base = mockIndicator(series, index -> index);
        IndicatorFamilyResult result = new IndicatorFamilyManager(series)
                .analyze(namedIndicators(testIndicator("base", base)), 0.90);

        assertThrows(UnsupportedOperationException.class, () -> result.familyByIndicator().put("other", "family-999"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.families()
                        .add(new IndicatorFamilyResult.Family("family-999", List.of("other"), "other",
                                series.numFactory().one(), series.numFactory().one())));
        assertThrows(UnsupportedOperationException.class, () -> result.families().get(0).indicatorNames().add("other"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.pairSimilarities()
                        .add(new IndicatorFamilyResult.PairSimilarity("a", "b", series.numFactory().numOf("0.5"),
                                series.numFactory().numOf("0.5"), series.numFactory().numOf("0.5"), 1,
                                series.numFactory().numOf("0.5"), series.numFactory().numOf("0.5"))));
    }

    @Test
    public void validatesInputs() {
        BarSeries series = increasingSeries(180);
        BarSeries otherSeries = increasingSeries(180);
        Indicator<Num> base = mockIndicator(series, index -> index);
        Indicator<Num> other = mockIndicator(otherSeries, index -> index);
        IndicatorFamilyManager manager = new IndicatorFamilyManager(series);
        BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> nullMetricFactory = (left, right) -> null;
        BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> wrongSeriesMetricFactory = (left,
                right) -> mockIndicator(otherSeries, index -> index);
        BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> outOfRangeMetricFactory = (left,
                right) -> constantIndicator(series, "1.01");

        assertThrows(NullPointerException.class, () -> new IndicatorFamilyManager(null));
        assertThrows(IllegalArgumentException.class, () -> new IndicatorFamilyManager(
                new BaseBarSeriesBuilder().withBarBuilderFactory(new MockBarBuilderFactory()).build()));
        assertThrows(IllegalArgumentException.class, () -> new IndicatorFamilyManager(series, 1));
        assertThrows(IllegalArgumentException.class, () -> new IndicatorFamilyManager(series, 0));
        assertThrows(IllegalArgumentException.class, () -> new IndicatorFamilyManager(series, 3, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new IndicatorFamilyManager(series, (left, right) -> constantIndicator(series, "0.50"), 0));
        assertThrows(NullPointerException.class, () -> new IndicatorFamilyManager(series, null));
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

        Map<String, Indicator<Num>> twoIndicators = namedIndicators(testIndicator("base", base),
                testIndicator("other", mockIndicator(series, index -> index * 2.0)));
        assertThrows(NullPointerException.class,
                () -> new IndicatorFamilyManager(series, nullMetricFactory).analyze(twoIndicators));
        assertThrows(IllegalArgumentException.class,
                () -> new IndicatorFamilyManager(series, wrongSeriesMetricFactory).analyze(twoIndicators));
        assertThrows(IllegalArgumentException.class,
                () -> new IndicatorFamilyManager(series, outOfRangeMetricFactory).analyze(twoIndicators));
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

    private static Indicator<Num> constantIndicator(BarSeries series, String value) {
        NumFactory numFactory = series.numFactory();
        List<Num> values = new ArrayList<>(series.getBarCount());
        for (int index = 0; index < series.getBarCount(); index++) {
            values.add(numFactory.numOf(value));
        }
        return new MockIndicator(series, values);
    }

    private static BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> mappedConstantMetric(BarSeries series,
            Indicator<Num> a, Indicator<Num> b, Indicator<Num> c) {
        return (left, right) -> {
            if (samePair(left, right, a, b) || samePair(left, right, b, c)) {
                return constantIndicator(series, "0.95");
            }
            if (samePair(left, right, a, c)) {
                return constantIndicator(series, "0.40");
            }
            throw new IllegalArgumentException("Unexpected indicator pair");
        };
    }

    private static boolean samePair(Indicator<Num> left, Indicator<Num> right, Indicator<Num> expectedLeft,
            Indicator<Num> expectedRight) {
        return (left == expectedLeft && right == expectedRight) || (left == expectedRight && right == expectedLeft);
    }

    private static void assertPairSimilaritiesEqual(List<IndicatorFamilyResult.PairSimilarity> expected,
            List<IndicatorFamilyResult.PairSimilarity> actual) {
        assertThat(actual).hasSize(expected.size());
        for (int index = 0; index < expected.size(); index++) {
            IndicatorFamilyResult.PairSimilarity expectedPair = expected.get(index);
            IndicatorFamilyResult.PairSimilarity actualPair = actual.get(index);
            assertThat(actualPair.firstIndicatorName()).isEqualTo(expectedPair.firstIndicatorName());
            assertThat(actualPair.secondIndicatorName()).isEqualTo(expectedPair.secondIndicatorName());
            assertThat(actualPair.similarity()).isEqualByComparingTo(expectedPair.similarity());
            assertThat(actualPair.signedAverageSimilarity())
                    .isEqualByComparingTo(expectedPair.signedAverageSimilarity());
            assertThat(actualPair.latestSignedSimilarity()).isEqualByComparingTo(expectedPair.latestSignedSimilarity());
            assertThat(actualPair.sampleCount()).isEqualTo(expectedPair.sampleCount());
            assertThat(actualPair.minimumSignedSimilarity())
                    .isEqualByComparingTo(expectedPair.minimumSignedSimilarity());
            assertThat(actualPair.maximumSignedSimilarity())
                    .isEqualByComparingTo(expectedPair.maximumSignedSimilarity());
        }
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
