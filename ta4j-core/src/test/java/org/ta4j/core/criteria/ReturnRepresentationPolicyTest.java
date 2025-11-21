/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package org.ta4j.core.criteria;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class ReturnRepresentationPolicyTest {

    @Test
    public void useOverridesDefaultRepresentation() {
        var original = ReturnRepresentationPolicy.getDefaultRepresentation();
        try {
            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.RATE_OF_RETURN);
            assertSame(ReturnRepresentation.RATE_OF_RETURN, ReturnRepresentationPolicy.getDefaultRepresentation());

            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.TOTAL_RETURN);
            assertSame(ReturnRepresentation.TOTAL_RETURN, ReturnRepresentationPolicy.getDefaultRepresentation());

            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.TOTAL_RETURN);
            assertSame(ReturnRepresentation.TOTAL_RETURN, ReturnRepresentationPolicy.getDefaultRepresentation());
        } finally {
            ReturnRepresentationPolicy.setDefaultRepresentation(original);
        }
    }

    @Test
    public void settingDefaultRepresentationToNullUsesDefault() {
        ReturnRepresentationPolicy.setDefaultRepresentation(null);
        assertSame(ReturnRepresentation.TOTAL_RETURN, ReturnRepresentationPolicy.getDefaultRepresentation());
    }

    @Test
    public void parseMatchesEnumNamesCaseInsensitive() {
        assertEquals(ReturnRepresentation.TOTAL_RETURN, ReturnRepresentation.parse("total_return"));
        assertEquals(ReturnRepresentation.RATE_OF_RETURN, ReturnRepresentation.parse("Rate_of_Return"));
        assertEquals(ReturnRepresentation.TOTAL_RETURN, ReturnRepresentation.parse("total return"));
        assertEquals(ReturnRepresentation.RATE_OF_RETURN, ReturnRepresentation.parse("rate-of-return"));
    }

    @Test
    public void parseHandlesExactEnumNames() {
        // Fast path: exact matches
        assertEquals(ReturnRepresentation.TOTAL_RETURN, ReturnRepresentation.parse("TOTAL_RETURN"));
        assertEquals(ReturnRepresentation.RATE_OF_RETURN, ReturnRepresentation.parse("RATE_OF_RETURN"));
    }

    @Test
    public void parseHandlesVariousFormats() {
        // Mixed separators and case
        assertEquals(ReturnRepresentation.TOTAL_RETURN, ReturnRepresentation.parse("Total Return"));
        assertEquals(ReturnRepresentation.RATE_OF_RETURN, ReturnRepresentation.parse("Rate Of Return"));
        assertEquals(ReturnRepresentation.TOTAL_RETURN, ReturnRepresentation.parse("total-return"));
        assertEquals(ReturnRepresentation.RATE_OF_RETURN, ReturnRepresentation.parse("RATE-OF-RETURN"));
        assertEquals(ReturnRepresentation.TOTAL_RETURN, ReturnRepresentation.parse("total___return")); // multiple
        // underscores
        assertEquals(ReturnRepresentation.RATE_OF_RETURN, ReturnRepresentation.parse("rate   of   return")); // multiple
        // spaces
    }

    @Test
    public void parseHandlesWhitespace() {
        assertEquals(ReturnRepresentation.TOTAL_RETURN, ReturnRepresentation.parse("  total_return  "));
        assertEquals(ReturnRepresentation.RATE_OF_RETURN, ReturnRepresentation.parse("\tRate Of Return\n"));
    }

    @Test
    public void parseReturnsNullOnInvalidName() {
        ReturnRepresentation result = ReturnRepresentation.parse("invalid_name");
        assertNull(result);
    }

    @Test
    public void parseReturnsNullOnEmptyString() {
        ReturnRepresentation result = ReturnRepresentation.parse("   ");
        assertNull(result);
    }

    @Test
    public void parseReturnsNullOnNull() {
        ReturnRepresentation result = ReturnRepresentation.parse(null);
        assertNull(result);
    }

    @Test
    public void parseErrorInStream() {
        ReturnRepresentation defaultRepresentation = Optional.ofNullable("GARBAGE IN")
                .map(ReturnRepresentation::parse)
                .orElse(ReturnRepresentation.TOTAL_RETURN);

        assertEquals(ReturnRepresentation.TOTAL_RETURN, defaultRepresentation);
    }
}
