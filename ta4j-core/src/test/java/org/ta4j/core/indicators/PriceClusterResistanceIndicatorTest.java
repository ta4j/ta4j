/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.supportresistance.PriceClusterResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.PriceClusterSupportIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PriceClusterResistanceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public PriceClusterResistanceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void supportAndResistanceShouldChooseDifferentClustersWhenFrequenciesTie() {
        BarSeries series = buildSeries(new double[] { 10, 10.1, 10.2, 15, 15.1, 15.2 },
                new double[] { 40, 40, 40, 40, 40, 40 });

        var support = new PriceClusterSupportIndicator(series, 0, numOf(0.3));
        var resistance = new PriceClusterResistanceIndicator(series, 0, numOf(0.3));

        assertThat(support.getClusterIndex(5)).as("support favours lower cluster").isEqualTo(2);

        assertThat(resistance.getClusterIndex(5)).as("resistance favours higher cluster").isEqualTo(5);
        assertThat(support.getValue(5)).isLessThan(resistance.getValue(5));
    }

    @Test
    public void shouldFavorHeavierHighClusterForResistance() {
        BarSeries series = buildSeries(new double[] { 10, 10, 15 }, new double[] { 20, 20, 200 });

        var resistance = new PriceClusterResistanceIndicator(series, 0, numOf(0.5));

        assertThat(resistance.getValue(2)).as("resistance favours heavier upper cluster")
                .isEqualByComparingTo(numOf(15));
        assertThat(resistance.getClusterIndex(2)).isEqualTo(2);
    }

    @Test
    public void shouldRoundTripSerializeAndDeserialize() {
        BarSeries series = buildSeries(new double[] { 10, 10.1, 10.2, 15, 15.1, 15.2 },
                new double[] { 40, 40, 40, 40, 40, 40 });

        var indicator = new PriceClusterResistanceIndicator(series, 0, numOf(0.3));

        String json = indicator.toJson();
        Indicator<?> restored = Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(PriceClusterResistanceIndicator.class);
        var restoredIndicator = (PriceClusterResistanceIndicator) restored;
        assertThat(restoredIndicator.toDescriptor()).isEqualTo(indicator.toDescriptor());
        int index = series.getEndIndex();
        assertThat(restoredIndicator.getValue(index)).isEqualByComparingTo(indicator.getValue(index));
        assertThat(restoredIndicator.getClusterIndex(index)).isEqualTo(indicator.getClusterIndex(index));
    }

    private BarSeries buildSeries(double[] closes, double[] volumes) {
        var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        BarSeries barSeries = builder.build();
        for (int i = 0; i < closes.length; i++) {
            barSeries.barBuilder()
                    .closePrice(closes[i])
                    .openPrice(closes[i])
                    .highPrice(closes[i])
                    .lowPrice(closes[i])
                    .volume(volumes[i])
                    .add();
        }
        return barSeries;
    }
}
