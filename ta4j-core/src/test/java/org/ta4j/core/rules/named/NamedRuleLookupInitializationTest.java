/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.named;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Locks the lookup contract: {@link NamedRule#lookup(String)} must resolve
 * rules registered through the default package scan without requiring a prior
 * {@link NamedRule#initializeRegistry(String...)} or
 * {@link NamedRule#requireRegistered(String)} call.
 *
 * <p>
 * {@code ScanOnlyProbeRule} lives in the default scan package but is never
 * referenced by this test, so only the scan can register it. After a full
 * registry reset plus an explicit unregister, {@code lookup} is the first
 * API call and must still resolve the rule — exactly like
 * {@code requireRegistered} does.
 */
public class NamedRuleLookupInitializationTest {

    @Test
    public void lookupResolvesDefaultScannedRulesWithoutPriorInitialization() {
        NamedRule.resetRegistryStateForTests();
        NamedRule.unregisterImplementation(ScanOnlyProbeRule.class);

        assertTrue("lookup must initialize the default registry and resolve scanned rules",
                NamedRule.lookup("ScanOnlyProbeRule").isPresent());
    }

    @Test
    public void lookupKeepsReturningEmptyForUnknownRules() {
        NamedRule.resetRegistryStateForTests();

        assertTrue("lookup of an unknown rule must stay empty", NamedRule.lookup("DefinitelyUnknownRule").isEmpty());
    }
}
