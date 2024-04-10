package org.ta4j.core.mocks;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

/**
 * @author Lukáš Kvídera
 */
public class MockBarSeriesBuilder extends BaseBarSeriesBuilder {

    private List<Double> data;
    private boolean defaultData;

    public MockBarSeriesBuilder withNumFactory(final NumFactory numFactory) {
        super.withNumFactory(numFactory);
        return this;
    }

    /**
     * Generates bars with given close prices.
     *
     * @param data close prices
     * @return this
     */
    public MockBarSeriesBuilder withData(final List<Double> data) {
        this.data = data;
        return this;
    }

    /**
     * Generates bars with given close prices.
     *
     * @param data close prices
     * @return this
     */
    public MockBarSeriesBuilder withData(final double... data) {
        withData(DoubleStream.of(data).boxed().collect(Collectors.toList()));
        return this;
    }

    private static void doublesToBars(final BarSeries series, final List<Double> data) {
        for (int i = 0; i < data.size(); i++) {
            series.barBuilder()
                    .endTime(ZonedDateTime.now().minusSeconds((data.size() + 1 - i)))
                    .closePrice(data.get(i))
                    .openPrice(0)
                    .add();
        }
    }

    public MockBarSeriesBuilder withDefaultData() {
        this.defaultData = true;
        return this;
    }

    private static void arbitraryBars(final BarSeries series) {
        for (double i = 0d; i < 5000; i++) {
            series.barBuilder()
                    .endTime(ZonedDateTime.now().minusMinutes((long) (5001 - i)))
                    .openPrice(i)
                    .closePrice(i + 1)
                    .highPrice(i + 2)
                    .lowPrice(i + 3)
                    .volume(i + 4)
                    .amount(i + 5)
                    .trades((int) (i + 6))
                    .add();
        }
    }

    @Override
    public BaseBarSeries build() {
        withBarBuilderFactory(new MockBarBuilderFactory());

        final var series = super.build();
        if (this.data != null) {
            doublesToBars(series, this.data);
        }
        if (this.defaultData) {
            arbitraryBars(series);
        }
        return series;
    }
}
