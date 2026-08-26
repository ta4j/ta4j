/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Reproducible Elliott Wave study execution and robustness analysis.
 *
 * <p>
 * Study runners orchestrate the swing, topology, and relationship-rule stages
 * over locked partitions. Reports, confirmation tracking, block-bootstrap
 * nulls, and detector-robustness matrices remain together here because they
 * describe study execution rather than one analysis stage.
 * </p>
 */
package org.ta4j.core.analysis.elliott.study;
