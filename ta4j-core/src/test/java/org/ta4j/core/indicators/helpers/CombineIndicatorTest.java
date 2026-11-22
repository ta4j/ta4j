/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

@SuppressWarnings("deprecation")
class CombineIndicatorTest extends AbstractIndicatorTest<CombineIndicator, Num> {

    CombineIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    void combinesValuesAtTheRequestedBar() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(2, 6).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ConstantIndicator<Num> divisor = new ConstantIndicator<>(series, numOf(2));
        CombineIndicator ratio = CombineIndicator.divide(close, divisor);

        assertNumEquals(1, ratio.getValue(0));
        assertNumEquals(3, ratio.getValue(1));
        assertTrue(CombineIndicator.divide(close, new ConstantIndicator<>(series, numOf(0))).getValue(1).isNaN());
    }

    @Test
    void rejectsIndependentSeriesWithMatchingIndexes() {
        BarSeries left = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(2).build();
        BarSeries right = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(3).build();

        assertThrows(IllegalArgumentException.class,
                () -> CombineIndicator.plus(new ClosePriceIndicator(left), new ClosePriceIndicator(right)));
    }

    @Test
    void rejectsMissingOperatorBeforeEvaluation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(2).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        assertThrows(NullPointerException.class, () -> new CombineIndicator(close, close, null));
    }
}
