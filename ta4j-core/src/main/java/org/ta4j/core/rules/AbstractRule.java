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
package org.ta4j.core.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Rule;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

/**
 * An abstract trading {@link Rule rule}.
 */
public abstract class AbstractRule implements Rule {

    /** The logger */
    protected final transient Logger log = LoggerFactory.getLogger(getClass());

    /** The class name */
    private final String className = getClass().getSimpleName();

    /** Configurable display name */
    private String name;

    /**
     * Traces the {@code isSatisfied()} method calls.
     *
     * @param index       the bar index
     * @param isSatisfied true if the rule is satisfied, false otherwise
     */
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        if (log.isTraceEnabled()) {
            log.trace("{}#isSatisfied({}): {}", className, index, isSatisfied);
        }
    }

    @Override
    public void setName(String name) {
        this.name = name == null || name.isBlank() ? null : name;
    }

    @Override
    public String getName() {
        return name != null ? name : createDefaultName();
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Creates the default JSON representation for the rule name. Sub-classes can
     * override to provide richer metadata.
     *
     * @return JSON payload describing the rule
     */
    protected String createDefaultName() {
        return createTypeOnlyName(className);
    }

    /**
     * Builds a JSON object containing only the rule type field.
     *
     * @param type rule type label
     * @return JSON string
     */
    protected String createTypeOnlyName(String type) {
        return ComponentSerialization.toJson(ComponentDescriptor.typeOnly(type));
    }

    /**
     * Builds a JSON object containing the type plus an optional array of child rule
     * names.
     *
     * @param type       rule type label
     * @param childNames child rule display names
     * @return JSON string
     */
    protected String createCompositeName(String type, String... childNames) {
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder().withType(type);
        if (childNames != null && childNames.length > 0) {
            for (String child : childNames) {
                if (child == null) {
                    builder.addChild(null);
                } else {
                    builder.addChild(ComponentSerialization.parse(child));
                }
            }
        }
        return ComponentSerialization.toJson(builder.build());
    }
}
