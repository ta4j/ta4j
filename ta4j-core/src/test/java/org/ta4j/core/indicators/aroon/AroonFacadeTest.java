/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.aroon;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;
import org.ta4j.core.BarSeries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.indicators.IndicatorUtils.isSameSeries;

import java.time.Duration;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AroonFacadeTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BaseBarSeries data;

    public AroonFacadeTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void init() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withName("Aroon data").build();
        Instant now = Instant.now();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(1)))
                .openPrice(168.28)
                .closePrice(169.64)
                .highPrice(169.87)
                .lowPrice(167.15)
                .volume(0)
                .add();

        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(2))))
                .openPrice(168.84)
                .closePrice(168.71)
                .highPrice(169.36)
                .lowPrice(168.20)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(3))))
                .openPrice(168.88)
                .closePrice(167.74)
                .highPrice(169.29)
                .lowPrice(166.41)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(4))))
                .openPrice(168.00)
                .closePrice(166.32)
                .highPrice(168.38)
                .lowPrice(166.18)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(5))))
                .openPrice(166.89)
                .closePrice(167.24)
                .highPrice(167.70)
                .lowPrice(166.33)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(6))))
                .openPrice(165.25)
                .closePrice(168.05)
                .highPrice(168.43)
                .lowPrice(165.00)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(7))))
                .openPrice(168.17)
                .closePrice(169.92)
                .highPrice(170.18)
                .lowPrice(167.63)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(8))))
                .openPrice(170.42)
                .closePrice(171.97)
                .highPrice(172.15)
                .lowPrice(170.06)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(9)))
                .openPrice(172.41)
                .closePrice(172.02)
                .highPrice(172.92)
                .lowPrice(171.31)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(10)))
                .openPrice(171.2)
                .closePrice(170.72)
                .highPrice(172.39)
                .lowPrice(169.55)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(11)))
                .openPrice(170.91)
                .closePrice(172.09)
                .highPrice(172.48)
                .lowPrice(169.57)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(12)))
                .openPrice(171.8)
                .closePrice(173.21)
                .highPrice(173.31)
                .lowPrice(170.27)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(13)))
                .openPrice(173.09)
                .closePrice(170.95)
                .highPrice(173.49)
                .lowPrice(170.80)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(14)))
                .openPrice(172.41)
                .closePrice(173.51)
                .highPrice(173.89)
                .lowPrice(172.20)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(15)))
                .openPrice(173.87)
                .closePrice(174.17)
                .highPrice(175)
                .lowPrice(172.96)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(16)))
                .openPrice(173)
                .closePrice(173.05)
                .highPrice(173.17)
                .lowPrice(172.06)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(17)))
                .openPrice(172.26)
                .closePrice(170.96)
                .highPrice(172.28)
                .lowPrice(170.50)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(18)))
                .openPrice(170.88)
                .closePrice(171.64)
                .highPrice(172.34)
                .lowPrice(170.26)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(19)))
                .openPrice(171.85)
                .closePrice(170.01)
                .highPrice(172.07)
                .lowPrice(169.34)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(20)))
                .openPrice(170.75)
                .closePrice(172.52)
                .highPrice(172.56)
                .lowPrice(170.36)
                .volume(0)
                .add(); // FB, daily, 9.19.'17

    }

    @Test
    public void testCreation() {
        final AroonFacade facade = new AroonFacade(data, 5);
        assertTrue(isSameSeries(data, facade.down().getBarSeries()));
    }

    @Test
    public void testNumericFacadesSameAsDefaultIndicators() {
        final AroonDownIndicator aroonDownIndicator = new AroonDownIndicator(data, 5);
        final AroonUpIndicator aroonUpIndicator = new AroonUpIndicator(data, 5);
        final AroonOscillatorIndicator aroonOscillatorIndicator = new AroonOscillatorIndicator(data, 5);

        final AroonFacade facade = new AroonFacade(data, 5);
        final NumericIndicator aroonUpNumeric = facade.up();
        final NumericIndicator aroonDownNumeric = facade.down();
        final NumericIndicator oscillatorNumeric = facade.oscillator();

        for (int i = data.getBeginIndex(); i <= data.getEndIndex(); i++) {
            assertNumEquals(aroonDownIndicator.getValue(i), aroonDownNumeric.getValue(i));
            assertNumEquals(aroonUpIndicator.getValue(i), aroonUpNumeric.getValue(i));
            assertNumEquals(aroonOscillatorIndicator.getValue(i), oscillatorNumeric.getValue(i));
        }
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new AroonFacade(series, 8).up(), stableIndexes(series)),
                serializationFixture(series, new AroonFacade(series, 8).down(), stableIndexes(series)),
                serializationFixture(series, new AroonFacade(series, 8).oscillator(), stableIndexes(series)));
    }

}
