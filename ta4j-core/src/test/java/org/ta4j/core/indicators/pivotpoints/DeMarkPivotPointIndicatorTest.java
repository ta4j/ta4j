/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.pivotpoints;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.pivotpoints.DeMarkReversalIndicator.DeMarkPivotLevel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DeMarkPivotPointIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public DeMarkPivotPointIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void usesEachPriorBarsOpenCloseRelationshipForPivotAndReversals() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int close : new int[] { 9, 12, 10, 11 }) {
            series.barBuilder().openPrice(10).highPrice(14).lowPrice(8).closePrice(close).add();
        }
        DeMarkPivotPointIndicator pivot = new DeMarkPivotPointIndicator(series, TimeLevel.BARBASED);
        DeMarkReversalIndicator resistance = new DeMarkReversalIndicator(pivot, DeMarkPivotLevel.RESISTANCE);
        DeMarkReversalIndicator support = new DeMarkReversalIndicator(pivot, DeMarkPivotLevel.SUPPORT);

        // The preceding bars close below, above, and exactly at their open,
        // respectively.
        double[] pivotValues = { 9.75, 12, 10.5 };
        double[] resistanceValues = { 11.5, 16, 13 };
        double[] supportValues = { 5.5, 10, 7 };
        for (int i = 0; i < pivotValues.length; i++) {
            assertThat(pivot.getValue(i + 1)).isEqualByComparingTo(numOf(pivotValues[i]));
            assertThat(resistance.getValue(i + 1)).isEqualByComparingTo(numOf(resistanceValues[i]));
            assertThat(support.getValue(i + 1)).isEqualByComparingTo(numOf(supportValues[i]));
        }
    }
}
