/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package eu.verdelhan.ta4j;

import static org.assertj.core.api.Assertions.*;
import org.assertj.core.data.Offset;

/**
 * Utility class for {@code TADecimal} tests.
 */
public class TADecimalTestsUtils {

    /** Offset for double equality checking */
    public static final Offset<Double> TA_OFFSET = Offset.offset(0.0001);

    /**
     * Verifies that the actual {@code TADecimal} value is equal to the given {@code String} representation.
     * @param actual the actual {@code TADecimal} value
     * @param expected the given {@code String} representation to compare the actual value to
     * @throws AssertionError if the actual value is not equal to the given {@code String} representation
     */
    public static void assertDecimalEquals(TADecimal actual, String expected) {
        assertThat(actual).isEqualTo(TADecimal.valueOf(expected));
    }

    /**
     * Verifies that the actual {@code TADecimal} value is equal to the given {@code Integer} representation.
     * @param actual the actual {@code TADecimal} value
     * @param expected the given {@code Integer} representation to compare the actual value to
     * @throws AssertionError if the actual value is not equal to the given {@code Integer} representation
     */
    public static void assertDecimalEquals(TADecimal actual, int expected) {
        assertThat(actual).isEqualTo(TADecimal.valueOf(expected));
    }

    /**
     * Verifies that the actual {@code TADecimal} value is equal (within a positive offset) to the given {@code Double} representation.
     * @param actual the actual {@code TADecimal} value
     * @param expected the given {@code Double} representation to compare the actual value to
     * @throws AssertionError if the actual value is not equal to the given {@code Double} representation
     */
    public static void assertDecimalEquals(TADecimal actual, double expected) {
        assertThat(actual.toDouble()).isEqualTo(expected, TA_OFFSET);
    }
}
