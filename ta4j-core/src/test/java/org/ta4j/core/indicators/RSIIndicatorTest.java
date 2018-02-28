/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.GainIndicator;
import org.ta4j.core.indicators.helpers.LossIndicator;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.TestUtils.assertNumMatches;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

public class RSIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TimeSeries data;
    private ExternalIndicatorTest xls;
    //private ExternalIndicatorTest sql;

    public RSIIndicatorTest(Function<Number, Num> numFunction) {
        super((data, params) -> new RSIIndicator((Indicator<Num>) data, (int) params[0]), numFunction);
        xls = new XLSIndicatorTest(this.getClass(), "RSI.xls", 10, numFunction);
        //sql = new SQLIndicatorTest(this.getClass(), "RSI.db", username, pass, table, column);
    }

    @Before
    public void setUp() throws Exception {
        data = new MockTimeSeries(numFunction,
                50.45, 50.30, 50.20,
                50.15, 50.05, 50.06,
                50.10, 50.08, 50.03,
                50.07, 50.01, 50.14,
                50.22, 50.43, 50.50,
                50.56, 50.52, 50.70,
                50.55, 50.62, 50.90,
                50.82, 50.86, 51.20,
                51.30, 51.10);
    }

    @Test
    public void firstValueShouldBeZero() throws Exception {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 14);
        assertNumEquals(0, indicator.getValue(0));
    }

    @Test
    public void hundredIfNoLoss() throws Exception {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(100, indicator.getValue(14));
        assertNumEquals(100, indicator.getValue(15));
    }

    @Test
    public void zeroIfNoGain() throws Exception {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(0, indicator.getValue(1));
        assertNumEquals(0, indicator.getValue(2));
    }

    @Test
    public void testUsingTimeFrame14UsingClosePricePass() {
        // find the maximum precision of the current Num class (up to 64 digits)
        // create 64 digit precision BigDecimalNum,
        // then transform it with the current numOf()
        // then transform it back into BigDecimalNum
        // if numOf() is DoubleNum.valueOf() then precision will be 16
        // if numOf() is BigDecimalNum.valueOf() then precision will be BigDecimalNum default (32)
        Num num = BigDecimalNum.valueOf(numOf(BigDecimalNum.valueOf("68.47467140686891745891139277307765935855946901587159762304918549", 64).getDelegate()).toString());
        int precision = ((BigDecimal) num.getDelegate()).precision();
        // our 32 digit calculations will be differ slightly from the first 32 digits of the 64 digit expected result 
        // so bump down the precision just a little to get a pass
        if (precision <= 16)
            precision -= 3; // DoubleNum propagates far more error so it has to bump down 3 digits for this test data
        else
            precision -= 1; // BigDecimalNum is so precise it only has to bump down 1
        usingTimeFrame14UsingClosePrice(precision);
    }

    @Test(expected = AssertionError.class)
    public void testUsingTimeFrame14UsingClosePriceFail() {
        Num num = BigDecimalNum.valueOf(numOf(BigDecimalNum.valueOf("68.47467140686891745891139277307765935855946901587159762304918549", 64).getDelegate()).toString());
        int precision = ((BigDecimal) num.getDelegate()).precision();
        // our 32 digit calculations will not match the first 32 digits of the 64 digit expected results
        // and our 16 digit calculations will not match the first 16 digits of the 64 digit expected results
        usingTimeFrame14UsingClosePrice(precision);
    }

    public void usingTimeFrame14UsingClosePrice(int precision) {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 14);
        assertNumMatches("68.47467140686891745891139277307765935855946901587159762304918549", indicator.getValue(15), precision);
        assertNumMatches("64.78361407616318060500150810244389574498371890364077611771330462", indicator.getValue(16), precision);
        assertNumMatches("72.07767796184254162132503853908729204188471901054560573155102940", indicator.getValue(17), precision);
        assertNumMatches("60.78000613652222875621591049003085035009611219640159711280821773", indicator.getValue(18), precision);
        assertNumMatches("63.64390001766678277442060892989089954716303111479626309796498855", indicator.getValue(19), precision);
        assertNumMatches("72.34337823720912781333178318202597949386893319906949927429396455", indicator.getValue(20), precision);
        assertNumMatches("67.38227542194746461101622925834558588616620256794318584501247184", indicator.getValue(21), precision);
        assertNumMatches("68.54383090897891183125622285563568410156452400468031044777754703", indicator.getValue(22), precision);
        assertNumMatches("76.27702700480215362448227687687201456306087413243254458111073709", indicator.getValue(23), precision);
        assertNumMatches("77.99083631939523394885491528464062999046196925343149284775230497", indicator.getValue(24), precision);
        assertNumMatches("67.48950614025902300618871095448921619512018125813311760441542342", indicator.getValue(25), precision);
    }

    @Test
    public void xlsTest() throws Exception {
        Indicator<Num> xlsClose = new ClosePriceIndicator(xls.getSeries());
        Indicator<Num> indicator;

        indicator = getIndicator(xlsClose, 1);
        assertIndicatorEquals(xls.getIndicator(1), indicator);
        assertNumEquals("100", indicator.getValue(indicator.getTimeSeries().getEndIndex()));

        indicator = getIndicator(xlsClose, 3);
        assertIndicatorEquals(xls.getIndicator(3), indicator);
        assertNumEquals("67.04537458239054018366892427448437610204731551106392023415107574", indicator.getValue(indicator.getTimeSeries().getEndIndex()));

        indicator = getIndicator(xlsClose, 13);
        assertIndicatorEquals(xls.getIndicator(13), indicator);
        assertNumEquals("52.58768185890309760191197582379221951878504634897434288638106563", indicator.getValue(indicator.getTimeSeries().getEndIndex()));
    }

    @Test
    public void onlineExampleTest() throws Exception {
        // from http://cns.bu.edu/~gsc/CN710/fincast/Technical%20_indicators/Relative%20Strength%20Index%20(RSI).htm
        // which uses a different calculation of RSI than ta4j
        TimeSeries series = new MockTimeSeries(numFunction,
                46.1250,
                47.1250, 46.4375, 46.9375, 44.9375, 44.2500, 44.6250, 45.7500,
                47.8125, 47.5625, 47.0000, 44.5625, 46.3125, 47.6875, 46.6875,
                45.6875, 43.0625, 43.5625, 44.8750, 43.6875);
        // ta4j RSI uses MMA for average gain and loss
        // then uses simple division of the two for RS
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(
                series), 14);
        Indicator<Num> close = new ClosePriceIndicator(series);
        Indicator<Num> gain = new GainIndicator(close);
        Indicator<Num> loss = new LossIndicator(close);
        // this site uses SMA for average gain and loss
        // then uses ratio of MMAs for RS (except for first calculation)
        Indicator<Num> avgGain = new SMAIndicator(gain, 14);
        Indicator<Num> avgLoss = new SMAIndicator(loss, 14);

        // first online calculation is simple division
        Num onlineRs = avgGain.getValue(14).dividedBy(avgLoss.getValue(14));
        assertNumEquals("0.58482142857142857142857142857143", avgGain.getValue(14));
        assertNumEquals("0.54464285714285714285714285714286", avgLoss.getValue(14));
        assertNumEquals("1.0737704918032786885245901639344", onlineRs);
        Num onlineRsi = numFunction.apply(100d).minus(numFunction.apply(100d).dividedBy(onlineRs.plus(numFunction.apply(1d))));
        // difference in RSI values:
        assertNumEquals("51.778656126482213438735177865612", onlineRsi);
        assertNumEquals("52.130477585417047356473650689120", indicator.getValue(14));

        // strange, online average gain and loss is not a simple moving average!
        // but they only use them for the first RS calculation
        // assertEquals(0.5430, avgGain.getValue(15).doubleValue(), TATestsUtils.GENERAL_OFFSET);
        // assertEquals(0.5772, avgLoss.getValue(15).doubleValue(), TATestsUtils.GENERAL_OFFSET);
        // second online calculation uses MMAs
        // MMA of average gain
        Num dividend = avgGain.getValue(14).multipliedBy(series.numOf(13)).plus(gain.getValue(15)).dividedBy(series.numOf(14));
        // MMA of average loss
        Num divisor = avgLoss.getValue(14).multipliedBy(series.numOf(13)).plus(loss.getValue(15)).dividedBy(series.numOf(14));
        onlineRs = dividend.dividedBy(divisor);
        assertNumEquals("0.94088397790055248618784530386739", onlineRs);
        onlineRsi = numFunction.apply(100d).minus(numFunction.apply(100d).dividedBy((onlineRs.plus(numFunction.apply(1d)))));
        // difference in RSI values:
        assertNumEquals("48.477085112439510389980074010817", onlineRsi);
        assertNumEquals("47.371031400457402995260283818450", indicator.getValue(15));
    }
}
