/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.List;
import java.util.Objects;

/**
 * Package-private canonical description of one supported built-in Monte Carlo
 * method graph node.
 *
 * <p>
 * A description is the internal, accelerator-neutral representation of a
 * {@link MonteCarloMethod} graph: a primitive {@link #type()}, an integer
 * {@link #version()} that binds the node's observable semantics including its
 * random-number consumption pattern, an ordered {@link #parameters()} list of
 * exact primitive values, and the ordered {@link #children()} of decorator
 * nodes. Two descriptions are equal exactly when their canonical identity
 * strings are equal, so parameter, nesting, ordering, or version changes always
 * produce a different identity.
 *
 * <p>
 * The description carries no {@code MonteCarloMethod} objects, concrete
 * classes, or ta4j domain values; parameters are {@code Integer},
 * {@code Double}, {@code Boolean}, or {@code String} values only. This keeps
 * the future operation-level request primitive enough for native providers to
 * consume without ever inspecting the method graph itself.
 *
 * <p>
 * This surface is internal on purpose: it is the seed of the operation-level
 * ABI for native acceleration and stays package-private until at least two
 * structurally different accelerated operations prove it.
 */
final class MonteCarloOperation {

    private final String type;
    private final int version;
    private final List<Object> parameters;
    private final List<MonteCarloOperation> children;
    private final String canonicalId;

    /**
     * Creates a canonical description node.
     *
     * @param type       primitive operation type key, not {@code null}
     * @param version    operation version, must be &gt;= 1; the version binds the
     *                   node's RNG-consumption and split semantics
     * @param parameters ordered exact parameter values ({@code Integer},
     *                   {@code Double}, {@code Boolean}, or {@code String}); empty
     *                   for parameterless nodes
     * @param children   ordered child descriptions; empty for leaf nodes
     */
    MonteCarloOperation(String type, int version, List<Object> parameters, List<MonteCarloOperation> children) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        this.version = version;
        this.parameters = List.copyOf(parameters);
        this.children = List.copyOf(children);
        this.canonicalId = buildCanonicalId();
    }

    /**
     * Operation type key of this node.
     *
     * @return the primitive type key
     */
    String type() {
        return type;
    }

    /**
     * Operation version binding this node's observable semantics.
     *
     * @return the version
     */
    int version() {
        return version;
    }

    /**
     * Ordered exact parameter values ({@code Integer}, {@code Double},
     * {@code Boolean}, or {@code String}).
     *
     * @return an immutable ordered parameter list
     */
    List<Object> parameters() {
        return parameters;
    }

    /**
     * Ordered child descriptions; empty for leaf nodes.
     *
     * @return an immutable ordered child list
     */
    List<MonteCarloOperation> children() {
        return children;
    }

    /**
     * Stable canonical identity string. Equivalent supported built-in graphs always
     * produce the same identity; any parameter, nesting, ordering, version, or
     * split-semantic change produces a different one.
     *
     * @return the canonical identity
     */
    String canonicalId() {
        return canonicalId;
    }

    private String buildCanonicalId() {
        StringBuilder builder = new StringBuilder();
        builder.append(type).append("@v").append(version).append('(');
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(canonicalParameter(parameters.get(i)));
        }
        builder.append(")[");
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(children.get(i).canonicalId());
        }
        return builder.append(']').toString();
    }

    private static String canonicalParameter(Object parameter) {
        if (parameter instanceof Double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("parameters must be finite");
            }
            // Exact bit-level encoding so distinct doubles never collapse.
            return "0x" + Long.toHexString(Double.doubleToLongBits(value));
        }
        if (parameter instanceof Integer || parameter instanceof Boolean) {
            return parameter.toString();
        }
        if (parameter instanceof String text) {
            if (text.isEmpty() || text.indexOf(',') >= 0 || text.indexOf('(') >= 0 || text.indexOf(')') >= 0
                    || text.indexOf('[') >= 0 || text.indexOf(']') >= 0) {
                throw new IllegalArgumentException("string parameters must not contain identity delimiters");
            }
            return text;
        }
        throw new IllegalArgumentException("unsupported parameter type: " + parameter.getClass().getName());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MonteCarloOperation operation && canonicalId.equals(operation.canonicalId);
    }

    @Override
    public int hashCode() {
        return canonicalId.hashCode();
    }

    @Override
    public String toString() {
        return canonicalId;
    }
}
