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
            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.DECIMAL);
            assertSame(ReturnRepresentation.DECIMAL, ReturnRepresentationPolicy.getDefaultRepresentation());

            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.MULTIPLICATIVE);
            assertSame(ReturnRepresentation.MULTIPLICATIVE, ReturnRepresentationPolicy.getDefaultRepresentation());

            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.LOG);
            assertSame(ReturnRepresentation.LOG, ReturnRepresentationPolicy.getDefaultRepresentation());

            ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.MULTIPLICATIVE);
            assertSame(ReturnRepresentation.MULTIPLICATIVE, ReturnRepresentationPolicy.getDefaultRepresentation());
        } finally {
            ReturnRepresentationPolicy.setDefaultRepresentation(original);
        }
    }

    @Test
    public void settingDefaultRepresentationToNullUsesDefault() {
        ReturnRepresentationPolicy.setDefaultRepresentation(null);
        assertSame(ReturnRepresentation.MULTIPLICATIVE, ReturnRepresentationPolicy.getDefaultRepresentation());
    }

    @Test
    public void parseMatchesEnumNamesCaseInsensitive() {
        assertEquals(ReturnRepresentation.MULTIPLICATIVE, ReturnRepresentation.parse("multiplicative"));
        assertEquals(ReturnRepresentation.DECIMAL, ReturnRepresentation.parse("Decimal"));
        assertEquals(ReturnRepresentation.PERCENTAGE, ReturnRepresentation.parse("percentage"));
        assertEquals(ReturnRepresentation.LOG, ReturnRepresentation.parse("log"));
        assertEquals(ReturnRepresentation.MULTIPLICATIVE, ReturnRepresentation.parse("multiplicative"));
        assertEquals(ReturnRepresentation.DECIMAL, ReturnRepresentation.parse("decimal"));
        assertEquals(ReturnRepresentation.PERCENTAGE, ReturnRepresentation.parse("Percentage"));
        assertEquals(ReturnRepresentation.LOG, ReturnRepresentation.parse("Log"));
    }

    @Test
    public void parseHandlesExactEnumNames() {
        // Fast path: exact matches
        assertEquals(ReturnRepresentation.MULTIPLICATIVE, ReturnRepresentation.parse("MULTIPLICATIVE"));
        assertEquals(ReturnRepresentation.DECIMAL, ReturnRepresentation.parse("DECIMAL"));
        assertEquals(ReturnRepresentation.PERCENTAGE, ReturnRepresentation.parse("PERCENTAGE"));
        assertEquals(ReturnRepresentation.LOG, ReturnRepresentation.parse("LOG"));
    }

    @Test
    public void parseHandlesVariousFormats() {
        // Mixed separators and case
        assertEquals(ReturnRepresentation.MULTIPLICATIVE, ReturnRepresentation.parse("Multiplicative"));
        assertEquals(ReturnRepresentation.DECIMAL, ReturnRepresentation.parse("Decimal"));
        assertEquals(ReturnRepresentation.PERCENTAGE, ReturnRepresentation.parse("Percentage"));
        assertEquals(ReturnRepresentation.LOG, ReturnRepresentation.parse("Log"));
        assertEquals(ReturnRepresentation.MULTIPLICATIVE, ReturnRepresentation.parse("multiplicative"));
        assertEquals(ReturnRepresentation.DECIMAL, ReturnRepresentation.parse("DECIMAL"));
        assertEquals(ReturnRepresentation.PERCENTAGE, ReturnRepresentation.parse("PERCENTAGE"));
        assertEquals(ReturnRepresentation.LOG, ReturnRepresentation.parse("LOG"));
        assertEquals(ReturnRepresentation.MULTIPLICATIVE, ReturnRepresentation.parse("multiplicative")); // multiple
        // underscores
        assertEquals(ReturnRepresentation.DECIMAL, ReturnRepresentation.parse("decimal")); // multiple
        // spaces
        assertEquals(ReturnRepresentation.LOG, ReturnRepresentation.parse("log"));
    }

    @Test
    public void parseHandlesWhitespace() {
        assertEquals(ReturnRepresentation.MULTIPLICATIVE, ReturnRepresentation.parse("  MULTIPLICATIVE  "));
        assertEquals(ReturnRepresentation.DECIMAL, ReturnRepresentation.parse("\tDecimal\n"));
        assertEquals(ReturnRepresentation.PERCENTAGE, ReturnRepresentation.parse("  percentage  "));
        assertEquals(ReturnRepresentation.LOG, ReturnRepresentation.parse("  LOG  "));
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
                .orElse(ReturnRepresentation.MULTIPLICATIVE);

        assertEquals(ReturnRepresentation.MULTIPLICATIVE, defaultRepresentation);
    }
}
