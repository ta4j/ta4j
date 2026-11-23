/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Testing the RWILowIndicator
 */
public class RWILowIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private final ExternalIndicatorTest xls;

    public RWILowIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new RWILowIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "RWIHL.xls", 9, numFactory);
    }

    @Test
    public void dependenciesExposeEachCalculatedAtr() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            series.barBuilder().openPrice(10).highPrice(12).lowPrice(8).closePrice(10).add();
        }
        RWILowIndicator rwi = new RWILowIndicator(series, 3);
        assertThat(rwi.getDependencies()).hasSize(2);
        for (int i = 0; i < 2; i++) {
            ATRIndicator atr = (ATRIndicator) rwi.getDependencies().get(i);
            assertThat(atr.getCountOfUnstableBars()).isEqualTo(i + 2);
            assertThat(atr.getValue(4)).isEqualByComparingTo(numOf(4));
        }
        assertThat(rwi.getValue(4)).isEqualByComparingTo(numFactory.one().dividedBy(numOf(2).sqrt()));
        assertThat(new RWILowIndicator(series, 1).getDependencies()).isEmpty();
    }

    @Test
    public void matchesFormulaWorksheetAndHardCodedLowValues() {
        List<RWIWorksheetFixture.Observation> rows = RWIWorksheetFixture.rows();
        BarSeries series = RWIWorksheetFixture.series(numFactory);
        RWILowIndicator indicator = new RWILowIndicator(series, 3);

        assertThat(rows).hasSize(9);
        assertEquals(3, indicator.getCountOfUnstableBars());
        for (int i = 0; i < rows.size(); i++) {
            Num actual = indicator.getValue(i);
            if (i < 3) {
                assertThat(actual.isNaN()).as("warm-up bar %s", i).isTrue();
            } else {
                RWIWorksheetFixture.Observation row = rows.get(i);
                assertThat(Double.isFinite(row.expectedLow())).as("reference bar %s", i).isTrue();
                assertThat(Num.isFinite(actual)).as("indicator bar %s", i).isTrue();
                assertEquals(row.expectedLow(), row.formulaLow(), 1e-10, "worksheet bar " + i);
                assertEquals(row.expectedLow(), actual.doubleValue(), 1e-10, "indicator bar " + i);
            }
        }
    }

    @Test
    public void randomWalkIndexLow() throws Exception {
        Indicator<Num> expected = xls.getIndicator(20);
        RWILowIndicator rwil = (RWILowIndicator) getIndicator(expected.getBarSeries(), 20);
        assertEquals(20, rwil.getCountOfUnstableBars());
        assertIndicatorEquals(expected, rwil);
    }

    @Test
    public void rejectsBarCountAboveHelperCapAndSharesOneTrueRange() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10).highPrice(12).lowPrice(8).closePrice(10).add();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RWILowIndicator(series, RWILowIndicator.MAX_BAR_COUNT + 1));
        RWILowIndicator rwi = new RWILowIndicator(series, 4);
        // ATR -> Wilder average -> true range
        Indicator<?> firstTrueRange = rwi.getDependencies().get(0).getDependencies().get(0).getDependencies().get(0);
        for (Indicator<?> atr : rwi.getDependencies()) {
            assertThat(atr.getDependencies().get(0).getDependencies()).containsExactly(firstTrueRange);
        }
    }
}
