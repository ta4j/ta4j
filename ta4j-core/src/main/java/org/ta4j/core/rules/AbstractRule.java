/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Rule;

/**
 * An abstract trading {@link Rule rule}.
 */
public abstract class AbstractRule implements Rule {

    /** The logger */
    protected final transient Logger log = LoggerFactory.getLogger(getClass());

    /** The class name */
    private final String className = getClass().getSimpleName();

    /** Configurable display name */
    private volatile String name;

    /**
     * Returns the display name to use in trace logs. Uses the configured name if
     * set, otherwise falls back to the class name.
     *
     * @return display name for tracing
     */
    protected String getTraceDisplayName() {
        return name != null ? name : className;
    }

    /**
     * Traces the {@code isSatisfied()} method calls.
     *
     * @param index       the bar index
     * @param isSatisfied true if the rule is satisfied, false otherwise
     */
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        if (log.isTraceEnabled()) {
            log.trace("{}#isSatisfied({}): {}", getTraceDisplayName(), index, isSatisfied);
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

    /**
     * @return true if a custom name was assigned via {@link #setName(String)}
     */
    public boolean hasCustomName() {
        return name != null;
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
        return className;
    }

    /**
     * Builds a JSON object containing the type plus child rule descriptors without
     * re-parsing already cached rule names.
     *
     * @param type     rule type label
     * @param children child rules
     * @return JSON string
     */
    protected String createCompositeName(String type, Rule... children) {
        StringBuilder builder = new StringBuilder(type);
        if (children != null && children.length > 0) {
            builder.append('(');
            for (int i = 0; i < children.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                if (children[i] == null) {
                    builder.append("null");
                } else {
                    builder.append(children[i].getName());
                }
            }
            builder.append(')');
        }
        return builder.toString();
    }
}
