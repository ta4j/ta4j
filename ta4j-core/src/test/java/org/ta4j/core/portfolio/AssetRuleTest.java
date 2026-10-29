/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.rules.OverIndicatorRule;

public class AssetRuleTest {

    @Test
    public void evaluatesSourceIndexAndOwnTradingRecordOnlyOnExactBars() {
        PortfolioSeriesView view = PortfolioSeries.union(
                Map.of("A", series("a", 1, 1, 1), "B", series("b", DoubleNumFactory.getInstance(), 1, "2", "3")));
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view);
        AssetRule rule = new AssetRule(source -> (index, tradingRecord) -> index == 1 && !tradingRecord.isClosed());
        assertTrue(record.enter("B", 0, view.numFactory().numOf(2), view.numFactory().one()));

        assertFalse(rule.isSatisfied("B", 0, record));
        assertFalse(rule.isSatisfied("B", 1, record));
        assertTrue(rule.isSatisfied("B", 2, record));
        assertFalse(rule.isSatisfied("A", 2, record));
        assertThrows(UnsupportedOperationException.class, rule::toJson);
    }

    @Test
    public void doesNotEvaluateAnAsOfSourceIndexAtAnInteriorGap() {
        DoubleNumFactory factory = DoubleNumFactory.getInstance();
        BarSeries sparse = new BaseBarSeriesBuilder().withName("sparse")
                .withNumFactory(factory)
                .withBars(List.of(series("a0", factory, 0, "4").getFirstBar(),
                        series("a2", factory, 2, "4").getFirstBar()))
                .build();
        PortfolioSeriesView view = PortfolioSeries.union(Map.of("A", sparse, "B", series("dense", 1, 1, 1)));
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view);
        AssetRule rule = new AssetRule(source -> (index, tradingRecord) -> true);

        assertEquals(0, view.getSourceIndex("A", 1));
        assertTrue(rule.isSatisfied("A", 0, record));
        assertFalse(rule.isSatisfied("A", 1, record));
        assertTrue(rule.isSatisfied("A", 2, record));
    }

    @Test
    public void reconstructsDescriptorBackedRuleForEachAsset() {
        BarSeries a = series("a", 1, 4);
        PortfolioSeriesView view = PortfolioSeries.union(Map.of("A", a, "B", series("b", 5, 2)));
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view);
        PortfolioRule original = AssetRule.of(new OverIndicatorRule(new ClosePriceIndicator(a), 3));
        PortfolioRule restored = PortfolioRule.fromJson(view, original.toJson());

        assertEquals(original.toDescriptor(), restored.toDescriptor());
        for (String asset : view.assets()) {
            for (int index = view.getBeginIndex(); index <= view.getEndIndex(); index++) {
                assertEquals(original.isSatisfied(asset, index, record), restored.isSatisfied(asset, index, record));
            }
        }
        assertTrue(restored.isSatisfied("A", 1, record));
        assertFalse(restored.isSatisfied("B", 1, record));
    }

    @Test
    public void replacesCachedRuleForReaddedLiveSource() {
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("A", series("old", 1)));
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view);
        AssetRule rule = new AssetRule(
                source -> (index, tradingRecord) -> source.getBar(index).getClosePrice().doubleValue() > 2);
        assertFalse(rule.isSatisfied("A", 0, record));
        assertTrue(view.removeAsset("A"));
        view.addAsset("A", series("new", 4));
        assertTrue(rule.isSatisfied("A", 0, record));
    }
}
