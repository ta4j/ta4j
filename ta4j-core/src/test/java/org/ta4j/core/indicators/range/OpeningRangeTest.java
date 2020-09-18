package org.ta4j.core.indicators.range;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import junit.framework.TestCase;

public class OpeningRangeTest extends TestCase {

  public void testGetBarsOfThePeriod() {
  }

  public void testGetDateFunction() {
    List<Function<ZonedDateTime, Integer>> functions =
        Arrays.asList(ZonedDateTime::getDayOfYear, ZonedDateTime::getYear);
    ZonedDateTime datetime = ZonedDateTime.now();
    ZonedDateTime datetime1 = ZonedDateTime.now();
    assertTrue(OpeningRange.barsInSamePeriod(datetime, datetime1, functions));
  }

  public void testBarsInSamePeriod() {
  }
}