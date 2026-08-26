/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Elliott Wave analysis support APIs.
 *
 * <p>
 * This package is the parent namespace for non-indicator Elliott support types.
 * Root-level types provide shared detector interfaces, configuration, filters,
 * and runner-facing helpers. Focused analysis stages live in the following
 * subpackages:
 * </p>
 * <ul>
 * <li>{@code swing} extracts causal swing pivots;</li>
 * <li>{@code topology} models and evaluates candidate wave grammars;</li>
 * <li>{@code rules} evaluates independently selectable relationship
 * evidence;</li>
 * <li>{@code study} runs reproducible studies, nulls, and robustness
 * analyses.</li>
 * </ul>
 *
 * <p>
 * First-class per-index Elliott outputs remain regular {@code *Indicator}
 * implementations under {@code org.ta4j.core.indicators.elliott}.
 * </p>
 */
package org.ta4j.core.analysis.elliott;
