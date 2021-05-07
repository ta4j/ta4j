package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.num.NaN.NaN;

public class PositionBasedIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

  private BarSeries series;
  private BaseTradingRecord tradingRecord;
  private TestPositionIndicator positionIndicator;

  public PositionBasedIndicatorTest(Function<Number, Num> numFunction) {
    super(numFunction);
  }

  @Before
  public void setUp() {
    series = new MockBarSeries(numFunction);
    tradingRecord = new BaseTradingRecord(PositionBasedIndicatorTest.class.getSimpleName());
    positionIndicator = new TestPositionIndicator(series, tradingRecord);
  }

  @Test
  public void indicatorReturnNanIfNoTradeAvailable() {
    for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
      assertEquals(NaN, positionIndicator.getValue(index));
      assertNull(positionIndicator.lastCalledEntryPosition);
      assertNull(positionIndicator.lastCalledExitPosition);
      assertEquals(-1, positionIndicator.lastCalledExitIndex);
      assertEquals(-1, positionIndicator.lastCalledEntryIndex);
      positionIndicator.reset();
    }
  }

  private class TestPositionIndicator extends PositionBasedIndicator {
    int lastCalledEntryIndex;
    Position lastCalledEntryPosition;
    Num lastReturnedEntryNumber;

    int lastCalledExitIndex;
    Position lastCalledExitPosition;
    Num lastReturnedExitNumber;

    public TestPositionIndicator(BarSeries series, BaseTradingRecord tradingRecord) {
      super(series, tradingRecord);
      reset();
    }

    @Override
    Num calculateLastPositionWasEntry(Position entryPosition, int index) {
      lastCalledEntryPosition = entryPosition;
      lastCalledEntryIndex = index;
      lastReturnedEntryNumber = getBarSeries().numOf(Math.random());
      return lastReturnedEntryNumber;
    }

    @Override
    Num calculateLastPositionWasExit(Position exitPosition, int index) {
      lastCalledExitPosition = exitPosition;
      lastCalledExitIndex = index;
      lastReturnedExitNumber = getBarSeries().numOf(Math.random());
      return lastReturnedExitNumber;
    }

    void reset() {
      lastCalledEntryIndex = -1;
      lastCalledEntryPosition = null;
      lastReturnedEntryNumber = null;

      lastCalledExitIndex = -1;
      lastCalledExitPosition = null;
      lastReturnedExitNumber = null;
    }
  }
}
