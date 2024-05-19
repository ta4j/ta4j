package org.ta4j.core.utils;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

public class CircularNumArrayTest {

  private CircularArray<Num> array;


  @Before
  public void setUp() {
    this.array = new CircularNumArray(3);
  }


  @Test
  public void capacity() {
    assertEquals(3, this.array.capacity());
  }


  @Test
  public void get() {
    assertEquals(NaN, this.array.get(0));
    assertEquals(NaN, this.array.get(1));
    assertEquals(NaN, this.array.get(3));
  }


  @Test
  public void set() {
    this.array.addLast(DecimalNumFactory.getInstance().one());
    this.array.addLast(DecimalNumFactory.getInstance().two());
    this.array.addLast(DecimalNumFactory.getInstance().three());

    assertEquals(DecimalNumFactory.getInstance().one(), this.array.get(0));
    assertEquals(DecimalNumFactory.getInstance().two(), this.array.get(1));
    assertEquals(DecimalNumFactory.getInstance().three(), this.array.get(2));

    this.array.addLast(DecimalNumFactory.getInstance().thousand());
    assertEquals(DecimalNumFactory.getInstance().thousand(), this.array.get(0));
    assertEquals(DecimalNumFactory.getInstance().two(), this.array.get(1));
    assertEquals(DecimalNumFactory.getInstance().three(), this.array.get(2));

    this.array.addLast(DecimalNumFactory.getInstance().minusOne());
    assertEquals(DecimalNumFactory.getInstance().thousand(), this.array.get(0));
    assertEquals(DecimalNumFactory.getInstance().minusOne(), this.array.get(1));
    assertEquals(DecimalNumFactory.getInstance().three(), this.array.get(2));

    this.array.addLast(DecimalNumFactory.getInstance().hundred());
    assertEquals(DecimalNumFactory.getInstance().thousand(), this.array.get(0));
    assertEquals(DecimalNumFactory.getInstance().minusOne(), this.array.get(1));
    assertEquals(DecimalNumFactory.getInstance().hundred(), this.array.get(2));
  }
}
