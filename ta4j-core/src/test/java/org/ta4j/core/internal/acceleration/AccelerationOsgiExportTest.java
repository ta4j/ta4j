/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.internal.acceleration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Locks the OSGi packaging contract of the newly-added public acceleration
 * API. {@link AccelerationRuntime} and its nested SPI types ({@code Provider},
 * {@code Request}, {@code Result}, {@code Backend}, {@code Status},
 * {@code Diagnostic}, {@code Scope}) are documented public API ({@code @since
 * 0.23.1}) implemented by provider artifacts such as ta4j-cli. The bundle must
 * therefore keep exporting the package that hosts them; a blanket
 * {@code !org.ta4j.core.internal.*} export exclusion hides the SPI from every
 * OSGi consumer and makes the documented extension contract unusable in OSGi.
 */
public class AccelerationOsgiExportTest {

    private static final String SPI_PACKAGE = "org.ta4j.core.internal.acceleration";

    @Test
    public void publicAccelerationSpiRemainsExportableFromTheOsgiBundle() throws Exception {
        Path pom = Path.of("pom.xml").toAbsolutePath();
        assertTrue("test must run from the ta4j-core module directory", Files.isRegularFile(pom));
        String xml = Files.readString(pom);
        Matcher matcher = Pattern.compile("-exportcontents:\\s*([^\\r\\n]*)").matcher(xml);
        assertTrue("ta4j-core pom must declare the bnd -exportcontents directive", matcher.find());
        String exportContents = matcher.group(1).trim();

        assertFalse("bnd -exportcontents must not blanket-exclude the public acceleration SPI package ("
                + SPI_PACKAGE + "): " + exportContents, exportContents.contains("!org.ta4j.core.internal.*"));

        // The package must still be covered by the include pattern so provider
        // bundles (ta4j-cli) can import it.
        assertTrue("bnd -exportcontents must include the public acceleration SPI package: " + exportContents,
                exportContents.contains("*org.ta4j.core*"));
    }
}
