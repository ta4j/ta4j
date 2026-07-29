/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ChopIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ChopIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void choppinessIsHighWhenPriceDoesNotMoveDirectionally() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withName("sideways series").build();
        for (int i = 0; i < 50; i++) {
            series.barBuilder().openPrice(21.5).highPrice(21.5 + 1).lowPrice(21.5 - 1).closePrice(21.5).add();
        }
        ChopIndicator chop = new ChopIndicator(series, 14);

        assertThat(chop.getValue(series.getEndIndex()).doubleValue()).isGreaterThan(85d);
    }

    @Test
    public void choppinessIsLowWhenPriceTrendsDirectionally() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withName("trending series").build();
        float value = 21.5f;
        for (int i = 0; i < 50; i++) {
            series.barBuilder().openPrice(value).highPrice(value + 1).lowPrice(value - 1).closePrice(value).add();
            value += 2.0f;
        }
        ChopIndicator chop = new ChopIndicator(series, 14);

        assertThat(chop.getValue(series.getEndIndex()).doubleValue()).isLessThan(30d);
    }

    @Test
    public void defaultPercentageAndDecimalOutputsAreEquivalent() {
        BarSeries series = representativeSeries();
        ChopIndicator defaultPercentage = new ChopIndicator(series, 4);
        ChopIndicator percentage = new ChopIndicator(series, 4, ReturnRepresentation.PERCENTAGE);
        ChopIndicator decimal = new ChopIndicator(series, 4, ReturnRepresentation.DECIMAL);
        int index = series.getEndIndex();

        assertThat(defaultPercentage.getValue(index)).isEqualByComparingTo(percentage.getValue(index));
        assertThat(percentage.getValue(index))
                .isEqualByComparingTo(decimal.getValue(index).multipliedBy(numFactory.numOf(100)));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void deprecatedConstructorPreservesArbitraryScaling() {
        BarSeries series = representativeSeries();
        ChopIndicator decimal = new ChopIndicator(series, 4, ReturnRepresentation.DECIMAL);
        ChopIndicator legacy = new ChopIndicator(series, 4, 37);
        int index = series.getEndIndex();

        assertThat(legacy.getValue(index))
                .isEqualByComparingTo(decimal.getValue(index).multipliedBy(numFactory.numOf(37)));
    }

    @Test
    public void warmupBoundaryDoesNotReadPreWindowHistory() {
        BarSeries series = representativeSeries();
        ChopIndicator chop = new ChopIndicator(series, 4);

        assertThat(chop.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(chop.getValue(3).isNaN()).isTrue();
        assertThat(chop.getValue(4).isNaN()).isFalse();
    }

    @Test
    public void flatRangeIsUnavailable() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 8; i++) {
            series.barBuilder().openPrice(10).highPrice(10).lowPrice(10).closePrice(10).add();
        }

        assertThat(new ChopIndicator(series, 4).getValue(series.getEndIndex()).isNaN()).isTrue();
    }

    @Test
    public void rejectsInvalidConfiguration() {
        BarSeries series = representativeSeries();

        assertThatNullPointerException().isThrownBy(() -> new ChopIndicator(null, 4));
        assertThatIllegalArgumentException().isThrownBy(() -> new ChopIndicator(series, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChopIndicator(series, 4, (ReturnRepresentation) null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ChopIndicator(series, 4, ReturnRepresentation.LOG));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChopIndicator(series, 4, ReturnRepresentation.MULTIPLICATIVE));
    }

    @SuppressWarnings("deprecation")
    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(
                serializationFixture(series, new ChopIndicator(series, 8, ReturnRepresentation.PERCENTAGE),
                        stableIndexes(series)),
                serializationFixture(series, new ChopIndicator(series, 8, 37), stableIndexes(series)));
    }

    private BarSeries representativeSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        double[] closes = { 10, 11, 9, 12, 10, 13, 11, 14 };
        for (double close : closes) {
            series.barBuilder()
                    .openPrice(close - 0.25)
                    .highPrice(close + 1)
                    .lowPrice(close - 1)
                    .closePrice(close)
                    .add();
        }
        return series;
    }
}
