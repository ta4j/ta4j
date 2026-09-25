/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

public class RebalancePolicyTest {

    private static final PortfolioSeries TEN_BARS = new PortfolioSeries(
            PortfolioFixtures.series("ALPHA", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

    @Test
    public void indexPoliciesSelectTheDocumentedBars() {
        assertEquals(List.of(0), selected(RebalancePolicy.atStart(), TEN_BARS));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), selected(RebalancePolicy.everyBar(), TEN_BARS));
        assertEquals(List.of(0, 4, 8), selected(RebalancePolicy.everyNthBar(4), TEN_BARS));
        assertEquals(List.of(3, 7), selected(RebalancePolicy.onIndexes(7, 3), TEN_BARS));
        assertEquals(List.of(3), selected(RebalancePolicy.onIndexes(Set.of(3)), TEN_BARS));
        assertEquals(List.of(0, 3), selected(RebalancePolicy.atStart().or(RebalancePolicy.onIndexes(3)), TEN_BARS));
    }

    @Test
    public void rejectsInvalidIndexPolicies() {
        assertThrows(IllegalArgumentException.class, () -> RebalancePolicy.everyNthBar(0));
        assertThrows(IllegalArgumentException.class, () -> RebalancePolicy.onIndexes(-1));
        assertThrows(IllegalArgumentException.class,
                () -> RebalancePolicy.firstBarOf(ChronoUnit.HOURS, ZoneOffset.UTC));
    }

    @Test
    public void firstBarOfMonthUsesAlignedTimestampsAndMidnightBarEnds() {
        // Daily bars ending at midnight: the bar ending 2026-02-01T00:00 covers January
        // 31.
        PortfolioSeries dailyBars = new PortfolioSeries(dailySeries("2026-01-30T00:00:00Z", 5));

        assertEquals(List.of(0, 3), selected(RebalancePolicy.firstBarOf(ChronoUnit.MONTHS, ZoneOffset.UTC), dailyBars));
        assertEquals(List.of(0, 1, 2, 3, 4),
                selected(RebalancePolicy.firstBarOf(ChronoUnit.DAYS, ZoneOffset.UTC), dailyBars));
        assertEquals(List.of(0, 4), selected(RebalancePolicy.firstBarOf(ChronoUnit.WEEKS, ZoneOffset.UTC), dailyBars));
        assertEquals(List.of(0),
                selected(RebalancePolicy.firstBarOf(IsoFields.QUARTER_YEARS, ZoneOffset.UTC), dailyBars));
        assertEquals(List.of(0), selected(RebalancePolicy.firstBarOf(ChronoUnit.YEARS, ZoneOffset.UTC), dailyBars));
    }

    private static List<Integer> selected(RebalancePolicy policy, PortfolioSeries series) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < series.getBarCount(); index++) {
            if (policy.shouldRebalance(series, index)) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private static BarSeries dailySeries(String firstEnd, int bars) {
        BarSeries series = new BaseBarSeriesBuilder().withName("DAILY")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        Instant end = Instant.parse(firstEnd);
        for (int i = 0; i < bars; i++) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(end.plus(Duration.ofDays(i)))
                    .openPrice(1)
                    .highPrice(1)
                    .lowPrice(1)
                    .closePrice(1)
                    .volume(0)
                    .add();
        }
        return series;
    }
}
