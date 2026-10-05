/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Serialization support for indicators, rules, strategies, and analysis
 * criteria.
 *
 * <p>
 * Use this package to persist and restore composable ta4j components through
 * descriptor-based JSON contracts. Canonical descriptor JSON is the durable
 * storage form; named shorthand and strategy JSON v2 are compact authoring
 * layers that normalize back to descriptors before reconstruction.
 * </p>
 *
 * <p>
 * Descriptor parsing treats malformed JSON-looking payloads as syntax errors
 * while preserving plain-text labels for non-JSON inputs. Numeric constructor
 * parameters follow finite JSON-number rules with exact integer conversion.
 * </p>
 */
package org.ta4j.core.serialization;
