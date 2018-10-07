package org.ta4j.core.trading.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Rule;

/**
 * An abstract trading {@link Rule rule}.
 */
public abstract class AbstractRule implements Rule {

    /** The logger */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** The class name */
    private final String className = getClass().getSimpleName();

    /**
     * Traces the isSatisfied() method calls.
     * @param index the bar index
     * @param isSatisfied true if the rule is satisfied, false otherwise
     */
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        log.trace("{}#isSatisfied({}): {}", className, index, isSatisfied);
    }
}
