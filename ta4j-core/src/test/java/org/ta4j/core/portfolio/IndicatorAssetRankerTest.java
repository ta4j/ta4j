/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

public class IndicatorAssetRankerTest {

    @Test
    public void ranksInEitherDirectionWithStableTiesAndExcludesMissingAndNan() {
        Map<String, BarSeries> sources = new LinkedHashMap<>();
        sources.put("BETA", series("beta", 5, 5));
        sources.put("NAN", series("nan", 9, 9));
        sources.put("GAMMA", series("gamma", 2, 2));
        sources.put("ALPHA", series("alpha", 5, 5));
        sources.put("LATE", series("late", DoubleNumFactory.getInstance(), 1, "9"));
        PortfolioSeriesView view = PortfolioSeries.union(sources);
        IndicatorAssetRanker descending = new IndicatorAssetRanker(
                source -> source.getName().equals("nan") ? new ConstantIndicator<Num>(source, NaN.NaN)
                        : new ClosePriceIndicator(source));
        IndicatorAssetRanker ascending = new IndicatorAssetRanker(
                source -> source.getName().equals("nan") ? new ConstantIndicator<Num>(source, NaN.NaN)
                        : new ClosePriceIndicator(source),
                false);

        assertEquals(List.of("ALPHA", "BETA", "GAMMA"), descending.rank(view, 0));
        assertEquals(List.of("GAMMA", "ALPHA", "BETA"), ascending.rank(view, 0));
        assertEquals(List.of("LATE", "ALPHA", "BETA", "GAMMA"), descending.rank(view, 1));
        assertThrows(UnsupportedOperationException.class, descending::toDescriptor);
    }

    @Test
    public void checksIndicatorStabilityAtEachSourceIndex() {
        PortfolioSeriesView view = PortfolioSeries.union(Map.of("EARLY", series("early", 1, 2, 3), "LATE",
                series("late", DoubleNumFactory.getInstance(), 1, "5", "6")));
        AssetRanker ranker = new IndicatorAssetRanker(source -> new SMAIndicator(new ClosePriceIndicator(source), 2));

        assertEquals(List.of(), ranker.rank(view, 0));
        assertEquals(List.of("EARLY"), ranker.rank(view, 1));
        assertEquals(List.of("LATE", "EARLY"), ranker.rank(view, 2));
    }

    @Test
    public void assetRankRuleUsesRankerOrderAndRoundTripsDescriptors() {
        BarSeries sample = series("sample", 4, 1);
        PortfolioSeriesView view = PortfolioSeries.union(Map.of("A", sample, "B", series("other", 2, 5)));
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view);
        AssetRankRule original = new AssetRankRule(IndicatorAssetRanker.of(new ClosePriceIndicator(sample)), 1);
        PortfolioRule restored = PortfolioRule.fromJson(view, original.toJson());

        assertEquals(original.toDescriptor(), restored.toDescriptor());
        assertTrue(restored.isSatisfied("A", 0, record));
        assertFalse(restored.isSatisfied("B", 0, record));
        assertFalse(restored.isSatisfied("A", 1, record));
        assertTrue(restored.isSatisfied("B", 1, record));
        AssetRanker custom = (portfolio, index) -> List.of("B", "A");
        assertTrue(new AssetRankRule(custom, 1).isSatisfied("B", 0, record));
        assertFalse(new AssetRankRule(custom, 1).isSatisfied("A", 0, record));
        assertThrows(UnsupportedOperationException.class, () -> new AssetRankRule(custom, 1).toJson());
        assertThrows(IllegalArgumentException.class, () -> new AssetRankRule(custom, 0));
    }

    @Test
    public void rebuildsIndicatorWhenLiveAssetSourceChanges() {
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("A", series("old", 1), "B", series("b", 2)));
        IndicatorAssetRanker ranker = new IndicatorAssetRanker(ClosePriceIndicator::new);
        assertEquals(List.of("B", "A"), ranker.rank(view, 0));
        assertTrue(view.removeAsset("A"));
        view.addAsset("A", series("replacement", 3));
        assertEquals(List.of("A", "B"), ranker.rank(view, 0));
    }

    @Test
    public void comparesScoresFromMixedFactoryLegacySnapshots() {
        PortfolioSeriesView view = new PortfolioSeries(series("A", DoubleNumFactory.getInstance(), 1, 3),
                series("B", DecimalNumFactory.getInstance(10), 2, 2));
        IndicatorAssetRanker ranker = new IndicatorAssetRanker(ClosePriceIndicator::new);
        assertEquals(List.of("B", "A"), ranker.rank(view, 0));
        assertEquals(List.of("A", "B"), ranker.rank(view, 1));
    }
}
