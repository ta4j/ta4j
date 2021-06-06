package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.REXIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

/**
 * Data source from MT5, EUR/USD, daily chart, from 1 Apr 2021 to 21 Apr 2021
 */
public class REXIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public REXIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {

        final List<Bar> bars = new ArrayList<>();

        bars.add(new MockBar(1.17298, 1.17759, 1.17799, 1.17124, numFunction)); //1
        bars.add(new MockBar(1.17760, 1.17608, 1.17864, 1.17488, numFunction)); //2
        bars.add(new MockBar(1.17543, 1.18125, 1.18196, 1.17382, numFunction)); //5
        bars.add(new MockBar(1.18125, 1.18740, 1.18776, 1.17951, numFunction)); //6
        bars.add(new MockBar(1.18730, 1.18668, 1.19147, 1.18609, numFunction)); //7
        bars.add(new MockBar(1.18666, 1.19124, 1.19273, 1.18605, numFunction)); //8
        bars.add(new MockBar(1.19121, 1.19010, 1.19202, 1.18672, numFunction)); //9
        bars.add(new MockBar(1.18881, 1.19099, 1.19190, 1.18712, numFunction)); //12
        bars.add(new MockBar(1.19093, 1.19474, 1.19560, 1.18775, numFunction)); //13
        bars.add(new MockBar(1.19478, 1.19784, 1.19874, 1.19459, numFunction)); //14
        bars.add(new MockBar(1.19781, 1.19655, 1.19933, 1.19561, numFunction)); //15
        bars.add(new MockBar(1.19654, 1.19809, 1.19949, 1.19504, numFunction)); //16
        bars.add(new MockBar(1.19716, 1.20367, 1.20481, 1.19426, numFunction)); //19
        bars.add(new MockBar(1.20364, 1.20343, 1.20798, 1.20224, numFunction)); //20
        bars.add(new MockBar(1.20343, 1.20343, 1.20436, 1.19984, numFunction)); //21

        data = new BaseBarSeries(bars);
    }

    @Test
    public void rexIndicatorUsingBarCount5UsingClosePrice() {
        final REXIndicator rexIndicator = new REXIndicator(data, 5, 5);
        final SMAIndicator signal = rexIndicator.getSignalIndicator();
        final SMAIndicator rex = rexIndicator.getRexIndicator();

        assertNumEquals(0.00582, rex.getValue(4));
        assertNumEquals(0.00536, rex.getValue(5));
        assertNumEquals(0.00601, rex.getValue(6));
        assertNumEquals(0.00453, rex.getValue(7));
        assertNumEquals(0.00378, rex.getValue(8));
        assertNumEquals(0.00582, rex.getValue(9));
        assertNumEquals(0.00355, rex.getValue(10));

        assertNumEquals(0.00510, signal.getValue(8));
        assertNumEquals(0.00510, signal.getValue(9));
        assertNumEquals(0.00474, signal.getValue(10));
        assertNumEquals(0.00436, signal.getValue(11));
        assertNumEquals(0.00466, signal.getValue(12));
        assertNumEquals(0.00458, signal.getValue(13));
        assertNumEquals(0.00397, signal.getValue(14));

    }

    @Test
    public void rexIndicatorUsingBarCount8And10UsingClosePrice() {
        final REXIndicator rexIndicator = new REXIndicator(data, 8, 9);
        final SMAIndicator signal = rexIndicator.getSignalIndicator();
        final SMAIndicator rex = rexIndicator.getRexIndicator();

        assertNumEquals(0.00536, rex.getValue(7));
        assertNumEquals(0.00528, rex.getValue(8));
        assertNumEquals(0.00631, rex.getValue(9));
        assertNumEquals(0.00436, rex.getValue(10));
        assertNumEquals(0.00305, rex.getValue(11));
        assertNumEquals(0.00550, rex.getValue(12));
        assertNumEquals(0.00402, rex.getValue(13));
        assertNumEquals(0.00431, rex.getValue(14));

        assertNumEquals(0.00478, signal.getValue(14));

    }

}
