/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class HurstExponentIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public HurstExponentIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void linearSeriesProducesUnitHurst() {
        Indicator<Num> source = source(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        HurstExponentIndicator hurst = new HurstExponentIndicator(source, 12);

        assertNumEquals(1d, hurst.getValue(11));
    }

    @Test
    public void constantSeriesProducesZeroHurst() {
        Indicator<Num> source = source(4, 4, 4, 4, 4, 4);
        HurstExponentIndicator hurst = new HurstExponentIndicator(source, 6);

        assertNumEquals(0d, hurst.getValue(5));
    }

    @Test
    public void defaultLagIsBoundedByShortWindow() {
        Indicator<Num> source = source(1, 4, 2, 8, 3, 9);
        HurstExponentIndicator defaultLag = new HurstExponentIndicator(source, 6);
        HurstExponentIndicator explicitLag = new HurstExponentIndicator(source, 6, 5);

        assertThat(defaultLag.getValue(5)).isEqualByComparingTo(explicitLag.getValue(5));
    }

    @Test
    public void warmupRequiresTheCompleteSourceQualifiedWindow() {
        BarSeries series = series(1, 2, 3, 4, 5, 6, 7, 8);
        Indicator<Num> source = new FixedIndicator<>(series, numbers(1, 2, 3, 4, 5, 6, 7, 8)) {
            @Override
            public int getCountOfUnstableBars() {
                return 2;
            }
        };
        HurstExponentIndicator hurst = new HurstExponentIndicator(source, 6);

        assertThat(hurst.getCountOfUnstableBars()).isEqualTo(7);
        assertThat(hurst.getValue(6).isNaN()).isTrue();
        assertThat(hurst.getValue(7).isNaN()).isFalse();
    }

    @Test
    public void invalidWindowRecoversAfterNonFiniteValueLeaves() {
        Indicator<Num> source = source(1, 2, Double.NaN, 4, 5, 6, 7, 8, 9);
        HurstExponentIndicator hurst = new HurstExponentIndicator(source, 4, 3);

        assertThat(hurst.getValue(3).isNaN()).isTrue();
        assertThat(hurst.getValue(5).isNaN()).isTrue();
        assertThat(hurst.getValue(6).isNaN()).isFalse();
    }

    @Test
    public void futureSuffixDoesNotChangeEarlierEstimate() {
        HurstExponentIndicator prefix = new HurstExponentIndicator(source(1, 3, 2, 7, 4, 9), 6);
        HurstExponentIndicator extended = new HurstExponentIndicator(source(1, 3, 2, 7, 4, 9, -20, 50), 6);

        assertThat(prefix.getValue(5)).isEqualByComparingTo(extended.getValue(5));
    }

    @Test
    public void barSeriesConstructorUsesClosePrices() {
        BarSeries series = series(1, 2, 3, 4, 5, 6);
        HurstExponentIndicator fromSeries = new HurstExponentIndicator(series, 6, 5);
        HurstExponentIndicator fromClosePrices = new HurstExponentIndicator(new ClosePriceIndicator(series), 6, 5);

        assertThat(fromSeries.getValue(5)).isEqualByComparingTo(fromClosePrices.getValue(5));
    }

    @Test
    public void rejectsInvalidConfiguration() {
        Indicator<Num> source = source(1, 2, 3, 4);

        assertThatNullPointerException().isThrownBy(() -> new HurstExponentIndicator((Indicator<Num>) null, 4));
        assertThatNullPointerException().isThrownBy(() -> new HurstExponentIndicator((BarSeries) null, 4));
        assertThatIllegalArgumentException().isThrownBy(() -> new HurstExponentIndicator(source, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> new HurstExponentIndicator(source, 4, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new HurstExponentIndicator(source, 4, 4));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        HurstExponentIndicator hurst = new HurstExponentIndicator(new ClosePriceIndicator(series), 20, 7);
        return List.of(serializationFixture(series, hurst, stableIndexes(series)));
    }

    private Indicator<Num> source(double... values) {
        return new FixedIndicator<>(series(values), numbers(values));
    }

    private BarSeries series(double... values) {
        double[] finitePrices = Arrays.stream(values).map(value -> Double.isFinite(value) ? value : 1d).toArray();
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(finitePrices).build();
    }

    private Num[] numbers(double... values) {
        return Arrays.stream(values)
                .mapToObj(value -> Double.isFinite(value) ? numFactory.numOf(value) : NaN.NaN)
                .toArray(Num[]::new);
    }
}
