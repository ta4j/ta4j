/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.forecast.KinematicKalmanForecastStateIndicator;
import org.ta4j.core.indicators.forecast.KinematicKalmanPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.state.KinematicKalmanForecastState;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.List;

public class KinematicKalmanFilterIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public KinematicKalmanFilterIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void exposesCorrectedPositionAndSeparateForecastApi() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 13, 16).build();
        Indicator<Num> source = new ClosePriceIndicator(series);
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(source);
        KinematicKalmanFilterIndicator filter = new KinematicKalmanFilterIndicator(state);

        KinematicKalmanForecastState lastState = state.getValue(3);
        KinematicKalmanPriceForecastIndicator oneBar = filter.forecast();
        KinematicKalmanPriceForecastIndicator threeBars = filter.forecast(3);

        assertEquals(lastState.position(), filter.getValue(3));
        assertEquals(state.getCountOfUnstableBars(), filter.getCountOfUnstableBars());
        assertEquals(1, oneBar.getHorizon());
        assertEquals(3, threeBars.getHorizon());
        assertEquals(lastState.position().plus(lastState.velocity()), oneBar.getValue(3).mean());
        assertEquals(lastState.position().plus(lastState.velocity().multipliedBy(numOf(3))),
                threeBars.getValue(3).mean());
    }

    @Test
    public void dynamicInvalidNoiseMakesSameBarFilterAndForecastUnavailable() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12).build();
        Indicator<Num> source = new ClosePriceIndicator(series);
        FixedIndicator<Num> processNoise = new FixedIndicator<>(series, numOf(1e-4), numFactory.zero(), numOf(1e-4));
        KinematicKalmanFilterIndicator filter = new KinematicKalmanFilterIndicator(source,
                new KalmanNoiseIndicator(processNoise), KalmanNoiseIndicator.constant(series, 1e-3));

        assertTrue(filter.getValue(1).isNaN());
        assertFalse(filter.forecast(2).getValue(1).isStable());
        assertTrue(filter.forecast(2).getValue(2).isStable());
    }

    @Test
    public void forecastRequiresPositiveHorizon() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10).build();
        KinematicKalmanFilterIndicator filter = new KinematicKalmanFilterIndicator(new ClosePriceIndicator(series));

        assertThrows(IllegalArgumentException.class, () -> filter.forecast(0));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12).build();
        return List.of(serializationFixture(series,
                new KinematicKalmanFilterIndicator(new ClosePriceIndicator(series), 0.01, 0.1)));
    }
}
