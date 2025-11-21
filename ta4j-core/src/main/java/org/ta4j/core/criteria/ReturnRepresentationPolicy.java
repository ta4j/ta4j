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

import java.util.Objects;
import java.util.Optional;

/**
 * Holds the global default {@link ReturnRepresentation} used across Ta4j
 * criteria.
 *
 * <p>
 * The default can be changed at runtime to unify outputs across criteria and
 * backtests:
 *
 * <pre>
 * ReturnRepresentationPolicy.use(ReturnRepresentation.RATE_OF_RETURN);
 * </pre>
 *
 * A JVM-wide override is also supported via the system property
 * {@value #SYSTEM_PROPERTY}. Allowed values match the names of
 * {@link ReturnRepresentation}.
 */
public final class ReturnRepresentationPolicy {

    /** System property used to select the default representation. */
    public static final String SYSTEM_PROPERTY = "ta4j.returns.representation";

    private static volatile ReturnRepresentation defaultRepresentation = Optional
            .ofNullable(System.getProperty(SYSTEM_PROPERTY))
            .map(ReturnRepresentation::parse)
            .orElse(ReturnRepresentation.TOTAL_RETURN);

    private ReturnRepresentationPolicy() {
    }

    /**
     * @return the currently configured default representation
     */
    public static ReturnRepresentation getDefaultRepresentation() {
        return defaultRepresentation;
    }

    /**
     * Sets the JVM-wide default representation for return-based criteria.
     *
     * @param representation the representation to use
     */
    public static void use(ReturnRepresentation representation) {
        setDefaultRepresentation(representation);
    }

    /**
     * Sets the JVM-wide default representation for return-based criteria.
     *
     * @param representation the representation to use
     */
    public static void setDefaultRepresentation(ReturnRepresentation representation) {
        defaultRepresentation = Objects.requireNonNull(representation, "representation");
    }
}
